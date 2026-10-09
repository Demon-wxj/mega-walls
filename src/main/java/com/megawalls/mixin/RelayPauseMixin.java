package com.megawalls.mixin;

import com.megawalls.minigame.ClassicDuel;
import com.megawalls.minigame.PotDuel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relay reserves and the protected handover retain their remaining potion durations. */
@Mixin(LivingEntity.class)
public abstract class RelayPauseMixin {
    @Inject(method="tickStatusEffects",at=@At("HEAD"),cancellable=true)
    private void relay$pauseEffects(CallbackInfo ci){
        if((Object)this instanceof ServerPlayerEntity p&&(ClassicDuel.paused(p)||PotDuel.paused(p)))ci.cancel();
    }
}
