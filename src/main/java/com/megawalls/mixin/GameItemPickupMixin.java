package com.megawalls.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
@Mixin(ItemEntity.class)
public abstract class GameItemPickupMixin {
    @Inject(method="onPlayerCollision",at=@At("HEAD"),cancellable=true)
    private void game$pickup(PlayerEntity player,CallbackInfo ci){
        if(!(player instanceof net.minecraft.server.network.ServerPlayerEntity p))return;
        if(!com.megawalls.minigame.Isolation.pickup(p,(ItemEntity)(Object)this))ci.cancel();
    }
}
