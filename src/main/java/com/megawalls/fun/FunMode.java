package com.megawalls.fun;

import com.megawalls.MWMod;
import com.megawalls.game.GameState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import java.util.*;

/** Optional, server-authoritative entertainment rules; never modifies class balance values. */
public final class FunMode {
    public static final int AURA = 1, BRIDGE = 2, AIR = 4, HOP = 8, SPIN = 16;
    private static final Identifier REACH = MWMod.id("fun_reach");
    private static final Identifier SPEED = MWMod.id("fun_speed");
    private static final Map<UUID, State> STATES = new HashMap<>();
    private record AuraAim(ServerPlayerEntity player, float yaw) {}
    private static final ThreadLocal<AuraAim> AURA_ATTACK = new ThreadLocal<>();
    private static final int MAX_AURA_TARGETS = 8;
    /** Full attack strength applies only during the authorized automatic attack call. */
    public static boolean auraAttack(net.minecraft.entity.player.PlayerEntity player) {
        var aim = AURA_ATTACK.get();
        return aim != null && aim.player() == player;
    }
    /** Extra sprint/enchantment knockback faces the victim without changing the real camera yaw. */
    public static float auraYaw(net.minecraft.entity.player.PlayerEntity player, float original) {
        var aim = AURA_ATTACK.get();
        return aim != null && aim.player() == player ? aim.yaw() : original;
    }
    private static final class State {
        int flags, reach = 3, speed = 1, phase;
        float oldFlySpeed;
        boolean oldAllowFlying, oldFlying;
        final FunBridge bridge = new FunBridge();
        net.minecraft.world.GameMode gameMode;
        net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
        State(ServerPlayerEntity p) {
            phase = GameState.phase(p); dimension = p.getWorld().getRegistryKey();
            gameMode = p.interactionManager.getGameMode();
            oldFlySpeed = p.getAbilities().getFlySpeed();
        }
    }
    private static boolean allowed(ServerPlayerEntity p) {
        return !com.megawalls.minigame.PotDuel.member(p) && !com.megawalls.minigame.ClassicDuel.member(p) && !com.megawalls.minigame.TntRun.member(p) && !com.megawalls.minigame.HotPotato.member(p) && !com.megawalls.minigame.Spleef.member(p) && p.isAlive() && !p.isSpectator()
            && !p.getCommandTags().contains("mw_eliminated");
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(FunPayload.ID, FunPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FunPayload.ID, FunPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FunLookPayload.ID, FunLookPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FunSpinPayload.ID, FunSpinPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FunPayload.ID, (packet, context) ->
            context.server().execute(() -> configure(context.player(), packet)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(CommandManager.literal("mwfun")
                .executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrThrow();
                    if (!p.isAlive() || p.isSpectator()) return 0;
                    if (!ServerPlayNetworking.canSend(p, FunPayload.ID)) {
                        p.sendMessage(Text.literal("客户端也需要安装娱乐面板版超级战墙 Mod。"), false); return 0;
                    }
                    if (!allowed(p)) return 0;
                    send(p, true); return 1;
                }).then(CommandManager.literal("off").executes(ctx -> {
                    clear(ctx.getSource().getPlayerOrThrow()); return 1;
                }))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var p : server.getPlayerManager().getPlayerList()) tick(p);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) clear(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var p : server.getPlayerManager().getPlayerList()) clear(p);
            STATES.clear();
        });
    }
    private static void configure(ServerPlayerEntity p, FunPayload packet) {
        if (!allowed(p)) { clear(p); send(p, false); return; }
        if (packet.flags() < 0 || packet.flags() > 31 || packet.reach() < 3 || packet.reach() > 12
                || !java.util.Set.of(1, 2, 4, 8).contains(packet.speed())) { send(p, false); return; }
        var state = STATES.computeIfAbsent(p.getUuid(), id -> new State(p));
        if ((state.flags & BRIDGE) != (packet.flags() & BRIDGE)) state.bridge.reset();
        if ((state.flags & SPIN) != 0 && (packet.flags() & SPIN) == 0) FunSpin.clear(p);
        boolean wasAir = (state.flags & AIR) != 0, air = (packet.flags() & AIR) != 0;
        if (air && !wasAir) {
            state.oldAllowFlying = p.getAbilities().allowFlying;
            state.oldFlying = p.getAbilities().flying;
            p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
        } else if (wasAir && !air) restoreFlight(p, state);
        state.flags = packet.flags(); state.reach = packet.reach(); state.speed = packet.speed();
        var attribute = p.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            attribute.removeModifier(REACH);
            if (state.reach > 3) attribute.addTemporaryModifier(new EntityAttributeModifier(REACH, state.reach - 3, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        var movement = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(SPEED);
            if (state.speed > 1) movement.addTemporaryModifier(new EntityAttributeModifier(SPEED, state.speed - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        p.getAbilities().setFlySpeed(state.oldFlySpeed * state.speed); p.sendAbilitiesUpdate();
        if (state.flags == 0 && state.reach == 3 && state.speed == 1) STATES.remove(p.getUuid());
        send(p, false);
    }
    private static void restoreFlight(ServerPlayerEntity p, State s) {
        if (p.interactionManager.getGameMode() == s.gameMode) {
            p.getAbilities().allowFlying = s.oldAllowFlying;
            p.getAbilities().flying = s.oldAllowFlying && s.oldFlying;
        }
        p.fallDistance = 0; p.sendAbilitiesUpdate();
    }
    public static void clear(ServerPlayerEntity p) {
        var s = STATES.remove(p.getUuid());
        FunSpin.clear(p);
        var attribute = p.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        if (attribute != null) attribute.removeModifier(REACH);
        var movement = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(SPEED);
        if (s != null) { p.getAbilities().setFlySpeed(s.oldFlySpeed); p.sendAbilitiesUpdate(); }
        if (s != null && (s.flags & AIR) != 0) restoreFlight(p, s);
        if (s != null) send(p, false);
    }
    private static void send(ServerPlayerEntity p, boolean open) {
        var s = STATES.get(p.getUuid());
        if (ServerPlayNetworking.canSend(p, FunPayload.ID))
            ServerPlayNetworking.send(p, new FunPayload(s == null ? 0 : s.flags, s == null ? 3 : s.reach, s == null ? 1 : s.speed, open));
    }
    /** Rebind the panel to this server UUID after a review-role PLAY initialization. */
    public static void sync(ServerPlayerEntity player) { send(player,false); }
    private static void tick(ServerPlayerEntity p) {
        var s = STATES.get(p.getUuid()); if (s == null) return;
        int phase = GameState.phase(p);
        if (!allowed(p) || p.interactionManager.getGameMode() != s.gameMode
                || p.getWorld().getRegistryKey() != s.dimension
                || (phase != s.phase && (phase == 0 || phase == 1 || phase == 4))) { clear(p); return; }
        s.phase = phase;
        if ((s.flags & AIR) != 0 && !p.hasVehicle()) {
            if (!p.getAbilities().flying || !p.getAbilities().allowFlying) {
                p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
            }
            p.fallDistance = 0;
        }
        if ((s.flags & BRIDGE) != 0 && !p.hasVehicle()) s.bridge.tick(p);
        else s.bridge.reset();
        if ((s.flags & SPIN) != 0 && !p.hasVehicle()) FunSpin.tick(p);
        else FunSpin.clear(p);
        if ((s.flags & AURA) != 0 && !p.isUsingItem() && phase != 1) {
            double range = p.getEntityInteractionRange();
            var targets = p.getServerWorld().getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range),
                target -> validTarget(p, target) && p.canSee(target) && p.canInteractWithEntity(target, 0));
            var nearest = targets.stream().sorted(Comparator.comparingDouble(p::squaredDistanceTo))
                .limit(MAX_AURA_TARGETS).toList();
            if (!nearest.isEmpty()) p.swingHand(Hand.MAIN_HAND, true);
            for (var target : nearest) {
                // Recheck after earlier hits: death, summons and combat callbacks can change state.
                if (!allowed(p) || !validTarget(p, target) || !p.canSee(target)
                        || !p.canInteractWithEntity(target, 0)) continue;
                double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ();
                float yaw = dx * dx + dz * dz < 1.0e-8 ? p.getYaw()
                    : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                AURA_ATTACK.set(new AuraAim(p, yaw));
                try { p.attack(target); }
                finally { AURA_ATTACK.remove(); }
                p.resetLastAttackedTicks();
            }
        }
    }
    private static boolean validTarget(ServerPlayerEntity p, LivingEntity target) {
        if (target == p || !target.isAlive() || target.isInvulnerable() || p.isTeammate(target)) return false;
        if (target instanceof ServerPlayerEntity other)
            return !other.isCreative() && GameState.enemy(p, other)
                && (GameState.phase(p) >= 2 || GameState.isPractice(p) && GameState.isPractice(other));
        return target instanceof HostileEntity && com.megawalls.skill.Summons.owner(target) == null
            && (!(target instanceof net.minecraft.entity.boss.WitherEntity wither) || com.megawalls.game.MatchTeams.core(wither) == null);
    }
}
