package com.megawalls.mixin;

import com.megawalls.game.MatchTerrainJournal;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MWTerrainJournalMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void mw$recordTerrain(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){
        if((Object)this instanceof ServerWorld w&&!MatchTerrainJournal.beforeChange(w,pos,state))cir.setReturnValue(false);
    }
}
