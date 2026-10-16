package com.megawalls.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface MWPlayerProtectionAccessor {
    @Accessor("joinInvulnerabilityTicks") int mw$getJoinInvulnerabilityTicks();
    @Accessor("joinInvulnerabilityTicks") void mw$setJoinInvulnerabilityTicks(int ticks);
}
