package com.megawalls.mixin;

import com.megawalls.minigame.BedSpecial;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
abstract class BedMagnetArrowMixin {
    // Yarn 1.21.1: knockback. Punch applies velocity separately from LivingEntity.takeKnockback.
    @Inject(method = "method_59957", at = @At("HEAD"), cancellable = true, remap = false)
    private void bed$absorbPunch(LivingEntity target, DamageSource source, CallbackInfo callback) {
        if (BedSpecial.magnet(target)) callback.cancel();
    }
}
