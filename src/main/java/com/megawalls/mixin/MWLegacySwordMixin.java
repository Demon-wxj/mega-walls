package com.megawalls.mixin;

import com.megawalls.game.MWLegacyCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class MWLegacySwordMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void mw$block(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){
        var stack=player.getStackInHand(hand);
        if(hand==Hand.MAIN_HAND&&stack.getItem() instanceof SwordItem&&MWLegacyCombat.active(player)){
            MWLegacyCombat.markBlocking(stack);
            player.setCurrentHand(hand);
            cir.setReturnValue(TypedActionResult.consume(stack));
        }
    }
    @Inject(method="getUseAction",at=@At("HEAD"),cancellable=true)
    private void mw$blockPose(ItemStack stack,CallbackInfoReturnable<UseAction> cir){
        if(MWLegacyCombat.hasBlockingTag(stack))cir.setReturnValue(UseAction.BLOCK);
    }
    @Inject(method="getMaxUseTime",at=@At("HEAD"),cancellable=true)
    private void mw$blockDuration(ItemStack stack,LivingEntity user,CallbackInfoReturnable<Integer> cir){
        if(stack.getItem() instanceof SwordItem&&user instanceof PlayerEntity p&&MWLegacyCombat.active(p))cir.setReturnValue(72000);
    }
}
