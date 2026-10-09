package com.megawalls.minigame;

import com.megawalls.MWMod;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Write-ahead journal for touched blocks only. Includes block entity contents and original air. */
public final class DuelArena {
    private static final Map<BlockPos,NbtCompound> originals=new LinkedHashMap<>();
    private static String dimension;
    private static BlockPos min,max;
    private static int writing;
    private static boolean restoring,failed;
    private static Iterator<Map.Entry<BlockPos,NbtCompound>> iterator;
    private static Path root(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("classic-pvp-restore");}
    private static Path header(MinecraftServer s){return root(s).resolve("header.dat");}
    private static Path entry(MinecraftServer s,BlockPos p){return root(s).resolve("block_"+p.asLong()+".dat");}
    private static Path legacy(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("classic-pvp-arena.dat");}
    public static boolean exists(MinecraftServer s){return Files.exists(header(s))||Files.exists(legacy(s));}
    public static boolean active(){return min!=null;}
    public static boolean failed(){return failed;}
    public static boolean contains(ServerWorld w,BlockPos p){return active()&&w.getRegistryKey().getValue().toString().equals(dimension)&&p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    public static boolean overlaps(ServerWorld w,BlockPos a,BlockPos b){return active()&&w.getRegistryKey().getValue().toString().equals(dimension)&&Math.max(a.getX(),b.getX())>=min.getX()&&Math.min(a.getX(),b.getX())<=max.getX()&&Math.max(a.getY(),b.getY())>=min.getY()&&Math.min(a.getY(),b.getY())<=max.getY()&&Math.max(a.getZ(),b.getZ())>=min.getZ()&&Math.min(a.getZ(),b.getZ())<=max.getZ();}
    public static void clear(){originals.clear();dimension=null;min=max=null;writing=0;restoring=failed=false;iterator=null;}
    public static boolean save(MinecraftServer s,ServerWorld w,BlockPos a,BlockPos b){
        if(exists(s)||active())return false;
        dimension=w.getRegistryKey().getValue().toString();min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        var n=new NbtCompound();n.putString("dimension",dimension);n.putLong("min",min.asLong());n.putLong("max",max.asLong());
        if(!GameSessions.write(header(s),n)){clear();return false;}return true;
    }
    public static boolean capture(ServerWorld w,BlockPos p){
        if(!contains(w,p)||failed)return false;if(originals.containsKey(p))return true;
        if(originals.size()>=100000)return false;
        var n=new NbtCompound();n.putLong("pos",p.asLong());n.put("state",NbtHelper.fromBlockState(w.getBlockState(p)));
        var be=w.getBlockEntity(p);if(be!=null)n.put("entity",be.createNbtWithIdentifyingData(w.getRegistryManager()));
        if(!GameSessions.write(entry(w.getServer(),p),n)){failed=true;return false;}originals.put(p.toImmutable(),n);return true;
    }
    public static boolean shell(ServerWorld w,BlockPos p){return active()&&w.getRegistryKey().getValue().toString().equals(dimension)&&p.getX()>=min.getX()-1&&p.getX()<=max.getX()+1&&p.getY()>=min.getY()-1&&p.getY()<=max.getY()+1&&p.getZ()>=min.getZ()-1&&p.getZ()<=max.getZ()+1&&!contains(w,p);}
    public static boolean editable(ServerWorld w,BlockPos p){return contains(w,p)&&(originals.containsKey(p)?added(w,p):ArenaBlocks.replaceableGround(w.getBlockState(p)));}
    public static boolean allowChange(ServerWorld w,BlockPos p){
        if(writing>0&&restoring)return true;
        if(shell(w,p))return false;
        return !contains(w,p)||ClassicDuel.running()&&editable(w,p)&&capture(w,p);
    }
    public static boolean allowFlow(ServerWorld w,BlockPos from,BlockPos to){boolean a=contains(w,from),b=contains(w,to);return !a&&!b||a&&b&&ClassicDuel.running()&&editable(w,to);}

    public static <T>T authorized(Supplier<T> action){writing++;try{return action.get();}finally{writing--;}}
    public static boolean set(ServerWorld w,BlockPos p,BlockState state){return contains(w,p)&&authorized(()->w.setBlockState(p,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE));}
    public static boolean added(ServerWorld w,BlockPos p){var n=originals.get(p);return contains(w,p)&&n!=null&&ArenaBlocks.replaceableGround(NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),n.getCompound("state")));}
    public static void beginReset(){restoring=true;iterator=originals.entrySet().iterator();}
    private record SpawnCell(BlockPos pos,BlockState state,net.minecraft.block.entity.BlockEntity entity){}
    /** Restore only touched spawn cells; retain every journal entry for the final arena reset. */
    public static boolean prepareSpawn(ServerWorld w,double x,double y,double z){
        if(w==null||failed||restoring||!active()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        var body=new Box(x-.3,y,z-.3,x+.3,y+1.8,z+.3);
        var first=BlockPos.ofFloored(body.minX,y-.01,body.minZ);
        var last=BlockPos.ofFloored(body.maxX-1.0E-7,body.maxY-1.0E-7,body.maxZ-1.0E-7);
        long cells=(long)(last.getX()-first.getX()+1)*(last.getY()-first.getY()+1)*(last.getZ()-first.getZ()+1);
        if(cells<1||cells>12||!contains(w,first)||!contains(w,last))return false;
        int previousWriting=writing;boolean previousRestoring=restoring;
        try{
            var restore=new ArrayList<SpawnCell>();
            for(var cursor:BlockPos.iterate(first,last)){
                var pos=cursor.toImmutable();var n=originals.get(pos);if(n==null)continue;
                if(!n.contains("state",NbtElement.COMPOUND_TYPE)||n.getLong("pos")!=pos.asLong())throw new IllegalStateException("Invalid spawn journal entry "+pos);
                var stateNbt=n.getCompound("state");var blockId=Identifier.tryParse(stateNbt.getString("Name"));
                if(blockId==null||!Registries.BLOCK.containsId(blockId))throw new IllegalStateException("Unknown original spawn block "+pos);
                var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),stateNbt);
                net.minecraft.block.entity.BlockEntity entity=null;
                if(n.contains("entity",NbtElement.COMPOUND_TYPE)){
                    entity=net.minecraft.block.entity.BlockEntity.createFromNbt(pos,state,n.getCompound("entity"),w.getRegistryManager());
                    if(entity==null||!entity.supports(state))throw new IllegalStateException("Invalid original spawn block entity "+pos);
                }
                restore.add(new SpawnCell(pos,state,entity));
            }
            writing=previousWriting+1;restoring=true;
            for(var cell:restore){
                w.getChunk(cell.pos());w.removeBlockEntity(cell.pos());w.setBlockState(cell.pos(),cell.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                if(!w.getBlockState(cell.pos()).equals(cell.state()))throw new IllegalStateException("Cannot restore spawn block "+cell.pos());
                if(cell.entity()!=null){w.addBlockEntity(cell.entity());cell.entity().markDirty();}
            }
            boolean safe=w.isSpaceEmpty(body)&&!w.isBlockSpaceEmpty(null,new Box(body.minX,y-.01,body.minZ,body.maxX,y,body.maxZ));
            if(!safe)MWMod.LOGGER.warn("UHC PvP relay spawn is obstructed or has no floor at {}, {}, {}",x,y,z);
            return safe;
        }catch(Exception e){failed=true;MWMod.LOGGER.error("Cannot prepare UHC PvP relay spawn; journal retained",e);return false;}
        finally{writing=previousWriting;restoring=previousRestoring;}
    }
    public static void load(MinecraftServer s){
        clear();try{var n=GameSessions.read(Files.exists(header(s))?header(s):legacy(s));dimension=n.getString("dimension");min=BlockPos.fromLong(n.getLong("min"));max=BlockPos.fromLong(n.getLong("max"));var w=world(s);if(w==null)throw new IllegalStateException("Missing journal dimension");
            if(n.contains("blocks")){for(var v:n.getList("blocks",NbtElement.COMPOUND_TYPE)){var block=(NbtCompound)v;var pos=BlockPos.fromLong(block.getLong("pos"));if(!contains(w,pos)||w.isOutOfHeightLimit(pos)||originals.size()>=131072)throw new IllegalStateException("Invalid legacy backup");originals.put(pos,block);}beginReset();return;}
            try(var files=Files.list(root(s))){for(var p:files.filter(p->p.getFileName().toString().startsWith("block_")&&p.toString().endsWith(".dat")).toList()){
                var block=GameSessions.read(p);var pos=BlockPos.fromLong(block.getLong("pos"));if(!contains(w,pos)||w.isOutOfHeightLimit(pos)||!p.equals(entry(s,pos))||originals.size()>=100000)throw new IllegalStateException("Invalid journal entry");originals.put(pos,block);
            }}beginReset();
        }catch(Exception e){failed=true;MWMod.LOGGER.error("UHC PvP recovery blocked; journal retained",e);}
    }
    private static ServerWorld world(MinecraftServer s){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));}
    public static boolean reset(MinecraftServer s,int budget){
        if(failed||iterator==null)return false;var w=world(s);
        try{writing++;int count=0;while(iterator.hasNext()&&count++<budget){var e=iterator.next();var pos=e.getKey();var n=e.getValue();w.getChunk(pos);
                var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),n.getCompound("state"));
                w.removeBlockEntity(pos);w.setBlockState(pos,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                if(!w.getBlockState(pos).equals(state))throw new IllegalStateException("Cannot restore "+pos);
                if(n.contains("entity")){var be=net.minecraft.block.entity.BlockEntity.createFromNbt(pos,state,n.getCompound("entity"),w.getRegistryManager());if(be==null)throw new IllegalStateException("Missing block entity "+pos);w.addBlockEntity(be);be.markDirty();}
            }
            if(!iterator.hasNext()){w.save(null,true,false);for(var pos:originals.keySet())Files.deleteIfExists(entry(s,pos));Files.deleteIfExists(header(s));Files.deleteIfExists(legacy(s));clear();return true;}
        }catch(Exception e){failed=true;MWMod.LOGGER.error("Cannot restore classic-pvp blocks",e);}finally{if(writing>0)writing--;}return false;
    }
}
