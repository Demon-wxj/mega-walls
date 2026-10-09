package com.megawalls.mixin;
import com.megawalls.minigame.DuelArena;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(World.class)
public abstract class DuelWorldMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void duel$change(BlockPos p,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ServerWorld w&&(!DuelArena.allowChange(w,p)||!com.megawalls.minigame.BedArena.allowChange(w,p)||!com.megawalls.minigame.PotArena.allowChange(w,p)))cir.setReturnValue(false);}
}
