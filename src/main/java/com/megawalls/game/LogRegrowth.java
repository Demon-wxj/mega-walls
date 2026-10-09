package com.megawalls.game;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Preparation-only log regrowth. A chest or player block at the old position is never replaced. */
public final class LogRegrowth {
    private record Entry(ServerWorld world,BlockPos pos,BlockState state,long due) {}
    private static final Map<BlockPos,Entry> latest=new HashMap<>();
    private static final PriorityQueue<Entry> due=new PriorityQueue<>(Comparator.comparingLong(Entry::due));
    public static void clear(){latest.clear();due.clear();}
    public static void register(){
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{
            if(!(world instanceof ServerWorld sw)||!(player instanceof ServerPlayerEntity sp)||sw!=sw.getServer().getOverworld()||!GameState.active(sp)||MatchFlow.phase(sw.getServer())!=1||!state.isIn(BlockTags.LOGS)||RegionProtection.contains(sw,pos))return;
            if(sp.isCreative()||!com.megawalls.passive.MiningTreasure.natural(sw,pos))return;
            var entry=new Entry(sw,pos.toImmutable(),state,sw.getServer().getTicks()+MapConfig.get(sw.getServer(),"log_restore")*20L);
            latest.put(entry.pos,entry);due.add(entry);
        });
        ServerTickEvents.END_SERVER_TICK.register(LogRegrowth::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->clear());
    }
    private static void tick(MinecraftServer server){
        if(MatchFlow.phase(server)!=1){clear();return;}
        int budget=128;
        while(!due.isEmpty()&&budget>0&&due.peek().due<=server.getTicks()){var entry=due.remove();if(latest.get(entry.pos)!=entry)continue;latest.remove(entry.pos);budget--;
            if(entry.world.getServer()==server&&entry.world.getBlockState(entry.pos).isAir()&&entry.world.getBlockEntity(entry.pos)==null&&!RegionProtection.contains(entry.world,entry.pos)
                    &&entry.world.setBlockState(entry.pos,entry.state,3))
                com.megawalls.passive.MiningTreasure.regrown(entry.world,entry.pos);
        }
    }
}
