package com.megawalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.megawalls.game.MWLegacyCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LivingEntity.class,priority=800)
public abstract class MWLegacyLivingMixin {
    @Shadow protected abstract void damageArmor(DamageSource source,float damage);

    @Inject(method="blockedByShield",at=@At("HEAD"),cancellable=true)
    private void mw$swordIsNotShield(DamageSource source,CallbackInfoReturnable<Boolean> cir){
        if((Object)this instanceof PlayerEntity p&&MWLegacyCombat.swordBlocking(p))cir.setReturnValue(false);
    }

    @Inject(method="applyArmorToDamage",at=@At("HEAD"),cancellable=true)
    private void mw$oldArmor(DamageSource source,float damage,CallbackInfoReturnable<Float> cir){
        if(!((Object)this instanceof ServerPlayerEntity p)||!MWLegacyCombat.active(p))return;
        boolean armored=!source.isIn(DamageTypeTags.BYPASSES_ARMOR)
                ||source.isOf(DamageTypes.LAVA)||source.isOf(DamageTypes.IN_FIRE);
        if(armored){
            if(damage>0&&MWLegacyCombat.swordBlocking(p))damage=(damage+1.0f)*0.5f;
            damageArmor(source,damage);
            damage*=1.0f-Math.clamp(p.getArmor(),0,20)*0.04f;
        }
        cir.setReturnValue(damage);
    }

    // Runs after combat-modes' priority-1000 expression hook. Reading the actual
    // field restores vanilla's stronger-hit difference rule even when the external
    // global "legacy combo" mode substitutes zero for this particular expression.
    @ModifyExpressionValue(method="damage",at=@At(value="FIELD",
            target="Lnet/minecraft/entity/LivingEntity;timeUntilRegen:I",opcode=Opcodes.GETFIELD))
    private int mw$keepHurtImmunity(int original){
        return (Object)this instanceof ServerPlayerEntity p&&MWLegacyCombat.active(p)?p.timeUntilRegen:original;
    }

    @Inject(method="clearActiveItem",at=@At("HEAD"))
    private void mw$stopBlocking(CallbackInfo ci){
        MWLegacyCombat.unmarkBlocking(((LivingEntity)(Object)this).getActiveItem());
    }
}
