package com.megawalls.mixin;
import com.megawalls.minigame.ClassicDuel;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Legacy food cadence in Mega Walls and classic duels; potion/skill healing is independent. */
@Mixin(value=HungerManager.class,priority=900)
public abstract class DuelHungerMixin {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustion;
    @Shadow private int foodTickTimer;
    @Inject(method="update",at=@At("HEAD"),cancellable=true)
    private void duel$food(net.minecraft.entity.player.PlayerEntity player,CallbackInfo ci){
        if(!(player instanceof ServerPlayerEntity p))return;
        if(ClassicDuel.paused(p)||com.megawalls.minigame.PotDuel.paused(p)){ci.cancel();return;}
        if(com.megawalls.game.GameState.active(p))return; // MWLegacyHungerMixin owns active Mega Walls.
        if(!(com.megawalls.minigame.GameSessions.mw(p)||ClassicDuel.fighter(p)||com.megawalls.minigame.PotDuel.fighter(p)))return;var difficulty=p.getServerWorld().getDifficulty();
        if(exhaustion>4){exhaustion-=4;if(saturationLevel>0)saturationLevel=Math.max(0,saturationLevel-1);else if(difficulty!=Difficulty.PEACEFUL)foodLevel=Math.max(0,foodLevel-1);}
        if(p.getServerWorld().getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)&&foodLevel>=18&&p.getHealth()<p.getMaxHealth()){if(++foodTickTimer>=80){p.heal(1);exhaustion=Math.min(40,exhaustion+3);foodTickTimer=0;}}
        else if(foodLevel<=0){if(++foodTickTimer>=80){if(p.getHealth()>10||difficulty==Difficulty.HARD||p.getHealth()>1&&difficulty==Difficulty.NORMAL)p.damage(p.getDamageSources().starve(),1);foodTickTimer=0;}}
        else foodTickTimer=0;ci.cancel();
    }
}
