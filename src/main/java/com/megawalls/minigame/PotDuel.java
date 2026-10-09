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

/** Potion duels and optional 4v4 relay. Combat uses the existing legacy integration. */
public final class PotDuel {
    public static final String ID="pot_pvp";
    public enum Stage { WAITING, COUNTDOWN, RUNNING, RESETTING }
    public record Point(double x,double y,double z,float yaw,float pitch){}
    public static final class Config {
        public String dimension="minecraft:overworld";
        public Point corner1,corner2,spawn1,spawn2;
        public int countdownSeconds=5,maxSeconds=600,pearls=16,heals=28,speeds=4,pearlSeconds=15;
        public boolean relay=false;
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Config config=new Config();
    private static boolean validConfig=true;
    private static Stage stage=Stage.WAITING;
    private static final Set<UUID> queue=new LinkedHashSet<>(),players=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static boolean relay;
    private static final Map<UUID,Integer> selectedTeams=new HashMap<>(),roundTeams=new HashMap<>();
    private static final UUID[] active=new UUID[2];
    private static int relayGapTicks;
    private static final Map<UUID,PausedVitals> pausedVitals=new HashMap<>();
    private static final Map<UUID,ServerPlayerEntity> pendingRespawns=new HashMap<>();
    private static final Set<UUID> relayRecovery=new HashSet<>();
    /** Only vitals are restored during a pause; equipment is never replenished. */
    private static final class PausedVitals {
        final float health,absorption;
        final int fireTicks;
        final net.minecraft.nbt.NbtCompound hunger=new net.minecraft.nbt.NbtCompound();
        PausedVitals(ServerPlayerEntity p){health=p.getHealth();absorption=p.getAbsorptionAmount();fireTicks=p.getFireTicks();p.getHungerManager().writeNbt(hunger);}
        void restore(ServerPlayerEntity p){p.setHealth(health);p.setAbsorptionAmount(absorption);p.getHungerManager().readNbt(hunger);p.setFireTicks(fireTicks);}
    }
    public static Stage stage(){return stage;}
    public static boolean running(){return stage==Stage.RUNNING;}
    public static boolean queued(ServerPlayerEntity p){return member(p)&&queue.contains(p.getUuid());}
    public static int queueSize(){return queue.size();}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(ID);}
    public static boolean inRound(ServerPlayerEntity p){return running()&&players.contains(p.getUuid());}
    public static boolean fighter(ServerPlayerEntity p){return inRound(p)&&alive.contains(p.getUuid())&&member(p)&&p.isAlive()&&!p.isSpectator()&&(!relay||relayGapTicks==0&&(p.getUuid().equals(active[0])||p.getUuid().equals(active[1])));}
    public static boolean paired(ServerPlayerEntity a,ServerPlayerEntity b){return a!=b&&fighter(a)&&fighter(b)&&GameSessions.same(a,b)&&(!relay||!Objects.equals(roundTeams.get(a.getUuid()),roundTeams.get(b.getUuid())));}
    public static boolean isRelay(){return relay;}
    public static boolean paused(ServerPlayerEntity p){return relay&&inRound(p)&&!fighter(p);}
    public static int selectedTeam(ServerPlayerEntity p){return (running()?roundTeams:selectedTeams).getOrDefault(p.getUuid(),-1);}
    public static int teamCount(int team){return (int)(running()?alive:queue).stream().filter(id->(running()?roundTeams:selectedTeams).getOrDefault(id,-1)==team).count();}
    public static boolean canStartQueue(){return relay?queue.size()==8&&teamCount(0)==4&&teamCount(1)==4:queue.size()==2;}
    public static boolean setRelay(ServerPlayerEntity p,boolean value){
        if(!member(p)||stage!=Stage.WAITING)return false;if(relay==value)return true;
        if(!queue.isEmpty()){p.sendMessage(Text.literal("请先退出参加，待参加队列清空后再切换模式。"),false);return false;}
        String old=JSON.toJson(config);config.relay=value;if(save(p.getServer(),old)!=1)return false;
        relay=config.relay;selectedTeams.clear();LobbyItems.refresh(p.getServer());p.sendMessage(Text.literal("Pot PVP 模式："+(relay?"4v4 车轮战":"1v1")),false);return true;
    }
    public static boolean selectTeam(ServerPlayerEntity p,int team){
        if(!relay||!member(p)||stage!=Stage.WAITING||team<0||team>1||!p.isAlive())return false;
        if(selectedTeams.getOrDefault(p.getUuid(),-1)==team&&queued(p))return true;
        if(teamCount(team)>=4){p.sendMessage(Text.literal((team==0?"红":"紫")+"队已满。"),false);return false;}
        if(!queued(p)&&!enroll(p))return false;
        selectedTeams.put(p.getUuid(),team);LobbyItems.refresh(p.getServer());p.sendMessage(Text.literal("已选择"+(team==0?"红":"紫")+"队；队内按参加顺序出战。"),false);return true;
    }
    public static boolean compatibility(){return FabricLoader.getInstance().isModLoaded("goldenagecombat");}
    private static boolean legacySettings(){
        try{Object holder=Class.forName("fuzs.goldenagecombat.GoldenAgeCombat").getField("CONFIG").get(null);var get=Class.forName("fuzs.puzzleslib.api.config.v3.ConfigHolder").getMethod("get",Class.class);
            Class<?> type=Class.forName("fuzs.goldenagecombat.config.ServerConfig");Object c=get.invoke(holder,type);
            for(String field:List.of("removeAttackCooldown","weakAttacksKnockBackPlayers","fishingRodKnockback"))if(!type.getField(field).getBoolean(c))return false;
            type=Class.forName("fuzs.goldenagecombat.config.CommonConfig");c=get.invoke(holder,type);return type.getField("oldAttackDamage").getBoolean(c);
        }catch(ReflectiveOperationException|LinkageError e){return false;}
    }
    private static Path configFile(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("pot-pvp.json");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("pot-pvp-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static boolean valid(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984&&Math.abs(p.y)<2048&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch);}
    private static boolean values(Config c){return c!=null&&c.dimension!=null&&Identifier.tryParse(c.dimension)!=null&&c.pearls>=1&&c.pearls<=16&&c.heals>=1&&c.speeds>=1&&c.heals+c.speeds<=32&&c.pearlSeconds>=1&&c.pearlSeconds<=60;}
    private static void load(MinecraftServer s){try{var c=Files.exists(configFile(s))?JSON.fromJson(Files.readString(configFile(s)),Config.class):new Config();if(!values(c))throw new IllegalArgumentException("Invalid duel config");config=c;relay=c.relay;validConfig=true;}catch(Exception e){validConfig=false;MWMod.LOGGER.error("Cannot read pot-pvp.json",e);}}
    private static int save(MinecraftServer s,String old){try{if(!values(config))throw new IllegalArgumentException("Invalid parameters");var temp=configFile(s).resolveSibling("pot-pvp.json.tmp");Files.writeString(temp,JSON.toJson(config));Files.move(temp,configFile(s),StandardCopyOption.REPLACE_EXISTING);return 1;}catch(Exception e){config=JSON.fromJson(old,Config.class);MWMod.LOGGER.error("Cannot save duel config",e);return 0;}}
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
        if(DuelArena.overlaps(w,a,b)||BedArena.overlaps(w,a,b)||TntRun.overlaps(w,a,b)||HotPotato.overlaps(w,a,b)||Spleef.overlaps(w,a,b))return "场地与运行中的其它游戏重叠";
        return "场地就绪";
    }
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{queue.clear();players.clear();alive.clear();pendingRespawns.clear();relayRecovery.clear();relay=false;selectedTeams.clear();clearRelayRound();stage=Stage.WAITING;PotArena.clear();load(s);if(PotArena.exists(s)){stage=Stage.RESETTING;PotArena.load(s);}});
        ServerTickEvents.END_SERVER_TICK.register(PotDuel::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{finish(s,null,"服务器关闭，对战取消。");if(stage==Stage.RESETTING)PotArena.restore(s,Integer.MAX_VALUE);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){
            if(relayRecovery.contains(p.getUuid())||relay&&inRound(p)){alive.remove(p.getUuid());pausedVitals.remove(p.getUuid());pendingRespawns.put(p.getUuid(),p);return;}
            recover(p);HubMenu.travel(p,GameSessions.HUB);
        }});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,src,amount)->{
            if(!(e instanceof ServerPlayerEntity p)||!member(p))return true;if(src.isOf(DamageTypes.GENERIC_KILL))return true;if(!fighter(p))return false;
            var attacker=Isolation.owner(src.getSource());if(attacker==null)attacker=Isolation.owner(src.getAttacker());return attacker==null||attacker==p||paired(attacker,p);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((e,src)->{if(e instanceof ServerPlayerEntity p&&member(p)){alive.remove(p.getUuid());pausedVitals.remove(p.getUuid());resolve(p.getServer());}});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(CommandManager.literal("pot")
            .then(CommandManager.literal("join").executes(c->(member(c.getSource().getPlayerOrThrow())||HubMenu.travel(c.getSource().getPlayerOrThrow(),ID))&&enroll(c.getSource().getPlayerOrThrow())?1:0))
            .then(CommandManager.literal("leave").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0))
            .then(CommandManager.literal("start").executes(c->requestStart(c.getSource().getPlayerOrThrow())?1:0))
            .then(CommandManager.literal("mode")
                .then(CommandManager.literal("1v1").executes(c->setRelay(c.getSource().getPlayerOrThrow(),false)?1:0))
                .then(CommandManager.literal("4v4").executes(c->setRelay(c.getSource().getPlayerOrThrow(),true)?1:0))
                .then(CommandManager.literal("relay").executes(c->setRelay(c.getSource().getPlayerOrThrow(),true)?1:0)))
            .then(CommandManager.literal("team")
                .then(CommandManager.literal("red").executes(c->selectTeam(c.getSource().getPlayerOrThrow(),0)?1:0))
                .then(CommandManager.literal("purple").executes(c->selectTeam(c.getSource().getPlayerOrThrow(),1)?1:0)))
            .then(CommandManager.literal("stop").executes(c->{finish(c.getSource().getServer(),null,"对战已取消。");return 1;}))
            .then(CommandManager.literal("reset").executes(c->{var s=c.getSource().getServer();finish(s,null,"场地正在重置。");if(stage==Stage.RESETTING&&PotArena.failed())PotArena.load(s);return 1;}))
            .then(CommandManager.literal("arena1").executes(c->point(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("arena2").executes(c->point(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("spawn1").executes(c->point(c.getSource().getPlayerOrThrow(),3)))
            .then(CommandManager.literal("spawn2").executes(c->point(c.getSource().getPlayerOrThrow(),4)))
            .then(CommandManager.literal("rule")
                .then(CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(CommandManager.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1800)).executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);int v=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value");switch(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")){case "pearls"->config.pearls=v;case "heals"->config.heals=v;case "speeds"->config.speeds=v;case "pearlCooldown"->config.pearlSeconds=v;default->{return 0;}}return save(c.getSource().getServer(),old);}))))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("Pot PVP："+stage+" / "+(relay?"4v4 车轮战 / 红 "+teamCount(0)+" 紫 "+teamCount(1):"1v1")+" / 等候 "+queue.size()+"/"+(relay?8:2)+" / "+issue(c.getSource().getServer())),false);return 1;}))));
    }
    private static int point(ServerPlayerEntity p,int id){if(stage!=Stage.WAITING)return 0;String dim=p.getWorld().getRegistryKey().getValue().toString();if((config.corner1!=null||config.corner2!=null||config.spawn1!=null||config.spawn2!=null)&&!dim.equals(config.dimension))return 0;String old=JSON.toJson(config);config.dimension=dim;var pt=new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());switch(id){case 1->config.corner1=pt;case 2->config.corner2=pt;case 3->config.spawn1=pt;case 4->config.spawn2=pt;}int result=save(p.getServer(),old);p.sendMessage(Text.literal(result==1?"已保存Pot PVP 点位。":"保存失败。"),false);return result;}
    public static boolean canEnter(ServerPlayerEntity p){return true;}
    public static void join(ServerPlayerEntity p){enroll(p);}
    public static boolean enroll(ServerPlayerEntity p){
        if(!member(p)||stage!=Stage.WAITING||!p.isAlive()||queue.size()>=(relay?8:2)&&!queued(p)){p.sendMessage(Text.literal("本局已满或正在进行，可留在小大厅旁观。"),false);return false;}
        queue.add(p.getUuid());p.changeGameMode(GameMode.ADVENTURE);LobbyItems.refresh(p.getServer());p.sendMessage(Text.literal("已加入 Pot PVP"),false);return true;
    }
    public static boolean requestStart(ServerPlayerEntity p){
        if(!queued(p)||stage!=Stage.WAITING||!p.isAlive()||p.isSpectator())return false;
        String problem=issue(p.getServer());if(!canStartQueue()||!problem.equals("场地就绪")){p.sendMessage(Text.literal("暂不能开始："+(!canStartQueue()?(relay?"需要红、紫两队各四名参加玩家":"需要两名参加玩家"):problem)),false);return false;}
        return start(p.getServer());
    }
    private static void tell(MinecraftServer s,String text){var audience=new HashSet<>(queue);audience.addAll(players);for(var id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(text),false);}}
    private static void teleport(ServerPlayerEntity p,Point pt){p.teleport(world(p.getServer()),pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    public static final Item PEARL_GROUP=Items.ENDER_PEARL;
    public static int pearlCooldownTicks(){return config.pearlSeconds*20;}
    private static UUID round;
    public static boolean protectedAt(ServerWorld w,BlockPos pos){return running()&&inside(w,pos)||PotArena.contains(w,pos);}
    public static boolean breakPlaced(ServerPlayerEntity p,BlockPos pos){
        if(!fighter(p)||!p.canInteractWithBlockAt(pos,0)||!PotArena.added(p.getServerWorld(),pos))return false;
        return PotArena.authorized(()->p.getServerWorld().breakBlock(pos,false,p));
    }
    public static boolean overlaps(ServerWorld w,BlockPos a,BlockPos b){
        if(!running()||!w.getRegistryKey().getValue().toString().equals(config.dimension))return false;
        var x=block(config.corner1);var y=block(config.corner2);
        return Math.max(a.getX(),b.getX())>=Math.min(x.getX(),y.getX())&&Math.min(a.getX(),b.getX())<=Math.max(x.getX(),y.getX())
            &&Math.max(a.getY(),b.getY())>=Math.min(x.getY(),y.getY())&&Math.min(a.getY(),b.getY())<=Math.max(x.getY(),y.getY())
            &&Math.max(a.getZ(),b.getZ())>=Math.min(x.getZ(),y.getZ())&&Math.min(a.getZ(),b.getZ())<=Math.max(x.getZ(),y.getZ());
    }
    public static void stamp(net.minecraft.entity.Entity e){if(round!=null&&e.getCommandTags().stream().noneMatch(t->t.startsWith("mg_pot_round_")))e.addCommandTag("mg_pot_round_"+round);}
    public static boolean current(net.minecraft.entity.Entity e){return running()&&round!=null&&e.getCommandTags().contains("mg_pot_round_"+round);}
    public static boolean permits(net.minecraft.entity.Entity e,ServerPlayerEntity target){return current(e)&&fighter(target);}
    private static void kit(ServerPlayerEntity p){
        p.getInventory().clear();
        var sword=new ItemStack(Items.DIAMOND_SWORD);
        LegacyDuelItems.sword(sword);
        p.getInventory().setStack(0,sword);
        var pearls=new ItemStack(Items.ENDER_PEARL,config.pearls);
        LegacyDuelItems.pearl(pearls,config.pearlSeconds);
        p.getInventory().setStack(1,pearls);
        p.getInventory().setStack(2,new ItemStack(Items.COOKED_BEEF,64));
        
        if(relay){
            p.getInventory().setStack(3,net.minecraft.component.type.PotionContentsComponent.createStack(Items.POTION,net.minecraft.potion.Potions.STRONG_SWIFTNESS));
            for(int slot=9;slot<=17;slot++)p.getInventory().setStack(slot,net.minecraft.component.type.PotionContentsComponent.createStack(Items.SPLASH_POTION,net.minecraft.potion.Potions.STRONG_HEALING));
        }else{
            int speed=config.speeds,heal=config.heals;
            for(int slot=3;slot<36;slot++){if(slot==8)continue;
                if(speed>0){p.getInventory().setStack(slot,net.minecraft.component.type.PotionContentsComponent.createStack(Items.POTION,net.minecraft.potion.Potions.STRONG_SWIFTNESS));speed--;}
                else if(heal-->0)p.getInventory().setStack(slot,net.minecraft.component.type.PotionContentsComponent.createStack(Items.SPLASH_POTION,net.minecraft.potion.Potions.STRONG_HEALING));
            }
        }
        p.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));p.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.equipStack(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
        p.getItemCooldownManager().remove(PEARL_GROUP);GameSessions.selectSlot(p,0);p.currentScreenHandler.syncState();
    }
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING)return false;queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});
        selectedTeams.keySet().retainAll(queue);
        if(!canStartQueue()||!issue(s).equals("场地就绪")){stage=Stage.WAITING;return false;}
        for(var id:queue){var p=s.getPlayerManager().getPlayer(id);recover(p);if(hasRecovery(p)){stage=Stage.WAITING;return false;}p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);if(!GameSessions.write(recovery(p),GameSessions.capture(p))){stage=Stage.WAITING;return false;}}
        if(!PotArena.begin(s,world(s),block(config.corner1),block(config.corner2))){for(var id:queue){var p=s.getPlayerManager().getPlayer(id);if(p!=null)recover(p);}stage=Stage.WAITING;return false;}
        clearRelayRound();players.clear();players.addAll(queue);alive.clear();alive.addAll(queue);
        if(relay){roundTeams.putAll(selectedTeams);relayRecovery.addAll(players);for(int team=0;team<2;team++)active[team]=nextFighter(team);}
        queue.clear();selectedTeams.clear();round=UUID.randomUUID();stage=Stage.RUNNING;int i=0;
        for(var id:players){var p=s.getPlayerManager().getPlayer(id);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.extinguish();p.setGlowing(false);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);kit(p);
            if(relay){int team=roundTeams.get(id);assignTeam(p,team);teleport(p,team==0?config.spawn1:config.spawn2);if(!id.equals(active[team])){p.changeGameMode(GameMode.SPECTATOR);pausedVitals.put(id,new PausedVitals(p));}}
            else teleport(p,i++==0?config.spawn1:config.spawn2);
        }
        LobbyItems.spectateAudience(s,ID);LobbyItems.refresh(s);
        tell(s,relay?"Pot PVP 4v4 车轮战开始：每队依次出战，胜者保留全部战斗状态；换人保护三秒。":"Pot PVP 开始");return true;
    }
    public static void recover(ServerPlayerEntity p){if(!hasRecovery(p)||!p.isAlive())return;try{var n=GameSessions.read(recovery(p));p.stopUsingItem();p.getItemCooldownManager().remove(PEARL_GROUP);GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p)){Files.delete(recovery(p));relayRecovery.remove(p.getUuid());}}catch(Exception e){MWMod.LOGGER.error("Cannot recover pot duel player; backup retained",e);p.sendMessage(Text.literal("Pot PVP 赛前档案恢复失败，备份已保留。"),false);}}
    public static void leave(ServerPlayerEntity p){queue.remove(p.getUuid());selectedTeams.remove(p.getUuid());alive.remove(p.getUuid());players.remove(p.getUuid());pausedVitals.remove(p.getUuid());pendingRespawns.remove(p.getUuid());recover(p);PrivateSidebar.clear(p);resolve(p.getServer());}
    // Relay deaths are resolved together at the end of the tick, including simultaneous knockouts.
    private static void resolve(MinecraftServer s){if(!relay&&running()&&alive.size()<=1)finish(s,alive.stream().findFirst().orElse(null),"本局平局。");}
    private static UUID nextFighter(int team){return alive.stream().filter(id->roundTeams.getOrDefault(id,-1)==team).findFirst().orElse(null);}
    private static void clearRelayRound(){roundTeams.clear();active[0]=active[1]=null;relayGapTicks=0;pausedVitals.clear();}
    private static void assignTeam(ServerPlayerEntity p,int id){
        String name="pot_relay_"+(id==0?"red":"purple");var scoreboard=p.getScoreboard();var team=scoreboard.getTeam(name);
        if(team==null)team=scoreboard.addTeam(name);team.setColor(id==0?Formatting.RED:Formatting.LIGHT_PURPLE);team.setDisplayName(Text.literal(id==0?"红队":"紫队"));team.setFriendlyFireAllowed(false);scoreboard.addScoreHolderToTeam(p.getName().getString(),team);
    }
    private static void forfeit(ServerPlayerEntity p){
        alive.remove(p.getUuid());pausedVitals.remove(p.getUuid());p.stopUsingItem();p.getInventory().clear();p.changeGameMode(GameMode.SPECTATOR);p.setCameraEntity(p);p.sendMessage(Text.literal("已淘汰，正在旁观车轮战。"),false);
    }
    private static void respawnTick(MinecraftServer s){
        for(var entry:new ArrayList<>(pendingRespawns.entrySet())){
            var p=entry.getValue();
            // AFTER_RESPAWN fires before the network handler starts owning the new player object.
            if(s.getPlayerManager().getPlayer(entry.getKey())!=p||p.networkHandler.player!=p)continue;
            pendingRespawns.remove(entry.getKey());if(!member(p))continue;
            if(relay&&inRound(p)){p.stopUsingItem();p.getInventory().clear();p.changeGameMode(GameMode.SPECTATOR);p.setCameraEntity(p);teleport(p,selectedTeam(p)==1?config.spawn2:config.spawn1);}
            else if(hasRecovery(p)){recover(p);HubMenu.travel(p,GameSessions.HUB);}
        }
    }
    private static boolean prepareRelaySpawns(MinecraftServer s){
        for(var id:active){var p=id==null?null:s.getPlayerManager().getPlayer(id);if(p!=null&&alive.contains(id)&&member(p)&&p.isAlive()){p.stopUsingItem();pausedVitals.putIfAbsent(id,new PausedVitals(p));p.changeGameMode(GameMode.SPECTATOR);}}
        var w=world(s);return w!=null&&PotArena.prepareSpawn(w,config.spawn1.x,config.spawn1.y,config.spawn1.z)&&PotArena.prepareSpawn(w,config.spawn2.x,config.spawn2.y,config.spawn2.z);
    }
    private static void relayTick(MinecraftServer s){
        for(var id:new ArrayList<>(alive)){
            var p=s.getPlayerManager().getPlayer(id);
            if(p==null||!member(p)||!p.isAlive()){alive.remove(id);pausedVitals.remove(id);continue;}
            if(id.equals(active[0])||id.equals(active[1])){if(p.isSpectator()||!inside(p.getServerWorld(),p.getBlockPos()))forfeit(p);}
            else if(!p.isSpectator()){p.stopUsingItem();p.changeGameMode(GameMode.SPECTATOR);}
        }
        // Each side retains its complete roster until individual eliminations; reserves never fight together.
        int red=teamCount(0),purple=teamCount(1);
        if(red==0||purple==0){finish(s,null,red==0&&purple==0?"Pot PVP 车轮战平局。":"Pot PVP 车轮战："+(red>0?"红队":"紫队")+"获胜！");return;}
        boolean replacement=!alive.contains(active[0])||!alive.contains(active[1]);
        if(replacement){
            if(!prepareRelaySpawns(s)){finish(s,null,"出生点无法安全恢复，车轮战已取消。");return;}
            cleanupEntities(s);round=UUID.randomUUID();relayGapTicks=60;
            for(int team=0;team<2;team++){
                if(!alive.contains(active[team]))active[team]=nextFighter(team);
                var p=s.getPlayerManager().getPlayer(active[team]);
                var saved=pausedVitals.get(p.getUuid());if(saved!=null)saved.restore(p);
                p.stopUsingItem();p.setCameraEntity(p);p.changeGameMode(GameMode.SURVIVAL);teleport(p,team==0?config.spawn1:config.spawn2);
                pausedVitals.putIfAbsent(p.getUuid(),new PausedVitals(p));
            }
            tell(s,"下一场将在 3 秒后开始，胜者血量、效果和消耗物品保持。");
        }
        for(var entry:pausedVitals.entrySet()){
            var p=s.getPlayerManager().getPlayer(entry.getKey());
            if(p!=null&&p.isAlive()&&alive.contains(entry.getKey()))entry.getValue().restore(p);
        }
        if(relayGapTicks>0){
            for(int team=0;team<2;team++){var p=s.getPlayerManager().getPlayer(active[team]);if(p!=null)teleport(p,team==0?config.spawn1:config.spawn2);}
            if(!replacement&&relayGapTicks==1){
                if(!prepareRelaySpawns(s)){finish(s,null,"出生点无法安全恢复，车轮战已取消。");return;}
                for(int team=0;team<2;team++){var p=s.getPlayerManager().getPlayer(active[team]);pausedVitals.get(p.getUuid()).restore(p);p.changeGameMode(GameMode.SURVIVAL);teleport(p,team==0?config.spawn1:config.spawn2);}
                relayGapTicks=0;pausedVitals.remove(active[0]);pausedVitals.remove(active[1]);tell(s,"下一场开始！");
            }else if(!replacement)relayGapTicks--;
        }
    }
    private static void cleanupEntities(MinecraftServer s){var remove=new ArrayList<net.minecraft.entity.Entity>();for(var w:s.getWorlds())for(var e:w.iterateEntities())if(!(e instanceof ServerPlayerEntity)&&(e.getCommandTags().contains("mg_source_"+ID)||e.getCommandTags().contains("mg_item_"+ID)))remove.add(e);remove.forEach(net.minecraft.entity.Entity::discard);}
    private static void finish(MinecraftServer s,UUID winner,String reason){if(stage==Stage.RESETTING||stage==Stage.WAITING&&queue.isEmpty()&&players.isEmpty())return;stage=Stage.RESETTING;players.addAll(queue);queue.clear();selectedTeams.clear();var p=winner==null?null:s.getPlayerManager().getPlayer(winner);tell(s,p==null?reason:"Pot PVP 胜者："+p.getName().getString());cleanupEntities(s);
        for(var id:new ArrayList<>(players)){var player=s.getPlayerManager().getPlayer(id);if(player!=null){recover(player);PrivateSidebar.clear(player);if(player.isAlive())HubMenu.travel(player,GameSessions.HUB);}}
        alive.clear();players.clear();clearRelayRound();LobbyItems.finishAudience(s,ID);round=null;if(PotArena.active())PotArena.beginReset();else stage=Stage.WAITING;
    }
    public static void tick(MinecraftServer s){
        respawnTick(s);
        if(stage==Stage.RESETTING){if(PotArena.restore(s,512))stage=Stage.WAITING;return;}
        if(stage==Stage.WAITING&&s.getTicks()%20==0){int before=queue.size();queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});selectedTeams.keySet().retainAll(queue);if(before!=queue.size())LobbyItems.refresh(s);}
        if(running()){
            if(relay)relayTick(s);
            else{for(var id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);if(p==null||!fighter(p)||!inside(p.getServerWorld(),p.getBlockPos()))alive.remove(id);}resolve(s);}
        }
        if(s.getTicks()%20!=0)return;
        for(var p:s.getPlayerManager().getPlayerList())if(member(p)){
            var lines=new ArrayList<String>();
            if(relay)lines.add("模式：4v4 车轮战");
            lines.add("人数："+(running()?alive.size():queue.size())+" / "+(relay?8:2));
            if(relay){lines.add("红队："+teamCount(0)+"  紫队："+teamCount(1));if(selectedTeam(p)>=0)lines.add("你的队伍："+(selectedTeam(p)==0?"红":"紫"));if(running())lines.add(relayGapTicks>0?"换人保护："+((relayGapTicks+19)/20)+" 秒":fighter(p)?"正在出战":alive.contains(p.getUuid())?"等待出战":"已淘汰");}
            PrivateSidebar.showGame(p,"Pot PVP",lines);
        }
    }
}
