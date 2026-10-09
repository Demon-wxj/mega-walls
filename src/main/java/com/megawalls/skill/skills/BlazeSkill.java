package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class BlazeSkill extends Skill {
    public BlazeSkill() { super("献祭爆裂", "三枚直击火球；同次施法每个敌人最多10HP，队友会挡球", 20); }
    @Override public int getCooldownTicks() {
        var rules=com.megawalls.passive.MiningTreasure.rules();
        return Math.max(super.getCooldownTicks(),rules.blazeWindupTicks+2*rules.blazeIntervalTicks+1);
    }
    @Override public void execute(ServerPlayerEntity player) {
        int delay = com.megawalls.passive.MiningTreasure.rules().blazeWindupTicks;
        int interval = com.megawalls.passive.MiningTreasure.rules().blazeIntervalTicks;
        SkillEffects.charge(player,0,delay);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.FIRE_CHARGE,
                player.getPos(),player.getPos(),1.5);
        for(int t=5;t<delay;t+=5){final int elapsed=t;SkillTasks.later(player,t,()->{
            SkillEffects.charge(player,elapsed,delay);
            if(elapsed%15==0)SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.FIRE_CHARGE,
                    player.getPos(),player.getPos(),1.5);
        });}
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ITEM_FIRECHARGE_USE,.6f,.7f);
        var hit=new java.util.HashSet<java.util.UUID>();
        for (int i=0;i<3;i++) SkillTasks.later(player,delay+i*interval,()->shoot(player,hit));
    }
    private void shoot(ServerPlayerEntity player,java.util.Set<java.util.UUID> hit){
        player.sendMessage(net.minecraft.text.Text.literal("§6献祭爆裂 §e火球发射！"),true);
        SkillProjectiles.fire(player,player.getRotationVec(1).multiply(1.5),hit);
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_BLAZE_SHOOT,.7f,1f);
    }
}
