package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class SkeletonSkill extends Skill {
    public SkeletonSkill() { super("爆炸箭", "碰撞爆炸；固定6HP真实伤害", 20); }
    @Override public void execute(ServerPlayerEntity player) {
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_ARROW_SHOOT,1,.7f);
        SkillEffects.aura(player,ParticleTypes.CRIT,.6);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.ARROW_DRAW,
                player.getPos(),player.getPos(),.8);
        SkillProjectiles.fire(player,player.getRotationVec(1).multiply(2.5),new java.util.HashSet<>());
    }
}
