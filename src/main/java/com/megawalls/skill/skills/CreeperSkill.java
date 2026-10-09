package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class CreeperSkill extends Skill {
    public CreeperSkill() { super("引爆", "4秒后引爆，5格内10HP（边缘衰减待核）", 20); }
    @Override public void execute(ServerPlayerEntity player) {
        sendMessage(player,"§a引爆倒计时：4秒");
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_CREEPER_PRIMED,1,1);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.CREEPER_FUSE,
                player.getPos(),player.getPos(),1.25);
        for(int i=0;i<8;i++){final int pulse=i;SkillTasks.later(player,i*10,()->{
            SkillEffects.ring(player,.8+pulse*.2,true);
            SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.CREEPER_FUSE,
                    player.getPos(),player.getPos(),.8+pulse*.2);
            player.sendMessage(net.minecraft.text.Text.literal("§a引爆 §e"+String.format(java.util.Locale.ROOT,"%.1f",(RestorationSkillRules.CREEPER_WINDUP-pulse*10)/20d)+"秒"),true);
        });}
        SkillTasks.later(player,RestorationSkillRules.CREEPER_WINDUP,()->{
            SkillEffects.explosion(player,5);
            SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.CREEPER_BLAST,
                    player.getPos(),player.getPos(),5);
            Terrain.blast(player,player.getPos().add(0,.5,0),3,player.getBlockY());
            for(var enemy:player.getServerWorld().getPlayers())
                if(player.squaredDistanceTo(enemy)<=25) TrueDamage.hit(player,enemy,RestorationSkillRules.CREEPER_DAMAGE);
        });
    }
}
