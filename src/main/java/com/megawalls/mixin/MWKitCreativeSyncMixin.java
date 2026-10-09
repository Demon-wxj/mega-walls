package com.megawalls.mixin;
import com.megawalls.client.KitCreativeInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MWKitCreativeSyncMixin {
    @Inject(method="onInventory",at=@At("RETURN"))
    private void mw$recordServerState(InventoryS2CPacket packet,CallbackInfo ci){
        if(packet.getSyncId()==0&&MinecraftClient.getInstance().currentScreen instanceof CreativeInventoryScreen)
            KitCreativeInventory.flushPrediction();
    }
}
