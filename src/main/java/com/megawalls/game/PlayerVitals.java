package com.megawalls.game;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import java.util.*;
public final class PlayerVitals {
    private record Pair(UUID attacker,UUID victim){}
    private record Hit(ServerPlayerEntity attacker,ServerPlayerEntity victim){}
    private static final Map<Pair,Hit> hits=new LinkedHashMap<>();
    public static void health(ServerPlayerEntity p){
        if(!com.megawalls.minigame.GameSessions.mw(p))return;
        var attr=p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);if(attr==null||attr.getBaseValue()==40)return;
        boolean full=p.getHealth()>=p.getMaxHealth();attr.setBaseValue(40);if(full&&p.isAlive())p.setHealth(p.getMaxHealth());
    }
    public static void record(ServerPlayerEntity victim,DamageSource source){
        if(source.isOf(net.minecraft.entity.damage.DamageTypes.ARROW)
            &&source.getAttacker() instanceof ServerPlayerEntity attacker&&attacker!=victim&&GameState.active(attacker)&&!attacker.isTeammate(victim)
            &&attacker.getServerWorld()==victim.getServerWorld()&&GameState.phase(attacker)!=1
            &&(GameState.participant(victim)||GameState.isPractice(victim)))hits.put(new Pair(attacker.getUuid(),victim.getUuid()),new Hit(attacker,victim));
    }
    public static String remaining(ServerPlayerEntity p){return String.format(Locale.ROOT,"%.1f/%.0f HP（%.1f❤）",Math.max(0,p.getHealth()),p.getMaxHealth(),Math.max(0,p.getHealth())/2)+(p.getAbsorptionAmount()>0?String.format(Locale.ROOT," + %.1f 吸收",p.getAbsorptionAmount()):"");}
    public static void flush(){for(var h:hits.values())if(!h.attacker.isRemoved()&&com.megawalls.minigame.GameSessions.mw(h.attacker))h.attacker.sendMessage(Text.literal("§e箭矢命中 §f"+h.victim.getName().getString()+" §7剩余 §c"+remaining(h.victim)),false);hits.clear();}
    public static void clear(){hits.clear();}
    public static void register(){
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e,s,b,d,blocked)->{if(!blocked&&d>0&&e instanceof ServerPlayerEntity p)record(p,s);});
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((e,s)->{if(e instanceof ServerPlayerEntity p)record(p,s);});
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->{if(com.megawalls.minigame.GameSessions.mw(p)){health(p);if(!alive)p.setHealth(p.getMaxHealth());}});
    }
}
