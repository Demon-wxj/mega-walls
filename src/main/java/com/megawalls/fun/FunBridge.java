package com.megawalls.fun;

import com.megawalls.game.RegionProtection;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

/** Per-player bridge history. Placement and inventory consumption stay authoritative on the server. */
public final class FunBridge {
    private static final int MAX_PLACEMENTS = 12;
    private static final double MAX_STEP = 8;
    private static final double EPSILON = 1.0E-5;
    private Vec3d previousFeet;
    private RegistryKey<World> dimension;
    private int bridgeY;

    /** Disabling bridge, mounting, or changing player lifecycle must discard the old trajectory. */
    public void reset() {
        previousFeet = null;
        dimension = null;
    }

    public void tick(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || player.hasVehicle()) {
            reset();
            return;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d feet = player.getPos();
        Vec3d from = previousFeet;
        boolean discontinuity = from == null || dimension != world.getRegistryKey();
        if (!discontinuity) {
            double dx = feet.x - from.x, dz = feet.z - from.z, dy = feet.y - from.y;
            discontinuity = dx * dx + dz * dz > MAX_STEP * MAX_STEP || Math.abs(dy) > 3
                || player.getAbilities().flying && Math.abs(dy) > 0.05
                || feet.y < bridgeY + 0.70 || feet.y > bridgeY + 3.5;
        }
        // Refresh on genuine ground contact, not a jump's rising feet or an old on-ground packet.
        boolean supported = player.isOnGround()
            && world.getBlockCollisions(player, player.getBoundingBox().offset(0, -0.03, 0)).iterator().hasNext();
        if (discontinuity || supported) {
            // A whole block must end below the feet; a half-block surface must never embed the player.
            bridgeY = MathHelper.floor(feet.y + EPSILON) - 1;
        } else if (!player.getAbilities().flying && feet.y > from.y + .02
                && MathHelper.floor(feet.y + EPSILON) - 1 > bridgeY) {
            // A real jump may build a staircase/tower once a complete block fits below
            // the rising feet. Never predict a higher block while it intersects the body.
            bridgeY = MathHelper.floor(feet.y + EPSILON) - 1;
        }
        if (discontinuity) from = feet;
        previousFeet = feet;
        dimension = world.getRegistryKey();

        Hand hand = blockHand(player, world);
        if (hand == null) return;
        double dx = feet.x - from.x, dz = feet.z - from.z;
        double speed = Math.sqrt(dx * dx + dz * dz);
        Set<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(BlockPos.ofFloored(feet.x, bridgeY, feet.z));
        // Fill the next movement before the previous path: at high speed the next tick needs support now.
        if (speed > 0.01) {
            double lead = Math.min(4, Math.max(0.75, speed + 0.45));
            addPath(candidates, feet.x, feet.z, feet.x + dx / speed * lead, feet.z + dz / speed * lead);
        }
        addPath(candidates, from.x, from.z, feet.x, feet.z);
        int placed = 0;
        for (BlockPos pos : candidates) {
            // Select again when one hand's stack is exhausted; the other hand can continue the bridge.
            hand = blockHand(player, world);
            if (hand == null) break;
            if (place(player, world, hand, pos) && ++placed >= MAX_PLACEMENTS) break;
        }
    }

    private Hand blockHand(ServerPlayerEntity player, ServerWorld world) {
        for (Hand hand : Hand.values()) {
            var stack = player.getStackInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) continue;
            var block = item.getBlock();
            var state = block.getDefaultState();
            // Plants, containers, gravity blocks, and partial/multiblock placements cannot form this bridge.
            if (!(block instanceof FallingBlock) && !state.hasBlockEntity()
                    && Block.isShapeFullCube(state.getCollisionShape(world, player.getBlockPos()))) return hand;
        }
        return null;
    }

    private boolean place(ServerPlayerEntity player, ServerWorld world, Hand hand, BlockPos pos) {
        if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos) || world.isOutOfHeightLimit(pos)
                || RegionProtection.contains(world, pos) || !world.canPlayerModifyAt(player, pos)) return false;
        var before = world.getBlockState(pos);
        if (!before.isReplaceable()) return false;
        var stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item) || stack.isEmpty()
                || !player.canPlaceOn(pos, Direction.UP, stack)) return false;
        var hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5),
            Direction.UP, pos, false);
        var context = new ItemPlacementContext(player, hand, stack, hit);
        if (!context.getBlockPos().equals(pos) || !context.canPlace()) return false;
        var expected = item.getBlock().getPlacementState(context);
        if (expected == null || !Block.isShapeFullCube(expected.getCollisionShape(world, pos))
                || !expected.canPlaceAt(world, pos) || !world.canPlace(expected, pos, ShapeContext.of(player))) return false;
        // Honor the same region/minigame callbacks as a normal block use; some modes place in the callback.
        ActionResult result = UseBlockCallback.EVENT.invoker().interact(player, world, hand, hit);
        if (result == ActionResult.PASS) result = item.place(context);
        var after = world.getBlockState(pos);
        boolean success = result.isAccepted() && after != before && after.isOf(item.getBlock())
            && Block.isShapeFullCube(after.getCollisionShape(world, pos));
        if (success) player.swingHand(hand, true);
        return success;
    }

    /** Supercover of the crossed grid cells, including both sides of a diagonal corner. */
    private void addPath(Set<BlockPos> positions, double x0, double z0, double x1, double z1) {
        int x = MathHelper.floor(x0), z = MathHelper.floor(z0);
        int endX = MathHelper.floor(x1), endZ = MathHelper.floor(z1);
        positions.add(new BlockPos(x, bridgeY, z));
        double dx = x1 - x0, dz = z1 - z0;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dx);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dz);
        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? x + 1 : x) - x0) / dx;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? z + 1 : z) - z0) / dz;
        // Eight-block accepted movement plus four-block prediction fits well below this bound.
        for (int count = 0; (x != endX || z != endZ) && count < 32; count++) {
            // Do not advance an axis already at its endpoint, including a nearly diagonal border hit.
            double nextX = x == endX ? Double.POSITIVE_INFINITY : tMaxX;
            double nextZ = z == endZ ? Double.POSITIVE_INFINITY : tMaxZ;
            if (Math.abs(nextX - nextZ) < EPSILON) {
                positions.add(new BlockPos(x + stepX, bridgeY, z));
                positions.add(new BlockPos(x, bridgeY, z + stepZ));
                x += stepX;
                z += stepZ;
                tMaxX += tDeltaX;
                tMaxZ += tDeltaZ;
            } else if (nextX < nextZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            positions.add(new BlockPos(x, bridgeY, z));
        }
    }
}
