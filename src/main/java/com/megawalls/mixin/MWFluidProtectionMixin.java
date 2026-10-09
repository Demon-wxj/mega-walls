package com.megawalls.mixin;
import com.megawalls.game.RegionProtection;
import net.minecraft.fluid.*;
import net.minecraft.block.BlockState;
import net.minecraft.world.WorldAccess;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FlowableFluid.class)
public abstract class MWFluidProtectionMixin {
    @Inject(method="flow",at=@At("HEAD"),cancellable=true)
    private void mw$protect(WorldAccess w,BlockPos pos,BlockState state,Direction dir,FluidState fluid,CallbackInfo ci){if(w instanceof ServerWorld sw&&(RegionProtection.contains(sw,pos)||com.megawalls.minigame.BedWars.mappedAt(sw,pos)||com.megawalls.minigame.PotArena.contains(sw,pos)||!com.megawalls.minigame.DuelArena.allowFlow(sw,pos.offset(dir.getOpposite()),pos)))ci.cancel();}
}
