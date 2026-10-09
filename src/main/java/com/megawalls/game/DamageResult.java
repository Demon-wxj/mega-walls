package com.megawalls.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** One synchronous damage invocation, including absorption, fatal and nested skill damage. */
public final class DamageResult {
    private static final ThreadLocal<Deque<Impact>> pending = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Impact {
        final ServerPlayerEntity victim;
        final DamageSource source;
        final float before;
        float loss = Float.NaN;
        Impact(ServerPlayerEntity victim, DamageSource source) {
            this.victim=victim; this.source=source; before=victim.getHealth()+victim.getAbsorptionAmount();
        }
    }
    private DamageResult() {}
    public static boolean track(ServerPlayerEntity victim, DamageSource source, Supplier<Boolean> damage) {
        var stack=pending.get();
        stack.push(new Impact(victim,source));
        try { return damage.get(); }
        finally { stack.pop(); if(stack.isEmpty())pending.remove(); }
    }
    /** Freeze before other damage listeners can heal or cause a nested hit. */
    public static float loss(ServerPlayerEntity victim, DamageSource source) {
        var impact=pending.get().peek();
        if(impact==null || impact.victim!=victim || impact.source!=source)return 0;
        if(Float.isNaN(impact.loss))impact.loss=Math.max(0,impact.before-Math.max(0,victim.getHealth())-victim.getAbsorptionAmount());
        return impact.loss;
    }
}
