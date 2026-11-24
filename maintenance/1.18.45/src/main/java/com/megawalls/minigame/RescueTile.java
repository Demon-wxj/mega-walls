package com.megawalls.minigame;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlimeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A temporary tile with normal slime bounce, independent of ordinary slime blocks players place. */
public final class RescueTile extends SlimeBlock {
    public static Block BLOCK;

    private RescueTile() {
        super(Settings.copy(Blocks.SLIME_BLOCK).dropsNothing());
    }

    public static void register() {
        BLOCK = Registry.register(Registries.BLOCK, Identifier.of("megawalls", "rescue_tile"), new RescueTile());
    }

    @Override public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float distance) {
        if (!world.isClient && entity instanceof ServerPlayerEntity player && BedWars.fighter(player)) {
            entity.handleFallDamage(distance, ArenaRuleAccess.bed().rescueFall / 100f, world.getDamageSources().fall());
        } else {
            super.onLandedUpon(world, state, pos, entity, distance);
        }
    }
}
