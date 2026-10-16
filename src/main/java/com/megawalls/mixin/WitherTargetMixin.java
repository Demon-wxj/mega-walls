package com.megawalls.mixin;

import com.megawalls.game.GameState;
import com.megawalls.game.MatchFlow;
import com.megawalls.game.MatchTeams;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The stationary match objective must survive Peaceful without changing ordinary withers. */
@Mixin(WitherEntity.class)
public abstract class WitherTargetMixin {
    @Inject(method="checkDespawn",at=@At("HEAD"),cancellable=true)
    private void mw$keepObjective(CallbackInfo ci){
        var core=(WitherEntity)(Object)this;
        if(!(core.getWorld() instanceof ServerWorld world))return;
        String team=MatchTeams.core(core);int phase=MatchFlow.phase(world.getServer());
        if(team!=null&&(phase==1||phase==2)&&MatchTeams.active(world.getServer()).contains(team)
                &&GameState.score(world.getScoreboard(),"#global","mw_wither_"+team)>0)ci.cancel();
    }
}
