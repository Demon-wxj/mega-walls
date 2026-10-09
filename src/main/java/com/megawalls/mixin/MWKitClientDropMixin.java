package com.megawalls.mixin;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevent the client from visually removing kit before the server rejects Q. */
@Mixin(ClientPlayerEntity.class)
public abstract class MWKitClientDropMixin {
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void mw$keep(boolean entire,CallbackInfoReturnable<Boolean> cir){
        if(com.megawalls.minigame.LobbyItems.isBound(((ClientPlayerEntity)(Object)this).getMainHandStack()))cir.setReturnValue(false);
    }
}
