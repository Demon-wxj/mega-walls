package com.megawalls.minigame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megawalls.MWMod;
import com.megawalls.menu.HubMenu;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockPredicatesChecker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Snowball Spleef: existing snow is the only editable terrain, backed up before play. */
public final class Spleef {
    public static final String ID="final_duel";
    public enum Stage { WAITING, COUNTDOWN, RUNNING, RESULT, RESETTING }
    public record Point(double x,double y,double z,float yaw,float pitch) {}
    public static final class Config {
        public String dimension="minecraft:overworld";
        public Point corner1,corner2;
        public List<Point> spawns=new ArrayList<>();
        public Double loseY;
        public int minPlayers=2,maxPlayers=SpleefRules.MAX_PLAYERS,countdownSeconds=10,durationSeconds=600,graceTicks=40,shotDelayTicks=4,resultSeconds=5;
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Config config=new Config();
    private static Stage stage=Stage.WAITING;
    private static boolean validConfig=true,resetFailed,authorized;
    private static final Set<UUID> queue=new LinkedHashSet<>(),players=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static final Map<BlockPos,BlockState> snow=new LinkedHashMap<>();
    private static Iterator<Map.Entry<BlockPos,BlockState>> restoring;
    private static String snapshotDimension;
    private static int deadline,started;
    private static long round;
    private record ThrowUse(int slot,long round) {}
    private static final Map<UUID,Integer> ballSlots=new HashMap<>();
    private static final Map<UUID,ThrowUse> throwing=new HashMap<>();
    public static Stage stage(){return stage;}
    public static int aliveCount(){return alive.size();}
    public static int queueSize(){return queue.size();}
    public static boolean queued(ServerPlayerEntity p){return queue.contains(p.getUuid());}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(ID);}
    public static boolean inRound(ServerPlayerEntity p){return (stage==Stage.RUNNING||stage==Stage.RESULT)&&players.contains(p.getUuid());}
    public static boolean playing(ServerPlayerEntity p){return stage==Stage.RUNNING&&member(p)&&alive.contains(p.getUuid());}
    private static Path configFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("spleef.json");}
    private static Path snapshotFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("spleef-snow.dat");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("spleef-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static ServerWorld world(MinecraftServer s,String dimension){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));}
    private static boolean pointValid(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984&&Math.abs(p.y)<2048&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch);}
    private static boolean values(Config c){return c!=null&&c.dimension!=null&&Identifier.tryParse(c.dimension)!=null&&c.spawns!=null&&c.spawns.size()<=32&&c.spawns.stream().allMatch(Spleef::pointValid)&&(c.loseY==null||Double.isFinite(c.loseY)&&Math.abs(c.loseY)<2048)&&SpleefRules.settings(c.minPlayers,c.maxPlayers,c.countdownSeconds,c.durationSeconds,c.graceTicks,c.shotDelayTicks,c.resultSeconds);}
    private static boolean save(MinecraftServer s){try{var path=configFile(s);var tmp=path.resolveSibling("spleef.json.tmp");Files.writeString(tmp,JSON.toJson(config));Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);return true;}catch(Exception e){MWMod.LOGGER.error("Cannot save Spleef config",e);return false;}}
    private static void load(MinecraftServer s){try{var c=Files.exists(configFile(s))?JSON.fromJson(Files.readString(configFile(s)),Config.class):new Config();if(c!=null&&c.minPlayers>=2&&c.minPlayers<=c.maxPlayers&&c.maxPlayers<=32&&c.maxPlayers>SpleefRules.MAX_PLAYERS){c.maxPlayers=SpleefRules.MAX_PLAYERS;c.minPlayers=Math.min(c.minPlayers,SpleefRules.MAX_PLAYERS);}if(!values(c))throw new IllegalArgumentException("Invalid Spleef settings");config=c;validConfig=true;}catch(Exception e){validConfig=false;MWMod.LOGGER.error("Cannot load spleef.json",e);}}
    private static int minX(){return MathHelper.floor(Math.min(config.corner1.x,config.corner2.x));}
    private static int maxX(){return MathHelper.floor(Math.max(config.corner1.x,config.corner2.x));}
    private static int minY(){return MathHelper.floor(Math.min(config.corner1.y,config.corner2.y));}
    private static int maxY(){return MathHelper.floor(Math.max(config.corner1.y,config.corner2.y));}
    private static int minZ(){return MathHelper.floor(Math.min(config.corner1.z,config.corner2.z));}
    private static int maxZ(){return MathHelper.floor(Math.max(config.corner1.z,config.corner2.z));}
    private static boolean bounded(){return pointValid(config.corner1)&&pointValid(config.corner2)&&SpleefRules.bounded(maxX()-minX()+1,maxY()-minY()+1,maxZ()-minZ()+1);}
    private static double loseY(){return config.loseY==null?minY()-.5:config.loseY;}
    private static int capacity(){return SpleefRules.capacity(config.maxPlayers,config.spawns.size());}
    private static boolean snow(BlockState state){return state.isOf(Blocks.SNOW_BLOCK)||state.isOf(Blocks.SNOW);}
    public static boolean inside(ServerWorld w,BlockPos p){return pointValid(config.corner1)&&pointValid(config.corner2)&&w.getRegistryKey().getValue().toString().equals(config.dimension)&&p.getX()>=minX()&&p.getX()<=maxX()&&p.getY()>=minY()&&p.getY()<=maxY()&&p.getZ()>=minZ()&&p.getZ()<=maxZ();}
    public static boolean overlaps(ServerWorld w,BlockPos a,BlockPos b){return (stage==Stage.RUNNING||stage==Stage.RESULT||stage==Stage.RESETTING)&&pointValid(config.corner1)&&pointValid(config.corner2)&&w.getRegistryKey().getValue().toString().equals(config.dimension)&&Math.max(a.getX(),b.getX())>=minX()&&Math.min(a.getX(),b.getX())<=maxX()&&Math.max(a.getY(),b.getY())>=minY()&&Math.min(a.getY(),b.getY())<=maxY()&&Math.max(a.getZ(),b.getZ())>=minZ()&&Math.min(a.getZ(),b.getZ())<=maxZ();}
    public static boolean protectedAt(ServerWorld w,BlockPos p){return (stage==Stage.RUNNING||stage==Stage.RESULT||stage==Stage.RESETTING)&&(inside(w,p)||w.getRegistryKey().getValue().toString().equals(snapshotDimension)&&snow.containsKey(p));}
    public static boolean allowChange(ServerWorld w,BlockPos p){return authorized||!protectedAt(w,p);}
    private static String issue(MinecraftServer s){
        if(!validConfig)return "配置无法读取";
        if(!bounded())return "需设置 arena1、arena2；区域边长最多 256×64×256，扫描体积最多 "+SpleefRules.MAX_SCAN_BLOCKS+" 格";
        if(config.spawns.size()<config.minPlayers)return "需设置足够的独立出生点";
        var w=world(s,config.dimension);if(w==null)return "维度不存在";
        if(loseY()>minY())return "淘汰高度应低于最低雪地层";
        var positions=new HashSet<BlockPos>();
        for(var pt:config.spawns){var p=BlockPos.ofFloored(pt.x,pt.y,pt.z);w.getChunk(p);boolean support=snow(w.getBlockState(p.down()))&&inside(w,p.down())||snow(w.getBlockState(p))&&inside(w,p);
            if(!positions.add(p)||pt.x<minX()+.3||pt.x>maxX()+.7||pt.z<minZ()+.3||pt.z>maxZ()+.7||pt.y<=loseY()||!support||!w.getWorldBorder().contains(p)||!w.isSpaceEmpty(new Box(pt.x-.3,pt.y,pt.z-.3,pt.x+.3,pt.y+1.8,pt.z+.3)))return "出生点需独立、位于选中雪地上方并有安全净空";
        }
        var a=new BlockPos(minX(),minY(),minZ());var b=new BlockPos(maxX(),maxY(),maxZ());
        if(w.isOutOfHeightLimit(a)||w.isOutOfHeightLimit(b)||!w.getWorldBorder().contains(a)||!w.getWorldBorder().contains(b))return "场地超出世界范围";
        if(TntRun.overlaps(w,a,b)||PotDuel.overlaps(w,a,b)||BedArena.overlaps(w,a,b)||DuelArena.overlaps(w,a,b)||HotPotato.overlaps(w,a,b))return "场地与正在运行的其它游戏重叠";
        return "场地就绪";
    }
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{queue.clear();players.clear();alive.clear();snow.clear();ballSlots.clear();throwing.clear();stage=Stage.WAITING;restoring=null;resetFailed=false;authorized=false;round=0;load(s);if(Files.exists(snapshotFile(s))){stage=Stage.RESETTING;loadSnapshot(s);}});
        ServerTickEvents.END_SERVER_TICK.register(Spleef::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(stage==Stage.RUNNING||stage==Stage.RESULT||stage==Stage.COUNTDOWN)cleanup(s,"服务器关闭，本局取消。");if(stage==Stage.RESETTING&&!resetFailed)restoreBatch(s,Integer.MAX_VALUE);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){if(inRound(p)){p.changeGameMode(GameMode.SPECTATOR);spectate(p);}else{recover(p);if(!hasRecovery(p))HubMenu.travel(p,GameSessions.HUB);}}});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,source,amount)->!(e instanceof ServerPlayerEntity p)||!member(p)||source.isOf(DamageTypes.GENERIC_KILL));
        ServerLivingEntityEvents.AFTER_DEATH.register((e,source)->{if(e instanceof ServerPlayerEntity p&&member(p)){eliminate(p,"死亡");resolve(p.getServer(),false);}});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(CommandManager.literal("spleef")
            .then(CommandManager.literal("join").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),ID)?1:0))
            .then(CommandManager.literal("enroll").executes(c->enroll(c.getSource().getPlayerOrThrow())?1:0))
            .then(CommandManager.literal("leave").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0))
            .then(CommandManager.literal("start").executes(c->{boolean ok=requestStart(c.getSource().getPlayerOrThrow());if(!ok)c.getSource().sendError(Text.literal("无法开局：请先参加游戏，并检查人数、场地和状态。"));return ok?1:0;}))
            .then(CommandManager.literal("stop").executes(c->{cleanup(c.getSource().getServer(),"本局已取消。");return 1;}))
            .then(CommandManager.literal("reset").executes(c->{var s=c.getSource().getServer();if(stage!=Stage.WAITING&&stage!=Stage.RESETTING)cleanup(s,"场地正在重置。");if(stage==Stage.RESETTING&&resetFailed)loadSnapshot(s);return 1;}))
            .then(CommandManager.literal("arena1").executes(c->point(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("arena2").executes(c->point(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("loseY")
                .then(CommandManager.argument("height",DoubleArgumentType.doubleArg(-2047,2047)).executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);config.loseY=DoubleArgumentType.getDouble(c,"height");return persist(c.getSource().getServer(),old);})))
            .then(CommandManager.literal("spawn")
                .then(CommandManager.literal("add").executes(c->point(c.getSource().getPlayerOrThrow(),0)))
                .then(CommandManager.literal("clear").executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);config.spawns.clear();return persist(c.getSource().getServer(),old);})))
            .then(CommandManager.literal("rule")
                .then(CommandManager.argument("name",StringArgumentType.word()).suggests((c,b)->{for(String value:List.of("min","max","duration","grace","shotDelay","result"))b.suggest(value);return b.buildFuture();})
                .then(CommandManager.argument("value",IntegerArgumentType.integer(0,3600)).executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);int v=IntegerArgumentType.getInteger(c,"value");
                    switch(StringArgumentType.getString(c,"name")){case "min"->config.minPlayers=v;case "max"->config.maxPlayers=v;case "duration"->config.durationSeconds=v;case "grace"->config.graceTicks=v;case "shotDelay"->config.shotDelayTicks=v;case "result"->config.resultSeconds=v;default->{return 0;}}
                    int result=persist(c.getSource().getServer(),old);c.getSource().sendFeedback(()->Text.literal(result==1?"决一死战规则已保存。":"参数超出范围或保存失败。"),false);return result;}))))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("决一死战："+stage+" / 排队 "+queue.size()+" / 存活 "+alive.size()+" / "+(resetFailed?"恢复失败；检查日志后 /spleef reset":issue(c.getSource().getServer()))),false);return 1;}))));
    }
    private static int persist(MinecraftServer s,String old){if(!values(config)||!save(s)){config=JSON.fromJson(old,Config.class);return 0;}validConfig=true;return 1;}
    private static int point(ServerPlayerEntity p,int which){
        if(stage!=Stage.WAITING){p.sendMessage(Text.literal("先结束对局并等待雪地恢复。"),true);return 0;}
        String dim=p.getServerWorld().getRegistryKey().getValue().toString();if((config.corner1!=null||config.corner2!=null||!config.spawns.isEmpty())&&!dim.equals(config.dimension)){p.sendMessage(Text.literal("场地点位必须在同一维度。"),false);return 0;}
        String old=JSON.toJson(config);config.dimension=dim;var pt=new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());
        if(which==1)config.corner1=pt;else if(which==2)config.corner2=pt;else config.spawns.add(pt);
        int result=persist(p.getServer(),old);p.sendMessage(Text.literal(result==1?"决一死战点位已保存。":"点位保存失败。"),true);return result;
    }
    public static boolean canEnter(ServerPlayerEntity p){if(stage==Stage.RUNNING||stage==Stage.RESULT||stage==Stage.RESETTING){p.sendMessage(Text.literal("决一死战正在比赛或恢复场地，请稍后加入。"),true);return false;}return true;}
    public static void joinLobby(ServerPlayerEntity p){if(member(p)){p.changeGameMode(GameMode.ADVENTURE);LobbyItems.sync(p);p.sendMessage(Text.literal("决一死战 · 点参加排队"),true);}}
    /** Compatibility alias: entry alone never silently enrolls the player. */
    public static void join(ServerPlayerEntity p){joinLobby(p);}
    public static boolean enroll(ServerPlayerEntity p){
        if(!member(p)||!p.isAlive()||!canEnter(p))return false;
        if(hasRecovery(p)){recover(p);if(hasRecovery(p))return false;}
        String issue=issue(p.getServer());if(!issue.equals("场地就绪")){p.sendMessage(Text.literal("决一死战暂未就绪："+issue),true);return false;}
        if(queue.size()>=capacity()&&!queued(p)){p.sendMessage(Text.literal("参赛席位已满。"),true);return false;}
        queue.add(p.getUuid());p.changeGameMode(GameMode.ADVENTURE);LobbyItems.sync(p);p.sendMessage(Text.literal("已参加 · "+queue.size()+" / "+capacity()),true);return true;
    }
    public static boolean canStartQueue(){return stage==Stage.WAITING&&queue.size()>=config.minPlayers;}
    public static boolean canStartQueue(ServerPlayerEntity p){return member(p)&&queued(p)&&p.isAlive()&&!p.isSpectator()&&canStartQueue()&&issue(p.getServer()).equals("场地就绪");}
    public static boolean requestStart(ServerPlayerEntity p){return canStartQueue(p)&&start(p.getServer());}
    private static void tell(MinecraftServer s,String message){var ids=new HashSet<>(queue);ids.addAll(players);for(UUID id:ids){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(message),false);}}
    private static void teleport(ServerPlayerEntity p,Point pt){var w=world(p.getServer(),config.dimension);if(w==null)return;p.teleport(w,pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    private static void spectate(ServerPlayerEntity p){var w=world(p.getServer(),config.dimension);if(w!=null&&!config.spawns.isEmpty()){var first=config.spawns.getFirst();teleport(p,new Point(first.x,Math.min(w.getTopY()-3,Math.max(first.y+4,maxY()+3)),first.z,first.yaw,30));}}
    private static boolean snapshot(MinecraftServer s){
        if(Files.exists(snapshotFile(s)))return false;snow.clear();var w=world(s,config.dimension);
        // Resolve each selected chunk once; empty height above a floor is not backup data.
        for(int cx=minX()>>4;cx<=maxX()>>4;cx++)for(int cz=minZ()>>4;cz<=maxZ()>>4;cz++){
            var chunk=w.getChunk(cx,cz);
            for(var mutable:BlockPos.iterate(Math.max(minX(),cx<<4),minY(),Math.max(minZ(),cz<<4),Math.min(maxX(),(cx<<4)+15),maxY(),Math.min(maxZ(),(cz<<4)+15))){
                var state=chunk.getBlockState(mutable);
                if(snow(state)){
                    if(snow.size()>=SpleefRules.MAX_SNOW_BLOCKS){snow.clear();tell(s,"选中雪地超过 "+SpleefRules.MAX_SNOW_BLOCKS+" 格，请缩小雪地范围。");return false;}
                    snow.put(mutable.toImmutable(),state);
                }
            }
        }
        if(snow.isEmpty())return false;
        var n=new NbtCompound();n.putString("dimension",config.dimension);var blocks=new NbtList();for(var entry:snow.entrySet()){var b=new NbtCompound();b.putLong("pos",entry.getKey().asLong());b.put("state",NbtHelper.fromBlockState(entry.getValue()));blocks.add(b);}n.put("blocks",blocks);snapshotDimension=config.dimension;
        if(!GameSessions.write(snapshotFile(s),n)){snow.clear();return false;}return true;
    }
    private static void loadSnapshot(MinecraftServer s){
        resetFailed=false;
        try{var n=GameSessions.read(snapshotFile(s));String dimension=n.getString("dimension");var w=world(s,dimension);if(w==null)throw new IllegalStateException("Snapshot dimension missing");var list=n.getList("blocks",NbtElement.COMPOUND_TYPE);if(list.isEmpty()||list.size()>SpleefRules.MAX_SNOW_BLOCKS)throw new IllegalStateException("Invalid snow snapshot size");
            var loaded=new LinkedHashMap<BlockPos,BlockState>();for(var value:list){var b=(NbtCompound)value;var pos=BlockPos.fromLong(b.getLong("pos"));var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),b.getCompound("state"));if(!snow(state)||w.isOutOfHeightLimit(pos)||loaded.put(pos,state)!=null)throw new IllegalStateException("Invalid or duplicate snow snapshot block");}
            snapshotDimension=dimension;snow.clear();snow.putAll(loaded);restoring=snow.entrySet().iterator();
        }catch(Exception e){resetFailed=true;restoring=null;MWMod.LOGGER.error("Spleef snow recovery blocked; snapshot retained",e);}
    }
    private static void restoreBatch(MinecraftServer s,int limit){
        if(restoring==null||resetFailed)return;
        try{var w=world(s,snapshotDimension);if(w==null)throw new IllegalStateException("Recovery dimension missing");int count=0;
            while(restoring.hasNext()&&count++<limit){var e=restoring.next();w.getChunk(e.getKey());var current=w.getBlockState(e.getKey());if(!current.isAir()&&!snow(current)&&!current.equals(e.getValue()))throw new IllegalStateException("Snow recovery conflicts with another block at "+e.getKey());
                authorized=true;try{w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);}finally{authorized=false;}if(!w.getBlockState(e.getKey()).equals(e.getValue()))throw new IllegalStateException("Snow restore failed at "+e.getKey());}
            if(!restoring.hasNext()){w.save(null,true,false);Files.deleteIfExists(snapshotFile(s));snow.clear();restoring=null;stage=Stage.WAITING;}
        }catch(Exception e){resetFailed=true;restoring=null;MWMod.LOGGER.error("Cannot restore Spleef snow; durable snapshot retained",e);}
    }
    private static void prune(MinecraftServer s){queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});}
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING)return false;prune(s);
        if(queue.size()<config.minPlayers||queue.size()>capacity()||!issue(s).equals("场地就绪")){stage=Stage.WAITING;return false;}
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);if(hasRecovery(p)){recover(p);if(hasRecovery(p)){stage=Stage.WAITING;return false;}}}
        // Closing first puts the cursor stack back before capturing the complete inventory.
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);}
        if(!snapshot(s)){stage=Stage.WAITING;tell(s,"雪地备份未完成，比赛没有开始。");return false;}
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);if(!GameSessions.write(recovery(p),GameSessions.capture(p))){cleanup(s,"玩家备份未完成，比赛取消。");return false;}}
        players.clear();players.addAll(queue);alive.clear();alive.addAll(queue);queue.clear();stage=Stage.RUNNING;started=s.getTicks();round=ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);
        ballSlots.clear();throwing.clear();
        int i=0;for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);p.getInventory().clear();p.clearStatusEffects();p.changeGameMode(GameMode.ADVENTURE);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.setGlowing(false);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);ballSlots.put(id,0);refill(p);giveShovel(p);GameSessions.selectSlot(p,0);teleport(p,config.spawns.get(i++));}
        LobbyItems.refresh(s);tell(s,"决一死战开始！铲子挖雪，雪球击退玩家、击碎雪地，最后存活者获胜。");return true;
    }
    private static boolean owned(ServerPlayerEntity p,ItemStack stack){
        if(stack.isEmpty()||!stack.isOf(Items.SNOWBALL)||round<=0)return false;
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);if(data==null)return false;var n=data.copyNbt();
        return n.getLong("mg_spleef_round")==round&&ID.equals(n.getString("mg_item_game"))&&p.getUuidAsString().equals(n.getString("mg_spleef_owner"))&&n.getBoolean("mg_bound");
    }
    private static ItemStack snowball(ServerPlayerEntity p){
        var item=new ItemStack(Items.SNOWBALL);var n=new NbtCompound();
        n.putLong("mg_spleef_round",round);n.putString("mg_spleef_owner",p.getUuidAsString());n.putString("mw_owner",p.getUuidAsString());n.putString("mg_item_game",ID);n.putBoolean("mg_bound",true);
        item.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));item.set(DataComponentTypes.MAX_STACK_SIZE,1);item.set(DataComponentTypes.CUSTOM_NAME,Text.literal("特殊雪球"));return item;
    }
    private static void normalizeBall(ItemStack stack){stack.setCount(1);if(!Integer.valueOf(1).equals(stack.get(DataComponentTypes.MAX_STACK_SIZE)))stack.set(DataComponentTypes.MAX_STACK_SIZE,1);}
    /** Keep one ball wherever the player sorted it, including an in-progress cursor move. */
    private static void refill(ServerPlayerEntity p){
        if(!playing(p))return;boolean kept=false;var inventory=p.getInventory();
        for(int slot=0;slot<inventory.size();slot++){var stack=inventory.getStack(slot);if(!stack.isOf(Items.SNOWBALL))continue;
            if(!kept&&owned(p,stack)){normalizeBall(stack);kept=true;ballSlots.put(p.getUuid(),slot);}else inventory.setStack(slot,ItemStack.EMPTY);
        }
        var screens=p.currentScreenHandler==p.playerScreenHandler?List.of(p.currentScreenHandler):List.of(p.currentScreenHandler,p.playerScreenHandler);
        for(var screen:screens){var stack=screen.getCursorStack();if(!stack.isOf(Items.SNOWBALL))continue;
            if(!kept&&owned(p,stack)){normalizeBall(stack);kept=true;}else screen.setCursorStack(ItemStack.EMPTY);
        }
        if(kept)return;int slot=ballSlots.getOrDefault(p.getUuid(),0);
        if(slot<0||slot>=inventory.size()||!inventory.getStack(slot).isEmpty())slot=inventory.getEmptySlot();
        if(slot>=0){inventory.setStack(slot,snowball(p));ballSlots.put(p.getUuid(),slot);}
    }
    private static void giveShovel(ServerPlayerEntity p){
        var shovel=new ItemStack(Items.DIAMOND_SHOVEL);var n=new NbtCompound();
        n.putLong("mg_spleef_round",round);n.putString("mg_spleef_owner",p.getUuidAsString());n.putString("mg_item_game",ID);n.putBoolean("mg_bound",true);
        shovel.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));
        shovel.set(DataComponentTypes.UNBREAKABLE,new UnbreakableComponent(true));
        shovel.set(DataComponentTypes.CAN_BREAK,new BlockPredicatesChecker(List.of(BlockPredicate.Builder.create().blocks(Blocks.SNOW_BLOCK,Blocks.SNOW).build()),false));
        p.getInventory().setStack(1,shovel);
    }
    private static boolean validFighter(ServerPlayerEntity p){return playing(p)&&p.isAlive()&&!p.isSpectator()&&!SpleefRules.eliminated(p.getX(),p.getY(),p.getZ(),minX(),maxX(),minZ(),maxZ(),loseY(),p.getServerWorld().getRegistryKey().getValue().toString().equals(config.dimension));}
    public static boolean canThrow(ServerPlayerEntity p,Hand hand){var stack=p.getStackInHand(hand);return hand==Hand.MAIN_HAND&&validFighter(p)&&p.getServer().getTicks()-started>=config.graceTicks&&owned(p,stack)&&stack.getCount()==1&&stack.getMaxCount()==1&&!p.getItemCooldownManager().isCoolingDown(Items.SNOWBALL);}
    /** Paired with SnowballItem.use: replenishment must happen after vanilla consumes the ball. */
    public static boolean beginThrow(ServerPlayerEntity p,Hand hand){if(!canThrow(p,hand))return false;throwing.put(p.getUuid(),new ThrowUse(p.getInventory().selectedSlot,round));return true;}
    public static TypedActionResult<ItemStack> finishThrow(ServerPlayerEntity p,TypedActionResult<ItemStack> result){
        var use=throwing.remove(p.getUuid());if(use==null||use.round!=round||!validFighter(p))return result;
        // Returning the replenished stack also prevents interactItem/interactBlock from
        // writing vanilla's now-empty result over the restored original hotbar slot.
        for(int slot=0;slot<p.getInventory().size();slot++)if(p.getInventory().getStack(slot).isOf(Items.SNOWBALL))p.getInventory().setStack(slot,ItemStack.EMPTY);
        if(p.currentScreenHandler.getCursorStack().isOf(Items.SNOWBALL))p.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        if(p.playerScreenHandler.getCursorStack().isOf(Items.SNOWBALL))p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
        var ball=snowball(p);p.getInventory().setStack(use.slot,ball);ballSlots.put(p.getUuid(),use.slot);p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();
        return new TypedActionResult<>(result.getResult(),ball);
    }
    public static boolean dig(ServerPlayerEntity p,BlockPos pos){
        if(!validFighter(p)||p.getServer().getTicks()-started<config.graceTicks||!p.canInteractWithBlockAt(pos,0))return false;
        var tool=p.getMainHandStack();var data=tool.get(DataComponentTypes.CUSTOM_DATA);
        if(!tool.isOf(Items.DIAMOND_SHOVEL)||data==null)return false;var n=data.copyNbt();
        if(!SpleefRules.projectile(n.getLong("mg_spleef_round"),round,true,true,true)||!ID.equals(n.getString("mg_item_game"))||!p.getUuidAsString().equals(n.getString("mg_spleef_owner")))return false;
        var w=p.getServerWorld();var state=w.getBlockState(pos);
        if(!w.getRegistryKey().getValue().toString().equals(snapshotDimension)||!inside(w,pos)||!snow.containsKey(pos)||!snow(state))return false;
        var hit=w.raycast(new RaycastContext(p.getEyePos(),Vec3d.ofCenter(pos),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()==HitResult.Type.BLOCK&&!hit.getBlockPos().equals(pos))return false;
        if(!removeSnow(w,pos,state))return false;
        p.incrementStat(Stats.MINED.getOrCreateStat(state.getBlock()));tool.postMine(w,state,pos,p);return true;
    }
    /** Called by the server-side constructor; a stale projectile never inherits a later round. */
    public static long stamp(SnowballEntity e){if(e.getOwner() instanceof ServerPlayerEntity p&&e.getWorld() instanceof ServerWorld&&throwing.containsKey(p.getUuid())&&canThrow(p,Hand.MAIN_HAND)){e.addCommandTag("mg_source_"+ID);e.addCommandTag("mg_spleef_round_"+round);p.getItemCooldownManager().set(Items.SNOWBALL,config.shotDelayTicks);return round;}return 0;}
    /** Command tags persist with unloaded entities; the nonce never transfers into a new match. */
    public static boolean currentShot(ProjectileEntity e){return round>0&&e instanceof SnowballEntity&&e.getWorld() instanceof ServerWorld&&e.getCommandTags().contains("mg_spleef_round_"+round)&&SpleefRules.projectileAge(e.age)&&e.getOwner() instanceof ServerPlayerEntity p&&validFighter(p)&&p.getServerWorld()==e.getWorld();}
    public static void hit(SnowballEntity e,long shotRound,HitResult hit){
        if(!(e.getWorld() instanceof ServerWorld w)||!(e.getOwner() instanceof ServerPlayerEntity p)||!currentShot(e)||shotRound!=round||p.getServer().getTicks()-started<config.graceTicks)return;
        if(hit instanceof EntityHitResult entityHit){
            if(!(entityHit.getEntity() instanceof ServerPlayerEntity target)||target==p||!validFighter(target)||target.getServerWorld()!=w)return;
            var velocity=e.getVelocity();var direction=new Vec3d(velocity.x,0,velocity.z);if(direction.lengthSquared()<.0001){var delta=target.getPos().subtract(p.getPos());direction=new Vec3d(delta.x,0,delta.z);}if(direction.lengthSquared()<.0001)return;
            target.takeKnockback(.4,-direction.x,-direction.z);target.velocityModified=true;target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(target));return;
        }
        if(!(hit instanceof BlockHitResult block))return;var pos=block.getBlockPos();var state=w.getBlockState(pos);
        if(!SpleefRules.projectile(shotRound,round,validFighter(p),p.getServerWorld()==w&&w.getRegistryKey().getValue().toString().equals(snapshotDimension),snow.containsKey(pos)&&snow(state))||p.getServer().getTicks()-started<config.graceTicks)return;
        removeSnow(w,pos,state);
    }
    private static boolean removeSnow(ServerWorld w,BlockPos pos,BlockState state){
        boolean before=authorized;authorized=true;boolean changed;try{changed=w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);}finally{authorized=before;}
        if(changed)w.syncWorldEvent(2001,pos,Block.getRawIdFromState(state));return changed;
    }
    public static void recover(ServerPlayerEntity p){if(!hasRecovery(p)||!p.isAlive())return;try{var n=GameSessions.read(recovery(p));GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p))Files.delete(recovery(p));}catch(Exception e){MWMod.LOGGER.error("Cannot restore Spleef player; backup retained",e);p.sendMessage(Text.literal("决一死战档案恢复失败，备份已保留，请联系管理员。"),false);}}
    private static void eliminate(ServerPlayerEntity p,String reason){if(stage!=Stage.RUNNING||!alive.remove(p.getUuid()))return;ballSlots.remove(p.getUuid());throwing.remove(p.getUuid());if(p.isAlive()){p.getInventory().clear();p.changeGameMode(GameMode.SPECTATOR);spectate(p);LobbyItems.sync(p);}tell(p.getServer(),p.getName().getString()+" 已淘汰（"+reason+"）。");}
    private static void resolve(MinecraftServer s,boolean timedOut){if(stage!=Stage.RUNNING)return;int outcome=SpleefRules.outcome(alive.size(),timedOut);if(outcome==0)return;stage=Stage.RESULT;deadline=s.getTicks()+config.resultSeconds*20;UUID winner=outcome==1?alive.iterator().next():null;var p=winner==null?null:s.getPlayerManager().getPlayer(winner);tell(s,p==null?(timedOut?"时间到，本局平局。":"无人存活，本局平局。"):"决一死战胜者："+p.getName().getString());for(UUID id:players){var player=s.getPlayerManager().getPlayer(id);if(player!=null&&player.isAlive()){player.getInventory().clear();player.changeGameMode(GameMode.SPECTATOR);player.sendMessage(Text.literal(p==null?"本局平局":id.equals(winner)?"你赢了！":"胜者 · "+p.getName().getString()),true);}}LobbyItems.refresh(s);}
    public static void leave(ServerPlayerEntity p){queue.remove(p.getUuid());eliminate(p,"离开或断线");players.remove(p.getUuid());alive.remove(p.getUuid());ballSlots.remove(p.getUuid());throwing.remove(p.getUuid());recover(p);PrivateSidebar.clear(p);resolve(p.getServer(),false);}
    private static void cleanup(MinecraftServer s,String reason){
        if(stage==Stage.RESETTING)return;if(stage==Stage.WAITING&&queue.isEmpty()&&players.isEmpty()&&!Files.exists(snapshotFile(s)))return;
        stage=Stage.RESETTING;players.addAll(queue);queue.clear();if(reason!=null)tell(s,reason);
        for(UUID id:new ArrayList<>(players)){var p=s.getPlayerManager().getPlayer(id);if(p!=null){recover(p);PrivateSidebar.clear(p);if(p.isAlive()&&!hasRecovery(p))HubMenu.travel(p,GameSessions.HUB);}}
        alive.clear();players.clear();ballSlots.clear();throwing.clear();round=0;
        if(Files.exists(snapshotFile(s))){resetFailed=false;if(snow.isEmpty())loadSnapshot(s);else restoring=snow.entrySet().iterator();}else stage=Stage.WAITING;
    }
    public static void tick(MinecraftServer s){
        if(stage==Stage.RESETTING){restoreBatch(s,512);return;}
        if(stage==Stage.RESULT){if(s.getTicks()>=deadline)cleanup(s,null);}
        if(stage==Stage.WAITING&&s.getTicks()%20==0){int before=queue.size();prune(s);if(queue.size()!=before)LobbyItems.refresh(s);}
        if(stage==Stage.RUNNING){
            for(UUID id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);if(p==null){alive.remove(id);continue;}if(!member(p)||!p.isAlive()||SpleefRules.eliminated(p.getX(),p.getY(),p.getZ(),minX(),maxX(),minZ(),maxZ(),loseY(),p.getServerWorld().getRegistryKey().getValue().toString().equals(config.dimension))){eliminate(p,"掉出雪地");continue;}p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);refill(p);}
            resolve(s,s.getTicks()-started>=config.durationSeconds*20);
        }
        if(s.getTicks()%20!=0)return;
        String state=switch(stage){case RUNNING->"比赛中";case RESULT->"结算 "+Math.max(0,(deadline-s.getTicks()+19)/20)+" 秒";case RESETTING->"恢复场地";default->"等待参加";};
        for(var p:s.getPlayerManager().getPlayerList())if(member(p)){var lines=new ArrayList<String>();lines.add("状态："+state);if(stage==Stage.RUNNING||stage==Stage.RESULT){lines.add("人数："+players.size());lines.add("存活："+alive.size());lines.add("剩余："+Math.max(0,config.durationSeconds-(s.getTicks()-started)/20)+" 秒");}else{lines.add("人数："+queue.size()+" / "+Math.max(config.minPlayers,capacity()));}PrivateSidebar.show(p,"决一死战",lines);}
    }
}
