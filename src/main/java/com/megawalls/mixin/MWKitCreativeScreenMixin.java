package com.megawalls.mixin;
import com.megawalls.client.KitCreativeInventory;
import com.megawalls.network.C2S_KitClick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeInventoryScreen.class)
public abstract class MWKitCreativeScreenMixin {
    @Shadow private Slot deleteItemSlot;
    // Vanilla catalogue clicks also delete/decrement a carried stack without a
    // slot update. Mirror their resulting ordinary cursor to the server.
    @Inject(method="onMouseClick",at=@At("RETURN"))
    private void mw$catalogueCursor(Slot slot,int id,int button,SlotActionType action,CallbackInfo ci){
        var p=MinecraftClient.getInstance().player;
        if(p!=null&&slot!=null&&slot!=deleteItemSlot&&slot.inventory!=p.getInventory()
                &&!(slot instanceof MWCreativeSlotAccessor)
                &&ClientPlayNetworking.canSend(C2S_KitClick.ID)&&KitCreativeInventory.hasKit())
            KitCreativeInventory.catalogueCursor();
    }
    @Inject(method="onMouseClick",at=@At("HEAD"),cancellable=true)
    private void mw$ownedClick(Slot slot,int id,int button,SlotActionType action,CallbackInfo ci){
        if(!ClientPlayNetworking.canSend(C2S_KitClick.ID)||!KitCreativeInventory.hasKit())return;
        var p=MinecraftClient.getInstance().player;
        boolean cursorKit=com.megawalls.minigame.LobbyItems.isBound(p.playerScreenHandler.getCursorStack());
        if(slot==deleteItemSlot&&slot!=null){
            if(action==SlotActionType.PICKUP||action==SlotActionType.QUICK_MOVE){
                KitCreativeInventory.delete(action==SlotActionType.QUICK_MOVE);ci.cancel();
            }else if(cursorKit)ci.cancel();
            return;
        }
        if(slot!=null&&slot.inventory!=p.getInventory()){
            // Catalogue clone/delete/swap operations must not overwrite or copy kit.
            if(cursorKit||action==SlotActionType.SWAP&&button>=0&&button<p.getInventory().size()
                    &&com.megawalls.minigame.LobbyItems.isBound(p.getInventory().getStack(button)))ci.cancel();
            return;
        }
        int target=slot==null?id:slot instanceof MWCreativeSlotAccessor wrapped?wrapped.mw$originalSlot().id
                :p.playerScreenHandler.getSlotIndex(p.getInventory(),slot.getIndex()).orElse(-1);
        if(target>=5||target==-999){
            KitCreativeInventory.click(target,button,action);ci.cancel();
        }
    }
}
