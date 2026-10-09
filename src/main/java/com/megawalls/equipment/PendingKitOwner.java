package com.megawalls.equipment;

import java.util.List;
import net.minecraft.item.ItemStack;

/** Private, player-persisted fallback for a bound cursor closed with a full inventory. */
public interface PendingKitOwner {
    List<ItemStack> mw$pendingKit();
}
