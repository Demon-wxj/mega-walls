package com.megawalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.megawalls.game.MWLegacyCombat;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PlayerEntity.class,priority=800)
public abstract class MWLegacyPlayerMixin {
    @Inject(method="getAttackCooldownProgress",at=@At("HEAD"),cancellable=true)
    private void mw$fullAttack(float baseTime,CallbackInfoReturnable<Float> cir){
        if(MWLegacyCombat.active((PlayerEntity)(Object)this)
                || com.megawalls.fun.FunMode.auraAttack((PlayerEntity)(Object)this))cir.setReturnValue(1.0f);
    }

    // 1.21.1 attack's fourth boolean is the sweep flag. Disable its stores, not just
    // the particles, so nearby entities never receive the modern secondary hit.
    @ModifyVariable(method="attack",at=@At("STORE"),ordinal=3)
    private boolean mw$noSweep(boolean sweeping){
        return !MWLegacyCombat.active((PlayerEntity)(Object)this)
                && !com.megawalls.fun.FunMode.auraAttack((PlayerEntity)(Object)this)&&sweeping;
    }

    // Only the active automatic attack receives the target-facing knockback direction.
    // The player's stored yaw, camera, movement and ordinary manual attacks stay unchanged.
    @ModifyExpressionValue(method="attack",at=@At(value="INVOKE",
            target="Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float mw$auraKnockbackYaw(float original){
        return com.megawalls.fun.FunMode.auraYaw((PlayerEntity)(Object)this,original);
    }

    @Inject(method="tick",at=@At("TAIL"))
    private void mw$clearOldBlocking(CallbackInfo ci){MWLegacyCombat.tick((PlayerEntity)(Object)this);}
}
