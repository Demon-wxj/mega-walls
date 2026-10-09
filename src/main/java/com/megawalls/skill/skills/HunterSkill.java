package com.megawalls.skill.skills;

import com.megawalls.skill.HunterTracker;
import com.megawalls.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class HunterSkill extends Skill {
    public HunterSkill() {
        super("鹰眼", "14秒内弓箭自动追踪最近敌人（满级）", 140);
    }

    @Override
    public void execute(ServerPlayerEntity player) {
        HunterTracker.activateEagleEye(player);
        com.megawalls.skill.SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.EAGLE_EYE,
                player.getPos(),player.getPos(),1.4);
        com.megawalls.skill.SkillEffects.aura(player,net.minecraft.particle.ParticleTypes.ENCHANT,.7);
        com.megawalls.skill.SkillTasks.later(player,280,()->{
            if(HunterTracker.remaining(player)==0)player.sendMessage(net.minecraft.text.Text.literal("§2鹰眼结束"),true);
        });
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 280, 0));
        player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
        sendMessage(player, "§2[鹰眼]§f 14秒弓箭自动追踪！夜视");
    }
}
