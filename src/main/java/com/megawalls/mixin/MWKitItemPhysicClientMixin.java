package com.megawalls.mixin;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional ItemPhysic 1.8.13: its client removes the held stack independently. */
@Pseudo
@Mixin(targets="team.creative.itemphysic.client.ItemPhysicClient",remap=false)
public abstract class MWKitItemPhysicClientMixin {
    @Shadow(remap=false) public static int throwCharge;

    @Inject(method="gameTick",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mw$keepKitBeforeChargedThrow(CallbackInfo ci){
        var player=MinecraftClient.getInstance().player;
        if(player!=null&&com.megawalls.minigame.LobbyItems.isBound(player.getMainHandStack())){
            // Reset charge so switching from a kit item cannot throw the next held item.
            throwCharge=0;
            ci.cancel();
        }
    }
}
