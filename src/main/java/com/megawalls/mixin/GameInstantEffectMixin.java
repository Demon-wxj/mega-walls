package com.megawalls.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
@Mixin(targets="net.minecraft.entity.effect.InstantHealthOrDamageStatusEffect")
public abstract class GameInstantEffectMixin {
    @Inject(method="applyInstantEffect",at=@At("HEAD"),cancellable=true)
    private void game$instant(Entity source,Entity attacker,LivingEntity target,int amplifier,double proximity,CallbackInfo ci){
        if(target instanceof net.minecraft.server.network.ServerPlayerEntity p&&(!com.megawalls.minigame.Isolation.permits(source,p)||!com.megawalls.minigame.Isolation.permits(attacker,p)))ci.cancel();
    }
}

