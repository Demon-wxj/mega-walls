package com.megawalls.skill.skills;
import com.megawalls.skill.*;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.util.*;
public class MoleSkill extends Skill {
    public MoleSkill(){super("挖挖挖","沿视线冲锋8格，路径8HP真实伤害",20);}
    @Override public void execute(ServerPlayerEntity p){
        SkillEffects.sound(p,net.minecraft.sound.SoundEvents.BLOCK_GRAVEL_BREAK,1,.7f);
        SkillTasks.later(p,ClassBalanceRules.MOLE_WINDUP,new Dig(p)::tick);
    }
    private static final class Dig {
        final ServerPlayerEntity p;final Vec3d origin,direction;final Set<UUID> hit=new HashSet<>();
        Vec3d last;int step;
        Dig(ServerPlayerEntity p){this.p=p;origin=p.getPos();last=origin;direction=p.getRotationVec(1);}
        void tick(){
            // Four half-block sweeps per tick: faster motion without skipping obstacles or targets.
            int segments=ClassBalanceRules.MOLE_SEGMENTS_PER_TICK;
            for(int i=0;i<segments&&step<16;i++)if(!step())return;
            if(step<16)SkillTasks.later(p,1,this::tick);
        }
        boolean step(){
            if(p.getPos().squaredDistanceTo(last)>9)return false;
            var world=p.getServerWorld();
            var next=origin.add(direction.multiply(++step*.5));var box=p.getBoundingBox().offset(next.subtract(p.getPos()));
            if(!GameState.insideArena(p,box))return false;
            for(var pos:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY,box.minZ),BlockPos.ofFloored(box.maxX-.001,box.maxY-.001,box.maxZ-.001))){
                if(!world.isChunkLoaded(pos)||!world.getWorldBorder().contains(pos))return false;
                var state=world.getBlockState(pos);if(state.getCollisionShape(world,pos).isEmpty())continue;
                if(!digBreakable(world,pos))return false;
            }
            var center=BlockPos.ofFloored(next);
            var material=world.getBlockState(center);
            if(material.isAir())material=world.getBlockState(center.down());
            for(var pos:BlockPos.iterate(center.add(-1,-1,-1),center.add(1,1,1)))
                if(digBreakable(world,pos))world.breakBlock(pos,true,p);
            var damageBox=new Box(center).expand(1);
            for(var enemy:world.getPlayers(enemy -> enemy.getBoundingBox().intersects(damageBox)))
                if(GameState.enemy(p,enemy)&&!hit.contains(enemy.getUuid())){
                    var velocity=enemy.getVelocity();
                    if(TrueDamage.hit(p,enemy,8)>0)hit.add(enemy.getUuid());
                    enemy.setVelocity(velocity);
                }
            p.requestTeleport(next.x,next.y,next.z);p.fallDistance=0;last=next;
            if(step%2==0)SkillEffects.cinematic(p,com.megawalls.network.S2C_SkillPattern.MOLE_BORE,
                    next,next.add(direction),1.5);
            if(step%2==0&&!material.isAir())world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,material),next.x,next.y+.3,next.z,5,.6,.2,.6,.04);
            if(step%4==0)SkillEffects.sound(p,net.minecraft.sound.SoundEvents.BLOCK_GRAVEL_BREAK,.5f,.8f);
            return true;
        }
        boolean digBreakable(net.minecraft.server.world.ServerWorld world,BlockPos pos){
            return !world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES)&&Terrain.breakable(world,pos);
        }
    }
}
