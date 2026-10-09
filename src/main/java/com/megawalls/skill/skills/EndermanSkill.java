package com.megawalls.skill.skills;

import com.megawalls.game.GameState;
import com.megawalls.skill.ClassBalanceRules;
import com.megawalls.skill.EnderRiftEffects;
import com.megawalls.skill.RestorationSkillRules;
import com.megawalls.skill.Skill;
import com.megawalls.skill.SkillEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Server-authoritative target choice: direct crosshair hit, then enemies in the view cone. */
public class EndermanSkill extends Skill {
    private static final double RANGE = 25;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final double MAX_AIM_MISS = 2;
    private static final double MIN_CONE_DOT = Math.cos(Math.toRadians(15));

    public EndermanSkill() {
        super("瞬移", "优先瞬移至准心命中的敌人；否则只选择偏离准心最多2格且在15度半角内的敌人；复制其位置与视角；超过10格穿墙时自身虚弱II 3秒；速度III 5秒", RestorationSkillRules.ENDERMAN_COOLDOWN);
    }

    @Override
    public boolean canExecute(ServerPlayerEntity player) {
        boolean found = destination(player) != null;
        if (!found) sendMessage(player, "§c面向的25格内没有可安全抵达的敌人，未消耗能量。");
        return found;
    }

    private record Destination(Vec3d position, float yaw, float pitch, boolean weakness) {}

    private Destination destination(ServerPlayerEntity player) {
        if (!GameState.insideArena(player, player.getBoundingBox())) return null;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1).normalize();
        Vec3d rayEnd = eye.add(look.multiply(RANGE));
        Destination bestDirect = null, bestCone = null;
        double directDistance = Double.POSITIVE_INFINITY, coneDot = MIN_CONE_DOT;
        double coneDistance = Double.POSITIVE_INFINITY;

        for (ServerPlayerEntity enemy : player.getServerWorld().getPlayers()) {
            if (!GameState.enemy(player, enemy) || player.squaredDistanceTo(enemy) > RANGE_SQUARED
                    || !GameState.insideArena(enemy, enemy.getBoundingBox())) continue;

            Vec3d target = enemy.getPos();
            var box = player.getBoundingBox().offset(target.subtract(player.getPos()));
            BlockPos feet = BlockPos.ofFloored(target);
            var world = player.getServerWorld();
            // Player overlap is intentional: the destination is the enemy's exact position.
            // Only block collisions should disqualify it, not the target player's collision box.
            if (!world.isChunkLoaded(feet) || !world.getWorldBorder().contains(box)
                    || !GameState.insideArena(player, box)
                    || world.getBlockCollisions(player, box).iterator().hasNext()
                    || !world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()
                    || world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty()
                    || world.getBlockState(feet).isIn(BlockTags.FIRE)) continue;

            Vec3d aimPoint = enemy.getBoundingBox().getCenter();
            Vec3d offset = aimPoint.subtract(eye);
            double length = offset.length();
            if (length <= 0.0001) continue;
            double dot = look.dotProduct(offset) / length;
            var directHit = enemy.getBoundingBox().raycast(eye, rayEnd);
            if (directHit.isEmpty() && (dot < MIN_CONE_DOT
                    || enemy.getBoundingBox().expand(MAX_AIM_MISS).raycast(eye, rayEnd).isEmpty())) continue;

            boolean obstructed = world.raycast(new RaycastContext(eye, aimPoint,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
                    .getType() != HitResult.Type.MISS;
            Destination candidate = new Destination(target, enemy.getYaw(), enemy.getPitch(),
                    ClassBalanceRules.teleportWeakness(player.squaredDistanceTo(enemy), obstructed));
            if (directHit.isPresent()) {
                double hitDistance = eye.squaredDistanceTo(directHit.get());
                if (hitDistance < directDistance) {
                    bestDirect = candidate;
                    directDistance = hitDistance;
                }
            } else if (dot > coneDot + 0.000001 || Math.abs(dot - coneDot) <= 0.000001 && length < coneDistance) {
                bestCone = candidate;
                coneDot = dot;
                coneDistance = length;
            }
        }
        return bestDirect != null ? bestDirect : bestCone;
    }

    @Override
    public void execute(ServerPlayerEntity player) {
        Destination safe = destination(player);
        if (safe == null) return;
        Vec3d origin = player.getPos();
        float originYaw = player.getYaw();
        player.teleport(player.getServerWorld(), safe.position().x, safe.position().y, safe.position().z,
                java.util.Set.of(), safe.yaw(), safe.pitch());
        player.setHeadYaw(safe.yaw());
        EnderRiftEffects.teleported(player, origin, originYaw);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 2));
        if (safe.weakness()) player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,
                ClassBalanceRules.ENDERMAN_WEAKNESS, 1));
        SkillEffects.sound(player, net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1, 1);
    }
}
