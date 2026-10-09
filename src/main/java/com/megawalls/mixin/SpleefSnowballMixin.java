package com.megawalls.mixin;

import com.megawalls.minigame.Spleef;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SnowballEntity.class)
public abstract class SpleefSnowballMixin {
    @Unique private long spleef$round;
    @Inject(method="<init>(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;)V",at=@At("RETURN"))
    private void spleef$launch(World world,LivingEntity owner,CallbackInfo ci){if(!world.isClient)spleef$round=Spleef.stamp((SnowballEntity)(Object)this);}
    @Inject(method="onCollision",at=@At("HEAD"),cancellable=true)
    private void spleef$collision(HitResult hit,CallbackInfo ci){var e=(SnowballEntity)(Object)this;if(spleef$round<=0&&!e.getCommandTags().contains("mg_source_"+Spleef.ID))return;if(!e.getWorld().isClient){if(spleef$round>0&&Spleef.currentShot(e))Spleef.hit(e,spleef$round,hit);e.getWorld().sendEntityStatus(e,(byte)3);e.discard();ci.cancel();}}
}
