package com.megawalls.mixin;
import com.megawalls.minigame.DuelArena;
import net.minecraft.fluid.*;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LavaFluid.class)
public abstract class DuelLavaMixin {
    @Inject(method="onRandomTick",at=@At("HEAD"),cancellable=true)
    private void duel$fire(net.minecraft.world.World world,BlockPos pos,FluidState state,Random random,CallbackInfo ci){if(world instanceof ServerWorld w&&DuelArena.contains(w,pos))ci.cancel();}
}
