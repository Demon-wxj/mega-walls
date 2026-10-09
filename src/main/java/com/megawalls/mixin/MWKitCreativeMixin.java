package com.megawalls.mixin;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MWKitCreativeMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onCreativeInventoryAction",at=@At("HEAD"),cancellable=true)
    private void mw$creative(CreativeInventoryActionC2SPacket packet,CallbackInfo ci){
        // Vanilla reschedules off-thread packets and invokes this handler again on the server thread.
        if(!player.getServer().isOnThread())return;
        int index=packet.slot();var screen=player.playerScreenHandler;
        boolean existing=index>=0&&index<screen.slots.size()&&com.megawalls.minigame.LobbyItems.isBound(screen.slots.get(index).getStack());
        if(existing||com.megawalls.minigame.LobbyItems.isBound(packet.stack())){ci.cancel();screen.syncState();}
    }
}
