package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class DreadlordSkill extends Skill {
    public DreadlordSkill() { super("影爆", "三枚凋零头；同一次施放每个敌人最多7HP", 20); }
    @Override public void execute(ServerPlayerEntity player) {
        SkillEffects.aura(player,ParticleTypes.SOUL,1);
        // Project shadow-wing theme: one visual burst, no duplicate damage or five repeated particle bursts.
        SkillEffects.shadowWings(player);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.SOUL_WINGS,
                player.getPos(),player.getPos(),1.8);
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_WITHER_SHOOT,.7f,.8f);
        var hit=new java.util.HashSet<java.util.UUID>();
        for(int i=-1;i<=1;i++) SkillProjectiles.fire(player,player.getRotationVec(1).rotateY((float)Math.toRadians(i*10)).multiply(1.5),hit);
    }
}
