package com.megawalls.mixin;
import com.megawalls.minigame.BedWars;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntity.class)
public abstract class BedDeathMixin {
    @Inject(method="dropInventory",at=@At("HEAD"),cancellable=true)
    private void bed$drop(CallbackInfo ci){if((Object)this instanceof ServerPlayerEntity p&&BedWars.inRound(p)){p.getInventory().dropAll();ci.cancel();}}
}
