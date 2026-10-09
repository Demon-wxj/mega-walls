package com.megawalls.game;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import java.util.*;
/** Recent real damage grants environmental-kill credit and assists. */
public final class CombatCredit {
    private static ServerPlayerEntity find(ServerPlayerEntity victim,UUID id){return victim.getServer().getPlayerManager().getPlayerList().stream().filter(p->p.getUuid().equals(id)).findFirst().orElse(null);}
    private record Hit(UUID attacker,int tick,float damage){}
    private static final Map<UUID,Map<UUID,Hit>> hits=new HashMap<>();
    public static ServerPlayerEntity attacker(DamageSource source){
        return source.getAttacker() instanceof ServerPlayerEntity p?p:com.megawalls.skill.Summons.owner(source.getAttacker());
    }
    public static void record(ServerPlayerEntity victim,DamageSource source,float amount){
        var p=attacker(source);if(p==null||amount<=0||!GameState.enemy(p,victim)||GameState.phase(victim)<2)return;
        int now=p.getServer().getTicks();var map=hits.computeIfAbsent(victim.getUuid(),key->new HashMap<>());map.values().removeIf(hit->now-hit.tick()>200);
        var prior=map.get(p.getUuid());map.put(p.getUuid(),new Hit(p.getUuid(),now,amount+(prior==null?0:prior.damage())));
    }
    public static ServerPlayerEntity resolve(ServerPlayerEntity victim,DamageSource source,boolean finalKill){
        var killer=attacker(source);var map=hits.remove(victim.getUuid());if(map==null)return killer;
        int now=victim.getServer().getTicks();map.values().removeIf(hit->now-hit.tick()>200);
        if(killer==null){var last=map.values().stream().max(Comparator.comparingInt(Hit::tick)).orElse(null);if(last!=null)killer=find(victim,last.attacker());}
        for(var hit:map.values())if(hit.damage()>=2&&(killer==null||!hit.attacker().equals(killer.getUuid()))){
            var helper=find(victim,hit.attacker());if(helper==null||!GameState.participant(helper)||helper.isTeammate(victim))continue;
            String objective=finalKill?"mw_final_assists":"mw_assists";int current=GameState.score(helper.getScoreboard(),helper.getName().getString(),objective);MatchFlow.score(helper.getServer(),helper.getName().getString(),objective,current+1);
        }
        return killer;
    }
    public static void clear(){hits.clear();}
}
