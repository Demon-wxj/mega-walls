package com.megawalls.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
@Mixin(LivingEntity.class)
public abstract class GameEffectMixin {
    @Inject(method="addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void game$effect(StatusEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> cir){
        if((Object)this instanceof net.minecraft.server.network.ServerPlayerEntity p&&!com.megawalls.minigame.Isolation.permits(source,p))cir.setReturnValue(false);
    }
}
