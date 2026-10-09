package com.megawalls.mixin;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ItemPhysic's DropPacket calls the inventory directly, bypassing PlayerEntity. */
@Mixin(PlayerInventory.class)
public abstract class MWKitInventoryDropMixin {
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void mw$keepSelectedKit(boolean entire,CallbackInfoReturnable<ItemStack> cir){
        // Reject before splitting/removing the stack; deleting its ItemEntity is too late.
        if(com.megawalls.minigame.LobbyItems.isBound(((PlayerInventory)(Object)this).getMainHandStack()))
            cir.setReturnValue(ItemStack.EMPTY);
    }
}
