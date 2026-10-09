package com.megawalls.mixin;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.megawalls.equipment.EquipmentHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
public abstract class MWKitPickupMixin {
    @Inject(method="onPlayerCollision",at=@At("HEAD"),cancellable=true)
    private void mw$pickup(PlayerEntity player,CallbackInfo ci){
        var item=(ItemEntity)(Object)this;
        if(com.megawalls.minigame.LobbyItems.isBound(item.getStack())){if(!item.getWorld().isClient)item.discard();ci.cancel();}
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void mw$legacy(CallbackInfo ci){
        var item=(ItemEntity)(Object)this;
        if(!item.getWorld().isClient&&com.megawalls.minigame.LobbyItems.isBound(item.getStack())){item.discard();ci.cancel();}
    }
}
