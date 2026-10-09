package com.megawalls.mixin;

import com.megawalls.nametag.NameTagHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class MWPlayerListMixin {
    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void megawalls$listName(CallbackInfoReturnable<Text> cir) {
        Text label = NameTagHandler.label((ServerPlayerEntity)(Object)this);
        if (label != null) cir.setReturnValue(label);
    }
}
