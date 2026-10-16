package com.megawalls.minigame;

import net.minecraft.entity.damage.DamageSource;

/** BedWars damage keeps vanilla explosion armor/enchantment tags and attribution,
 * while a map's Peaceful/Easy/Hard setting cannot erase or rebalance a PvP blast. */
public final class BedBlastDamage extends DamageSource {
    public BedBlastDamage(DamageSource source) {
        super(source.getTypeRegistryEntry(),source.getSource(),source.getAttacker());
    }
    @Override public boolean isScaledWithDifficulty(){return false;}
}
