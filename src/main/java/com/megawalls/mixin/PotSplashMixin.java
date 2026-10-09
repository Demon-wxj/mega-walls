package com.megawalls.mixin;
import com.megawalls.minigame.*;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PotionEntity.class)
public abstract class PotSplashMixin {
    @Inject(method="onCollision",at=@At("HEAD"),cancellable=true)
    private void pot$round(HitResult hit,CallbackInfo ci){var e=(PotionEntity)(Object)this;
        if(e.getWorld() instanceof ServerWorld&&e.getCommandTags().contains("mg_source_pot_pvp")&&(!PotDuel.current(e)||!(e.getOwner() instanceof ServerPlayerEntity p)||!PotDuel.fighter(p)||p.getWorld()!=e.getWorld())){e.discard();ci.cancel();}
    }
}
