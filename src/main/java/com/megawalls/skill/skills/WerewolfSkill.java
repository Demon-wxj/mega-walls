package com.megawalls.skill.skills;

import com.megawalls.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;

public class WerewolfSkill extends Skill {
    public WerewolfSkill() {
        super("狼人变身", "8秒速度II；近战30%吸血，每次有效近战触发5格1HP范围伤害", 60);
    }

    @Override
    public void execute(ServerPlayerEntity player) {
        com.megawalls.skill.SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.WOLF_FORM,
                player.getPos(),player.getPos(),1.8);
        // 变身粒子
        player.getServerWorld().spawnParticles(
                ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + .5, player.getZ(), 5, 0.4, 0.2, 0.4, 0.02);
        player.getServerWorld().spawnParticles(
                ParticleTypes.SMOKE, player.getX(), player.getY() + .4, player.getZ(), 6, 0.4, 0.2, 0.4, 0.01);
        for(int t=0;t<160;t+=40)com.megawalls.skill.SkillTasks.later(player,t,()->com.megawalls.skill.SkillEffects.aura(player,ParticleTypes.CRIT,.5));
        // 变身buff
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 1));
        com.megawalls.skill.WerewolfCombat.activate(player);
        // Direct-hit lifesteal and the non-recursive splash are resolved by WerewolfCombat.
        com.megawalls.skill.SkillTasks.later(player, 160, () -> {
                if (player.isAlive() && com.megawalls.skill.WerewolfCombat.remaining(player)==0) {
                    player.getServerWorld().spawnParticles(ParticleTypes.SMOKE,player.getX(),player.getY()+1,player.getZ(),8,.3,.5,.3,.01);
                    sendMessage(player, "§f[狼人变身]§f 变身结束");
                }
        });
        player.playSound(net.minecraft.sound.SoundEvents.ENTITY_WOLF_HOWL, 1.0f, 1.0f);
        sendMessage(player, "§f[狼人变身]§f 8秒速度II、近战30%吸血；有效近战触发5格1HP范围伤害");
    }
}
