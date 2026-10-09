package com.megawalls.mixin;
import com.megawalls.minigame.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EnderPearlEntity.class)
public abstract class PotPearlMixin {
    @Inject(method="onCollision",at=@At("HEAD"),cancellable=true)
    private void pot$destination(HitResult hit,CallbackInfo ci){var e=(EnderPearlEntity)(Object)this;
        if(!(e.getWorld() instanceof ServerWorld w)||!(e.getCommandTags().contains("mg_source_pot_pvp")||e.getOwner() instanceof ServerPlayerEntity owner&&PotDuel.member(owner)))return;
        var dest=new net.minecraft.util.math.Vec3d(e.lastRenderX,e.lastRenderY,e.lastRenderZ);
        if(!PotDuel.current(e)||!(e.getOwner() instanceof ServerPlayerEntity p)||!PotDuel.fighter(p)||p.getWorld()!=w
            ||!PotDuel.inside(w,BlockPos.ofFloored(dest))||!PotDuel.inside(w,BlockPos.ofFloored(dest.add(0,1.8,0)))
            ||!w.getWorldBorder().contains(BlockPos.ofFloored(dest))||!w.isSpaceEmpty(p,p.getBoundingBox().offset(dest.subtract(p.getPos())))){
            e.discard();ci.cancel();
        }
    }
    // Preserve the 1.21.4 pre-collision landing position, and validate that same position above.
    @ModifyArg(method="onCollision",at=@At(value="INVOKE",target="Lnet/minecraft/world/TeleportTarget;<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;FFLnet/minecraft/world/TeleportTarget$PostDimensionTransition;)V"),index=1)
    private net.minecraft.util.math.Vec3d pot$legacyLanding(net.minecraft.util.math.Vec3d vanilla){
        var e=(EnderPearlEntity)(Object)this;
        return e.getCommandTags().contains("mg_source_pot_pvp")?new net.minecraft.util.math.Vec3d(e.lastRenderX,e.lastRenderY,e.lastRenderZ):vanilla;
    }
    @Redirect(method="onCollision",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean pot$noMite(ServerWorld w,Entity entity){return !((EnderPearlEntity)(Object)this).getCommandTags().contains("mg_source_pot_pvp")&&w.spawnEntity(entity);}
}
