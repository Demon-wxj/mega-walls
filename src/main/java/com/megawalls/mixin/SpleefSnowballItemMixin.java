package com.megawalls.mixin;

import com.megawalls.minigame.Spleef;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowballItem.class)
public abstract class SpleefSnowballItemMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void spleef$prepare(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){
        if(player instanceof ServerPlayerEntity p&&Spleef.inRound(p)&&!Spleef.beginThrow(p,hand))cir.setReturnValue(TypedActionResult.fail(p.getStackInHand(hand)));
    }
    @Inject(method="use",at=@At("RETURN"),cancellable=true)
    private void spleef$replace(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){
        if(player instanceof ServerPlayerEntity p)cir.setReturnValue(Spleef.finishThrow(p,cir.getReturnValue()));
    }
}
