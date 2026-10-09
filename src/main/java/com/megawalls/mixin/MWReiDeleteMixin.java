package com.megawalls.mixin;
import com.megawalls.client.KitCreativeInventory;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Keep REI's normal delete gesture; only its authoritative cursor removal changes. */
@Pseudo
@Mixin(targets="me.shedaniel.rei.impl.client.ClientHelperImpl",remap=false)
public abstract class MWReiDeleteMixin {
    @Inject(method="canDeleteItems",at=@At("HEAD"),cancellable=true,remap=false)
    private void mw$ownCursorPermission(CallbackInfoReturnable<Boolean> cir){
        if(KitCreativeInventory.canDeleteCursor())cir.setReturnValue(true);
    }
    @Inject(method="sendDeletePacket",at=@At("HEAD"),cancellable=true,remap=false)
    private void mw$deleteOwnCursor(CallbackInfo ci){
        if(!KitCreativeInventory.canDeleteCursor())return;
        KitCreativeInventory.deleteCursor();
        ((MWHandledCursorAccessor)MinecraftClient.getInstance().currentScreen).mw$cursorDragging(false);
        ci.cancel();
    }
}
