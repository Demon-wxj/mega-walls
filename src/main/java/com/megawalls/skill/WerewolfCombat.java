package com.megawalls.skill;

import com.megawalls.game.GameState;
import com.megawalls.player.MWPlayerData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Measures actual health lost, including fatal hits, instead of incoming damage. */
public final class WerewolfCombat {
    private record Form(int expires,net.minecraft.server.world.ServerWorld world,long match,int phase) {}
    private record Hit(ServerPlayerEntity attacker, DamageSource source, float health,int tick) {}
    private static final Map<UUID, Form> forms = new HashMap<>();
    private static final Map<UUID, Hit> hits = new HashMap<>();
    private static final Set<UUID> resolving = new HashSet<>();
    public static int remaining(ServerPlayerEntity p){var f=forms.get(p.getUuid());return f==null?0:Math.max(0,f.expires()-p.getServer().getTicks());}
    public static void activate(ServerPlayerEntity player) { forms.put(player.getUuid(), new Form(player.getServer().getTicks() + 160,player.getServerWorld(),com.megawalls.game.MatchFlow.matchId(player.getServer()),GameState.phase(player))); }
    public static void forget(UUID uuid) { forms.remove(uuid); hits.values().removeIf(h -> h.attacker().getUuid().equals(uuid)); resolving.remove(uuid); }
    public static void clear() { forms.clear(); hits.clear(); resolving.clear(); }
    private static boolean active(ServerPlayerEntity player) {
        Form form = forms.get(player.getUuid());
        return form != null && form.expires() > player.getServer().getTicks() && GameState.active(player)
                &&form.world()==player.getServerWorld()&&form.match()==com.megawalls.game.MatchFlow.matchId(player.getServer())
                &&RestorationSkillRules.samePhaseLifetime(form.phase(),GameState.phase(player))
                && MWPlayerData.getServer(player).getClassId() == 13;
    }
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim && source.getAttacker() instanceof ServerPlayerEntity attacker
                    && source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK) && active(attacker) && GameState.enemy(attacker, victim) && !resolving.contains(attacker.getUuid())) {
                hits.values().removeIf(h->h.tick()!=attacker.getServer().getTicks());
                hits.put(victim.getUuid(), new Hit(attacker, source, victim.getHealth()+victim.getAbsorptionAmount(),attacker.getServer().getTicks()));
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, damage, blocked) -> {
            if (entity instanceof ServerPlayerEntity victim) finish(victim, source, blocked);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity victim) { finish(victim, source, false); forms.remove(victim.getUuid()); }
        });
    }
    private static void finish(ServerPlayerEntity victim, DamageSource source, boolean blocked) {
        Hit hit = hits.get(victim.getUuid());
        if (hit == null || hit.source() != source) return;
        hits.remove(victim.getUuid());
        var attacker = hit.attacker();
        float lost = Math.max(0, hit.health() - Math.max(0, victim.getHealth())-victim.getAbsorptionAmount());
        if (blocked || hit.tick()!=attacker.getServer().getTicks() || lost <= 0 || !active(attacker)) return;
        float before=attacker.getHealth();attacker.heal(lost * 0.3f);
        SkillEffects.claws(attacker,victim.getPos());
        SkillEffects.cinematic(attacker,com.megawalls.network.S2C_SkillPattern.WOLF_HIT,
                victim.getPos(),victim.getPos(),.8);
        if(attacker.getHealth()>before)attacker.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                attacker.getX(),attacker.getY()+.9,attacker.getZ(),2,.25,.15,.25,0);
        // The historical pulse is caused by a successful direct melee hit, never by its own splash.
        resolving.add(attacker.getUuid());
        try {
            for(var enemy:attacker.getServerWorld().getPlayers())
                if(GameState.enemy(attacker,enemy)&&attacker.squaredDistanceTo(enemy)<=RestorationSkillRules.LYCANTHROPY_RADIUS*RestorationSkillRules.LYCANTHROPY_RADIUS)
                    TrueDamage.hit(attacker,enemy,RestorationSkillRules.LYCANTHROPY_SPLASH);
        } finally { resolving.remove(attacker.getUuid()); }
    }
}
