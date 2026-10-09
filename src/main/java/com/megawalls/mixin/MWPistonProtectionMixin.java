package com.megawalls.mixin;
import com.megawalls.game.RegionProtection;
import net.minecraft.block.*;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PistonBlock.class)
public abstract class MWPistonProtectionMixin {
    @Inject(method="isMovable",at=@At("HEAD"),cancellable=true)
    private static void mw$protect(BlockState state,World w,BlockPos pos,Direction direction,boolean canBreak,Direction pistonDir,CallbackInfoReturnable<Boolean> cir){if(w instanceof ServerWorld sw&&(com.megawalls.minigame.DuelArena.contains(sw,pos)||com.megawalls.minigame.DuelArena.contains(sw,pos.offset(direction))||com.megawalls.minigame.PotArena.contains(sw,pos)||com.megawalls.minigame.PotArena.contains(sw,pos.offset(direction))||com.megawalls.minigame.BedWars.mappedAt(sw,pos)||com.megawalls.minigame.BedWars.mappedAt(sw,pos.offset(direction))||RegionProtection.contains(sw,pos)||RegionProtection.contains(sw,pos.offset(direction))))cir.setReturnValue(false);}
}
