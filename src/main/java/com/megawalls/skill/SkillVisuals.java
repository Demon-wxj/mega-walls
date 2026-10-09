package com.megawalls.skill;

import com.megawalls.game.GameState;
import com.megawalls.player.MWPlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.*;
import net.minecraft.sound.*;
import java.util.*;

/** Bounded, short-lived visual models. Damage is owned exclusively by the skill. */
public final class SkillVisuals {
    private record Visual(ServerPlayerEntity owner,ServerWorld world,int cls,int phase,long match,Entity entity,Vec3d origin,int born,int life,boolean impactSound) {}
    private static final List<Visual> active=new ArrayList<>();
    private static void add(ServerPlayerEntity p,Entity entity,Vec3d origin,int life,boolean impactSound){
        entity.setPosition(origin);
        if(p.getServerWorld().spawnEntity(entity))active.add(new Visual(p,p.getServerWorld(),MWPlayerData.getServer(p).getClassId(),GameState.phase(p),com.megawalls.game.MatchFlow.matchId(p.getServer()),entity,origin,p.getServer().getTicks(),life,impactSound));
    }
    public static void ironPunch(ServerPlayerEntity p){
        for(int i=0;i<6;i++){
            double a=i*Math.PI/3;
            add(p,new SkillModels.Iron(SkillModels.IRON,p.getServerWorld()),p.getPos().add(Math.cos(a)*3,1.5,Math.sin(a)*3),12,false);
        }
    }
    public static void lightning(ServerPlayerEntity p,Vec3d at){
        add(p,new SkillModels.Bolt(SkillModels.BOLT,p.getServerWorld()),at,8,false);
        p.getServerWorld().spawnParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y+1,at.z,18,.3,.8,.3,.08);
        SkillEffects.cinematic(p,com.megawalls.network.S2C_SkillPattern.LIGHTNING,at,at.add(0,6,0),1.4);
    }
    public static void forget(UUID id){for(var v:new ArrayList<>(active))if(v.owner.getUuid().equals(id))remove(v);}
    public static void clear(){for(var v:active)v.entity.discard();active.clear();}
    private static void remove(Visual v){v.entity.discard();active.remove(v);}
    public static void tick(MinecraftServer server){
        for(var v:new ArrayList<>(active)){
            int age=server.getTicks()-v.born;
            if(v.entity.isRemoved()||!GameState.active(v.owner)||v.owner.isRemoved()||v.owner.getServerWorld()!=v.world
                ||MWPlayerData.getServer(v.owner).getClassId()!=v.cls||!RestorationSkillRules.samePhaseLifetime(v.phase,GameState.phase(v.owner))
                ||v.match!=com.megawalls.game.MatchFlow.matchId(server)||age>=v.life){remove(v);continue;}
            if(v.entity instanceof SkillModels.Iron){
                double fall=1.5*Math.min(1,age*age/64d);
                v.entity.setPosition(v.origin.add(0,-fall,0));
                if(age==8){
                    var at=v.entity.getPos();
                    v.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState()),at.x,at.y+.15,at.z,3,.25,.1,.25,.04);
                    if(v.impactSound)v.world.playSound(null,at.x,at.y,at.z,SoundEvents.BLOCK_ANVIL_LAND,SoundCategory.PLAYERS,.2f,1.5f);
                }
            }
        }
    }
}
