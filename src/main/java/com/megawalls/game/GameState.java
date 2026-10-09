package com.megawalls.game;

import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.network.ServerPlayerEntity;

/** Shared datapack state and server-authoritative combat checks. */
public final class GameState {
    private GameState() {}
    public static int score(Scoreboard board, String holder, String objective) {
        var obj = board.getNullableObjective(objective);
        if (obj == null) return 0;
        var value = board.getScore(ScoreHolder.fromName(holder), obj);
        return value == null ? 0 : value.getScore();
    }
    public static int phase(ServerPlayerEntity player) {
        return com.megawalls.minigame.GameSessions.mw(player) ? score(player.getScoreboard(), "#global", "mw_phase") : -1;
    }
    public static boolean participant(ServerPlayerEntity player) {
        var team = player.getScoreboardTeam();
        return com.megawalls.minigame.GameSessions.mw(player) && player.getCommandTags().contains("mw_player") && team != null
                && MatchTeams.active(player.getServer()).contains(team.getName());
    }
    public static boolean active(ServerPlayerEntity player) {
        int phase = phase(player);
        return (isPractice(player) || phase >= 1 && phase <= 3 && participant(player) && MatchFlow.roster(player)>0)
                && player.isAlive() && !player.isSpectator() && !player.getCommandTags().contains("mw_eliminated");
    }
    public static boolean isPractice(ServerPlayerEntity player) {
        int phase = phase(player);
        return (phase == 0 || phase == 4) && player.getCommandTags().contains("mw_practice");
    }
    public static boolean enemy(ServerPlayerEntity player, ServerPlayerEntity other) {
        return other != player && active(player) && active(other)
                && player.getServerWorld() == other.getServerWorld() && !player.isTeammate(other);
    }
    /** Arena points describe the horizontal battle boundary; practice has its own staging area. */
    public static boolean insideArena(ServerPlayerEntity player,net.minecraft.util.math.Box box) {
        var server=player.getServer();
        if(isPractice(player)||MapConfig.get(server,"arena1_set")!=1||MapConfig.get(server,"arena2_set")!=1)return true;
        if(player.getServerWorld()!=server.getOverworld())return false;
        var a=MapConfig.point(server,"arena1");var b=MapConfig.point(server,"arena2");
        return box.minX>=Math.min(a.getX(),b.getX())&&box.maxX<=Math.max(a.getX(),b.getX())+1
                &&box.minZ>=Math.min(a.getZ(),b.getZ())&&box.maxZ<=Math.max(a.getZ(),b.getZ())+1;
    }
}
