package com.megawalls.skill;

import com.megawalls.game.GameState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.particle.ParticleTypes;
import java.util.*;

/** Server ray-swept projectiles. No vanilla projectile damage can accidentally stack with the ability. */
public final class SkillProjectiles {
    private static final class Shot {
        final ServerPlayerEntity owner; final ServerWorld world; final int cls, phase; final long match; final Set<UUID> damaged;
        Vec3d pos, velocity; int life = 80;
        net.minecraft.entity.projectile.ProjectileEntity model;
        final Set<Integer> collisionSounds;
        Shot(ServerPlayerEntity p, Vec3d v, Set<UUID> hit) { owner=p; world=p.getServerWorld(); cls=com.megawalls.player.MWPlayerData.getServer(p).getClassId(); phase=GameState.phase(p);match=com.megawalls.game.MatchFlow.matchId(p.getServer()); pos=p.getEyePos().add(p.getRotationVec(1).multiply(.4)); velocity=v; damaged=hit; collisionSounds=shots.stream().filter(s->s.damaged==hit).findFirst().map(s->s.collisionSounds).orElseGet(HashSet::new); }
    }
    private static final List<Shot> shots = new ArrayList<>();
    public static void fire(ServerPlayerEntity p, Vec3d velocity, Set<UUID> hit) {
        if(!GameState.active(p)||GameState.phase(p)==1)return;
        var shot=new Shot(p,velocity,hit);
        shot.model=shot.cls==2?new SkillModels.Arrow(SkillModels.ARROW,shot.world):shot.cls==8?new SkillModels.Skull(SkillModels.SKULL,shot.world):new SkillModels.Fireball(SkillModels.FIREBALL,shot.world);
        shot.model.setOwner(p);moveModel(shot);shot.world.spawnEntity(shot.model);shots.add(shot);
        int effect=shot.cls==10?com.megawalls.network.S2C_SkillPattern.FIREBALL:
                shot.cls==8?com.megawalls.network.S2C_SkillPattern.SOUL_TRAIL:-1;
        if(effect>=0)SkillEffects.cinematic(p,effect,shot.pos,shot.pos.add(shot.velocity.normalize()),.7);
    }
    private static void moveModel(Shot shot){
        shot.model.setPosition(shot.pos);shot.model.setVelocity(shot.velocity);
        shot.model.setYaw((float)Math.toDegrees(Math.atan2(shot.velocity.x,shot.velocity.z)));
        shot.model.setPitch((float)Math.toDegrees(Math.atan2(shot.velocity.y,shot.velocity.horizontalLength())));
        shot.model.velocityDirty=true;
    }
    private static void remove(Shot shot){shot.model.discard();shots.remove(shot);}
    public static void forget(UUID uuid) { for (var shot : new ArrayList<>(shots)) if (shot.owner.getUuid().equals(uuid)) remove(shot); }
    public static void clear() { for(var shot:shots)shot.model.discard();shots.clear(); }
    public static void tick(MinecraftServer server) {
        for (var shot : new ArrayList<>(shots)) {
            var p=shot.owner;
            if (!GameState.active(p) || p.isRemoved() || p.getServerWorld()!=shot.world || --shot.life<=0
                    || shot.match!=com.megawalls.game.MatchFlow.matchId(server)||!RestorationSkillRules.samePhaseLifetime(shot.phase,GameState.phase(p))
                    || com.megawalls.player.MWPlayerData.getServer(p).getClassId()!=shot.cls) { remove(shot); continue; }
            Vec3d end=shot.pos.add(shot.velocity);
            if (!shot.world.isChunkLoaded(BlockPos.ofFloored(end)) || !shot.world.getWorldBorder().contains(BlockPos.ofFloored(end))) { remove(shot); continue; }
            var block=shot.world.raycast(new RaycastContext(shot.pos,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
            Vec3d impact=block.getPos(); boolean collided=block.getType()!=HitResult.Type.MISS;
            ServerPlayerEntity directTarget=null;
            double nearest=shot.pos.squaredDistanceTo(impact);
            for(var enemy:shot.world.getPlayers()) {
                // Historical fireballs can be intercepted by teammates. Other skills keep their existing collision rules.
                if(enemy==p||!GameState.active(enemy)||shot.cls!=10&&!GameState.enemy(p,enemy))continue;
                var hit=enemy.getBoundingBox().expand(.15).raycast(shot.pos,end);
                if(hit.isPresent() && shot.pos.squaredDistanceTo(hit.get())<=nearest) {impact=hit.get();nearest=shot.pos.squaredDistanceTo(impact);collided=true;directTarget=enemy;}
            }
            for(int i=0;i<2;i++) {
                Vec3d point=shot.pos.lerp(impact,i/2d);
                shot.world.spawnParticles(shot.cls==10?ParticleTypes.FLAME:shot.cls==8?ParticleTypes.SOUL:ParticleTypes.CRIT,point.x,point.y,point.z,1,0,0,0,0);
            }
            if(!collided&&server.getTicks()%3==0&&(shot.cls==10||shot.cls==8))
                SkillEffects.cinematic(p,shot.cls==10?com.megawalls.network.S2C_SkillPattern.FIREBALL:
                        com.megawalls.network.S2C_SkillPattern.SOUL_TRAIL,shot.pos,shot.pos.add(shot.velocity.normalize()),.7);
            if(collided) { explode(p,shot.cls,impact,shot.damaged,directTarget,shot.collisionSounds.add(server.getTicks()/2),shot.velocity);remove(shot); }
            else {shot.pos=end;if(shot.cls==2)shot.velocity=shot.velocity.multiply(.99).add(0,-.05,0);moveModel(shot);}
        }
    }
    public static void explode(ServerPlayerEntity p,int cls,Vec3d point,Set<UUID> damaged) {
        explode(p,cls,point,damaged,null,true,p.getRotationVec(1));
    }
    private static void explode(ServerPlayerEntity p,int cls,Vec3d point,Set<UUID> damaged,ServerPlayerEntity directTarget,boolean sound,Vec3d visualDirection) {
        if(!GameState.active(p)||GameState.phase(p)==1)return;
        var world=p.getServerWorld(); double radius=cls==2?4:3;
        SkillEffects.cinematic(p,cls==10?com.megawalls.network.S2C_SkillPattern.FIRE_IMPACT:
                cls==8?com.megawalls.network.S2C_SkillPattern.SOUL_IMPACT:
                com.megawalls.network.S2C_SkillPattern.ARROW_IMPACT,point,point.add(visualDirection.normalize()),cls==10?.8:radius);
        if(cls!=10)world.spawnParticles(ParticleTypes.EXPLOSION,point.x,point.y,point.z,1,0,0,0,0);
        world.spawnParticles(cls==10?ParticleTypes.FLAME:cls==8?ParticleTypes.SOUL:ParticleTypes.CRIT,point.x,point.y,point.z,10,.3,.3,.3,.04);
        if(cls==2||cls==8) Terrain.blast(p,point,cls==2?2.5:1.5);
        if(sound)world.playSound(null,point.x,point.y,point.z,cls==10?net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH:net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.PLAYERS,.5f,cls==10?1.4f:1f);
        for(var enemy:world.getPlayers(enemy -> GameState.enemy(p,enemy))) {
            if(cls==10&&enemy!=directTarget)continue;
            if(!GameState.enemy(p,enemy) || enemy.getPos().squaredDistanceTo(point)>radius*radius || damaged.contains(enemy.getUuid()))continue;
            float amount=cls==2?RestorationSkillRules.EXPLOSIVE_ARROW_DAMAGE:cls==8?7:RestorationSkillRules.BLAZE_DIRECT_DAMAGE;
            if(TrueDamage.hit(p,enemy,amount)<=0)continue;
            damaged.add(enemy.getUuid());
            Vec3d push=enemy.getPos().subtract(point).normalize();enemy.addVelocity(push.x*.4,.2,push.z*.4);enemy.velocityModified=true;
        }
    }
}
