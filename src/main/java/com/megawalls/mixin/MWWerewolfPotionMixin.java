package com.megawalls.mixin;

import com.megawalls.skill.WerewolfPotionRules;
import java.util.function.Consumer;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PotionItem.class)
public abstract class MWWerewolfPotionMixin {
    @Redirect(method="finishUsing", at=@At(value="INVOKE", target="Lnet/minecraft/component/type/PotionContentsComponent;forEachEffect(Ljava/util/function/Consumer;)V"))
    private void mw$kitEffects(PotionContentsComponent potion, Consumer<StatusEffectInstance> apply,
                              ItemStack stack, World world, LivingEntity user) {
        boolean restricted = user instanceof ServerPlayerEntity player && WerewolfPotionRules.suppressJump(player, stack);
        potion.forEachEffect(effect -> {
            if (!restricted || !effect.getEffectType().equals(StatusEffects.JUMP_BOOST)) apply.accept(effect);
        });
    }
}
