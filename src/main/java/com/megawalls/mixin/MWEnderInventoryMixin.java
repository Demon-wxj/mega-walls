package com.megawalls.mixin;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntity.class)
public abstract class MWEnderInventoryMixin {
    @Inject(method="dropInventory",at=@At("HEAD"),cancellable=true)
    private void mw$keep(CallbackInfo ci){
        if((Object)this instanceof ServerPlayerEntity p){
            if(com.megawalls.passive.EnderRetention.capture(p)){ci.cancel();return;}
            com.megawalls.equipment.EquipmentHandler.removeKit(p);
        }
    }
    @Redirect(method="dropInventory",at=@At(value="INVOKE",target="Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
    private boolean mw$normalLoot(net.minecraft.world.GameRules rules,net.minecraft.world.GameRules.Key<net.minecraft.world.GameRules.BooleanRule> key){
        if((Object)this instanceof ServerPlayerEntity p&&com.megawalls.equipment.EquipmentHandler.matchDeath(p))return false;
        return rules.getBoolean(key);
    }
}
