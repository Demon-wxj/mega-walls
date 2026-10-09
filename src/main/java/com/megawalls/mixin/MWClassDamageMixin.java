package com.megawalls.mixin;
import com.megawalls.passive.LegacyBattle;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(LivingEntity.class)
public abstract class MWClassDamageMixin {
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float mw$classDamage(float amount,DamageSource source,float original){
        return (Object)this instanceof ServerPlayerEntity player?LegacyBattle.adjust(player,source,amount):amount;
    }
}
