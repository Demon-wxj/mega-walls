package com.megawalls.maintenance;
import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
public final class KitPotions {
    public static boolean isKitPotion(ItemStack stack) {
        var custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        return stack.isOf(Items.POTION) && custom != null && custom.copyNbt().getBoolean("mw_kit");
    }
    public static void normalize(ItemStack stack) { if (isKitPotion(stack)) stack.set(DataComponentTypes.MAX_STACK_SIZE, 64); }
    /** Merge only fully identical owned kit potions, retaining the leftmost slot. */
    public static void merge(ServerPlayerEntity player) {
        // Moving the active stack during drinking cancels vanilla item use.
        if (player.isUsingItem()) return;
        var inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < 36; i++) {
            ItemStack target = inventory.getStack(i);
            if (!isKitPotion(target) || !EquipmentHandler.ownedBy(player, target)) continue;
            for (int j = i + 1; j < 36 && target.getCount() < target.getMaxCount(); j++) {
                ItemStack source = inventory.getStack(j);
                if (!isKitPotion(source) || !ItemStack.areItemsAndComponentsEqual(target, source)) continue;
                int moved = Math.min(source.getCount(), target.getMaxCount() - target.getCount());
                target.increment(moved); source.decrement(moved);
                if (source.isEmpty()) inventory.setStack(j, ItemStack.EMPTY);
                changed |= moved > 0;
            }
        }
        if (changed) { inventory.markDirty(); player.currentScreenHandler.sendContentUpdates(); }
    }
}
