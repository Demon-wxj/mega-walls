package com.megawalls.mixin;

import com.megawalls.minigame.Isolation;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ItemPhysic's charged drop bypasses ServerPlayerEntity.dropItem. */
@Pseudo
@Mixin(targets="team.creative.itemphysic.common.packet.DropPacket",remap=false)
public abstract class ItemPhysicGameDropMixin {
    @Inject(method="createEntity",at=@At("RETURN"),cancellable=true,remap=false)
    private void game$drop(ItemStack stack,PlayerEntity player,boolean random,boolean retain,CallbackInfoReturnable<ItemEntity> cir){
        if(player instanceof ServerPlayerEntity p&&cir.getReturnValue()!=null){
            var item=cir.getReturnValue();Isolation.dropped(p,item);
            if(item.isRemoved())cir.setReturnValue(null);
        }
    }
}
