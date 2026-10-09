package com.megawalls.mixin;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerListEntry.class)
public class MWClassSkinMixin {
    @Inject(method="getSkinTextures",at=@At("RETURN"),cancellable=true)
    private void skin(CallbackInfoReturnable<SkinTextures> cir){
        var entry=(PlayerListEntry)(Object)this;
        cir.setReturnValue(com.megawalls.client.ClassSkinTextures.replace(entry.getProfile().getId(),cir.getReturnValue()));
    }
}
