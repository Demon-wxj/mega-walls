package com.megawalls.minigame;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;

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
}
