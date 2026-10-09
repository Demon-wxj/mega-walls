package com.megawalls.mixin;

import com.megawalls.minigame.LegacyDuelItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class DuelSwordUseMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void duel$use(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        var stack=player.getStackInHand(hand);
        if(LegacyDuelItems.blocking(stack)){player.setCurrentHand(hand);cir.setReturnValue(TypedActionResult.consume(stack));}
    }
    @Inject(method="getUseAction",at=@At("HEAD"),cancellable=true)
    private void duel$action(ItemStack stack,CallbackInfoReturnable<UseAction> cir) {
        if(LegacyDuelItems.blocking(stack))cir.setReturnValue(UseAction.BLOCK);
    }
    @Inject(method="getMaxUseTime",at=@At("HEAD"),cancellable=true)
    private void duel$duration(ItemStack stack,LivingEntity user,CallbackInfoReturnable<Integer> cir) {
        if(LegacyDuelItems.blocking(stack))cir.setReturnValue(72000);
    }
}
