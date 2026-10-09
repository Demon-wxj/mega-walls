package com.megawalls.mixin;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TntEntity.class)
public abstract class BedTntMixin {
    @Inject(method="explode",at=@At("HEAD"),cancellable=true)
    private void bed$explode(CallbackInfo ci){var e=(TntEntity)(Object)this;if(e.getCommandTags().contains("mg_source_bedwars")){com.megawalls.minigame.BedSpecial.explode(e);ci.cancel();}}
}
