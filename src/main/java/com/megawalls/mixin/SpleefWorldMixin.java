package com.megawalls.mixin;

import com.megawalls.minigame.Spleef;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class SpleefWorldMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void spleef$change(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ServerWorld w&&!Spleef.allowChange(w,pos))cir.setReturnValue(false);}
    @Inject(method="breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void spleef$break(BlockPos pos,boolean drops,Entity entity,int depth,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ServerWorld w&&!Spleef.allowChange(w,pos))cir.setReturnValue(false);}
}
