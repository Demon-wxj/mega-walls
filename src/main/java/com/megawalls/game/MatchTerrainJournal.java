package com.megawalls.game;

import com.megawalls.MWMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** First-change write-ahead journal for the configured Overworld Mega Walls arena.
 * Each record is durable before the corresponding world mutation is permitted.
 * No full-map/block scan occurs during a tick; only loaded block entities are
 * snapshotted at begin/chunk load to cover container and hopper inventory changes.
 */
public final class MatchTerrainJournal {
    private static final int VERSION = 1, MAX_FRAME = 4 * 1024 * 1024, MAX_BLOCKS = 1_000_000;
    private static Journal active;
    private static String error = "";
    private static boolean replaying, registered;
    private record Before(BlockState state, NbtCompound blockEntity) {}
    private static final class Journal {
        final MinecraftServer server;
        final String id;
        final BlockPos min, max;
        final Map<Long, Before> blocks = new LinkedHashMap<>();
        final Set<UUID> spawned = new HashSet<>();
        final Map<UUID, ServerWorld> destroyed = new HashMap<>();
        FileChannel channel;
        boolean failed, batching, armed;
        Journal(MinecraftServer server, String id, BlockPos min, BlockPos max) { this.server=server; this.id=id; this.min=min; this.max=max; }
        boolean contains(BlockPos p) { return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ(); }
    }

    private static Path file(MinecraftServer s) { return s.getSavePath(WorldSavePath.ROOT).resolve("megawalls-terrain").resolve("active.journal"); }
    public static String lastError() { return error; }
    public static boolean pending(MinecraftServer s) { return Files.exists(file(s)); }
    public static boolean failed(MinecraftServer s) { return pending(s)&&(active==null||active.server!=s||active.failed); }
    public static int recordedBlocks() { return active==null?0:active.blocks.size(); }
    public static BlockBox bounds(MinecraftServer s) { return active==null||active.server!=s?null:new BlockBox(active.min.getX(),active.min.getY(),active.min.getZ(),active.max.getX(),active.max.getY(),active.max.getZ()); }
    public static List<BlockPos> recordedPositions(MinecraftServer s) {
        if(pending(s)&&(active==null||active.server!=s||active.failed)&&!load(s))return List.of();
        return active==null||active.server!=s?List.of():active.blocks.keySet().stream().map(BlockPos::fromLong).toList();
    }

    public static void register() {
        if(registered)return; registered=true;
        TerrainEntityCleanup.register();
        ServerLifecycleEvents.SERVER_STARTED.register(s->{
            if(pending(s)&&!load(s)){
                if(MatchFlow.running(s))MatchFlow.finish(s,0);
                MatchFlow.announce(s,"§c地图恢复日志读取失败；已停止对局。请先修复恢复日志并重置，不能直接开始新局。");
                MWMod.LOGGER.error("Mega Walls terrain recovery failed at startup: {}",lastError());
            }else if(pending(s)||MatchFlow.running(s)||MatchFlow.phase(s)==4||MapConfig.get(s,"wall_saved")==1){
                // An interrupted round is never resumed from an old phase clock.
                // Restore the original terrain before the first player joins.
                if(MatchFlow.reset(s)!=1){
                    MWMod.LOGGER.error("Mega Walls automatic startup reset failed; keep recovery data and block a new round: {}",lastError());
                }else{
                    MWMod.LOGGER.info("Mega Walls interrupted round recovered automatically at startup");
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(MatchTerrainJournal::flush);
        ServerLifecycleEvents.SERVER_STOPPED.register(MatchTerrainJournal::clear);
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            if(!replaying&&active!=null&&active.server==world.getServer()&&world==world.getServer().getOverworld()&&!active.failed)
                captureContainers(world,chunk);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            // Chunk unloading and dimension transfer are not proof of permanent disappearance.
            var reason=entity.getRemovalReason();
            if(!replaying&&active!=null&&active.server==world.getServer()&&reason!=null
                    &&reason.shouldDestroy()&&active.spawned.contains(entity.getUuid()))
                active.destroyed.put(entity.getUuid(),world);
        });
    }

    public static boolean begin(MinecraftServer s) {
        if(pending(s))return fail(null,"存在尚未恢复的地图日志，请先重置上一局",null);
        clear(s); error="";
        try {
            TerrainEntityCleanup.ensureAvailable(s);
            var w=s.getOverworld();
            int margin=MapConfig.get(s,"journal_margin");
            if(margin==0)margin=16;
            if(margin<0||margin>64)throw new IOException("journal_margin 必须为 0（默认16）到64格");
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE,count=0;
            if(MapConfig.get(s,"arena1_set")==1&&MapConfig.get(s,"arena2_set")==1){
                var a=MapConfig.point(s,"arena1");var b=MapConfig.point(s,"arena2");
                minX=Math.min(a.getX(),b.getX());minZ=Math.min(a.getZ(),b.getZ());
                maxX=Math.max(a.getX(),b.getX());maxZ=Math.max(a.getZ(),b.getZ());
            }else{
                for(String key:MapConfig.POINTS){
                    if(key.equals("lobby")||key.equals("training")||key.startsWith("arena")||MapConfig.get(s,key+"_set")!=1)continue;
                    if((key.startsWith("purple")||key.startsWith("yellow"))&&MatchTeams.count(s)!=4)continue;
                    if(key.startsWith("wall")&&Integer.parseInt(key.substring(4))>MapConfig.walls(s)*2)continue;
                    var p=MapConfig.point(s,key);minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxZ=Math.max(maxZ,p.getZ());count++;
                }
                if(count<3)throw new IOException("至少需要3个战场点位来界定恢复范围");
            }
            var min=new BlockPos(minX-margin,w.getBottomY(),minZ-margin);
            var max=new BlockPos(maxX+margin,w.getTopY()-1,maxZ+margin);
            if((long)max.getX()-min.getX()>1024||(long)max.getZ()-min.getZ()>1024||!w.getWorldBorder().contains(min)||!w.getWorldBorder().contains(max))
                throw new IOException("恢复范围越界或单边超过1024格，请检查战场点位");
            var j=new Journal(s,UUID.randomUUID().toString(),min,max);
            Files.createDirectories(file(s).getParent());
            j.channel=FileChannel.open(file(s),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            active=j;
            var header=new NbtCompound();header.putInt("version",VERSION);header.putString("id",j.id);header.putLong("min",min.asLong());header.putLong("max",max.asLong());header.putString("dimension","minecraft:overworld");
            append(j,header);
            j.batching=true;
            for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
                if(w.isChunkLoaded(x,z)&&!captureContainers(w,w.getChunk(x,z)))return false;
            // Walls are a known large mutation batch. Durably record them now once,
            // so the later 2048-block removal ticks do not issue 2048 fsyncs each.
            for(int segment=0;segment<MapConfig.walls(s);segment++){
                String first="wall"+(segment*2+1),second="wall"+(segment*2+2);
                if(MapConfig.get(s,first+"_set")!=1||MapConfig.get(s,second+"_set")!=1)continue;
                var a=MapConfig.point(s,first);var b=MapConfig.point(s,second);
                long volume=((long)Math.abs(a.getX()-b.getX())+1)*(Math.abs(a.getY()-b.getY())+1)*(Math.abs(a.getZ()-b.getZ())+1);
                if(volume>262144)throw new IOException("分隔墙快照超过262144格保护上限");
                for(var p:BlockPos.iterate(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()))){w.getChunk(p);if(!capture(w,p))return false;}
            }
            j.channel.force(true);j.batching=false;j.armed=true;
            MWMod.LOGGER.info("Mega Walls terrain journal started: {} -> {}, {} baseline blocks",min,max,j.blocks.size());
            return true;
        }catch(Exception e){return fail(active,"无法建立地图恢复日志，未允许开始新局",e);}
    }

    private static boolean captures(ServerWorld w,BlockPos p) {
        return !replaying&&active!=null&&active.server==w.getServer()&&w==w.getServer().getOverworld()&&active.contains(p);
    }

    /** Called before the central World.setBlockState mutation. False cancels it. */
    public static boolean beforeChange(ServerWorld w,BlockPos p,BlockState next) {
        if(!captures(w,p)||!active.armed)return true;
        if(active.failed)return false;
        if(w.getBlockState(p).equals(next))return true;
        return capture(w,p);
    }

    /** Records both halves of double chests before an interaction mutates inventory. */
    public static boolean beforeContainer(ServerWorld w,BlockPos p) {
        if(!captures(w,p)||!active.armed)return true;
        if(active.failed)return false;
        if(w.getBlockEntity(p)!=null&&!capture(w,p))return false;
        for(Direction direction:Direction.Type.HORIZONTAL){var other=p.offset(direction);if(captures(w,other)&&w.getBlockEntity(other)!=null&&!capture(w,other))return false;}
        return true;
    }

    private static boolean captureContainers(ServerWorld w,WorldChunk chunk) {
        for(var entry:new ArrayList<>(chunk.getBlockEntities().entrySet()))
            if(captures(w,entry.getKey())&&!capture(w,entry.getKey(),chunk))return false;
        return true;
    }

    private static boolean capture(ServerWorld w,BlockPos p) {
        return capture(w,p,null);
    }

    private static boolean capture(ServerWorld w,BlockPos p,WorldChunk loadingChunk) {
        var j=active;if(j==null||!j.contains(p))return true;if(j.failed)return false;
        long packed=p.asLong();if(j.blocks.containsKey(packed))return true;
        try{
            if(j.blocks.size()>=MAX_BLOCKS)throw new IOException("地图日志达到100万格保护上限");
            // CHUNK_LOAD runs before the full-chunk future completes. Query its
            // supplied chunk directly; world lookups here can wait on this callback.
            var state=loadingChunk==null?w.getBlockState(p):loadingChunk.getBlockState(p);
            var be=loadingChunk==null?w.getBlockEntity(p):loadingChunk.getBlockEntities().get(p);var n=new NbtCompound();
            n.putString("kind","block");n.putLong("pos",packed);n.put("state",NbtHelper.fromBlockState(state));
            NbtCompound data=be==null?null:be.createNbtWithIdentifyingData(w.getRegistryManager());
            if(data!=null)n.put("be",data);
            append(j,n);j.blocks.put(packed,new Before(state,data));return true;
        }catch(Exception e){return fail(j,"地图变更记录失败；为保护恢复依据已阻止继续改块",e);}
    }

    public static boolean beforeSpawn(ServerWorld w,Entity e) {
        if(!(e instanceof ItemEntity||e instanceof ExperienceOrbEntity||e instanceof ProjectileEntity||e instanceof TntEntity||e instanceof FallingBlockEntity)
                ||!captures(w,e.getBlockPos())||!active.armed)return true;
        var owner=com.megawalls.minigame.Isolation.owner(e);
        if(owner!=null&&!com.megawalls.minigame.GameSessions.mw(owner))return true;
        var j=active;if(j.failed)return false;if(j.spawned.contains(e.getUuid()))return true;
        try{var n=new NbtCompound();n.putString("kind","entity");n.putUuid("uuid",e.getUuid());append(j,n);j.spawned.add(e.getUuid());return true;}
        catch(Exception ex){return fail(j,"无法记录本局临时实体",ex);}
    }

    private static void append(Journal j,NbtCompound n)throws IOException {
        var bytes=new ByteArrayOutputStream();NbtIo.write(n,new DataOutputStream(bytes));byte[] data=bytes.toByteArray();
        if(data.length>MAX_FRAME)throw new IOException("单项地图快照过大");
        var crc=new CRC32();crc.update(data);var frame=ByteBuffer.allocate(data.length+8).putInt(data.length).put(data).putInt((int)crc.getValue());frame.flip();
        while(frame.hasRemaining())j.channel.write(frame);
        if(!j.batching)j.channel.force(false);
    }

    private static NbtCompound readFrame(RandomAccessFile in)throws IOException {
        int length=in.readInt();if(length<1||length>MAX_FRAME)throw new IOException("无效日志帧长度");
        byte[] data=new byte[length];in.readFully(data);int expected=in.readInt();var crc=new CRC32();crc.update(data);
        if((int)crc.getValue()!=expected)throw new IOException("日志校验失败");
        // NbtIo.write writes a named root compound (the root name is empty).
        // NbtIo.read is the unnamed/network form and would consume that name as
        // an immediate END tag; use the paired file-format compound reader.
        return NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(data)),NbtSizeTracker.of(MAX_FRAME));
    }

    private static boolean load(MinecraftServer s) {
        clear(s);error="";
        try(var in=new RandomAccessFile(file(s).toFile(),"rw")){
            var h=readFrame(in);
            if(h.getInt("version")!=VERSION||!h.getString("dimension").equals("minecraft:overworld"))throw new IOException("不支持的地图恢复日志");
            String id=UUID.fromString(h.getString("id")).toString();var min=BlockPos.fromLong(h.getLong("min"));var max=BlockPos.fromLong(h.getLong("max"));
            if(min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()||(long)max.getX()-min.getX()>1024||(long)max.getZ()-min.getZ()>1024)throw new IOException("日志范围无效");
            var j=new Journal(s,id,min,max);active=j;
            while(in.getFilePointer()<in.length()){
                long start=in.getFilePointer();NbtCompound n;
                try{n=readFrame(in);}catch(EOFException incomplete){in.setLength(start);MWMod.LOGGER.warn("Removed unapplied incomplete tail from Mega Walls terrain journal");break;}
                if(n.getString("kind").equals("entity")){j.spawned.add(n.getUuid("uuid"));continue;}
                if(!n.getString("kind").equals("block"))throw new IOException("未知日志记录类型");
                long pos=n.getLong("pos");if(!j.contains(BlockPos.fromLong(pos))||j.blocks.size()>=MAX_BLOCKS)throw new IOException("日志坐标或数量无效");
                var state=NbtHelper.toBlockState(s.getOverworld().getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),n.getCompound("state"));
                if(!NbtHelper.fromBlockState(state).equals(n.getCompound("state")))throw new IOException("日志中的方块或属性在当前版本不可用");
                if(j.blocks.putIfAbsent(pos,new Before(state,n.contains("be")?n.getCompound("be"):null))!=null)throw new IOException("日志出现重复初始快照");
            }
            j.channel=FileChannel.open(file(s),StandardOpenOption.WRITE,StandardOpenOption.APPEND);j.armed=true;return true;
        }catch(Exception e){return fail(active,"地图恢复日志读取失败，原文件已保留",e);}
    }

    public static boolean restore(MinecraftServer s) {
        if(!pending(s))return true;
        if(active==null||active.server!=s||active.failed){if(!load(s))return false;}
        var j=active;var w=s.getOverworld();
        try{
            replaying=true;
            // Close container screens before their inventory objects are replaced.
            for(var p:s.getPlayerManager().getPlayerList())if(p.getServerWorld()==w&&j.contains(p.getBlockPos()))p.closeHandledScreen();
            for(long packed:j.blocks.keySet())w.getChunk(BlockPos.fromLong(packed));
            var box=new BlockBox(j.min.getX(),j.min.getY(),j.min.getZ(),j.max.getX(),j.max.getY(),j.max.getZ());
            w.clearUpdatesInArea(box);w.getBlockTickScheduler().clearNextTicks(box);w.getFluidTickScheduler().clearNextTicks(box);
            for(var entry:j.blocks.entrySet()){
                var p=BlockPos.fromLong(entry.getKey());var before=entry.getValue();
                // Container onStateReplaced callbacks may scatter contents even when
                // ordinary block drops are suppressed. Remove round inventory first.
                if(w.getBlockEntity(p) instanceof net.minecraft.inventory.Inventory inventory)inventory.clear();
                if(!w.getBlockState(p).equals(before.state()))w.setBlockState(p,before.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS);
                if(!w.getBlockState(p).equals(before.state()))throw new IOException("无法恢复方块 "+p);
                if(before.blockEntity()!=null){
                    var be=w.getBlockEntity(p);if(be==null)throw new IOException("缺失待恢复方块实体 "+p);
                    be.read(before.blockEntity().copy(),w.getRegistryManager());be.markDirty();w.updateListeners(p,before.state(),before.state(),Block.NOTIFY_LISTENERS);
                }
            }
            TerrainEntityCleanup.finishRestore(s,w,j.spawned,j.destroyed);
            j.channel.force(true);j.channel.close();j.channel=null;
            Files.move(file(s),file(s).resolveSibling(j.id+".restored.journal"));
            active=null;error="";return true;
        }catch(Exception e){return fail(j,"地图恢复未完成，日志保留，可在排除问题后重试重置",e);}
        finally{replaying=false;}
    }

    public static boolean flush(MinecraftServer s) {
        if(active==null||active.server!=s||active.channel==null)return true;
        try{active.channel.force(true);return true;}catch(IOException e){return fail(active,"地图日志保存失败",e);}
    }

    /** Forget runtime handles only. Pending on-disk recovery is never deleted here. */
    public static void clear(MinecraftServer s) {
        if(active!=null&&active.server==s){try{if(active.channel!=null)active.channel.close();}catch(IOException e){MWMod.LOGGER.error("Cannot close terrain journal",e);}active=null;}
    }

    private static boolean fail(Journal j,String message,Exception cause) {
        error=message;if(j!=null){j.failed=true;j.armed=true;}
        if(cause==null)MWMod.LOGGER.error(message);else MWMod.LOGGER.error(message,cause);
        return false;
    }
}
