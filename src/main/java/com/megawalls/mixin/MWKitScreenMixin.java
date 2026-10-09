package com.megawalls.mixin;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Worn kit armor is fixed; other bound items remain sortable within the owner's inventory. */
@Mixin(ScreenHandler.class)
public abstract class MWKitScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mw$transfer(int index, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        var screen = (ScreenHandler) (Object) this;
        // Migrate old kit armor immediately, including the first click after loading a save.
        if(player instanceof ServerPlayerEntity owner) EquipmentHandler.bind(owner);
        if (player instanceof ServerPlayerEntity sp && !sp.isCreative() && com.megawalls.minigame.Isolation.inRound(sp)
                && !(com.megawalls.minigame.Spleef.playing(sp) && screen == sp.playerScreenHandler)) {
            ci.cancel(); screen.syncState(); return;
        }
        var slot = index >= 0 && index < screen.slots.size() ? screen.slots.get(index) : null;
        var cursor = screen.getCursorStack();
        boolean cursorKit = com.megawalls.minigame.LobbyItems.isBound(cursor);
        boolean slotKit = slot != null && com.megawalls.minigame.LobbyItems.isBound(slot.getStack());
        boolean external = slot != null && slot.inventory != player.getInventory();
        // Drag start/end use -999 too. Only pickup-family clicks outside are drops.
        boolean deny = EquipmentHandler.isLockedArmor(player,slot)
                || slotKit && external
                || cursorKit && action == SlotActionType.PICKUP && external
                || cursorKit && index == -999 && (action == SlotActionType.PICKUP || action == SlotActionType.QUICK_MOVE)
                || cursorKit && action == SlotActionType.QUICK_CRAFT
                    && (ScreenHandler.unpackQuickCraftButton(button) == 2
                        || ScreenHandler.unpackQuickCraftStage(button) == 1 && external)
                || slotKit && (action == SlotActionType.THROW || action == SlotActionType.CLONE)
                || external && action == SlotActionType.SWAP && button >= 0
                    && button < player.getInventory().size()
                    && com.megawalls.minigame.LobbyItems.isBound(player.getInventory().getStack(button));
        // A bundle is another inventory even when the bundle itself sits in the hotbar.
        if (slot != null && action == SlotActionType.PICKUP
                && (cursorKit && slot.getStack().contains(DataComponentTypes.BUNDLE_CONTENTS)
                    || slotKit && cursor.contains(DataComponentTypes.BUNDLE_CONTENTS))) deny = true;
        if (player instanceof ServerPlayerEntity p
                && (!EquipmentHandler.ownedBy(p, cursor)
                    || slotKit && !EquipmentHandler.ownedBy(p, slot.getStack()))) deny = true;
        if (deny) {
            ci.cancel();
            if (player instanceof ServerPlayerEntity) screen.syncState();
            return;
        }
        if (slotKit && !external && action == SlotActionType.QUICK_MOVE && screen != player.playerScreenHandler) {
            // Chest/furnace Shift normally exports items. Use the normal player-inventory
            // transfer instead, preserving its armor, hotbar and stack-capacity rules.
            var ownSlot = player.playerScreenHandler.getSlotIndex(player.getInventory(), slot.getIndex());
            if (ownSlot.isPresent()) player.playerScreenHandler.onSlotClick(ownSlot.getAsInt(), button, action, player);
            ci.cancel();
            if (player instanceof ServerPlayerEntity) {
                screen.syncState();
                player.playerScreenHandler.sendContentUpdates();
            }
        }
    }

    @Redirect(method = "internalOnSlotClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;canTakeItems(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private boolean mw$canTake(Slot slot, PlayerEntity player) {
        // PICKUP_ALL visits every slot, including slots other than the clicked one.
        return !EquipmentHandler.isLockedArmor(player,slot) && slot.canTakeItems(player) && (!com.megawalls.minigame.LobbyItems.isBound(slot.getStack())
                || slot.inventory == player.getInventory()
                    && (!(player instanceof ServerPlayerEntity p) || EquipmentHandler.ownedBy(p, slot.getStack())));
    }

    @Inject(method = "onClosed", at = @At("HEAD"))
    private void mw$close(PlayerEntity player, CallbackInfo ci) {
        var screen = (ScreenHandler) (Object) this;
        if (!(player instanceof ServerPlayerEntity p) || !com.megawalls.minigame.LobbyItems.isBound(screen.getCursorStack())) return;
        var held = screen.getCursorStack();
        screen.setCursorStack(ItemStack.EMPTY);
        if(com.megawalls.minigame.LobbyItems.isControl(held)){
            if(p.isAlive()&&com.megawalls.minigame.LobbyItems.authorized(p,held))p.getInventory().insertStack(held);
            com.megawalls.minigame.LobbyItems.sync(p);
            return;
        }
        // Death discards kit. A live player's full inventory instead keeps a persisted
        // pending stack; closing the screen must never eject an unrelated normal item.
        if (p.isAlive() && EquipmentHandler.ownedBy(p, held)) EquipmentHandler.returnOrReserve(p, held);
    }
}
