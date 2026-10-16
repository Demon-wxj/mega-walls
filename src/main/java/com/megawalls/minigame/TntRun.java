package com.megawalls.minigame;

import com.google.gson.*;
import com.megawalls.MWMod;
import com.megawalls.menu.HubMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Only explicitly selected floor planes disappear. Durable pre-match snapshot owns reset. */
public final class TntRun {
    public static final String ID="tnt_run";
    public enum Stage { WAITING, COUNTDOWN, RUNNING, RESETTING }
    public record Point(double x,double y,double z,float yaw,float pitch){}
    public static final class Config {
        public String dimension="minecraft:overworld";
        public Point corner1,corner2;
        public List<Integer> layers=new ArrayList<>();
        public List<Point> spawns=new ArrayList<>();
        public int minPlayers=2,maxPlayers=10,countdownSeconds=10,delayTicks=8,graceTicks=40;
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Config config=new Config();
    private static boolean validConfig=true,resetFailed;
    private static Stage stage=Stage.WAITING;
    private static final Set<UUID> queue=new LinkedHashSet<>(),players=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static final Map<BlockPos,BlockState> floor=new LinkedHashMap<>();
    private static final Map<BlockPos,Integer> pending=new LinkedHashMap<>();
    private static Iterator<Map.Entry<BlockPos,BlockState>> restoring;
    private static String snapshotDimension;
    private static String startFailure="请检查人数和当前状态";
    private static int started;
    public static Stage stage(){return stage;}
    public static int aliveCount(){return alive.size();}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(ID);}
    public static boolean queued(ServerPlayerEntity p){return member(p)&&queue.contains(p.getUuid());}
    public static int queueSize(){return queue.size();}
    public static String lastStartIssue(){return startFailure;}
    public static boolean canStartQueue(){return stage==Stage.WAITING&&queue.size()>=config.minPlayers&&queue.size()<=config.maxPlayers;}
    public static boolean inRound(ServerPlayerEntity p){return stage==Stage.RUNNING&&players.contains(p.getUuid());}
    private static Path configFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("tnt-run.json");}
    private static Path snapshotFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("tnt-run-floor.dat");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("tnt-run-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static ServerWorld world(MinecraftServer s,String dimension){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));}
    private static boolean pointValid(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984&&Math.abs(p.y)<2048&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch);}
    private static boolean values(Config c){return c!=null&&c.dimension!=null&&Identifier.tryParse(c.dimension)!=null&&c.layers!=null&&c.layers.size()<=16&&c.layers.stream().allMatch(Objects::nonNull)&&c.spawns!=null&&c.spawns.size()<=32&&c.spawns.stream().allMatch(TntRun::pointValid)&&c.minPlayers>=2&&c.maxPlayers>=c.minPlayers&&c.maxPlayers<=32&&c.delayTicks>=1&&c.delayTicks<=100&&c.graceTicks>=0&&c.graceTicks<=200;}
    private static boolean save(MinecraftServer s){try{Path tmp=configFile(s).resolveSibling("tnt-run.json.tmp");Files.writeString(tmp,JSON.toJson(config));Files.move(tmp,configFile(s),StandardCopyOption.REPLACE_EXISTING);return true;}catch(Exception e){MWMod.LOGGER.error("Cannot save TNT Run config",e);return false;}}
    private static void load(MinecraftServer s){try{var c=Files.exists(configFile(s))?JSON.fromJson(Files.readString(configFile(s)),Config.class):new Config();if(!values(c))throw new IllegalArgumentException("Invalid TNT Run settings");config=c;validConfig=true;}catch(Exception e){validConfig=false;MWMod.LOGGER.error("Cannot load tnt-run.json",e);}}
    private static int minX(){return MathHelper.floor(Math.min(config.corner1.x,config.corner2.x));}
    private static int maxX(){return MathHelper.floor(Math.max(config.corner1.x,config.corner2.x));}
    private static int minZ(){return MathHelper.floor(Math.min(config.corner1.z,config.corner2.z));}
    private static int maxZ(){return MathHelper.floor(Math.max(config.corner1.z,config.corner2.z));}
    public static boolean inside(ServerWorld w,BlockPos p){return pointValid(config.corner1)&&pointValid(config.corner2)&&w.getRegistryKey().getValue().toString().equals(config.dimension)&&p.getX()>=minX()&&p.getX()<=maxX()&&p.getZ()>=minZ()&&p.getZ()<=maxZ()&&p.getY()>=Math.floor(Math.min(config.corner1.y,config.corner2.y))&&p.getY()<=Math.floor(Math.max(config.corner1.y,config.corner2.y));}
    public static boolean overlaps(ServerWorld w,BlockPos a,BlockPos b){return stage==Stage.RUNNING&&pointValid(config.corner1)&&pointValid(config.corner2)&&w.getRegistryKey().getValue().toString().equals(config.dimension)
        &&Math.max(a.getX(),b.getX())>=minX()&&Math.min(a.getX(),b.getX())<=maxX()
        &&Math.max(a.getY(),b.getY())>=Math.floor(Math.min(config.corner1.y,config.corner2.y))&&Math.min(a.getY(),b.getY())<=Math.floor(Math.max(config.corner1.y,config.corner2.y))
        &&Math.max(a.getZ(),b.getZ())>=minZ()&&Math.min(a.getZ(),b.getZ())<=maxZ();}
    public static boolean protectedAt(ServerWorld w,BlockPos p){return (stage==Stage.RUNNING||stage==Stage.RESETTING)&&(inside(w,p)||w.getRegistryKey().getValue().toString().equals(snapshotDimension)&&floor.containsKey(p));}
    private record ArenaCheck(Map<BlockPos,BlockState> blocks,String issue) {
        boolean ready(){return issue.equals("场地就绪");}
    }
    private static ArenaCheck blocked(String reason){return new ArenaCheck(Map.of(),reason);}
    /** The same read-only preflight used by status and start; loads selected chunks only. */
    public static String arenaIssue(MinecraftServer s){return checkArena(s).issue;}
    private static String issue(MinecraftServer s){return arenaIssue(s);}
    /** Null means a safe runnable floor or a dry, noncolliding decoration left untouched. */
    public static String floorBlockIssue(ServerWorld w,BlockPos pos,BlockState state){
        String reason=null;
        var shape=state.getCollisionShape(w,pos);
        if(!state.getFluidState().isEmpty())reason="地板层不能包含液体或含水方块";
        else if(shape.isEmpty())return null;
        else if(state.hasBlockEntity())reason="可踩地板不能包含方块实体";
        else for(var axis:Direction.Axis.values()){
            double min=shape.getMin(axis),max=shape.getMax(axis);
            if(!Double.isFinite(min)||!Double.isFinite(max)||min<-.0000001||max>1.0000001){reason="碰撞形状超出一格范围，不支持高于一格的栏杆、墙柱等地板";break;}
        }
        return reason==null?null:reason+"：Y="+pos.getY()+"，"+Registries.BLOCK.getId(state.getBlock())+"，坐标 ("+pos.getX()+", "+pos.getY()+", "+pos.getZ()+")";
    }
    public static boolean isRunnableFloor(ServerWorld w,BlockPos pos,BlockState state){return floorBlockIssue(w,pos,state)==null&&!state.getCollisionShape(w,pos).isEmpty();}
    /** Intersects the actual collision pieces just beneath the player's feet, including steps. */
    private static boolean supportsFeet(ServerWorld w,BlockPos pos,BlockState state,Box body){
        var probe=new Box(body.minX+.0000001,body.minY-.01,body.minZ+.0000001,body.maxX-.0000001,body.minY+.0000001,body.maxZ-.0000001);
        for(var piece:state.getCollisionShape(w,pos).getBoundingBoxes())if(piece.offset(pos.getX(),pos.getY(),pos.getZ()).intersects(probe))return true;
        return false;
    }
    private static Iterable<BlockPos> beneathFeet(Box body){
        int y=MathHelper.floor(body.minY-.01);
        return BlockPos.iterate(MathHelper.floor(body.minX+.0000001),y,MathHelper.floor(body.minZ+.0000001),MathHelper.floor(body.maxX-.0000001),y,MathHelper.floor(body.maxZ-.0000001));
    }
    /** One actual contact surface, even when the player's body straddles four cells. */
    private static BlockPos footstep(ServerWorld w,Box body){
        double x=(body.minX+body.maxX)*.5,z=(body.minZ+body.maxZ)*.5;
        var center=BlockPos.ofFloored(x,body.minY-.01,z);
        BlockPos selected=null;double bestArea=-1,bestDistance=Double.POSITIVE_INFINITY;
        var centerProbe=new Box(x-.0000001,body.minY-.01,z-.0000001,x+.0000001,body.minY+.0000001,z+.0000001);
        for(var mutable:beneathFeet(body)){
            var pos=mutable.toImmutable();var original=floor.get(pos);var state=w.getBlockState(pos);
            if(original==null||!state.equals(original)||!supportsFeet(w,pos,state,body))continue;
            double area=0;
            for(var local:state.getCollisionShape(w,pos).getBoundingBoxes()){
                var piece=local.offset(pos.getX(),pos.getY(),pos.getZ());
                if(piece.maxY<=body.minY-.01||piece.minY>=body.minY+.0000001)continue;
                if(pos.equals(center)&&piece.intersects(centerProbe))return pos;
                area+=Math.max(0,Math.min(body.maxX,piece.maxX)-Math.max(body.minX,piece.minX))
                    *Math.max(0,Math.min(body.maxZ,piece.maxZ)-Math.max(body.minZ,piece.minZ));
            }
            double distance=(pos.getX()+.5-x)*(pos.getX()+.5-x)+(pos.getZ()+.5-z)*(pos.getZ()+.5-z);
            if(area>bestArea+.0000001||Math.abs(area-bestArea)<=.0000001&&distance<bestDistance){selected=pos;bestArea=area;bestDistance=distance;}
        }
        return selected;
    }
    private static ArenaCheck checkArena(MinecraftServer s){
        if(!validConfig||!values(config))return blocked("配置无法读取");
        if(!pointValid(config.corner1)||!pointValid(config.corner2)||config.layers.isEmpty()||config.spawns.size()<2)return blocked("需设置 arena1、arena2、地板层和至少两个出生点");
        long cells=(long)(maxX()-minX()+1)*(maxZ()-minZ()+1)*config.layers.size();
        if(maxX()-minX()>255||maxZ()-minZ()>255||cells>65536)return blocked("地板总面积最多 65536 格，单边最多 256 格");
        var w=world(s,config.dimension);if(w==null)return blocked("维度不存在");
        for(int y:config.layers){var p=new BlockPos(minX(),y,minZ());if(!inside(w,p)||w.isOutOfHeightLimit(p)||y>=Math.max(config.corner1.y,config.corner2.y))return blocked("地板层 Y="+y+" 必须位于场地范围内并留出头部空间");}
        var selected=new LinkedHashMap<BlockPos,BlockState>();
        for(int y:config.layers){
            int count=0;
            for(int x=minX();x<=maxX();x++)for(int z=minZ();z<=maxZ();z++){
                var pos=new BlockPos(x,y,z);w.getChunk(pos);var state=w.getBlockState(pos);
                String problem=floorBlockIssue(w,pos,state);if(problem!=null)return blocked(problem);
                if(!state.getCollisionShape(w,pos).isEmpty()){selected.put(pos,state);count++;}
            }
            if(count==0)return blocked("地板层 Y="+y+" 没有可跑地板；请站在实际地板上重新设置 layer add");
        }
        int spawnNumber=0;
        for(var pt:config.spawns){spawnNumber++;var p=BlockPos.ofFloored(pt.x,pt.y,pt.z);w.getChunk(p);
            var body=new Box(pt.x-.3,pt.y,pt.z-.3,pt.x+.3,pt.y+1.8,pt.z+.3);int groundY=MathHelper.floor(pt.y-.01);
            boolean supported=false;
            if(config.layers.contains(groundY))for(var mutable:beneathFeet(body)){
                var pos=mutable.toImmutable();var state=selected.get(pos);if(state!=null&&supportsFeet(w,pos,state,body)){supported=true;break;}
            }
            if(!inside(w,p)||!w.getWorldBorder().contains(body)||!supported||!w.isSpaceEmpty(body))return blocked("出生点 "+spawnNumber+" 需位于选定地板层上方，有实际碰撞支撑且有两格净空；脚下层 Y="+groundY+"，坐标 ("+pt.x+", "+pt.y+", "+pt.z+")");
        }
        if(PotDuel.overlaps(w,BlockPos.ofFloored(config.corner1.x(),config.corner1.y(),config.corner1.z()),BlockPos.ofFloored(config.corner2.x(),config.corner2.y(),config.corner2.z()))||Spleef.overlaps(w,BlockPos.ofFloored(config.corner1.x(),config.corner1.y(),config.corner1.z()),BlockPos.ofFloored(config.corner2.x(),config.corner2.y(),config.corner2.z())))return blocked("场地与进行中的 Pot PVP 或决一死战重叠");
        return new ArenaCheck(selected,"场地就绪");
    }
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{queue.clear();players.clear();alive.clear();floor.clear();pending.clear();stage=Stage.WAITING;restoring=null;resetFailed=false;load(s);if(Files.exists(snapshotFile(s))){stage=Stage.RESETTING;loadSnapshot(s);}});
        ServerTickEvents.END_SERVER_TICK.register(TntRun::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{finish(s,null,"服务器关闭，本局取消。");if(stage==Stage.RESETTING&&!resetFailed)restoreBatch(s,Integer.MAX_VALUE);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){if(inRound(p)){p.changeGameMode(GameMode.SPECTATOR);teleport(p,config.spawns.getFirst());}else{recover(p);HubMenu.travel(p,GameSessions.HUB);}}});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->!(entity instanceof ServerPlayerEntity p)||!member(p)||source.isOf(DamageTypes.GENERIC_KILL));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayerEntity p&&member(p)){eliminate(p,"死亡");resolve(p.getServer());}});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(CommandManager.literal("tntrun")
            .then(CommandManager.literal("join").executes(c->{var p=c.getSource().getPlayerOrThrow();return (member(p)||HubMenu.travel(p,ID))&&enroll(p)?1:0;}))
            .then(CommandManager.literal("leave").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0))
            .then(CommandManager.literal("start").executes(c->{boolean ok=c.getSource().getEntity() instanceof ServerPlayerEntity p?requestStart(p):start(c.getSource().getServer());if(!ok)c.getSource().sendError(Text.literal("无法开局："+startFailure));return ok?1:0;}))
            .then(CommandManager.literal("stop").executes(c->{finish(c.getSource().getServer(),null,"已取消本局。");return 1;}))
            .then(CommandManager.literal("reset").executes(c->{var s=c.getSource().getServer();if(stage==Stage.RUNNING)finish(s,null,"已重置场地。");if(stage==Stage.RESETTING&&resetFailed)loadSnapshot(s);return 1;}))
            .then(CommandManager.literal("arena1").executes(c->point(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("arena2").executes(c->point(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("spawn")
                .then(CommandManager.literal("add").executes(c->point(c.getSource().getPlayerOrThrow(),0)))
                .then(CommandManager.literal("clear").executes(c->{if(stage!=Stage.WAITING)return 0;var old=JSON.toJson(config);config.spawns.clear();return persist(c.getSource().getServer(),old);})))
            .then(CommandManager.literal("layer")
                .then(CommandManager.literal("add").executes(c->point(c.getSource().getPlayerOrThrow(),3)))
                .then(CommandManager.literal("clear").executes(c->{if(stage!=Stage.WAITING)return 0;var old=JSON.toJson(config);config.layers.clear();return persist(c.getSource().getServer(),old);})))
            .then(CommandManager.literal("rule")
                .then(CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(CommandManager.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,200)).executes(c->{
                    if(stage!=Stage.WAITING)return 0;var old=JSON.toJson(config);int v=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value");
                    switch(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")){case "min"->config.minPlayers=v;case "max"->config.maxPlayers=v;case "delay"->config.delayTicks=v;case "grace"->config.graceTicks=v;default->{return 0;}}
                    int result=persist(c.getSource().getServer(),old);c.getSource().sendFeedback(()->Text.literal(result==1?"TNT 跑酷规则已保存。":"参数超出范围或保存失败。"),false);return result;
                }))))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("TNT 跑酷："+stage+" / 排队 "+queue.size()+"/"+config.maxPlayers+" / 存活 "+alive.size()+" / 地板层 "+config.layers+" / "+(resetFailed?"恢复失败，请检查日志后 /tntrun reset":issue(c.getSource().getServer()))),false);return 1;}))));
    }
    private static int persist(MinecraftServer s,String old){if(!values(config)||!save(s)){config=JSON.fromJson(old,Config.class);return 0;}return 1;}
    private static int point(ServerPlayerEntity p,int which){
        if(stage!=Stage.WAITING){p.sendMessage(Text.literal("先停止对局并等待场地恢复。"),false);return 0;}
        String dimension=p.getWorld().getRegistryKey().getValue().toString();
        if((config.corner1!=null||config.corner2!=null||!config.spawns.isEmpty()||!config.layers.isEmpty())&&!dimension.equals(config.dimension)){p.sendMessage(Text.literal("场地点位必须在同一维度。"),false);return 0;}
        String old=JSON.toJson(config);config.dimension=dimension;var pt=new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());
        if(which==1)config.corner1=pt;else if(which==2)config.corner2=pt;else if(which==3){int y=MathHelper.floor(p.getY()-.01);if(!config.layers.contains(y))config.layers.add(y);}else config.spawns.add(pt);
        int result=persist(p.getServer(),old);p.sendMessage(Text.literal(result==1?"已保存 TNT 跑酷点位；地板层："+config.layers:"点位保存失败。"),false);return result;
    }
    public static boolean canEnter(ServerPlayerEntity p){if(stage==Stage.RUNNING||stage==Stage.RESETTING||queue.size()>=config.maxPlayers&&!queue.contains(p.getUuid())){p.sendMessage(Text.literal("TNT 跑酷正在比赛、恢复场地或已满员，请稍后加入。"),false);return false;}return true;}
    public static void join(ServerPlayerEntity p){enroll(p);}
    public static boolean enroll(ServerPlayerEntity p){
        if(!member(p)||stage!=Stage.WAITING||!p.isAlive()||!canEnter(p))return false;
        p.changeGameMode(GameMode.ADVENTURE);queue.add(p.getUuid());LobbyItems.refresh(p.getServer());
        p.sendMessage(Text.literal("已参加 TNT 跑酷，可选择开始游戏。"),false);return true;
    }
    public static boolean requestStart(ServerPlayerEntity p){
        if(!queued(p)||!p.isAlive()||p.isSpectator()||stage!=Stage.WAITING){startFailure="需要已参加的存活玩家在等待阶段开局";p.sendMessage(Text.literal("无法开局："+startFailure),false);return false;}
        if(!canStartQueue()){startFailure="参加人数需为 "+config.minPlayers+"–"+config.maxPlayers+" 人";p.sendMessage(Text.literal(startFailure),false);return false;}
        boolean ok=start(p.getServer());if(!ok)p.sendMessage(Text.literal("无法开局："+startFailure),false);return ok;
    }
    private static void tell(MinecraftServer s,String message){var audience=new HashSet<>(queue);audience.addAll(players);for(UUID id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(message),false);}}
    private static void teleport(ServerPlayerEntity p,Point pt){p.teleport(world(p.getServer(),config.dimension),pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    private static boolean snapshot(MinecraftServer s,Map<BlockPos,BlockState> selected){
        if(Files.exists(snapshotFile(s))){startFailure="上一局地板备份尚未恢复，请等待恢复";return false;}
        floor.clear();floor.putAll(selected);
        if(floor.isEmpty()){startFailure="选定层没有可跑地板";return false;}
        var n=new NbtCompound();n.putString("dimension",config.dimension);var blocks=new NbtList();
        for(var entry:floor.entrySet()){var b=new NbtCompound();b.putLong("pos",entry.getKey().asLong());b.put("state",NbtHelper.fromBlockState(entry.getValue()));blocks.add(b);}n.put("blocks",blocks);
        snapshotDimension=config.dimension;boolean ok=GameSessions.write(snapshotFile(s),n);if(!ok){startFailure="地板备份无法写入，比赛没有开始；请检查日志和存档权限";floor.clear();}return ok;
    }
    private static void loadSnapshot(MinecraftServer s){
        resetFailed=false;
        try{var n=GameSessions.read(snapshotFile(s));String dimension=n.getString("dimension");var w=world(s,dimension);if(w==null)throw new IllegalStateException("Snapshot dimension missing");
            var list=n.getList("blocks",NbtElement.COMPOUND_TYPE);if(list.isEmpty()||list.size()>65536)throw new IllegalStateException("Invalid floor snapshot size");
            var loaded=new LinkedHashMap<BlockPos,BlockState>();for(var value:list){var b=(NbtCompound)value;var pos=BlockPos.fromLong(b.getLong("pos"));var state=NbtHelper.toBlockState(w.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),b.getCompound("state"));if(state.isAir()||state.hasBlockEntity()||w.isOutOfHeightLimit(pos))throw new IllegalStateException("Invalid floor snapshot block");loaded.put(pos,state);}
            snapshotDimension=dimension;floor.clear();floor.putAll(loaded);restoring=floor.entrySet().iterator();
        }catch(Exception e){resetFailed=true;restoring=null;MWMod.LOGGER.error("TNT Run floor recovery blocked; snapshot retained",e);}
    }
    private static void restoreBatch(MinecraftServer s,int limit){
        if(restoring==null||resetFailed)return;
        try{var w=world(s,snapshotDimension);int count=0;while(restoring.hasNext()&&count++<limit){var e=restoring.next();w.getChunk(e.getKey());w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);if(!w.getBlockState(e.getKey()).equals(e.getValue()))throw new IllegalStateException("Floor restore failed at "+e.getKey());}
            if(!restoring.hasNext()){
                // Flush restored chunks before removing the durable recovery journal.
                w.save(null,true,false);Files.deleteIfExists(snapshotFile(s));floor.clear();pending.clear();restoring=null;stage=Stage.WAITING;LobbyItems.refresh(s);
            }
        }catch(Exception e){resetFailed=true;restoring=null;MWMod.LOGGER.error("Cannot restore TNT Run floor; snapshot retained",e);}
    }
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING){startFailure="当前状态为 "+stage+"，请等待上一局停止并恢复";return false;}
        queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive()||p.isSpectator();});
        if(queue.size()<config.minPlayers||queue.size()>config.maxPlayers){startFailure="参加人数需为 "+config.minPlayers+"–"+config.maxPlayers+" 人";return false;}
        if(Files.exists(snapshotFile(s))){stage=Stage.RESETTING;loadSnapshot(s);startFailure="上一局地板备份尚未恢复，请等待恢复；恢复失败时 /tntrun reset";return false;}
        var check=checkArena(s);if(!check.ready()){startFailure=check.issue;return false;}
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);recover(p);if(hasRecovery(p)){startFailure=p.getName().getString()+" 的旧档案尚未恢复，请检查日志";return false;}}
        var captured=new ArrayList<UUID>();
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);p.closeHandledScreen();if(!GameSessions.write(recovery(p),GameSessions.capture(p))){startFailure="玩家档案备份写入失败，比赛没有开始；请检查日志";recoverAttempt(s,captured);return false;}captured.add(id);}
        if(!snapshot(s,check.blocks)){recoverAttempt(s,captured);tell(s,startFailure);return false;}
        players.clear();players.addAll(queue);alive.clear();alive.addAll(queue);queue.clear();pending.clear();stage=Stage.RUNNING;started=s.getTicks();
        int i=0;for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);com.megawalls.fun.FunMode.clear(p);p.getInventory().clear();p.clearStatusEffects();p.changeGameMode(GameMode.ADVENTURE);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.setGlowing(false);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);teleport(p,config.spawns.get(i++%config.spawns.size()));}
        startFailure="场地就绪";
        LobbyItems.spectateAudience(s,ID);LobbyItems.refresh(s);tell(s,"TNT 跑酷开始！脚下地板即将消失，掉到下层继续跑，最后一人获胜。");return true;
    }
    private static void recoverAttempt(MinecraftServer s,List<UUID> captured){for(UUID id:captured){var p=s.getPlayerManager().getPlayer(id);if(p!=null)recover(p);}}
    public static void recover(ServerPlayerEntity p){if(!hasRecovery(p)||!p.isAlive())return;try{var n=GameSessions.read(recovery(p));GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p))Files.delete(recovery(p));}catch(Exception e){MWMod.LOGGER.error("Cannot restore TNT Run player; backup retained",e);p.sendMessage(Text.literal("TNT 跑酷档案恢复失败，备份已保留，请检查服务器日志。"),false);}}
    private static void eliminate(ServerPlayerEntity p,String reason){if(stage!=Stage.RUNNING||!alive.remove(p.getUuid()))return;if(p.isAlive()){p.changeGameMode(GameMode.SPECTATOR);teleport(p,config.spawns.getFirst());}tell(p.getServer(),p.getName().getString()+" 已淘汰（"+reason+"）。");}
    private static void resolve(MinecraftServer s){if(stage==Stage.RUNNING&&alive.size()<=1)finish(s,alive.stream().findFirst().orElse(null),"无人存活，本局平局。");}
    public static void leave(ServerPlayerEntity p){queue.remove(p.getUuid());eliminate(p,"离开或断线");players.remove(p.getUuid());recover(p);PrivateSidebar.clear(p);resolve(p.getServer());}
    private static void finish(MinecraftServer s,UUID winner,String reason){
        if(stage==Stage.RESETTING||stage==Stage.WAITING&&queue.isEmpty()&&players.isEmpty())return;
        stage=Stage.RESETTING;pending.clear();players.addAll(queue);queue.clear();
        var p=winner==null?null:s.getPlayerManager().getPlayer(winner);tell(s,p==null?reason:"TNT 跑酷胜者："+p.getName().getString());
        for(UUID id:new ArrayList<>(players)){var player=s.getPlayerManager().getPlayer(id);if(player!=null){recover(player);PrivateSidebar.clear(player);if(player.isAlive())HubMenu.travel(player,GameSessions.HUB);}}
        alive.clear();players.clear();LobbyItems.finishAudience(s,ID);
        if(Files.exists(snapshotFile(s))){resetFailed=false;restoring=floor.entrySet().iterator();}else stage=Stage.WAITING;
    }
    public static void tick(MinecraftServer s){
        if(stage==Stage.RESETTING){restoreBatch(s,512);return;}
        if(stage==Stage.COUNTDOWN){stage=Stage.WAITING;LobbyItems.refresh(s);}
        if(stage==Stage.RUNNING){
            for(UUID id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);if(p==null){alive.remove(id);continue;}if(!member(p)||!p.isAlive()||!inside(p.getServerWorld(),p.getBlockPos())){eliminate(p,"掉出场地");continue;}
                p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);
                if(s.getTicks()-started<config.graceTicks||!p.isOnGround())continue;
                var pos=footstep(p.getServerWorld(),p.getBoundingBox());
                // Repeated contact never restarts or shortens the first-touch deadline.
                if(pos!=null)pending.putIfAbsent(pos,s.getTicks()+config.delayTicks);
            }
            resolve(s);if(stage!=Stage.RUNNING)return;
            var w=world(s,snapshotDimension);var it=pending.entrySet().iterator();while(it.hasNext()){var e=it.next();if(s.getTicks()>=e.getValue()){if(w.getBlockState(e.getKey()).equals(floor.get(e.getKey())))w.setBlockState(e.getKey(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);it.remove();}}
            for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&!alive.contains(id)&&p.isAlive()&&!inside(p.getServerWorld(),p.getBlockPos()))teleport(p,config.spawns.getFirst());}
        }
        if(s.getTicks()%20!=0)return;var audience=new HashSet<>(queue);audience.addAll(players);
        String state=stage==Stage.RUNNING?"比赛中":"等待开始";
        for(UUID id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&member(p))PrivateSidebar.show(p,"TNT 跑酷",List.of("状态："+state,"存活："+alive.size(),"排队："+queue.size()+" / "+config.maxPlayers,"地板层数："+config.layers.size()));}
    }
}
