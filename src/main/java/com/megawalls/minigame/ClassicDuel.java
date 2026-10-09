package com.megawalls.minigame;

import com.google.gson.*;
import com.megawalls.MWMod;
import com.megawalls.menu.HubMenu;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.*;
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

/** Build duel with optional four-player-per-side relay; the combat rules remain shared. */
public final class ClassicDuel {
    public static final String ID="classic_pvp";
    public enum Stage { WAITING, COUNTDOWN, RUNNING, RESETTING }
    public record Point(double x,double y,double z,float yaw,float pitch){}
    public static final class Config {
        public String dimension="minecraft:overworld";
        public Point corner1,corner2,spawn1,spawn2;
        public int countdownSeconds=5,maxSeconds=600,blocks=64,apples=6,arrows=16;
        public boolean relay=false;
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Config config=new Config();
    private static boolean validConfig=true;
    private static Stage stage=Stage.WAITING;
    private static final Set<UUID> queue=new LinkedHashSet<>(),players=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static final Map<UUID,Integer> teams=new LinkedHashMap<>();
    private static final UUID[] active=new UUID[2];
    private static int nextBoutTick=-1;
    private static final Map<UUID,net.minecraft.nbt.NbtCompound> resting=new HashMap<>();
    private record RespawnReturn(ServerPlayerEntity player,net.minecraft.server.network.ServerPlayNetworkHandler connection,int tick){}
    private static final Map<UUID,RespawnReturn> respawnReturns=new HashMap<>();
    private static final Set<UUID> relayRecoveries=new HashSet<>();
    public static boolean isRelay(){return config.relay;}
    public static int selectedTeam(ServerPlayerEntity p){return teams.getOrDefault(p.getUuid(),-1);}
    public static int teamCount(int team){return (int)(running()?alive:queue).stream().filter(id->teams.getOrDefault(id,-1)==team).count();}
    public static boolean canStartQueue(){return isRelay()?queue.size()==8&&teamCount(0)==4&&teamCount(1)==4:queue.size()==2;}
    public static boolean setRelay(ServerPlayerEntity p,boolean relay){
        if(!member(p)||stage!=Stage.WAITING)return false;
        if(config.relay==relay)return true;
        if(!queue.isEmpty()){p.sendMessage(Text.literal("请先退出参加，再切换对局模式。"),false);return false;}
        String old=JSON.toJson(config);config.relay=relay;if(save(p.getServer(),old)!=1)return false;
        teams.clear();LobbyItems.refresh(p.getServer());return true;
    }
    public static boolean selectTeam(ServerPlayerEntity p,int team){
        if(!member(p)||stage!=Stage.WAITING||!isRelay()||team<0||team>1||!p.isAlive())return false;
        if(selectedTeam(p)!=team&&teamCount(team)>=4){p.sendMessage(Text.literal("该队已满（4 人）。"),false);return false;}
        if(!queued(p)&&!enroll(p))return false;
        teams.put(p.getUuid(),team);LobbyItems.refresh(p.getServer());
        p.sendMessage(Text.literal("已选择"+(team==0?"红队":"紫队")),false);return true;
    }
    public static boolean paused(ServerPlayerEntity p){return isRelay()&&inRound(p)&&!fighter(p);}
    public static Stage stage(){return stage;}
    public static boolean running(){return stage==Stage.RUNNING;}
    public static boolean queued(ServerPlayerEntity p){return member(p)&&queue.contains(p.getUuid());}
    public static int queueSize(){return queue.size();}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(ID);}
    public static boolean inRound(ServerPlayerEntity p){return running()&&players.contains(p.getUuid());}
    public static boolean fighter(ServerPlayerEntity p){return inRound(p)&&alive.contains(p.getUuid())&&member(p)&&p.isAlive()&&!p.isSpectator()&&(!isRelay()||nextBoutTick<0&&(p.getUuid().equals(active[0])||p.getUuid().equals(active[1])));}
    public static boolean paired(ServerPlayerEntity a,ServerPlayerEntity b){return a!=b&&fighter(a)&&fighter(b)&&GameSessions.same(a,b)&&(!isRelay()||selectedTeam(a)!=selectedTeam(b));}
    public static boolean compatibility(){return FabricLoader.getInstance().isModLoaded("goldenagecombat");}
    private static boolean legacySettings(){
        try{Object holder=Class.forName("fuzs.goldenagecombat.GoldenAgeCombat").getField("CONFIG").get(null);var get=Class.forName("fuzs.puzzleslib.api.config.v3.ConfigHolder").getMethod("get",Class.class);
            Class<?> type=Class.forName("fuzs.goldenagecombat.config.ServerConfig");Object c=get.invoke(holder,type);
            for(String field:List.of("removeAttackCooldown","weakAttacksKnockBackPlayers","fishingRodKnockback"))if(!type.getField(field).getBoolean(c))return false;
            type=Class.forName("fuzs.goldenagecombat.config.CommonConfig");c=get.invoke(holder,type);return type.getField("oldAttackDamage").getBoolean(c);
        }catch(ReflectiveOperationException|LinkageError e){return false;}
    }
    private static Path configFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("classic-pvp.json");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("classic-pvp-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static boolean valid(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984&&Math.abs(p.y)<2048&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch);}
    private static boolean values(Config c){return c!=null&&c.dimension!=null&&Identifier.tryParse(c.dimension)!=null&&c.blocks>=1&&c.blocks<=64&&c.apples>=1&&c.apples<=64&&c.arrows>=0&&c.arrows<=64;}
    private static void load(MinecraftServer s){try{var c=Files.exists(configFile(s))?JSON.fromJson(Files.readString(configFile(s)),Config.class):new Config();if(!values(c))throw new IllegalArgumentException("Invalid duel config");config=c;validConfig=true;}catch(Exception e){validConfig=false;MWMod.LOGGER.error("Cannot read classic-pvp.json",e);}}
    private static int save(MinecraftServer s,String old){try{if(!values(config))throw new IllegalArgumentException("Invalid parameters");var temp=configFile(s).resolveSibling("classic-pvp.json.tmp");Files.writeString(temp,JSON.toJson(config));Files.move(temp,configFile(s),StandardCopyOption.REPLACE_EXISTING);return 1;}catch(Exception e){config=JSON.fromJson(old,Config.class);MWMod.LOGGER.error("Cannot save duel config",e);return 0;}}
    public static boolean inside(ServerWorld w,BlockPos p){if(!valid(config.corner1)||!valid(config.corner2)||!w.getRegistryKey().getValue().toString().equals(config.dimension))return false;var a=config.corner1;var b=config.corner2;return p.getX()>=Math.floor(Math.min(a.x,b.x))&&p.getX()<=Math.floor(Math.max(a.x,b.x))&&p.getY()>=Math.floor(Math.min(a.y,b.y))&&p.getY()<=Math.floor(Math.max(a.y,b.y))&&p.getZ()>=Math.floor(Math.min(a.z,b.z))&&p.getZ()<=Math.floor(Math.max(a.z,b.z));}
    private static ServerWorld world(MinecraftServer s){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(config.dimension)));}
    private static BlockPos block(Point p){return BlockPos.ofFloored(p.x,p.y,p.z);}
    private static String issue(MinecraftServer s){
        if(!validConfig)return "配置无法读取";
        if(!compatibility())return "缺少 Golden Age Combat 1.21.1，请安装配套旧版战斗组件";
        if(!legacySettings())return "旧版战斗配置不匹配：需开启移除冷却、弱攻击击退、鱼竿击退和旧版武器伤害";
        if(!valid(config.corner1)||!valid(config.corner2)||!valid(config.spawn1)||!valid(config.spawn2))return "需设置 arena1、arena2、spawn1、spawn2";
        var a=block(config.corner1);var b=block(config.corner2);long dx=Math.abs(a.getX()-b.getX())+1,dy=Math.abs(a.getY()-b.getY())+1,dz=Math.abs(a.getZ()-b.getZ())+1;
        if(dx>1024||dz>1024||dy>384)return "场地水平单边最多 1024 格，高度最多 384 格";
        var w=world(s);if(w==null)return "维度不存在";if(w.isOutOfHeightLimit(a)||w.isOutOfHeightLimit(b)||!w.getWorldBorder().contains(a)||!w.getWorldBorder().contains(b))return "范围超出世界边界";
        if(block(config.spawn1).getSquaredDistance(block(config.spawn2))<16)return "两个出生点至少相距 4 格";
        for(var pt:List.of(config.spawn1,config.spawn2)){var pos=block(pt);w.getChunk(pos);if(!inside(w,pos)||!inside(w,pos.down())||!inside(w,pos.up())||w.getBlockState(pos.down()).getCollisionShape(w,pos.down()).isEmpty()||!w.isSpaceEmpty(new Box(pt.x-.3,pt.y,pt.z-.3,pt.x+.3,pt.y+1.8,pt.z+.3)))return "出生点需要安全地板、头部净空且在范围内";}
        var area1=block(config.corner1);var area2=block(config.corner2);
        if(PotDuel.overlaps(w,area1,area2)||Spleef.overlaps(w,area1,area2)||HotPotato.overlaps(w,area1,area2)||TntRun.overlaps(w,area1,area2)||BedArena.overlaps(w,area1,area2))return "场地与进行中的其它游戏重叠";
        return "场地就绪";
    }
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{queue.clear();players.clear();alive.clear();clearRelay();respawnReturns.clear();relayRecoveries.clear();stage=Stage.WAITING;DuelArena.clear();load(s);if(DuelArena.exists(s)){stage=Stage.RESETTING;DuelArena.load(s);}});
        ServerTickEvents.END_SERVER_TICK.register(ClassicDuel::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{finish(s,null,"服务器关闭，对战取消。");if(stage==Stage.RESETTING)DuelArena.reset(s,Integer.MAX_VALUE);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){if(relayRecoveries.contains(p.getUuid())||isRelay()&&inRound(p)){p.changeGameMode(GameMode.SPECTATOR);respawnReturns.put(p.getUuid(),new RespawnReturn(p,p.networkHandler,p.getServer().getTicks()));}else{recover(p);HubMenu.travel(p,GameSessions.HUB);}}});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,src,amount)->{
            if(!(e instanceof ServerPlayerEntity p)||!member(p))return true;if(src.isOf(DamageTypes.GENERIC_KILL))return true;if(!fighter(p))return false;
            var attacker=Isolation.owner(src.getSource());if(attacker==null)attacker=Isolation.owner(src.getAttacker());return attacker==null||attacker==p||paired(attacker,p);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((e,src)->{if(e instanceof ServerPlayerEntity p&&member(p)){alive.remove(p.getUuid());if(!isRelay())resolve(p.getServer());}});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(CommandManager.literal("classic")
            .then(CommandManager.literal("join").executes(c->(member(c.getSource().getPlayerOrThrow())||HubMenu.travel(c.getSource().getPlayerOrThrow(),ID))&&enroll(c.getSource().getPlayerOrThrow())?1:0))
            .then(CommandManager.literal("leave").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0))
            .then(CommandManager.literal("start").executes(c->requestStart(c.getSource().getPlayerOrThrow())?1:0))
            .then(CommandManager.literal("mode")
                .then(CommandManager.literal("1v1").executes(c->setRelay(c.getSource().getPlayerOrThrow(),false)?1:0))
                .then(CommandManager.literal("4v4").executes(c->setRelay(c.getSource().getPlayerOrThrow(),true)?1:0)))
            .then(CommandManager.literal("team")
                .then(CommandManager.literal("red").executes(c->selectTeam(c.getSource().getPlayerOrThrow(),0)?1:0))
                .then(CommandManager.literal("purple").executes(c->selectTeam(c.getSource().getPlayerOrThrow(),1)?1:0)))
            .then(CommandManager.literal("stop").executes(c->{finish(c.getSource().getServer(),null,"对战已取消。");return 1;}))
            .then(CommandManager.literal("reset").executes(c->{var s=c.getSource().getServer();finish(s,null,"场地正在重置。");if(stage==Stage.RESETTING&&DuelArena.failed())DuelArena.load(s);return 1;}))
            .then(CommandManager.literal("arena1").executes(c->point(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("arena2").executes(c->point(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("spawn1").executes(c->point(c.getSource().getPlayerOrThrow(),3)))
            .then(CommandManager.literal("spawn2").executes(c->point(c.getSource().getPlayerOrThrow(),4)))
            .then(CommandManager.literal("rule")
                .then(CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(CommandManager.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1800)).executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);int v=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value");switch(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")){case "blocks"->config.blocks=v;case "apples"->config.apples=v;case "arrows"->config.arrows=v;default->{return 0;}}return save(c.getSource().getServer(),old);}))))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("UHC PvP："+stage+" / "+(isRelay()?"4v4 车轮战 · 红 "+teamCount(0)+"/4 紫 "+teamCount(1)+"/4":"1v1 · 等候 "+queue.size()+"/2")+" / "+(DuelArena.failed()?"恢复失败，请检查日志后 /classic reset":issue(c.getSource().getServer()))),false);return 1;}))));
    }
    private static int point(ServerPlayerEntity p,int id){if(stage!=Stage.WAITING)return 0;String dim=p.getWorld().getRegistryKey().getValue().toString();if((config.corner1!=null||config.corner2!=null||config.spawn1!=null||config.spawn2!=null)&&!dim.equals(config.dimension))return 0;String old=JSON.toJson(config);config.dimension=dim;var pt=new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());switch(id){case 1->config.corner1=pt;case 2->config.corner2=pt;case 3->config.spawn1=pt;case 4->config.spawn2=pt;}int result=save(p.getServer(),old);p.sendMessage(Text.literal(result==1?"已保存UHC PvP 点位。":"保存失败。"),false);return result;}
    public static boolean canEnter(ServerPlayerEntity p){return true;}
    public static void join(ServerPlayerEntity p){enroll(p);}
    public static boolean enroll(ServerPlayerEntity p){
        if(!member(p)||stage!=Stage.WAITING||!p.isAlive()||queue.size()>=(isRelay()?8:2)&&!queued(p)){p.sendMessage(Text.literal("本局已满或正在进行，可留在小大厅旁观。"),false);return false;}
        queue.add(p.getUuid());p.changeGameMode(GameMode.ADVENTURE);LobbyItems.refresh(p.getServer());p.sendMessage(Text.literal("已加入 UHC PvP"),false);return true;
    }
    public static boolean requestStart(ServerPlayerEntity p){
        if(!queued(p)||stage!=Stage.WAITING||!p.isAlive()||p.isSpectator())return false;
        String problem=issue(p.getServer());if(!canStartQueue()||!problem.equals("场地就绪")){p.sendMessage(Text.literal("暂不能开始："+(!canStartQueue()?(isRelay()?"红、紫队各需 4 名参加玩家":"需要两名参加玩家"):problem)),false);return false;}
        return start(p.getServer());
    }
    private static void tell(MinecraftServer s,String text){var audience=new HashSet<>(queue);audience.addAll(players);for(var id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(text),false);}}
    private static void teleport(ServerPlayerEntity p,Point pt){p.teleport(world(p.getServer()),pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    private static void kit(ServerPlayerEntity p){p.getInventory().clear();var sword=new ItemStack(Items.DIAMOND_SWORD);LegacyDuelItems.sword(sword);p.getInventory().setStack(0,sword);p.getInventory().setStack(1,new ItemStack(Items.FISHING_ROD));p.getInventory().setStack(2,new ItemStack(Items.BOW));p.getInventory().setStack(3,new ItemStack(Items.COBBLESTONE,config.blocks));p.getInventory().setStack(4,new ItemStack(Items.GOLDEN_APPLE,config.apples));p.getInventory().setStack(5,new ItemStack(Items.WATER_BUCKET));p.getInventory().setStack(6,new ItemStack(Items.LAVA_BUCKET));p.getInventory().setStack(7,new ItemStack(Items.DIAMOND_PICKAXE));if(config.arrows>0)p.getInventory().setStack(9,new ItemStack(Items.ARROW,config.arrows));p.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));p.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.equipStack(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));GameSessions.selectSlot(p,0);p.currentScreenHandler.syncState();}
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING)return false;queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});
        teams.keySet().retainAll(queue);
        if(!canStartQueue()||!issue(s).equals("场地就绪")){stage=Stage.WAITING;return false;}
        for(var id:queue){var p=s.getPlayerManager().getPlayer(id);recover(p);if(hasRecovery(p)){stage=Stage.WAITING;return false;}p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);if(!GameSessions.write(recovery(p),GameSessions.capture(p))){stage=Stage.WAITING;return false;}}
        if(!DuelArena.save(s,world(s),block(config.corner1),block(config.corner2))){for(var id:queue){var q=s.getPlayerManager().getPlayer(id);if(q!=null)recover(q);}stage=Stage.WAITING;tell(s,"场地恢复日志创建失败，比赛未开始。");return false;}
        players.clear();players.addAll(queue);alive.clear();alive.addAll(queue);if(isRelay())relayRecoveries.addAll(players);queue.clear();stage=Stage.RUNNING;int i=0;nextBoutTick=-1;resting.clear();Arrays.fill(active,null);
        for(var id:players){var p=s.getPlayerManager().getPlayer(id);p.clearStatusEffects();p.changeGameMode(isRelay()?GameMode.SPECTATOR:GameMode.SURVIVAL);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.extinguish();p.setGlowing(false);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);kit(p);teleport(p,isRelay()?(selectedTeam(p)==0?config.spawn1:config.spawn2):(i++==0?config.spawn1:config.spawn2));if(isRelay()){assignTeam(p,selectedTeam(p));resting.put(id,combatState(p));}}
        if(isRelay()){chooseActive();if(!deploy(s,false))return false;}
        LobbyItems.spectateAudience(s,ID);LobbyItems.refresh(s);
        tell(s,"UHC PvP "+(isRelay()?"4v4 车轮战":"1v1")+" 开始");return true;
    }
    public static void recover(ServerPlayerEntity p){if(!hasRecovery(p)||!p.isAlive())return;try{var n=GameSessions.read(recovery(p));p.stopUsingItem();GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p)){Files.delete(recovery(p));relayRecoveries.remove(p.getUuid());}}catch(Exception e){MWMod.LOGGER.error("Cannot recover classic duel player; backup retained",e);p.sendMessage(Text.literal("UHC PvP 赛前档案恢复失败，备份已保留。"),false);}}
    public static void leave(ServerPlayerEntity p){queue.remove(p.getUuid());alive.remove(p.getUuid());players.remove(p.getUuid());teams.remove(p.getUuid());resting.remove(p.getUuid());respawnReturns.remove(p.getUuid());recover(p);PrivateSidebar.clear(p);if(!isRelay())resolve(p.getServer());}
    private static void clearRelay(){teams.clear();Arrays.fill(active,null);nextBoutTick=-1;resting.clear();}
    private static int remaining(int team){return (int)alive.stream().filter(id->teams.getOrDefault(id,-1)==team).count();}
    private static void assignTeam(ServerPlayerEntity p,int side){String name="uhc_relay_"+(side==0?"red":"purple");var board=p.getScoreboard();var team=board.getTeam(name);if(team==null)team=board.addTeam(name);team.setColor(side==0?Formatting.RED:Formatting.LIGHT_PURPLE);team.setDisplayName(Text.literal(side==0?"红队":"紫队"));team.setFriendlyFireAllowed(false);board.addScoreHolderToTeam(p.getName().getString(),team);}
    private static void completeRespawns(MinecraftServer s){for(var entry:new ArrayList<>(respawnReturns.entrySet())){
        var pending=entry.getValue();var p=pending.player();if(s.getTicks()<=pending.tick())continue;respawnReturns.remove(entry.getKey());
        if(s.getPlayerManager().getPlayer(p.getUuid())!=p||p.networkHandler!=pending.connection()||pending.connection().player!=p||!p.isAlive()||p.isDisconnected()||!member(p))continue;
        if(isRelay()&&inRound(p)){p.setCameraEntity(p);p.changeGameMode(GameMode.SPECTATOR);teleport(p,selectedTeam(p)==1?config.spawn2:config.spawn1);}
        else{recover(p);HubMenu.travel(p,GameSessions.HUB);}
    }}
    private static void chooseActive(){for(int team=0;team<2;team++)if(active[team]==null||!alive.contains(active[team])){final int side=team;active[team]=alive.stream().filter(id->teams.getOrDefault(id,-1)==side).findFirst().orElse(null);}}
    private static net.minecraft.nbt.NbtCompound combatState(ServerPlayerEntity p){var n=GameSessions.capture(p);n.putInt("fireTicks",p.getFireTicks());return n;}
    private static void restoreCombat(ServerPlayerEntity p,net.minecraft.nbt.NbtCompound n){
        p.clearStatusEffects();for(var value:n.getList("effects",net.minecraft.nbt.NbtElement.COMPOUND_TYPE)){var effect=net.minecraft.entity.effect.StatusEffectInstance.fromNbt((net.minecraft.nbt.NbtCompound)value);if(effect!=null)p.addStatusEffect(effect);}
        p.setHealth(n.getFloat("health"));p.setAbsorptionAmount(n.getFloat("absorption"));p.getHungerManager().readNbt(n);
        p.setFireTicks(n.getInt("fireTicks"));
    }
    private static boolean deploy(MinecraftServer s,boolean protect){
        for(var id:active){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.changeGameMode(GameMode.SPECTATOR);}
        var w=world(s);for(var pt:List.of(config.spawn1,config.spawn2))if(!DuelArena.prepareSpawn(w,pt.x,pt.y,pt.z)){finish(s,null,"出生点无法安全恢复，车轮战已取消。");return false;}
        for(int side=0;side<2;side++){var p=s.getPlayerManager().getPlayer(active[side]);if(p==null)continue;
            var n=resting.get(p.getUuid());if(n!=null)restoreCombat(p,n);p.stopUsingItem();p.setCameraEntity(p);p.changeGameMode(GameMode.SURVIVAL);teleport(p,side==0?config.spawn1:config.spawn2);
            if(!protect)resting.remove(p.getUuid());
        }
        return true;
    }
    private static void resolve(MinecraftServer s){
        if(!running())return;
        if(!isRelay()){if(alive.size()<=1)finish(s,alive.stream().findFirst().orElse(null),"本局平局。");return;}
        int red=remaining(0),purple=remaining(1);
        if(red==0||purple==0){finish(s,null,red==0&&purple==0?"UHC PvP 车轮战平局。":"UHC PvP 车轮战胜者："+(red>0?"红队":"紫队"));return;}
        boolean change=active[0]==null||active[1]==null||!alive.contains(active[0])||!alive.contains(active[1]);
        if(change){
            for(var id:active)if(id!=null){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&alive.contains(id))resting.put(id,combatState(p));else if(p!=null&&p.isAlive()&&member(p)&&inRound(p))p.changeGameMode(GameMode.SPECTATOR);}
            cleanupProjectiles(s);chooseActive();nextBoutTick=s.getTicks()+60;if(!deploy(s,true))return;
            tell(s,"下一轮 3 秒后开始 · 红队剩余 "+red+" 人 / 紫队剩余 "+purple+" 人");
        }
    }
    private static void cleanupProjectiles(MinecraftServer s){var remove=new ArrayList<net.minecraft.entity.Entity>();for(var w:s.getWorlds())for(var e:w.iterateEntities())if(e instanceof net.minecraft.entity.projectile.ProjectileEntity&&e.getCommandTags().contains("mg_source_"+ID))remove.add(e);remove.forEach(net.minecraft.entity.Entity::discard);}
    private static void cleanupEntities(MinecraftServer s){var remove=new ArrayList<net.minecraft.entity.Entity>();for(var w:s.getWorlds())for(var e:w.iterateEntities())if(!(e instanceof ServerPlayerEntity)&&(e.getCommandTags().contains("mg_source_"+ID)||e.getCommandTags().contains("mg_item_"+ID)))remove.add(e);remove.forEach(net.minecraft.entity.Entity::discard);}
    private static void finish(MinecraftServer s,UUID winner,String reason){if(stage==Stage.RESETTING||stage==Stage.WAITING&&queue.isEmpty()&&players.isEmpty())return;stage=Stage.RESETTING;players.addAll(queue);queue.clear();var p=winner==null?null:s.getPlayerManager().getPlayer(winner);tell(s,p==null?reason:"UHC PvP 胜者："+p.getName().getString());cleanupEntities(s);
        for(var id:new ArrayList<>(players)){var player=s.getPlayerManager().getPlayer(id);if(player!=null){recover(player);PrivateSidebar.clear(player);if(player.isAlive())HubMenu.travel(player,GameSessions.HUB);}}
        alive.clear();players.clear();clearRelay();LobbyItems.finishAudience(s,ID);if(DuelArena.exists(s))DuelArena.beginReset();else stage=Stage.WAITING;
    }
    public static void tick(MinecraftServer s){
        completeRespawns(s);
        if(stage==Stage.RESETTING){if(DuelArena.reset(s,1024))stage=Stage.WAITING;return;}
        if(stage==Stage.WAITING&&s.getTicks()%20==0){int before=queue.size();queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});teams.keySet().retainAll(queue);if(before!=queue.size())LobbyItems.refresh(s);}
        if(running()){
            for(var id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);boolean deployed=!isRelay()||id.equals(active[0])||id.equals(active[1]);if(p==null||!member(p)||!p.isAlive()||deployed&&(p.isSpectator()||!inside(p.getServerWorld(),p.getBlockPos())))alive.remove(id);else if(!deployed&&!p.isSpectator()){p.stopUsingItem();p.changeGameMode(GameMode.SPECTATOR);}}
            resolve(s);
            if(running()&&isRelay()&&nextBoutTick>=0){
                for(int side=0;side<2;side++){var p=s.getPlayerManager().getPlayer(active[side]);if(p!=null&&p.isAlive()){var n=resting.get(p.getUuid());if(n!=null){p.setHealth(n.getFloat("health"));p.setAbsorptionAmount(n.getFloat("absorption"));p.getHungerManager().readNbt(n);p.setFireTicks(n.getInt("fireTicks"));}teleport(p,side==0?config.spawn1:config.spawn2);}}
                if(s.getTicks()>=nextBoutTick){nextBoutTick=-1;if(deploy(s,false))tell(s,"车轮战下一轮开始");}
            }
        }
        if(s.getTicks()%20!=0)return;
        for(var p:s.getPlayerManager().getPlayerList())if(member(p)){
            var lines=new ArrayList<String>();
            if(isRelay()){lines.add("4v4 车轮战");lines.add("§c红队："+(running()?remaining(0):teamCount(0))+" / 4");lines.add("§5紫队："+(running()?remaining(1):teamCount(1))+" / 4");if(selectedTeam(p)>=0)lines.add(selectedTeam(p)==0?"§c你的队伍：红队":"§5你的队伍：紫队");if(nextBoutTick>=0)lines.add("下一轮："+Math.max(1,(nextBoutTick-s.getTicks()+19)/20)+" 秒");}
            else lines.add("人数："+(running()?alive.size():queue.size())+" / 2");
            PrivateSidebar.showGame(p,"UHC PvP",lines);
        }
    }
}
