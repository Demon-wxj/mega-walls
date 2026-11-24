package com.megawalls.minigame;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import java.util.*;

/** Fields live in bedwars.json alongside the native settings. A blast never calls World.createExplosion. */
public class BedBowRules extends BedUtilitySettings {
    public boolean bowEnabled=true,bowExplosion=true,bowTerrain=true,bowObsidian=false;
    public int bowDamage=10,bowBlastRadius=3,bowBreakRadius=2,bowWitherSeconds=5,bowKnockback=100;
    public static boolean valid(boolean original,Object rules){if(!original)return false;var c=(BedBowRules)rules;return c.utilitiesValid()&&c.bowDamage>=0&&c.bowDamage<=80&&c.bowBlastRadius>=1&&c.bowBlastRadius<=8&&c.bowBreakRadius>=1&&c.bowBreakRadius<=6&&c.bowWitherSeconds>=0&&c.bowWitherSeconds<=30&&c.bowKnockback>=0&&c.bowKnockback<=200;}
    public static boolean active(){return BedWars.deathmatch()&&ArenaRuleAccess.bed().bowEnabled;}
    public static void legacyHit(WitherSkullEntity e){impact(e,new BlockHitResult(e.getPos(),Direction.UP,e.getBlockPos(),false));}
    public static void impact(WitherSkullEntity skull,HitResult hit){
        if(!(skull.getWorld() instanceof ServerWorld w)||!active()||!BedWars.projectileValid(skull)||!(skull.getOwner() instanceof ServerPlayerEntity owner))return;
        var rules=ArenaRuleAccess.bed();Vec3d at=hit.getPos();if(!BedArena.contains(w,BlockPos.ofFloored(at))||hit.getType()==HitResult.Type.MISS)return;
        Entity direct=hit instanceof EntityHitResult ehr?ehr.getEntity():null;
        var targets=new LinkedHashMap<ServerPlayerEntity,Double>();
        for(var p:w.getPlayers()){
            if(!BedWars.permits(skull,p)||!p.isAlive()||!BedArena.contains(w,p.getBlockPos()))continue;
            if(p==direct){targets.put(p,1d);continue;}
            if(!rules.bowExplosion)continue;
            double d=p.getPos().distanceTo(at)/rules.bowBlastRadius;if(d>=1)continue;
            double exposure=(1-d)*Explosion.getExposure(at,p);if(exposure>0)targets.put(p,exposure);
        }
        if(rules.bowExplosion&&rules.bowTerrain)breakPlaced(w,at,rules);
        var source=new BedBlastDamage(w.getDamageSources().explosion(skull,owner));
        for(var entry:targets.entrySet()){
            var p=entry.getKey();double amount=entry.getValue();if(rules.bowDamage<=0||!p.damage(source,(float)(rules.bowDamage*(0.2+0.8*amount))))continue;
            if(p.isAlive()&&rules.bowWitherSeconds>0)p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER,rules.bowWitherSeconds*20,0),owner);
            if(rules.bowExplosion&&rules.bowKnockback>0){Vec3d dir=p.getPos().subtract(at);p.takeKnockback(amount*rules.bowKnockback/100d,-dir.x,-dir.z);p.velocityModified=true;}
        }
        if(rules.bowExplosion){w.spawnParticles(ParticleTypes.EXPLOSION,at.x,at.y,at.z,1,0,0,0,0);w.playSound(null,at.x,at.y,at.z,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.BLOCKS,1.7f,1f);}
        else w.spawnParticles(ParticleTypes.SMOKE,at.x,at.y,at.z,3,.08,.08,.08,.01);
    }
    // Sample vanilla-style blast rays, with the same step and resistance attenuation. Decide all candidates before editing.
    // Unjournalled terrain is opaque to the blast even when made from wool or glass.
    static void breakPlaced(ServerWorld w,Vec3d hit,BedBowRules c){
        if(!c.bowTerrain||!c.bowExplosion||!BedArena.contains(w,BlockPos.ofFloored(hit)))return;
        var memo=new HashMap<BlockPos,Cell>();var remove=new HashSet<BlockPos>();var cursor=new BlockPos.Mutable();
        for(int a=0;a<12;a++)for(int b=0;b<12;b++)for(int k=0;k<12;k++){
            if(a!=0&&a!=11&&b!=0&&b!=11&&k!=0&&k!=11)continue;
            double x=a/5.5-1,y=b/5.5-1,z=k/5.5-1,len=Math.sqrt(x*x+y*y+z*z);x/=len;y/=len;z/=len;
            double power=c.bowBreakRadius*(.7+w.random.nextFloat()*.6);
            for(double d=.025;d<=c.bowBreakRadius+.025&&power>0;d+=.3,power-=.225){
                cursor.set(hit.x+x*d,hit.y+y*d,hit.z+z*d);Cell cell=memo.get(cursor);
                if(cell==null){var pos=cursor.toImmutable();cell=cell(w,pos,c);memo.put(pos,cell);}
                if(cell.solid){if(!cell.removable)break;power-=(cell.resistance+.3)*.3;if(power>0)remove.add(cell.pos);}
            }
        }
        for(BlockPos pos:remove)if(BedArena.added(w,pos))BedArena.set(w,pos,Blocks.AIR.getDefaultState());
    }
    private record Cell(BlockPos pos,boolean solid,boolean removable,float resistance){}
    private static Cell cell(ServerWorld w,BlockPos pos,BedBowRules c){
        if(!BedArena.contains(w,pos)||!w.isChunkLoaded(pos))return new Cell(pos,true,false,0);
        var state=w.getBlockState(pos);if(state.isAir())return new Cell(pos,false,false,0);
        if(!BedArena.added(w,pos)||state.getBlock() instanceof BedBlock||state.hasBlockEntity()||!state.getFluidState().isEmpty()||state.isIn(BlockTags.WITHER_IMMUNE)||state.getHardness(w,pos)<0||state.isReplaceable())return new Cell(pos,true,false,0);
        boolean obs=state.isOf(Blocks.OBSIDIAN)||state.isOf(Blocks.CRYING_OBSIDIAN);
        return new Cell(pos,true,!obs||c.bowObsidian,obs?0:state.getBlock().getBlastResistance());
    }
}
