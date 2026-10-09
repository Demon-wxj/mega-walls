package com.megawalls.skill.skills;

import com.megawalls.game.GameState;
import com.megawalls.skill.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;

/** Leap applies its landing debuff; independent actual fall damage owns Drop Shock. */
public class SpiderSkill extends Skill {
    public SpiderSkill() {
        super("跳跃", "跃起获得吸收I 5秒；落地5格缓慢II 4秒，落震独立按摔伤触发", 60);
    }
    @Override public void execute(ServerPlayerEntity player) {
        var look=player.getRotationVec(1);
        var horizontal=new net.minecraft.util.math.Vec3d(look.x,0,look.z);
        if(horizontal.lengthSquared()<1.0e-6){
            double yaw=Math.toRadians(player.getYaw());
            horizontal=new net.minecraft.util.math.Vec3d(-Math.sin(yaw),0,Math.cos(yaw));
        }
        double forward=ClassBalanceRules.SPIDER_FORWARD*(player.isSneaking()?.5:1);
        horizontal=horizontal.normalize().multiply(forward);
        player.setVelocity(horizontal.x,ClassBalanceRules.SPIDER_UP,horizontal.z);
        player.fallDistance=0;
        // Send the owner one authoritative impulse. A second velocityModified packet can race
        // with the first upward movement packet, whose vanilla server jump resets Y to 0.42.
        // velocityDirty still updates observers without overwriting the owner's 1.25 impulse.
        player.velocityModified=false;
        player.velocityDirty=true;
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION,100,0));
        player.getServerWorld().spawnParticles(ParticleTypes.CLOUD,player.getX(),player.getY(),player.getZ(),10,.5,.5,.5,.1);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.WEB_LEAP,
                player.getPos(),player.getPos(),2);
        SkillTasks.landing(player,()->{
            SkillEffects.ring(player,5,false);
            SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.WEB_IMPACT,
                    player.getPos(),player.getPos(),4.8);
            for(var enemy:player.getServerWorld().getPlayers()) {
                if(com.megawalls.game.GameEvents.canControl(player,enemy)&&player.squaredDistanceTo(enemy)<=25)
                    enemy.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,80,1));
            }
        });
    }
}
