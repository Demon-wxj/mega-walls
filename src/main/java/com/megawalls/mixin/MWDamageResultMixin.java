package com.megawalls.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.megawalls.game.DamageResult;
import com.megawalls.minigame.GameSessions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class MWDamageResultMixin {
    @WrapMethod(method="damage")
    private boolean mw$damageResult(DamageSource source, float amount, Operation<Boolean> original) {
        if((Object)this instanceof ServerPlayerEntity player && GameSessions.mw(player))
            return DamageResult.track(player,source,()->original.call(source,amount));
        return original.call(source,amount);
    }
}
