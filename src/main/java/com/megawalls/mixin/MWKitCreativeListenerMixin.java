package com.megawalls.mixin;
import com.megawalls.client.KitCreativeInventory;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryListener;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CreativeInventoryListener.class)
public abstract class MWKitCreativeListenerMixin {
    @Inject(method="onSlotUpdate",at=@At("HEAD"),cancellable=true)
    private void mw$noEcho(ScreenHandler handler,int slot,ItemStack stack,CallbackInfo ci){
        if(KitCreativeInventory.suppressListener())ci.cancel();
    }
}
