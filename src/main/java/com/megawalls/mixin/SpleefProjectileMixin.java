package com.megawalls.mixin;

import com.megawalls.minigame.Spleef;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Discards a match's shots promptly on elimination, exit, reset and chunk reload. */
@Mixin(ProjectileEntity.class)
public abstract class SpleefProjectileMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void spleef$stale(CallbackInfo ci){var e=(ProjectileEntity)(Object)this;if(e.getWorld() instanceof ServerWorld&&e.getCommandTags().contains("mg_source_"+Spleef.ID)&&!Spleef.currentShot(e)){e.discard();ci.cancel();}}
}
