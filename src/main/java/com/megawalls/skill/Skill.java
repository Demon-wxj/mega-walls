package com.megawalls.skill;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 技能基类（满级数据，无等级系统）
 */
public abstract class Skill {
    protected final String name;
    protected final String description;
    protected final int cooldownTicks;
    protected final int energyCost;

    public Skill(String name, String description, int cooldownTicks) {
        this.name = name;
        this.description = description;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = 100;
    }

    public abstract void execute(ServerPlayerEntity player);
    public boolean canExecute(ServerPlayerEntity player) { return true; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getEnergyCost() { return energyCost; }

    protected void sendMessage(ServerPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), false);
    }

    /**
     * 发送技能命中消息，显示敌人剩余血量（心数）
     */
    protected void sendHitMessage(ServerPlayerEntity player, LivingEntity target) {
        float hearts = target.getHealth() / 2.0f;
        String healthStr = String.format("%.1f", hearts);
        player.sendMessage(Text.literal(
                "§f命中 §e" + target.getName().getString() + " §f(剩余 §c" + healthStr + "❤§f)"
        ), false);
    }

    /**
     * 发送技能命中消息（你的XX命中了：玩家名）
     */
    protected void sendSkillHitMessage(ServerPlayerEntity player, LivingEntity target) {
        player.sendMessage(Text.literal(
                "§f你的 §a" + name + " §f命中了：§e" + target.getName().getString()
        ), false);
        sendHitMessage(player, target);
    }
}
