package com.megawalls.skill;
import com.megawalls.game.GameState;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import java.util.*;
public final class Summons {
    /** Retains vanilla movement, but all explosions are explicitly non-destructive to map blocks. */
    private static final class MinionCreeper extends CreeperEntity {
        int fuse;
        MinionCreeper(net.minecraft.world.World world){super(EntityType.CREEPER,world);}
        @Override protected void initGoals(){super.initGoals();goalSelector.clear(goal->goal instanceof net.minecraft.entity.ai.goal.CreeperIgniteGoal);}
        @Override public void ignite(){}
        @Override public boolean isIgnited(){return false;}
        @Override public boolean shouldSave(){return false;}
        @Override public void tick(){setFuseSpeed(-1);super.tick();}
        // Falling must not advance the vanilla fuse behind the controlled explosion path.
        @Override public boolean handleFallDamage(float distance,float multiplier,net.minecraft.entity.damage.DamageSource source){return false;}
    }
    private record Pet(MobEntity mob,ServerPlayerEntity owner,int until,net.minecraft.server.world.ServerWorld world,long match,int cls,int phase,boolean deathSpawn,String team){}
    private static final Map<UUID,Pet> pets=new HashMap<>();
    public static void register(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity.getCommandTags().contains("mw_summon")&&!pets.containsKey(entity.getUuid()))entity.discard();
        });
    }
    public static ServerPlayerEntity owner(Entity entity){var pet=entity==null?null:pets.get(entity.getUuid());return pet==null?null:pet.owner();}
    public static boolean enemy(ServerPlayerEntity owner,ServerPlayerEntity other){return !other.getUuid().equals(owner.getUuid())&&GameState.active(other)&&other.getServerWorld()==owner.getServerWorld()&&!owner.isTeammate(other)&&(GameState.isPractice(owner)&&GameState.isPractice(other)||GameState.participant(owner)&&GameState.participant(other)&&GameState.phase(owner)>=2&&GameState.phase(owner)<=3);}
    public static void spawnDeath(ServerPlayerEntity p,int kind,int duration){spawn(p,kind,duration,true);}
    public static void spawn(ServerPlayerEntity p,int kind,int duration){
        spawn(p,kind,duration,false);
    }
    private static Pet pet(MobEntity mob,ServerPlayerEntity p,int duration,boolean deathSpawn){
        return new Pet(mob,p,p.getServer().getTicks()+duration,p.getServerWorld(),com.megawalls.game.MatchFlow.matchId(p.getServer()),com.megawalls.player.MWPlayerData.getServer(p).getClassId(),GameState.phase(p),deathSpawn,p.getScoreboardTeam()==null?"":p.getScoreboardTeam().getName());
    }
    private static void spawn(ServerPlayerEntity p,int kind,int duration,boolean deathSpawn){
        if(kind==11)kind=110+p.getRandom().nextInt(5);
        long owned=pets.values().stream().filter(x->x.owner().getUuid().equals(p.getUuid())&&!x.mob().isRemoved()).count();
        if(owned+(kind==114?2:1)>4)return;
        MobEntity mob=switch(kind){
            case 10 -> new BlazeEntity(EntityType.BLAZE,p.getServerWorld());
            case 7 -> new MinionCreeper(p.getServerWorld());
            case 111 -> new WolfEntity(EntityType.WOLF,p.getServerWorld());
            case 112 -> new PigEntity(EntityType.PIG,p.getServerWorld());
            case 113 -> new SheepEntity(EntityType.SHEEP,p.getServerWorld());
            case 114 -> new SkeletonEntity(EntityType.SKELETON,p.getServerWorld());
            default -> new SpiderEntity(EntityType.SPIDER,p.getServerWorld());
        };
        mob.refreshPositionAndAngles(p.getX(),p.getY(),p.getZ(),p.getYaw(),0);
        if(!p.getServerWorld().isSpaceEmpty(mob))return;
        mob.setPersistent();mob.addCommandTag("mw_summon");
        pets.put(mob.getUuid(),pet(mob,p,duration,deathSpawn));
        if(p.getServerWorld().spawnEntity(mob)){
            if(mob instanceof PigEntity pig)pig.saddle(new ItemStack(Items.SADDLE),net.minecraft.sound.SoundCategory.NEUTRAL);
            if(kind==114){
                mob.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
                var chicken=new ChickenEntity(EntityType.CHICKEN,p.getServerWorld());chicken.refreshPositionAndAngles(p.getX(),p.getY(),p.getZ(),p.getYaw(),0);
                chicken.addCommandTag("mw_summon");pets.put(chicken.getUuid(),pet(chicken,p,duration,deathSpawn));
                if(p.getServerWorld().spawnEntity(chicken))mob.startRiding(chicken,true);
                else pets.remove(chicken.getUuid());
            }
        }else pets.remove(mob.getUuid());
    }
    public static void tick(MinecraftServer server){
        for(var pet:new ArrayList<>(pets.values())){
            var mob=pet.mob();var owner=pet.owner();
            var current=server.getPlayerManager().getPlayer(owner.getUuid());
            String currentTeam=current==null||current.getScoreboardTeam()==null?"":current.getScoreboardTeam().getName();
            if(current==null||current.getServerWorld()!=pet.world()||mob.getWorld()!=pet.world()
                ||pet.match()!=com.megawalls.game.MatchFlow.matchId(server)||!currentTeam.equals(pet.team())
                ||com.megawalls.player.MWPlayerData.getServer(current).getClassId()!=pet.cls()
                ||!RestorationSkillRules.samePhaseLifetime(pet.phase(),GameState.phase(current))
                ||!pet.deathSpawn()&&!GameState.active(current)||mob.isRemoved()||!mob.isAlive()||server.getTicks()>=pet.until()
                ||(!GameState.isPractice(current)&&(GameState.phase(current)<2||GameState.phase(current)>3))){mob.discard();pets.remove(mob.getUuid());continue;}
            if(mob instanceof MinionCreeper creeper){
                var target=mob.getTarget();
                if(target instanceof ServerPlayerEntity enemy&&enemy(owner,enemy)&&mob.squaredDistanceTo(enemy)<=9&&mob.canSee(enemy)) {
                    if(creeper.fuse++==0)pet.world().playSound(null,mob.getX(),mob.getY(),mob.getZ(),net.minecraft.sound.SoundEvents.ENTITY_CREEPER_PRIMED,net.minecraft.sound.SoundCategory.HOSTILE,1,1);
                    if(creeper.fuse>=30){
                        explodeMinion(pet);
                        mob.discard();pets.remove(mob.getUuid());continue;
                    }
                }else creeper.fuse=Math.max(0,creeper.fuse-1);
            }
            if(server.getTicks()%5!=0)continue;
            ServerPlayerEntity target=null;double distance=400;
            for(var p:owner.getServerWorld().getPlayers(p->enemy(owner,p)&&!p.isInvisible()))if(mob.squaredDistanceTo(p)<distance){target=p;distance=mob.squaredDistanceTo(p);}
            mob.setTarget(target);
            if(mob instanceof SheepEntity&&target!=null){
                mob.getNavigation().startMovingTo(target,1.2);
                if(mob.squaredDistanceTo(target)<=6.25){
                    owner.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,mob.getX(),mob.getY(),mob.getZ(),1,0,0,0,0);
                    for(var p:owner.getServerWorld().getPlayers(p->enemy(owner,p)&&mob.squaredDistanceTo(p)<=9))TrueDamage.hitFromSummon(mob,p,4);
                    mob.discard();pets.remove(mob.getUuid());
                }
            }
        }
    }
    private static void explodeMinion(Pet pet){
        var mob=pet.mob();var point=mob.getPos();var world=pet.world();
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,point.x,point.y,point.z,1,0,0,0,0);
        world.playSound(null,point.x,point.y,point.z,net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,net.minecraft.sound.SoundCategory.HOSTILE,1,1);
        // Vanilla strength-3 explosion formula, but only legal opponents receive damage or knockback.
        // Running a vanilla world explosion would still knock friendly players back after damage is vetoed.
        for(var target:world.getPlayers(target->enemy(pet.owner(),target))){
            double distance=target.getPos().distanceTo(point)/6;
            if(distance>1)continue;
            double exposure=net.minecraft.world.explosion.Explosion.getExposure(point,target);
            double impact=(1-distance)*exposure;
            float damage=(float)((impact*impact+impact)*.5*7*6+1);
            if(!target.damage(target.getDamageSources().explosion(mob,mob),damage))continue;
            var direction=target.getEyePos().subtract(point).normalize().multiply(impact);
            target.addVelocity(direction.x,direction.y,direction.z);target.velocityModified=true;
        }
    }
    public static void forget(UUID id){for(var pet:new ArrayList<>(pets.values()))if(pet.owner().getUuid().equals(id)){pet.mob().discard();pets.remove(pet.mob().getUuid());}}
    public static void clear(){pets.values().forEach(p->p.mob().discard());pets.clear();}
}
