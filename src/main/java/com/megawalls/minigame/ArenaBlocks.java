package com.megawalls.minigame;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;

/** Replaceable ground may host construction; thicker map snow remains original terrain. */
public final class ArenaBlocks {
    public static boolean replaceableGround(BlockState state){
        return state.isReplaceable()&&(!state.isOf(Blocks.SNOW)||state.get(SnowBlock.LAYERS)==1);
    }
}
