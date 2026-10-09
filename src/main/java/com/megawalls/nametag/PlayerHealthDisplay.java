package com.megawalls.nametag;

import com.megawalls.game.GameState;
import com.megawalls.minigame.GameSessions;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;

/** Vanilla below-name rendering, with authoritative HP sent only to MW viewers. */
public final class PlayerHealthDisplay {
    private static final Map<UUID, ScoreboardObjective> objectives = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> values = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 5 != 0) return;
        for (var viewer : server.getPlayerManager().getPlayerList()) {
            if (!GameSessions.mw(viewer)) { clear(viewer); continue; }
            var objective = objectives.computeIfAbsent(viewer.getUuid(), id -> {
                var board = new Scoreboard();
                var result = board.addObjective("mw_vitals", ScoreboardCriterion.DUMMY,
                        Text.literal("❤").formatted(Formatting.RED), ScoreboardCriterion.RenderType.INTEGER, false, null);
                viewer.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(result, 0));
                viewer.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.BELOW_NAME, result));
                return result;
            });
            var previous = values.computeIfAbsent(viewer.getUuid(), id -> new HashMap<>());
            var current = new HashSet<String>();
            for (var target : server.getPlayerManager().getPlayerList()) {
                if (!GameSessions.mw(target) || target == viewer || target.getServerWorld() != viewer.getServerWorld()
                        || target.isSpectator() || !target.isAlive() || target.squaredDistanceTo(viewer) > 64 * 64
                        || target.isInvisibleTo(viewer) || target.getCommandTags().contains("mw_eliminated")) continue;
                String name = target.getGameProfile().getName();
                // Hypixel-style number is HP: 40 HP = 20 hearts. Absorption is also real survivable HP.
                int hp = (int)Math.ceil(Math.max(0, target.getHealth()) + target.getAbsorptionAmount());
                current.add(name);
                if (!Objects.equals(previous.put(name, hp), hp))
                    viewer.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(name, objective.getName(), hp, Optional.empty(), Optional.empty()));
            }
            for (String name : new ArrayList<>(previous.keySet())) if (!current.contains(name)) {
                previous.remove(name);
                viewer.networkHandler.sendPacket(new ScoreboardScoreResetS2CPacket(name, objective.getName()));
            }
        }
    }

    public static void clear(ServerPlayerEntity viewer) {
        var objective = objectives.remove(viewer.getUuid());
        values.remove(viewer.getUuid());
        if (objective == null) return;
        viewer.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.BELOW_NAME,
                viewer.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME)));
        viewer.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, 1));
    }
    public static void forget(UUID id) { objectives.remove(id); values.remove(id); }
    public static void clearAll() { objectives.clear(); values.clear(); }
}
