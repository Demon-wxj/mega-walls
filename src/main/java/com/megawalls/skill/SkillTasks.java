package com.megawalls.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Delays use game ticks and never block the server thread. */
public final class SkillTasks {
    private record Task(ServerPlayerEntity player, net.minecraft.server.world.ServerWorld world, int cls, int phase, long match, int deadline, int expires, BooleanSupplier ready, Runnable action) {}
    private static Task task(ServerPlayerEntity p,int delay,int expiry,BooleanSupplier ready,Runnable action){
        return new Task(p,p.getServerWorld(),com.megawalls.player.MWPlayerData.getServer(p).getClassId(),com.megawalls.game.GameState.phase(p),com.megawalls.game.MatchFlow.matchId(p.getServer()),p.getServer().getTicks()+delay,p.getServer().getTicks()+expiry,ready,action);
    }
    private static final List<Task> tasks = new ArrayList<>();
    public static void later(ServerPlayerEntity player, int ticks, Runnable action) {
        tasks.add(task(player,ticks,ticks+1,()->true,action));
    }
    public static void landing(ServerPlayerEntity player, Runnable action) {
        boolean[] airborne={!player.isOnGround()};
        tasks.add(task(player,2,100,()->{if(!player.isOnGround())airborne[0]=true;return airborne[0]&&player.isOnGround();},action));
    }
    public static void forget(java.util.UUID uuid) { tasks.removeIf(t -> t.player().getUuid().equals(uuid)); SkillVisuals.forget(uuid); }
    public static void clear() { tasks.clear(); SkillVisuals.clear(); }
    public static void tick(MinecraftServer server) {
        SkillVisuals.tick(server);
        var pending = new ArrayList<>(tasks);
        for (Task task : pending) {
            var player = task.player();
            if (player.getServer() != server || player.isRemoved()
                    || player.getServerWorld()!=task.world() || com.megawalls.player.MWPlayerData.getServer(player).getClassId()!=task.cls()
                    || task.match()!=com.megawalls.game.MatchFlow.matchId(server)
                    || !RestorationSkillRules.samePhaseLifetime(task.phase(),com.megawalls.game.GameState.phase(player))
                    || !com.megawalls.game.GameState.active(player)) { tasks.remove(task); continue; }
            boolean due = server.getTicks() >= task.deadline();
            if (server.getTicks() > task.expires()) { tasks.remove(task); continue; }
            boolean landed = task.ready().getAsBoolean();
            if (due && landed) { tasks.remove(task); task.action().run(); }
        }
    }
}
