package com.megawalls.mixin;

import com.megawalls.game.GameState;
import com.megawalls.player.MWPlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MWConsumeMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void mw$consume(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (!(user instanceof ServerPlayerEntity player) || !GameState.active(player)) return;
        ItemStack stack = (ItemStack)(Object)this;
        var custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (stack.isOf(Items.POTION) && custom != null && custom.copyNbt().getBoolean("mw_kit")) {
            int heal = custom.copyNbt().getInt("mw_heal");
            if (heal > 0) player.heal(Math.min(20, heal));
        }
        var potion=stack.get(DataComponentTypes.POTION_CONTENTS);
        boolean meaningfulPotion=potion!=null&&potion.getEffects().iterator().hasNext()
                ||custom!=null&&custom.copyNbt().getInt("mw_heal")>0;
        if (stack.isOf(Items.POTION) && meaningfulPotion && GameState.phase(player)!=1 && MWPlayerData.getServer(player).getClassId() == 4) {
            for (var enemy : player.getServerWorld().getPlayers(p -> com.megawalls.game.GameEvents.canControl(player, p) && player.squaredDistanceTo(p) <= 16)) {
                enemy.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.BLINDNESS, 48, 0));
            }
        }
        if (stack.isOf(Items.COOKED_BEEF) && MWPlayerData.getServer(player).getClassId()==13 && com.megawalls.passive.PassiveHandler.ready(player,"devour",120))
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.REGENERATION,100,0));
        if (stack.isOf(Items.GOLDEN_APPLE) && MWPlayerData.getServer(player).getClassId()==11)
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.REGENERATION,100,2));
        if (stack.isOf(Items.POTION) && MWPlayerData.getServer(player).getClassId()==6 && custom!=null && custom.copyNbt().getInt("mw_heal")>0)
            com.megawalls.passive.LegacyBattle.empower(player,"potion",.1f,140);
        if(custom!=null&&custom.copyNbt().getInt("mw_food_energy")>0&&MWPlayerData.getServer(player).getClassId()==12)
            com.megawalls.skill.EnergyRules.food(player,custom.copyNbt().getInt("mw_food_energy"));
    }
}
