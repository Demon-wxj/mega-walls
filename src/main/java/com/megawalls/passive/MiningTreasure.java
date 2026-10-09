package com.megawalls.passive;

import com.megawalls.game.GameState;
import com.megawalls.game.RegionProtection;
import com.megawalls.player.MWPlayerData;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Project Classic profile: probabilities remain explicit, not claimed as official loot odds. */
public final class MiningTreasure {
    public static final class Entry {
        public String item; public int min, max, weight;
        public Entry(String item,int min,int max,int weight) {this.item=item;this.min=min;this.max=max;this.weight=weight;}
    }
    public static final class Rules {
        public int blazeTimingVersion=3;
        public int blazeWindupTicks=com.megawalls.skill.ClassBalanceRules.BLAZE_WINDUP, blazeIntervalTicks=6;
        public double chestChance=.05, herobrineChestChance=.10;
        // Dec 2016 patch confirms a reduced rate for soil/wood, but gives no exact ratio.
        public double softBlockChanceMultiplier=.5;
        // June 2017 player example says 10 -> 30; March patch says "300% more".
        // Keep the conflicting interpretation explicit/configurable, not a flat bonus.
        public int molemanIronMultiplier=3;
        public int rolls=4;
        public List<Entry> loot=new ArrayList<>(List.of(
            new Entry("minecraft:bread",2,4,20), new Entry("minecraft:cooked_beef",1,3,20),
            new Entry("minecraft:baked_potato",2,5,15), new Entry("minecraft:arrow",4,12,20),
            new Entry("minecraft:iron_ingot",2,5,15), new Entry("minecraft:coal",2,6,8),
            new Entry("minecraft:tnt",1,2,2), new Entry("minecraft:bow",1,1,3),
            new Entry("minecraft:torch",4,12,8), new Entry("minecraft:furnace",1,1,4),
            new Entry("minecraft:cobblestone",8,24,8), new Entry("minecraft:redstone",2,6,4),
            new Entry("minecraft:cake",1,1,3)));
        void validate() {
            if(blazeWindupTicks<1||blazeWindupTicks>100||blazeIntervalTicks<1||blazeIntervalTicks>40
                ||!Double.isFinite(chestChance)||!Double.isFinite(herobrineChestChance)
                ||!Double.isFinite(softBlockChanceMultiplier)||softBlockChanceMultiplier<0||softBlockChanceMultiplier>1
                ||chestChance<0||chestChance>1||herobrineChestChance<0||herobrineChestChance>1
                ||molemanIronMultiplier<1||molemanIronMultiplier>8
                ||rolls<1||rolls>20||loot==null||loot.isEmpty()||loot.size()>100)
                throw new IllegalArgumentException("Invalid Classic timing or chest configuration");
            for(var entry:loot) {
                var id=entry==null?null:Identifier.tryParse(entry.item);
                if(id==null||!Registries.ITEM.containsId(id)||Registries.ITEM.get(id)==Items.AIR
                    ||entry.min<1||entry.max<entry.min||entry.max>64||entry.weight<1||entry.weight>10000
                    ||entry.max>new ItemStack(Registries.ITEM.get(id)).getMaxCount())
                    throw new IllegalArgumentException("Invalid Classic loot entry");
            }
        }
    }
    private static Rules rules=new Rules();
    public static Rules rules() { return rules; }
    public static final class Saved extends net.minecraft.world.PersistentState {
        final Set<Long> used=new HashSet<>();
        final Set<Long> gathered=new HashSet<>();
        final Set<Long> artificial=new HashSet<>();
        final Map<Long,String> chests=new HashMap<>();
        static final Type<Saved> TYPE=new Type<>(Saved::new,(nbt,lookup)->{
            var state=new Saved();for(long pos:nbt.getLongArray("used"))state.used.add(pos);
            for(long pos:nbt.getLongArray("gathered"))state.gathered.add(pos);
            for(long pos:nbt.getLongArray("artificial"))state.artificial.add(pos);
            // Old treasure records cannot distinguish mined and player-placed resource positions.
            if(!nbt.contains("gathered"))state.gathered.addAll(state.used);
            var boxes=nbt.getCompound("chests");for(String key:boxes.getKeys())state.chests.put(Long.parseLong(key),boxes.getString(key));return state;
        },null);
        @Override public net.minecraft.nbt.NbtCompound writeNbt(net.minecraft.nbt.NbtCompound nbt,net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
            nbt.putLongArray("used",used.stream().mapToLong(Long::longValue).toArray());
            nbt.putLongArray("gathered",gathered.stream().mapToLong(Long::longValue).toArray());
            nbt.putLongArray("artificial",artificial.stream().mapToLong(Long::longValue).toArray());
            var boxes=new net.minecraft.nbt.NbtCompound();chests.forEach((pos,id)->boxes.putString(Long.toString(pos),id));nbt.put("chests",boxes);return nbt;
        }
    }
    private static Saved saved(ServerWorld world) {return world.getPersistentStateManager().getOrCreate(Saved.TYPE,"megawalls_treasure");}
    public static boolean eligible(BlockState state) {
        return state.isOf(Blocks.STONE)||state.isOf(Blocks.DEEPSLATE)||state.isOf(Blocks.GRANITE)
            ||state.isOf(Blocks.DIORITE)||state.isOf(Blocks.ANDESITE)||state.isOf(Blocks.TUFF)
            ||state.isIn(BlockTags.COAL_ORES)||state.isIn(BlockTags.IRON_ORES)
            ||state.isIn(BlockTags.GOLD_ORES)||state.isIn(BlockTags.REDSTONE_ORES)
            ||state.isIn(BlockTags.LAPIS_ORES)||state.isIn(BlockTags.DIAMOND_ORES)||state.isIn(BlockTags.EMERALD_ORES);
    }
    private static boolean softBlock(BlockState state) {
        return state.isOf(Blocks.DIRT)||state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.SAND)
            ||state.isOf(Blocks.RED_SAND)||state.isOf(Blocks.GRAVEL)||state.isOf(Blocks.COARSE_DIRT)
            ||state.isOf(Blocks.FARMLAND)||state.isOf(Blocks.OAK_LOG)||state.isOf(Blocks.SPRUCE_LOG)
            ||state.isOf(Blocks.BIRCH_LOG)||state.isOf(Blocks.JUNGLE_LOG)||state.isOf(Blocks.ACACIA_LOG)
            ||state.isOf(Blocks.DARK_OAK_LOG);
    }
    public static double chance(int classId,BlockState state) {
        double base=classId==6?rules.herobrineChestChance:rules.chestChance;
        return eligible(state)?base:softBlock(state)?base*rules.softBlockChanceMultiplier:0;
    }
    private static boolean canFind(ServerPlayerEntity player) {
        int phase=GameState.phase(player);
        return GameState.active(player)&&(phase==1||phase==2)&&!player.isCreative()
            &&player.getServerWorld()==player.getServer().getOverworld();
    }
    public static void placed(ServerWorld world,BlockPos pos,BlockState state) {
        if(gatherable(state)&&com.megawalls.game.MatchFlow.running(world.getServer())) {
            var data=saved(world);data.used.add(pos.asLong());data.artificial.add(pos.asLong());data.markDirty();
        }
    }
    private static boolean gatherable(BlockState state){
        return eligible(state)||softBlock(state)||state.isIn(BlockTags.LOGS)||state.isIn(BlockTags.SHOVEL_MINEABLE);
    }
    public static boolean natural(ServerWorld world,BlockPos pos){
        var data=saved(world);return !data.artificial.contains(pos.asLong())&&!data.gathered.contains(pos.asLong());
    }
    public static boolean mayGather(ServerPlayerEntity player,BlockPos pos){
        int phase=GameState.phase(player);
        return GameState.active(player)&&!player.isCreative()&&phase>=1&&phase<=3
                &&natural(player.getServerWorld(),pos);
    }
    /** Called once after successful manual breaking, independently of treasure chest rolls. */
    public static boolean claimGathering(ServerPlayerEntity player,BlockPos pos,BlockState old){
        if(!gatherable(old)||!mayGather(player,pos))return false;
        var data=saved(player.getServerWorld());data.gathered.add(pos.asLong());data.markDirty();return true;
    }
    /** Legitimate map log regrowth restores gathering, but never grants another treasure roll. */
    public static void regrown(ServerWorld world,BlockPos pos){
        var data=saved(world);
        if(!data.artificial.contains(pos.asLong())&&data.gathered.remove(pos.asLong()))data.markDirty();
    }
    public static boolean migrateTiming(com.google.gson.JsonObject json,Rules loaded){
        if(json.has("blazeTimingVersion")&&json.get("blazeTimingVersion").getAsInt()>=3)return false;
        if(loaded.blazeWindupTicks==80||!json.has("blazeTimingVersion")&&loaded.blazeWindupTicks==6)
            loaded.blazeWindupTicks=com.megawalls.skill.ClassBalanceRules.BLAZE_WINDUP;
        loaded.blazeTimingVersion=3;
        json.addProperty("blazeWindupTicks",loaded.blazeWindupTicks);
        json.addProperty("blazeTimingVersion",3);
        return true;
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            rules=new Rules();
            var path=FabricLoader.getInstance().getConfigDir().resolve("megawalls-classic.json");
            var gson=new GsonBuilder().setPrettyPrinting().create();
            try {
                if(Files.exists(path)) {
                    var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    var loaded=gson.fromJson(json,Rules.class);
                    if(loaded==null)throw new IllegalArgumentException("Empty Classic config");
                    loaded.validate();
                    if(migrateTiming(json,loaded)){
                        var backup=path.resolveSibling("megawalls-classic.pre-1.18.12.json");
                        if(!Files.exists(backup))Files.copy(path,backup);
                        Files.writeString(path,gson.toJson(json));
                    }
                    rules=loaded;
                } else { Files.createDirectories(path.getParent()); Files.writeString(path,gson.toJson(rules)); }
            } catch(Exception e) { com.megawalls.MWMod.LOGGER.error("Invalid megawalls-classic.json; using documented defaults",e); }
        });
    }
    public static void onBreak(ServerPlayerEntity player,BlockPos pos,BlockState old) {
        int cls=MWPlayerData.getServer(player).getClassId();
        if(!canFind(player)||(!eligible(old)&&!softBlock(old)))return;
        var world=player.getServerWorld();
        var data=saved(world);
        if(!data.used.add(pos.asLong()))return;
        data.markDirty();
        if(player.getRandom().nextDouble()>=chance(cls,old))return;
        create(player,pos);
    }
    public static boolean create(ServerPlayerEntity player,BlockPos pos) {
        var world=player.getServerWorld();
        if(!canFind(player)||!world.getBlockState(pos).isAir()
            ||RegionProtection.contains(world,pos)||!world.getWorldBorder().contains(pos))return false;
        // Never join a pre-existing player chest or overwrite block entities.
        for(var direction:net.minecraft.util.math.Direction.Type.HORIZONTAL)
            if(world.getBlockState(pos.offset(direction)).isOf(Blocks.CHEST))return false;
        if(!world.setBlockState(pos,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,player.getHorizontalFacing().getOpposite()),3))return false;
        if(!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))return false;
        String id=UUID.randomUUID().toString();
        var marker=new net.minecraft.nbt.NbtCompound();marker.putString("mw_treasure_id",id);
        chest.readComponents(net.minecraft.component.ComponentMap.EMPTY,net.minecraft.component.ComponentChanges.builder()
            .add(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("采矿宝箱"))
            .add(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(marker)).build());
        fill(player,chest);
        var data=saved(world);data.chests.put(pos.asLong(),id);data.markDirty();
        world.playSound(null,pos,net.minecraft.sound.SoundEvents.BLOCK_CHEST_OPEN,net.minecraft.sound.SoundCategory.BLOCKS,.7f,1.2f);
        player.sendMessage(net.minecraft.text.Text.literal("§6发现采矿宝箱！§e 打开箱子领取物资。"),true);
        return true;
    }
    public static void fill(ServerPlayerEntity player,net.minecraft.inventory.Inventory inventory) {
        var random=player.getRandom();
        List<ItemStack> items=new ArrayList<>();
        int cls=MWPlayerData.getServer(player).getClassId();
        int total=rules.loot.stream().mapToInt(e->e.weight).sum();
        for(int i=0;i<rules.rolls;i++) {
            int choice=random.nextInt(total);
            for(var entry:rules.loot) {choice-=entry.weight;if(choice<0){
                var item=Registries.ITEM.get(Identifier.of(entry.item));
                int count=entry.min+random.nextInt(entry.max-entry.min+1);
                if(cls==12&&item==Items.IRON_INGOT)count*=rules.molemanIronMultiplier;
                // Configured bonus may exceed 64. Split valid stacks instead of silently overstacking.
                int limit=new ItemStack(item).getMaxCount();
                while(count>0){int amount=Math.min(count,limit);items.add(new ItemStack(item,amount));count-=amount;}
                break;
            }}
        }
        if(cls==11&&random.nextFloat()<.6f)items.add(new ItemStack(Items.GOLDEN_APPLE));
        if(cls==4&&random.nextFloat()<.35f)for(int i=0;i<3;i++) {
            var potion=new ItemStack(Items.POTION);
            potion.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,new net.minecraft.component.type.PotionContentsComponent(
                Optional.empty(),Optional.of(0xFFFF77),List.of(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.ABSORPTION,1200,1))));
            items.add(potion);
        }
        List<Integer> slots=new ArrayList<>();
        for(int i=0;i<inventory.size();i++)if(inventory.getStack(i).isEmpty())slots.add(i);
        for(var item:items) {if(slots.isEmpty())break;inventory.setStack(slots.remove(random.nextInt(slots.size())),item);}
        inventory.markDirty();
    }
    public static void clearChests(MinecraftServer server) {
        for(var world:server.getWorlds()) {
            clearChests(world,pos->true);
        }
    }
    /** Clear only generated chests selected by the active arena; ordinary map/player chests are never touched. */
    public static void clearChests(ServerWorld world,java.util.function.Predicate<BlockPos> inArena) {
            var data=saved(world);
            var entries=data.chests.entrySet().iterator();boolean changed=false;
            while(entries.hasNext()) {
                var entry=entries.next();
                var pos=BlockPos.fromLong(entry.getKey());
                if(!inArena.test(pos))continue;
                if(world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    var marker=chest.getComponents().get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                    if(marker!=null&&entry.getValue().equals(marker.copyNbt().getString("mw_treasure_id"))) {
                        chest.clear();world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
                    }
                }
                entries.remove();changed=true;
            }
            // A new match alone does not restore mined terrain. Clearing every world's used set enables repeat loot rolls.
            if(changed)data.markDirty();
    }
    /** Called only for positions successfully restored by the arena journal. Other maps keep their anti-reroll history. */
    public static void resetRestoredPositions(ServerWorld world,Collection<BlockPos> restored){
        var data=saved(world);boolean changed=false;
        for(var pos:restored){
            changed|=data.used.remove(pos.asLong());
            changed|=data.gathered.remove(pos.asLong());
            changed|=data.artificial.remove(pos.asLong());
            changed|=data.chests.remove(pos.asLong())!=null;
        }
        if(changed)data.markDirty();
    }
}
