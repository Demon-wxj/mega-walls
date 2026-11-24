package com.megawalls.mixin;

import com.megawalls.minigame.BedSpecial;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class BedMagnetDamageMixin {
    // Yarn 1.21.1: modifyAppliedDamage. Intermediary target for the overlay build.
    @Inject(method = "method_6036", at = @At("RETURN"), cancellable = true, remap = false)
    private void bed$applyMagnetDamage(DamageSource source, float amount, CallbackInfoReturnable<Float> callback) {
        float damage = callback.getReturnValueF();
        float adjusted = BedSpecial.appliedDamage((LivingEntity)(Object)this, source, damage);
        if (adjusted != damage) callback.setReturnValue(adjusted);
    }
}
