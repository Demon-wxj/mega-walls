package com.megawalls.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.megawalls.MWMod;
import com.megawalls.game.GameState;
import com.megawalls.game.MatchFlow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.Difficulty;

/** A Minecraft world has one difficulty. Concurrent combat therefore has priority
 * over peaceful minigames; the original world setting is recovered when games end. */
public final class GameDifficulty {
    private record Original(int schema,int difficulty,boolean locked){}
    private static final Gson JSON=new Gson();
    private static final Set<String> PEACEFUL_GAMES=Set.of(GameSessions.HOT,TntRun.ID,Spleef.ID,"clocktower","flip7");
    private static MinecraftServer serverRunning;
    private static Original original;
    private static boolean recoveryBlocked,warned;
    private GameDifficulty(){}
    public static Path recoveryFile(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).resolve("minigame-difficulty-recovery.json");}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            serverRunning=server;original=null;recoveryBlocked=false;warned=false;
            // The map uses scripted targets, never naturally spawned creatures.
            for(var world:server.getWorlds())world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,server);
            if(!recoverStale(server)){recoveryBlocked=true;return;}
            update(server);
        });
        ServerTickEvents.START_SERVER_TICK.register(GameDifficulty::update);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(server==serverRunning)release(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(server==serverRunning){serverRunning=null;original=null;recoveryBlocked=false;warned=false;}});
    }
    /** Called after a membership change too; startup callbacks before SERVER_STARTED are harmless. */
    public static void update(MinecraftServer server){
        if(server!=serverRunning||recoveryBlocked||!server.isOnThread())return;
        Difficulty desired=requested(server);
        if(desired==null){release(server);return;}
        // Vanilla enforces Hard in Hardcore; avoid trying to change it every tick.
        if(server.getSaveProperties().isHardcore())desired=Difficulty.HARD;
        if(original==null){
            var properties=server.getSaveProperties();
            var captured=new Original(1,properties.getDifficulty().getId(),properties.isDifficultyLocked());
            try{write(recoveryFile(server),captured);original=captured;}
            catch(Exception error){warn("Cannot preserve original game difficulty; automatic switch skipped",error);return;}
        }
        if(server.getSaveProperties().getDifficulty()!=desired)server.setDifficulty(desired,true);
    }
    private static Difficulty requested(MinecraftServer server){
        boolean combat=MatchFlow.running(server)||ClassicDuel.running()||PotDuel.running()||BedWars.stage()==BedWars.Stage.RUNNING
                ||server.getPlayerManager().getPlayerList().stream().anyMatch(p->GameSessions.mw(p)&&GameState.isPractice(p)&&p.isAlive()&&!p.isSpectator());
        if(combat)return Difficulty.NORMAL;
        return server.getPlayerManager().getPlayerList().stream().anyMatch(p->PEACEFUL_GAMES.contains(GameSessions.game(p)))?Difficulty.PEACEFUL:null;
    }
    private static void release(MinecraftServer server){
        if(original==null)return;
        try{
            restore(server,original);
            Files.deleteIfExists(recoveryFile(server));original=null;warned=false;
        }catch(Exception error){warn("Cannot finish restoring original game difficulty; recovery file retained",error);}
    }
    private static void restore(MinecraftServer server,Original state){
        server.setDifficulty(Difficulty.byId(state.difficulty),true);
        if(server.getSaveProperties().isDifficultyLocked()!=state.locked)server.setDifficultyLocked(state.locked);
    }
    private static boolean recoverStale(MinecraftServer server){
        var path=recoveryFile(server);if(!Files.exists(path))return true;
        try{
            var json=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!json.has("schema")||json.get("schema").getAsInt()!=1||!json.has("difficulty")
                    ||!json.has("locked")||!json.get("locked").isJsonPrimitive()||!json.get("locked").getAsJsonPrimitive().isBoolean())throw new IOException("Invalid difficulty recovery document");
            int difficulty=json.get("difficulty").getAsInt();if(difficulty<0||difficulty>3)throw new IOException("Invalid original difficulty");
            restore(server,new Original(1,difficulty,json.get("locked").getAsBoolean()));
            Files.delete(path);MWMod.LOGGER.info("Recovered the world's original difficulty and lock after an interrupted minigame");return true;
        }catch(Exception error){warn("Cannot read/restore difficulty recovery document; it is retained and automatic switching is disabled",error);return false;}
    }
    private static void write(Path path,Original state)throws IOException{
        if(Files.exists(path))throw new IOException("A prior difficulty recovery file still exists");
        var temp=Files.createTempFile(path.getParent(),"minigame-difficulty-",".tmp");
        try{
            Files.writeString(temp,JSON.toJson(state),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException|AccessDeniedException|FileAlreadyExistsException error){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static void warn(String message,Exception error){if(!warned){warned=true;MWMod.LOGGER.error(message,error);}}
}
