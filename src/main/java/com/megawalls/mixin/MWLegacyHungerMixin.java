package com.megawalls.mixin;

import com.megawalls.game.GameState;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mega Walls owns its food cadence even when another combat mode disables DuelHungerMixin. */
@Mixin(value=HungerManager.class,priority=1500)
public abstract class MWLegacyHungerMixin {
    @Shadow private int foodLevel;
    @Shadow private int prevFoodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustion;
    @Shadow private int foodTickTimer;

    @Inject(method="update",at=@At("HEAD"),cancellable=true)
    private void mw$legacyFood(PlayerEntity player,CallbackInfo ci) {
        if(!(player instanceof ServerPlayerEntity p)||!GameState.active(p))return;
        prevFoodLevel=foodLevel;
        var difficulty=p.getServerWorld().getDifficulty();
        if(exhaustion>4){
            exhaustion-=4;
            if(saturationLevel>0)saturationLevel=Math.max(0,saturationLevel-1);
            else if(difficulty!=Difficulty.PEACEFUL)foodLevel=Math.max(0,foodLevel-1);
        }
        if(p.getServerWorld().getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)&&foodLevel>=18&&p.canFoodHeal()){
            if(++foodTickTimer>=80){p.heal(1);exhaustion=Math.min(40,exhaustion+3);foodTickTimer=0;}
        }else if(foodLevel<=0){
            if(++foodTickTimer>=80){
                if(p.getHealth()>10||difficulty==Difficulty.HARD||p.getHealth()>1&&difficulty==Difficulty.NORMAL)
                    p.damage(p.getDamageSources().starve(),1);
                foodTickTimer=0;
            }
        }else foodTickTimer=0;
        ci.cancel();
    }
}
