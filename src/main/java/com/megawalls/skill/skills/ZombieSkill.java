package com.megawalls.skill.skills;

import com.megawalls.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;

public class ZombieSkill extends Skill {
    public ZombieSkill() {
        super("治愈之环", "治愈自己7HP，治愈周围队友6HP（旧版满级）", 100);
    }

    @Override
    public void execute(ServerPlayerEntity player) {
        float before=player.getHealth();
        player.heal(7.0f);
        float selfHeal=player.getHealth()-before, teamHeal=0;
        int healedAllies=0;
        com.megawalls.skill.SkillEffects.wave(player,net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,10,false);
        com.megawalls.skill.SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.HEAL,
                player.getPos(),player.getPos(),4.5);
        if(player.getHealth()>before)com.megawalls.skill.SkillEffects.aura(player, net.minecraft.particle.ParticleTypes.HEART, .6);
        for (var ally : player.getServerWorld().getPlayers()) {
            if (ally != player && com.megawalls.game.GameState.active(ally) && player.isTeammate(ally) && player.squaredDistanceTo(ally) <= 100) {
                float allyBefore=ally.getHealth();
                ally.heal(com.megawalls.player.MWPlayerData.getServer(ally).getClassId()==1?3:6);
                if(ally.getHealth()>allyBefore){
                    teamHeal+=ally.getHealth()-allyBefore;healedAllies++;
                    com.megawalls.skill.SkillEffects.aura(ally,net.minecraft.particle.ParticleTypes.HEART,.6);
                    com.megawalls.skill.SkillEffects.cinematic(ally,com.megawalls.network.S2C_SkillPattern.HEAL,
                            ally.getPos(),ally.getPos(),.75);
                }
            }
        }
        com.megawalls.skill.SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE,.7f,1f);
        sendMessage(player, String.format(java.util.Locale.ROOT,"§a[治愈之环]§f 实际自愈 %.1f HP；治疗 %d 名队友，共 %.1f HP",selfHeal,healedAllies,teamHeal));
    }
}
