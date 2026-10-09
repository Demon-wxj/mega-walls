package com.megawalls.mixin;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PersistentProjectileEntity.class)
public abstract class MWKitArrowMixin {
    @Inject(method="tryPickup",at=@At("HEAD"),cancellable=true)
    private void mw$arrow(PlayerEntity player,CallbackInfoReturnable<Boolean> cir){
        var arrow=(PersistentProjectileEntity)(Object)this;
        if(player instanceof net.minecraft.server.network.ServerPlayerEntity p&&!com.megawalls.minigame.Isolation.permits(arrow,p)){cir.setReturnValue(false);return;}
        if(com.megawalls.equipment.EquipmentHandler.isEquipmentItem(arrow.getItemStack()))cir.setReturnValue(false);
    }
}
