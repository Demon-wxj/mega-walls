package com.megawalls.mixin;

import com.megawalls.minigame.Isolation;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ItemPhysic 1.8.13 inserts directly, without ItemEntity.onPlayerCollision. */
@Pseudo
@Mixin(targets="team.creative.itemphysic.server.ItemPhysicServer",remap=false)
public abstract class ItemPhysicGamePickupMixin {
    @Inject(method="playerPickup",at=@At("HEAD"),cancellable=true,remap=false)
    private static void game$pickup(ItemEntity item,PlayerEntity player,CallbackInfo ci){
        if(player instanceof ServerPlayerEntity p&&!Isolation.pickup(p,item))ci.cancel();
    }
}
