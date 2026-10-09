package com.megawalls.skill.skills;

import com.megawalls.skill.Skill;
import com.megawalls.skill.ClassBalanceRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;

public class SquidSkill extends Skill {
    public SquidSkill() {
        super("鱿鱼飞溅", ClassBalanceRules.squidDescription(), 40);
    }

    @Override
    public void execute(ServerPlayerEntity player) {
        com.megawalls.skill.SkillEffects.wave(player,ParticleTypes.SPLASH,5,true);
        com.megawalls.skill.SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.TIDAL,
                player.getPos(),player.getPos(),4.5);
        // 水粒子特效
        player.getServerWorld().spawnParticles(
                ParticleTypes.SPLASH, player.getX(), player.getY() + .3, player.getZ(), 10, 1, .15, 1, 0.08);
        player.getServerWorld().spawnParticles(
                ParticleTypes.FALLING_WATER, player.getX(), player.getY() + .5, player.getZ(), 6, 1, .2, 1, 0.05);
        // Pull and heal only after accepted damage, including absorption consumed.
        float totalHeal = 0;
        int targets = 0;
        for (var enemy : player.getServerWorld().getPlayers()) {
            if (com.megawalls.game.GameState.enemy(player, enemy) && player.squaredDistanceTo(enemy) <= ClassBalanceRules.SQUID_RADIUS * ClassBalanceRules.SQUID_RADIUS) {
                float lost = com.megawalls.skill.TrueDamage.hit(player, enemy, ClassBalanceRules.SQUID_DAMAGE);
                if (lost > 0) {
                    Vec3d pull = player.getPos().subtract(enemy.getPos()).normalize().multiply(0.5);
                    enemy.setVelocity(pull.x,.3,pull.z);enemy.velocityModified=true;
                    totalHeal += lost * ClassBalanceRules.SQUID_HEAL_RATIO;
                    targets++;
                    var from=enemy.getPos().add(0,1,0);var to=player.getPos().add(0,1,0);
                    if(player.getServerWorld().raycast(new net.minecraft.world.RaycastContext(from,to,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player)).getType()==net.minecraft.util.hit.HitResult.Type.MISS)
                        com.megawalls.skill.SkillEffects.tether(enemy,player,ParticleTypes.BUBBLE);
                }

            }
        }
        int refund = Math.min(targets * ClassBalanceRules.SQUID_REFUND_PER_TARGET, ClassBalanceRules.SQUID_REFUND_CAP);
        com.megawalls.skill.EnergyRules.gain(player, refund);
        float healthBefore=player.getHealth();
        player.heal(Math.min(totalHeal, ClassBalanceRules.SQUID_HEAL_CAP));
        if(player.getHealth()>healthBefore)com.megawalls.skill.SkillEffects.aura(player,ParticleTypes.HEART,.5);
        player.playSound(net.minecraft.sound.SoundEvents.ENTITY_SQUID_SQUIRT, 1.0f, 1.0f);
        sendMessage(player, "§f[鱿鱼飞溅]§f 有效命中 " + targets + " 人；治疗 "
                + String.format(java.util.Locale.ROOT,"%.1f",player.getHealth()-healthBefore) + " HP；返能 " + refund);
    }
}
