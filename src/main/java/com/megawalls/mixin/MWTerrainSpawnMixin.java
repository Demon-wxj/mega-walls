package com.megawalls.mixin;

import com.megawalls.game.MatchTerrainJournal;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class MWTerrainSpawnMixin {
    @Inject(method="spawnEntity",at=@At("HEAD"),cancellable=true)
    private void mw$recordTemporaryEntity(Entity entity,CallbackInfoReturnable<Boolean> cir){
        var world=(ServerWorld)(Object)this;
        if(!com.megawalls.minigame.BedArena.beforeSpawn(world,entity)||!MatchTerrainJournal.beforeSpawn(world,entity))cir.setReturnValue(false);
    }
}
