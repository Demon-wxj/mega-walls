package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class HerobrineSkill extends Skill {
    public HerobrineSkill() { super("雷神之怒", "5格内4.5HP真实伤害", 20); }
    @Override public void execute(ServerPlayerEntity player) {
        player.getServerWorld().spawnParticles(ParticleTypes.ELECTRIC_SPARK,player.getX(),player.getY()+.3,player.getZ(),12,1,.2,1,.04);
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,.6f,1.3f);
        for(var enemy:player.getServerWorld().getPlayers()) {
            if(!GameState.enemy(player,enemy)||player.squaredDistanceTo(enemy)>25)continue;
            var at=enemy.getPos();
            float before=enemy.getHealth()+enemy.getAbsorptionAmount();
            TrueDamage.hit(player,enemy,4.5f);
            if(enemy.getHealth()+enemy.getAbsorptionAmount()<before&&player.canSee(enemy))SkillVisuals.lightning(player,at);
        }
    }
}
