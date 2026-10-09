package com.megawalls.mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class BedMagnetMixin {
    @Inject(method="takeKnockback",at=@At("HEAD"),cancellable=true)
    private void bed$magnet(double strength,double x,double z,CallbackInfo ci){if(com.megawalls.minigame.BedSpecial.magnet((LivingEntity)(Object)this))ci.cancel();}
}
