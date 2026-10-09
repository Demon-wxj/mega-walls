package com.megawalls.mixin;

import com.megawalls.game.MWLegacyCombat;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional pinned 1.21.1 sword-blocking component: MW owns reduction and has no parry. */
@Pseudo
@Mixin(targets="fuzs.swordblockingmechanics.handler.SwordBlockingHandler",remap=false)
public abstract class MWLegacySwordCompatMixin {
    @Inject(method="isActiveItemStackBlocking",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mw$oneBlockingRule(PlayerEntity player,CallbackInfoReturnable<Boolean> cir){
        if(!player.getWorld().isClient&&MWLegacyCombat.active(player))cir.setReturnValue(false);
    }
}
