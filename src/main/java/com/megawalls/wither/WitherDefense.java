package com.megawalls.wither;

import com.megawalls.game.*;
import com.megawalls.skill.SkillModels;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.effect.*;
import java.util.*;

/** Stationary core defense: server-owned collision, no vanilla griefing or friendly fire. */
public final class WitherDefense {
    private static final Map<UUID,WitherEntity> cores=new HashMap<>();
    private static final Map<UUID,Integer> skullAt=new HashMap<>(),rageAt=new HashMap<>();
    private static final List<Shot> shots=new ArrayList<>();
    private static final class Shot {
        final WitherEntity core;final ServerWorld world;final SkillModels.Skull model;
        Vec3d pos;final Vec3d velocity;int life=60;
        Shot(WitherEntity core,ServerPlayerEntity target){
            this.core=core;world=(ServerWorld)core.getWorld();pos=core.getPos().add(0,2.2,0);
            velocity=target.getEyePos().subtract(pos).normalize().multiply(.65);
            model=new SkillModels.Skull(SkillModels.SKULL,world);model.setOwner(core);move();world.spawnEntity(model);
        }
        void move(){model.setPosition(pos);model.setVelocity(velocity);model.velocityDirty=true;}
    }
    public static void register(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(e instanceof WitherEntity core)cores.put(core.getUuid(),core);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((e,w)->{if(e instanceof WitherEntity){cores.remove(e.getUuid());skullAt.remove(e.getUuid());rageAt.remove(e.getUuid());}});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->{clear();cores.clear();});
    }
    public static void clear(){for(var shot:shots)shot.model.discard();shots.clear();skullAt.clear();rageAt.clear();}
    public static boolean active(WitherEntity core){
        if(!(core.getWorld() instanceof ServerWorld world)||!core.isAlive()||core.isRemoved()||MatchFlow.phase(world.getServer())!=2)return false;
        String team=MatchTeams.core(core);
        return team!=null&&MatchTeams.active(world.getServer()).contains(team)&&GameState.score(world.getScoreboard(),"#global","mw_wither_"+team)>0;
    }
    public static boolean enemy(WitherEntity core,ServerPlayerEntity p){
        return active(core)&&p.getServerWorld()==core.getWorld()&&GameState.participant(p)&&GameState.active(p)
            &&!p.isCreative()&&!MatchTeams.core(core).equals(p.getScoreboardTeam().getName());
    }
    public static void tick(MinecraftServer server){
        if(MatchFlow.phase(server)!=2){clear();return;}
        int now=server.getTicks();
        for(var core:new ArrayList<>(cores.values())){
            if(core.getServer()!=server||!active(core))continue;
            var world=(ServerWorld)core.getWorld();String team=MatchTeams.core(core);
            boolean rage=MatchFlow.timer(server)<MapConfig.get(server,"rage")*20;
            if(rage&&now%20==0)for(var p:world.getPlayers(p->GameState.participant(p)&&GameState.active(p)&&team.equals(p.getScoreboardTeam().getName())&&core.squaredDistanceTo(p)<=400)){
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,40,2,true,false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,40,1,true,false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION,40,1,true,false));
            }
            var target=world.getPlayers(p->enemy(core,p)&&core.squaredDistanceTo(p)<=900&&core.canSee(p)).stream().min(Comparator.comparingDouble(core::squaredDistanceTo)).orElse(null);
            if(target==null)continue;
            if(now>=skullAt.getOrDefault(core.getUuid(),0)){
                shots.add(new Shot(core,target));skullAt.put(core.getUuid(),now+40);
                world.playSound(null,core.getBlockPos(),net.minecraft.sound.SoundEvents.ENTITY_WITHER_SHOOT,net.minecraft.sound.SoundCategory.HOSTILE,.8f,1);
            }
            if(rage&&now>=rageAt.getOrDefault(core.getUuid(),0)){
                special(core,target,world.getRandom().nextInt(4));rageAt.put(core.getUuid(),now+60);
            }
        }
        for(var shot:new ArrayList<>(shots)){
            if(!active(shot.core)||--shot.life<=0){remove(shot);continue;}
            Vec3d end=shot.pos.add(shot.velocity);
            if(!shot.world.isChunkLoaded(BlockPos.ofFloored(end))||!shot.world.getWorldBorder().contains(BlockPos.ofFloored(end))){remove(shot);continue;}
            var block=shot.world.raycast(new net.minecraft.world.RaycastContext(shot.pos,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,shot.core));
            boolean blocked=block.getType()!=net.minecraft.util.hit.HitResult.Type.MISS;
            double closest=shot.pos.squaredDistanceTo(block.getPos());ServerPlayerEntity hit=null;
            for(var p:shot.world.getPlayers(p->enemy(shot.core,p))){
                var intersection=p.getBoundingBox().expand(.15).raycast(shot.pos,end);
                if(intersection.isPresent()&&shot.pos.squaredDistanceTo(intersection.get())<closest){closest=shot.pos.squaredDistanceTo(intersection.get());hit=p;}
            }
            if(hit!=null||blocked){
                Vec3d impact=hit==null?block.getPos():shot.pos.add(shot.velocity.normalize().multiply(Math.sqrt(closest)));
                shot.world.spawnParticles(ParticleTypes.SMOKE,impact.x,impact.y,impact.z,6,.15,.15,.15,.02);
                if(hit!=null)hit.damage(hit.getDamageSources().mobAttack(shot.core),5);
                remove(shot);
            }else{shot.pos=end;shot.move();}
        }
    }
    private static void remove(Shot shot){shot.model.discard();shots.remove(shot);}
    public static void special(WitherEntity core,ServerPlayerEntity target,int skill){
        if(!enemy(core,target)||core.squaredDistanceTo(target)>900||!core.canSee(target)
            ||MatchFlow.timer(target.getServer())>=MapConfig.get(target.getServer(),"rage")*20)return;
        var world=target.getServerWorld();float damage=switch(skill){case 0->8;case 1->3;case 2->5;default->4;};
        var kind=skill==0?net.minecraft.entity.damage.DamageTypes.LIGHTNING_BOLT:skill==1?net.minecraft.entity.damage.DamageTypes.ON_FIRE:net.minecraft.entity.damage.DamageTypes.MOB_ATTACK;
        var type=world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getEntry(kind.getValue()).orElseThrow();
        if(!target.damage(new net.minecraft.entity.damage.DamageSource(type,core),damage)||!target.isAlive())return;
        switch(skill){
            case 0 -> world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,target.getX(),target.getY()+1,target.getZ(),20,.5,1,.5,.1);
            case 1 -> {target.setFireTicks(100);world.spawnParticles(ParticleTypes.FLAME,target.getX(),target.getY()+1,target.getZ(),12,.4,.8,.4,.03);}
            case 2 -> {target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,100,1));world.spawnParticles(ParticleTypes.CLOUD,target.getX(),target.getY(),target.getZ(),15,1,.1,1,.1);}
            default -> {var direction=target.getPos().subtract(core.getPos()).normalize();target.setVelocity(direction.x*1.5,.8,direction.z*1.5);target.velocityModified=true;}
        }
    }
}
