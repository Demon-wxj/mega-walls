package com.megawalls.passive;
import com.megawalls.game.GameState;
import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.EnergyRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.*;
import java.util.*;
/** Class damage bonuses use percentages, not the vanilla Strength potion's flat damage. */
public final class LegacyBattle {
    private record Buff(int cls,int end,float multiplier){}
    private record Fall(DamageSource source,float healthAndAbsorption,int tick,net.minecraft.server.world.ServerWorld world,long match){}
    private static final Map<UUID,Map<String,Buff>> buffs=new HashMap<>();
    private static final Map<UUID,Fall> falls=new HashMap<>();
    private static final Set<UUID> charged=new HashSet<>();
    private static Buff strongest(ServerPlayerEntity p){
        var sources=buffs.get(p.getUuid());if(sources==null)return null;
        int now=p.getServer().getTicks(),cls=MWPlayerData.getServer(p).getClassId();
        sources.values().removeIf(b->b.end()<=now||b.cls()!=cls);
        if(sources.isEmpty()){buffs.remove(p.getUuid());return null;}
        return sources.values().stream().max(Comparator.comparingDouble(Buff::multiplier).thenComparingInt(Buff::end)).orElse(null);
    }
    public static int buffRemaining(ServerPlayerEntity p){var b=strongest(p);return b==null?0:b.end()-p.getServer().getTicks();}
    public static void empower(ServerPlayerEntity p,float bonus,int ticks){empower(p,"class",bonus,ticks);}
    /** Each source expires independently; weaker bonuses cannot replace or prolong stronger ones. */
    public static void empower(ServerPlayerEntity p,String source,float bonus,int ticks){
        if(!Float.isFinite(bonus)||bonus<0||ticks<=0)return;
        buffs.computeIfAbsent(p.getUuid(),id->new HashMap<>()).put(source,
                new Buff(MWPlayerData.getServer(p).getClassId(),p.getServer().getTicks()+ticks,1+bonus));
    }
    public static float adjust(ServerPlayerEntity victim,DamageSource source,float amount){
        if(!GameState.active(victim))return amount;
        if(source.isOf(DamageTypes.FALL)) {
            int cls=MWPlayerData.getServer(victim).getClassId();
            if(cls==5)return amount*.25f;
            if(cls==3)falls.put(victim.getUuid(),new Fall(source,victim.getHealth()+victim.getAbsorptionAmount(),victim.getServer().getTicks(),victim.getServerWorld(),com.megawalls.game.MatchFlow.matchId(victim.getServer())));
        }
        if(!source.isOf(DamageTypes.PLAYER_ATTACK)||!(source.getAttacker() instanceof ServerPlayerEntity p)||!GameState.enemy(p,victim))return amount;
        var buff=strongest(p);
        if(buff!=null)amount*=buff.multiplier();
        return amount;
    }
    /** Independent 2017 passive: only real fall damage creates a shock, including a fatal landing. */
    public static void finishFall(ServerPlayerEntity p,DamageSource source){
        var fall=falls.remove(p.getUuid());
        if(fall==null||fall.source()!=source||!source.isOf(DamageTypes.FALL)
                ||fall.tick()!=p.getServer().getTicks()||fall.world()!=p.getServerWorld()
                ||fall.match()!=com.megawalls.game.MatchFlow.matchId(p.getServer())||MWPlayerData.getServer(p).getClassId()!=3)return;
        float lost=Math.max(0,fall.healthAndAbsorption()-Math.max(0,p.getHealth())-p.getAbsorptionAmount());
        float amount=com.megawalls.skill.RestorationSkillRules.dropShockDamage(lost);
        if(amount<=0)return;
        com.megawalls.skill.SkillEffects.explosion(p,com.megawalls.skill.RestorationSkillRules.DROP_SHOCK_RADIUS);
        for(var enemy:p.getServerWorld().getPlayers()) {
            if(p.squaredDistanceTo(enemy)>16)continue;
            if(p.isAlive())com.megawalls.skill.TrueDamage.hit(p,enemy,amount);
            else com.megawalls.skill.TrueDamage.hitFromDeath(p,enemy,amount,source);
        }
        if(p.isAlive())com.megawalls.skill.Terrain.blast(p,p.getPos(),2.5);
    }
    public static void tick(ServerPlayerEntity p){
        var d=MWPlayerData.getServer(p);
        if(d.getClassId()==5&&d.getEnergy()==100&&GameState.active(p)){
            if(!charged.contains(p.getUuid())&&PassiveHandler.ready(p,"soulcharge",com.megawalls.skill.RestorationSkillRules.SOUL_CHARGE_COOLDOWN)){
                charged.add(p.getUuid());p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,200,0));
            }
        }else charged.remove(p.getUuid());
        var fall=falls.get(p.getUuid());if(fall!=null&&fall.tick()<p.getServer().getTicks())falls.remove(p.getUuid());
        strongest(p);
    }
    public static void forget(UUID id){buffs.remove(id);falls.remove(id);charged.remove(id);}
    public static void clear(){buffs.clear();falls.clear();charged.clear();}
}
