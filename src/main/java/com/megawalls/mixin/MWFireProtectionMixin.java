package com.megawalls.mixin;
import com.megawalls.game.RegionProtection;
import net.minecraft.block.FireBlock;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FireBlock.class)
public abstract class MWFireProtectionMixin {
    @Inject(method="trySpreadingFire",at=@At("HEAD"),cancellable=true)
    private void mw$protect(World w,BlockPos pos,int spread,Random random,int age,CallbackInfo ci){if(w instanceof ServerWorld sw&&(RegionProtection.contains(sw,pos)||com.megawalls.minigame.BedWars.mappedAt(sw,pos)||com.megawalls.minigame.PotArena.contains(sw,pos)))ci.cancel();}
}
