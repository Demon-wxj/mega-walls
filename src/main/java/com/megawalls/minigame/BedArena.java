package com.megawalls.minigame;

import com.megawalls.MWMod;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Write-ahead journal for touched blocks only. Includes block entity contents and original air. */
public final class BedArena {
    private static final Map<BlockPos,NbtCompound> originals=new LinkedHashMap<>();
    private static String dimension;
    private static BlockPos min,max;
    private static int writing;
    private static boolean restoring,failed;
    private static Iterator<Map.Entry<BlockPos,NbtCompound>> iterator;
    private static Path root(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("bedwars-restore");}
    private static Path header(MinecraftServer s){return root(s).resolve("header.dat");}
    private static Path entry(MinecraftServer s,BlockPos p){return root(s).resolve("block_"+p.asLong()+".dat");}
    public static boolean exists(MinecraftServer s){return Files.exists(header(s));}
    public static boolean active(){return min!=null;}
    public static boolean failed(){return failed;}
    public static boolean contains(ServerWorld w,BlockPos p){return active()&&w.getRegistryKey().getValue().toString().equals(dimension)&&p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    public static boolean overlaps(ServerWorld w,BlockPos a,BlockPos b){return active()&&w.getRegistryKey().getValue().toString().equals(dimension)&&Math.max(a.getX(),b.getX())>=min.getX()&&Math.min(a.getX(),b.getX())<=max.getX()&&Math.max(a.getY(),b.getY())>=min.getY()&&Math.min(a.getY(),b.getY())<=max.getY()&&Math.max(a.getZ(),b.getZ())>=min.getZ()&&Math.min(a.getZ(),b.getZ())<=max.getZ();}
    public static void clear(){originals.clear();dimension=null;min=max=null;writing=0;restoring=failed=false;iterator=null;}
    public static boolean begin(MinecraftServer s,ServerWorld w,BlockPos a,BlockPos b){
        if(exists(s)||active())return false;
        dimension=w.getRegistryKey().getValue().toString();min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        try{validateBounds(w,min,max);}catch(Exception e){clear();MWMod.LOGGER.error("Invalid bedwars journal bounds",e);return false;}
        var n=new NbtCompound();n.putString("dimension",dimension);n.putLong("min",min.asLong());n.putLong("max",max.asLong());
        if(!GameSessions.write(header(s),n)){clear();return false;}return true;
    }
    public static boolean capture(ServerWorld w,BlockPos p){
        if(!contains(w,p)||failed)return false;if(originals.containsKey(p))return true;
        if(originals.size()>=100000){failed=true;MWMod.LOGGER.error("Bedwars journal reached 100000 blocks; stop further changes and retain recovery");return false;}
        var n=new NbtCompound();n.putLong("pos",p.asLong());n.put("state",NbtHelper.fromBlockState(w.getBlockState(p)));
        var be=w.getBlockEntity(p);if(be!=null)n.put("entity",be.createNbtWithIdentifyingData(w.getRegistryManager()));
        if(!GameSessions.write(entry(w.getServer(),p),n)){failed=true;return false;}originals.put(p.toImmutable(),n);return true;
    }
    public static boolean allowChange(ServerWorld w,BlockPos p){return !contains(w,p)||writing>0&&(restoring||capture(w,p));}
    /** Natural block physics is frozen here; a rejected source removal must not create a falling duplicate. */
    public static boolean beforeSpawn(ServerWorld w,net.minecraft.entity.Entity entity){return !(entity instanceof net.minecraft.entity.FallingBlockEntity&&contains(w,entity.getBlockPos()));}
    public static <T>T authorized(Supplier<T> action){writing++;try{return action.get();}finally{writing--;}}
    public static boolean set(ServerWorld w,BlockPos p,BlockState state){return contains(w,p)&&authorized(()->w.setBlockState(p,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE));}
    public static boolean added(ServerWorld w,BlockPos p){var n=originals.get(p);return contains(w,p)&&n!=null&&ArenaBlocks.replaceableGround(NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),n.getCompound("state")));}
    public static void beginReset(){restoring=true;iterator=originals.entrySet().iterator();}
    private static void validateBounds(ServerWorld w,BlockPos a,BlockPos b){
        if(a.getX()>b.getX()||a.getY()>b.getY()||a.getZ()>b.getZ()
                ||(long)b.getX()-a.getX()>1023||(long)b.getY()-a.getY()>1023||(long)b.getZ()-a.getZ()>1023
                ||w.isOutOfHeightLimit(a)||w.isOutOfHeightLimit(b)||!w.getWorldBorder().contains(a)||!w.getWorldBorder().contains(b))
            throw new IllegalStateException("Invalid journal bounds");
    }
    private static BlockState validatedState(ServerWorld w,NbtCompound n){
        if(!n.contains("state",NbtElement.COMPOUND_TYPE))throw new IllegalStateException("Missing journal block state");
        var encoded=n.getCompound("state");
        if(!encoded.contains("Name",NbtElement.STRING_TYPE)||Identifier.tryParse(encoded.getString("Name"))==null)
            throw new IllegalStateException("Invalid journal block ID");
        var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),encoded);
        if(!NbtHelper.fromBlockState(state).equals(encoded))throw new IllegalStateException("Unknown or noncanonical journal block state");
        return state;
    }
    private static void validateBlockEntity(ServerWorld w,BlockPos pos,BlockState state,NbtCompound n){
        if(!n.contains("entity")){if(state.hasBlockEntity())throw new IllegalStateException("Missing journal block entity");return;}
        if(!n.contains("entity",NbtElement.COMPOUND_TYPE))throw new IllegalStateException("Invalid journal block entity tag");
        var data=n.getCompound("entity");
        if(!data.contains("id",NbtElement.STRING_TYPE))throw new IllegalStateException("Missing journal block entity ID");
        var id=Identifier.tryParse(data.getString("id"));
        var type=id==null?null:Registries.BLOCK_ENTITY_TYPE.get(id);
        if(type==null||!type.supports(state)||!data.contains("x",NbtElement.INT_TYPE)||!data.contains("y",NbtElement.INT_TYPE)
                ||!data.contains("z",NbtElement.INT_TYPE)||data.getInt("x")!=pos.getX()||data.getInt("y")!=pos.getY()||data.getInt("z")!=pos.getZ()
                ||net.minecraft.block.entity.BlockEntity.createFromNbt(pos,state,data.copy(),w.getRegistryManager())==null)
            throw new IllegalStateException("Invalid or unsupported journal block entity");
    }
    public static void load(MinecraftServer s){
        clear();try{var n=GameSessions.read(header(s));
            if(!n.contains("dimension",NbtElement.STRING_TYPE)||Identifier.tryParse(n.getString("dimension"))==null
                    ||!n.contains("min",NbtElement.LONG_TYPE)||!n.contains("max",NbtElement.LONG_TYPE))throw new IllegalStateException("Invalid journal header fields");
            dimension=n.getString("dimension");min=BlockPos.fromLong(n.getLong("min"));max=BlockPos.fromLong(n.getLong("max"));var w=world(s);if(w==null)throw new IllegalStateException("Missing journal dimension");validateBounds(w,min,max);
            var checked=new LinkedHashMap<BlockPos,NbtCompound>();
            try(var files=Files.list(root(s))){for(var p:files.filter(p->p.getFileName().toString().startsWith("block_")&&p.toString().endsWith(".dat")).toList()){
                var block=GameSessions.read(p);if(!block.contains("pos",NbtElement.LONG_TYPE))throw new IllegalStateException("Missing journal position");
                var pos=BlockPos.fromLong(block.getLong("pos"));if(!contains(w,pos)||w.isOutOfHeightLimit(pos)||!p.equals(entry(s,pos))||checked.size()>=100000)throw new IllegalStateException("Invalid journal entry");
                var state=validatedState(w,block);validateBlockEntity(w,pos,state,block);
                if(checked.putIfAbsent(pos,block)!=null)throw new IllegalStateException("Duplicate journal position");
            }}originals.putAll(checked);beginReset();
        }catch(Exception e){failed=true;MWMod.LOGGER.error("Bedwars recovery blocked; journal retained",e);}
    }
    private static ServerWorld world(MinecraftServer s){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));}
    public static boolean restore(MinecraftServer s,int budget){
        if(failed||iterator==null)return false;var w=world(s);
        try{writing++;int count=0;while(iterator.hasNext()&&count++<budget){var e=iterator.next();var pos=e.getKey();var n=e.getValue();w.getChunk(pos);
                var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),n.getCompound("state"));
                w.removeBlockEntity(pos);w.setBlockState(pos,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                if(!w.getBlockState(pos).equals(state))throw new IllegalStateException("Cannot restore "+pos);
                if(n.contains("entity")){var be=net.minecraft.block.entity.BlockEntity.createFromNbt(pos,state,n.getCompound("entity"),w.getRegistryManager());if(be==null)throw new IllegalStateException("Missing block entity "+pos);w.addBlockEntity(be);be.markDirty();}
            }
            if(!iterator.hasNext()){w.save(null,true,false);for(var pos:originals.keySet())Files.deleteIfExists(entry(s,pos));Files.delete(header(s));clear();return true;}
        }catch(Exception e){failed=true;MWMod.LOGGER.error("Cannot restore bedwars blocks",e);}finally{if(writing>0)writing--;}return false;
    }
}
