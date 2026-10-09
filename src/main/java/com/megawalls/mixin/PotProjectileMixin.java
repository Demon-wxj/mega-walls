package com.megawalls.mixin;
import com.megawalls.minigame.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ProjectileEntity.class)
public abstract class PotProjectileMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void pot$stale(CallbackInfo ci){var e=(ProjectileEntity)(Object)this;
        if(e.getWorld() instanceof ServerWorld&&e.getCommandTags().contains("mg_source_pot_pvp")&&(!PotDuel.current(e)||!(e.getOwner() instanceof ServerPlayerEntity p)||!PotDuel.fighter(p)||p.getWorld()!=e.getWorld())){e.discard();ci.cancel();}
    }
}
