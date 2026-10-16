package com.megawalls.minigame;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.Items;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import java.util.List;
import java.util.Optional;

/** Item components introduced after 1.21.1 are represented by synchronized custom data. */
public final class LegacyDuelItems {
    public static void sword(ItemStack stack) {
        var data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        data.putBoolean("mw_duel_block",true);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
    }
    public static boolean blocking(ItemStack stack) {
        return stack.getItem() instanceof SwordItem && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getBoolean("mw_duel_block");
    }
    public static void pearl(ItemStack stack,int seconds) {
        var data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        data.putInt("mw_pot_pearl_ticks",seconds*20);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
    }
    /** This kit's extended Speed II is a single effect, not vanilla's 90-second potion plus an extra one. */
    public static ItemStack potSpeed() {
        var stack=new ItemStack(Items.POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS,new PotionContentsComponent(Optional.empty(),Optional.empty(),
                List.of(new StatusEffectInstance(StatusEffects.SPEED,8*60*20,1))));
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("迅捷药水 · 速度 II（8:00）"));
        return stack;
    }
}
