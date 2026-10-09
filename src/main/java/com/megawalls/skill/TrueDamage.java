package com.megawalls.skill;

import com.megawalls.game.GameState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class TrueDamage {
    private record DeathImpact(ServerPlayerEntity owner, ServerPlayerEntity target) {}
    private static final ThreadLocal<DeathImpact> deathImpact = new ThreadLocal<>();
    public static boolean isDeathImpact(DamageSource source, ServerPlayerEntity target) {
        var impact = deathImpact.get();
        return impact != null && impact.owner() == source.getAttacker() && impact.target() == target;
    }
    public static float hit(ServerPlayerEntity player, ServerPlayerEntity victim, float amount) {
        if (!GameState.enemy(player, victim) || GameState.phase(player) == 1) return 0;
        return apply(player, victim, amount);
    }

    /** Only the spider's death-event fall impact may finish after its owner dies. */
    public static float hitFromDeath(ServerPlayerEntity player, ServerPlayerEntity victim, float amount, DamageSource cause) {
        int phase = GameState.phase(player);
        boolean matchOwner = GameState.participant(player) && (phase == 2 || phase == 3)
                && Math.abs(com.megawalls.game.MatchFlow.roster(player)) > 0;
        if (cause == null || !cause.isOf(net.minecraft.entity.damage.DamageTypes.FALL)
                || player.isAlive() || com.megawalls.player.MWPlayerData.getServer(player).getClassId() != 3
                || !(matchOwner || GameState.isPractice(player)) || !GameState.active(victim)
                || player == victim || player.isTeammate(victim) || player.getServerWorld() != victim.getServerWorld()) return 0;
        var previous = deathImpact.get();
        deathImpact.set(new DeathImpact(player, victim));
        try { return apply(player, victim, amount); }
        finally { if (previous == null) deathImpact.remove(); else deathImpact.set(previous); }
    }

    private static float apply(ServerPlayerEntity player, ServerPlayerEntity victim, float amount) {
        var type = player.getServerWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE)
                .getEntry(Identifier.of("megawalls", "skill")).orElseThrow();
        return apply(new DamageSource(type, player, player), victim, amount);
    }

    public static float hitFromSummon(net.minecraft.entity.LivingEntity summon, ServerPlayerEntity victim, float amount) {
        var owner = Summons.owner(summon);
        if (owner == null || !Summons.enemy(owner, victim) || summon.getWorld() != victim.getWorld()) return 0;
        var type = victim.getServerWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE)
                .getEntry(Identifier.of("megawalls", "skill")).orElseThrow();
        return apply(new DamageSource(type, summon, summon), victim, amount);
    }

    private static float apply(DamageSource source, ServerPlayerEntity victim, float amount) {
        if (amount <= 0 || !Float.isFinite(amount)) return 0;
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        int immunity = victim.timeUntilRegen;
        var memory=(com.megawalls.mixin.MWHurtMemoryAccessor)victim;
        float previousHit=memory.mw$getLastDamageTaken();
        try {
            victim.timeUntilRegen = 0;
            victim.damage(source, amount);
        } finally { victim.timeUntilRegen = immunity; memory.mw$setLastDamageTaken(previousHit); }
        return Math.max(0, before - Math.max(0, victim.getHealth()) - victim.getAbsorptionAmount());
    }
}
