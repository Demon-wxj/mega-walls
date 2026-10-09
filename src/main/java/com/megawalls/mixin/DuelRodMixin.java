package com.megawalls.mixin;
import com.megawalls.minigame.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// Run before the compatibility mod's rod damage and pull injections.
@Mixin(value=FishingBobberEntity.class,priority=900)
public abstract class DuelRodMixin {
    private boolean allowed(Entity e){var hook=(FishingBobberEntity)(Object)this;var owner=hook.getOwner();if(owner instanceof ServerPlayerEntity a&&e instanceof ServerPlayerEntity b)return !PotDuel.member(a)&&!PotDuel.member(b)&&GameSessions.same(a,b)&&(!ClassicDuel.member(a)&&!ClassicDuel.member(b)||ClassicDuel.paired(a,b));return !(owner instanceof ServerPlayerEntity a&&ClassicDuel.member(a));}
    @Inject(method="onEntityHit",at=@At("HEAD"),cancellable=true)
    private void duel$hit(EntityHitResult hit,CallbackInfo ci){if(!allowed(hit.getEntity()))ci.cancel();}
    @Inject(method="pullHookedEntity",at=@At("HEAD"),cancellable=true)
    private void duel$pull(Entity e,CallbackInfo ci){if(!allowed(e))ci.cancel();}
}
