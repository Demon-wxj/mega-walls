package com.megawalls.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface MWHurtMemoryAccessor {
    @Accessor("lastDamageTaken") float mw$getLastDamageTaken();
    @Accessor("lastDamageTaken") void mw$setLastDamageTaken(float amount);
}
