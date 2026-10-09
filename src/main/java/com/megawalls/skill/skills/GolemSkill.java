package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
public class GolemSkill extends Skill {
    public GolemSkill() { super("钢铁之击", "4.5格内5HP真实伤害并拉拽", 40); }
    @Override public void execute(ServerPlayerEntity player) {
        SkillVisuals.ironPunch(player);
        SkillEffects.cinematic(player,com.megawalls.network.S2C_SkillPattern.IRON_PUNCH,
                player.getPos(),player.getPos(),4.5);
        SkillEffects.aura(player,new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState()),2);
        SkillEffects.ring(player,4.5,false);
        SkillEffects.sound(player,net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_ATTACK,1,.7f);
        for(var enemy:player.getServerWorld().getPlayers()) {
            if(!GameState.enemy(player,enemy)||player.squaredDistanceTo(enemy)>20.25)continue;
            if(TrueDamage.hit(player,enemy,5)<=0)continue;
            var pull=player.getPos().subtract(enemy.getPos()).normalize();enemy.addVelocity(pull.x*.8,.3,pull.z*.8);enemy.velocityModified=true;
        }
    }
}
