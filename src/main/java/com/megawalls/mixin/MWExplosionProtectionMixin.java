package com.megawalls.mixin;
import com.megawalls.game.RegionProtection;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
@Mixin(Explosion.class)
public abstract class MWExplosionProtectionMixin {
    @Shadow @Final private net.minecraft.world.World world;
    @Shadow public abstract List<BlockPos> getAffectedBlocks();
    @Inject(method="affectWorld",at=@At("HEAD"))
    private void mw$protect(boolean particles,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(world instanceof ServerWorld serverWorld)getAffectedBlocks().removeIf(p->RegionProtection.contains(serverWorld,p)||com.megawalls.minigame.DuelArena.contains(serverWorld,p)||com.megawalls.minigame.PotArena.contains(serverWorld,p)||com.megawalls.minigame.BedWars.mappedAt(serverWorld,p));
    }
}
