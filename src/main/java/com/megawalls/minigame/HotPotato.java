package com.megawalls.minigame;

import com.google.gson.*;
import com.megawalls.MWMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Configurable, server-authoritative hot potato. No explosive damage or shared match globals. */
public final class HotPotato {
    public enum Stage { WAITING, RUNNING, RESULT }
    public record Point(double x,double y,double z,float yaw,float pitch){}
    public static final class Config {
        public int[] roundSeconds={110,90,70,50,35};
        /** Read and retain legacy JSON only; matches now start on a player's request. */
        @Deprecated public int countdownSeconds=10;
        public int minPlayers=2,maxPlayers=10,passCooldownTicks=10;
        public int roundDecreaseSeconds=2,minimumRoundSeconds=5;
        public int finalSeconds=20,resultSeconds=5,runnerSpeed=1,holderSpeed=2,passKnockbackPercent=25;
        public int assistThresholdSeconds=20,assistDurationSeconds=6,assistSpeed=3;
        public boolean assistRevealOthers=true;
        public String dimension="minecraft:overworld";
        public Point corner1,corner2;
        public List<Point> spawns=new ArrayList<>();
    }
    private static Config config=new Config();
    private static Stage stage=Stage.WAITING;
    private static final Set<UUID> queue=new LinkedHashSet<>(),players=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static UUID holder;
    private static int deadline,passAfter,round;
    private static String resultText="";
    private static final Identifier SPEED_ID=Identifier.of("megawalls","hot_potato_speed");
    private static final HotPotatoRules.HoldAssist holdAssist=new HotPotatoRules.HoldAssist();
    private static final Map<UUID,Boolean> roundGlowBefore=new HashMap<>(),assistGlowBefore=new HashMap<>();
    private static final Map<UUID,Integer> punchAfter=new HashMap<>();
    private static boolean assistApplied;
    private static boolean validConfig=true;
    public static Stage stage(){return stage;}
    public static UUID holder(){return holder;}
    public static int aliveCount(){return alive.size();}
    public static boolean inRound(ServerPlayerEntity p){return (stage==Stage.RUNNING||stage==Stage.RESULT)&&players.contains(p.getUuid());}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(GameSessions.HOT);}
    public static boolean queued(ServerPlayerEntity p){return queue.contains(p.getUuid());}
    public static int queueSize(){return queue.size();}
    public static boolean canStartQueue(){return stage==Stage.WAITING&&queue.size()>=config.minPlayers&&queue.size()<=config.maxPlayers;}
    public static boolean canStartQueue(ServerPlayerEntity p){return member(p)&&queued(p)&&canStartQueue()&&HotPotatoRules.queueReadyToStart(queue.size(),config.minPlayers,issue(p.getServer()).equals("场地就绪"));}
    private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("hot-potato.json");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("hot-potato-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static boolean save(MinecraftServer s){try{var tmp=file(s).resolveSibling("hot-potato.json.tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(config));Files.move(tmp,file(s),StandardCopyOption.REPLACE_EXISTING);validConfig=true;return true;}catch(Exception e){MWMod.LOGGER.error("Cannot save hot potato config",e);return false;}}
    private static boolean values(Config c){return c!=null&&c.roundSeconds!=null&&c.roundSeconds.length==5&&java.util.Arrays.stream(c.roundSeconds).allMatch(v->v>=5&&v<=300)&&c.roundDecreaseSeconds>=0&&c.roundDecreaseSeconds<=30&&c.minimumRoundSeconds>=5&&c.minimumRoundSeconds<=60&&c.minimumRoundSeconds<=c.roundSeconds[0]&&c.minPlayers>=2&&c.maxPlayers>=c.minPlayers&&c.maxPlayers<=32&&c.passCooldownTicks>=1&&c.passCooldownTicks<=100&&c.finalSeconds>=c.minimumRoundSeconds&&c.finalSeconds<=60&&c.resultSeconds>=3&&c.resultSeconds<=15&&c.runnerSpeed>=0&&c.runnerSpeed<=3&&c.holderSpeed>=c.runnerSpeed&&c.holderSpeed<=4&&c.passKnockbackPercent>=0&&c.passKnockbackPercent<=100&&c.assistThresholdSeconds>=1&&c.assistThresholdSeconds<=300&&c.assistDurationSeconds>=1&&c.assistDurationSeconds<=30&&c.assistSpeed>=0&&c.assistSpeed<=6&&c.spawns!=null&&c.spawns.size()<=32&&c.dimension!=null&&Identifier.tryParse(c.dimension)!=null;}
    private static void load(MinecraftServer s){try{var next=Files.exists(file(s))?new Gson().fromJson(Files.readString(file(s)),Config.class):new Config();if(!values(next))throw new IllegalArgumentException("Invalid settings");config=next;validConfig=true;}catch(Exception e){validConfig=false;MWMod.LOGGER.error("Cannot read hot-potato.json",e);}}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{queue.clear();players.clear();alive.clear();holder=null;deadline=0;passAfter=0;round=0;resultText="";holdAssist.clear();assistApplied=false;roundGlowBefore.clear();assistGlowBefore.clear();punchAfter.clear();stage=Stage.WAITING;load(s);});
        ServerTickEvents.END_SERVER_TICK.register(HotPotato::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->leave(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{finish(s,null,"本局取消");returnToLobby(s);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){if(inRound(p)){p.getInventory().clear();p.changeGameMode(GameMode.SPECTATOR);teleport(p,config.spawns.getFirst());}else{recover(p);com.megawalls.menu.HubMenu.travel(p,GameSessions.HUB);}}});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(!(entity instanceof ServerPlayerEntity p)||!member(p))return true;
            if(source.isOf(DamageTypes.GENERIC_KILL))return true;
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayerEntity p&&member(p))eliminate(p,"死亡");});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(CommandManager.literal("hp")
            .then(CommandManager.literal("join").executes(c->{var p=c.getSource().getPlayerOrThrow();return (member(p)||com.megawalls.menu.HubMenu.travel(p,GameSessions.HOT))&&enroll(p)?1:0;}))
            .then(CommandManager.literal("leave").executes(c->com.megawalls.menu.HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0))
            .then(CommandManager.literal("start").executes(c->{var source=c.getSource();var player=source.getPlayer();return (player==null?start(source.getServer()):requestStart(player))?1:0;}))
            .then(CommandManager.literal("stop").executes(c->{finish(c.getSource().getServer(),null,"本局取消");returnToLobby(c.getSource().getServer());return 1;}))
            .then(CommandManager.literal("arena1").executes(c->point(c.getSource().getPlayerOrThrow(),1)))
            .then(CommandManager.literal("arena2").executes(c->point(c.getSource().getPlayerOrThrow(),2)))
            .then(CommandManager.literal("spawn")
                .then(CommandManager.literal("add").executes(c->point(c.getSource().getPlayerOrThrow(),0)))
                .then(CommandManager.literal("clear").executes(c->{if(stage!=Stage.WAITING)return 0;var old=new ArrayList<>(config.spawns);config.spawns.clear();if(!save(c.getSource().getServer())){config.spawns=old;return 0;}return 1;})))
            .then(CommandManager.literal("rule")
                .then(CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(CommandManager.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,300)).executes(c->{
                    if(stage!=Stage.WAITING)return 0;var old=new Gson().toJson(config);int v=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value");
                    switch(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")){case "round1"->config.roundSeconds[0]=v;case "round2"->config.roundSeconds[1]=v;case "round3"->config.roundSeconds[2]=v;case "round4"->config.roundSeconds[3]=v;case "round5"->config.roundSeconds[4]=v;case "decrease"->config.roundDecreaseSeconds=v;case "minimum"->config.minimumRoundSeconds=v;case "min"->config.minPlayers=v;case "max"->config.maxPlayers=v;case "pass"->config.passCooldownTicks=v;case "final"->config.finalSeconds=v;case "result"->config.resultSeconds=v;case "runnerSpeed"->config.runnerSpeed=v;case "holderSpeed"->config.holderSpeed=v;case "knockback"->config.passKnockbackPercent=v;case "assistAfter"->config.assistThresholdSeconds=v;case "assistDuration"->config.assistDurationSeconds=v;case "assistSpeed"->config.assistSpeed=v;case "assistReveal"->{if(v>1)return 0;config.assistRevealOthers=v==1;}default->{return 0;}}
                    if(!values(config)||!save(c.getSource().getServer())){config=new Gson().fromJson(old,Config.class);return 0;}c.getSource().sendFeedback(()->Text.literal("烫手山芋规则已保存。"),false);return 1;
                }))))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("烫手山芋："+stage+" / 排队 "+queue.size()+"/"+config.maxPlayers+" / 存活 "+alive.size()+" / "+issue(c.getSource().getServer())),false);return 1;}))));
    }
    private static int point(ServerPlayerEntity p,int which){
        if(stage!=Stage.WAITING){p.sendMessage(Text.literal("先停止当前对局再修改场地。"),false);return 0;}
        var point=new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());
        if((config.corner1!=null||config.corner2!=null||!config.spawns.isEmpty())&&!config.dimension.equals(p.getWorld().getRegistryKey().getValue().toString()))return 0;
        var old=new Gson().toJson(config);config.dimension=p.getWorld().getRegistryKey().getValue().toString();
        if(which==1)config.corner1=point;else if(which==2)config.corner2=point;else{if(config.spawns.size()>=32)return 0;config.spawns.add(point);}
        if(!save(p.getServer())){config=new Gson().fromJson(old,Config.class);return 0;}p.sendMessage(Text.literal("已保存烫手山芋场地点位。"),false);return 1;
    }
    public static boolean inside(net.minecraft.server.world.ServerWorld world,BlockPos pos){return config.corner1!=null&&config.corner2!=null&&world.getRegistryKey().getValue().toString().equals(config.dimension)
        &&pos.getX()>=Math.floor(Math.min(config.corner1.x,config.corner2.x))&&pos.getX()<=Math.floor(Math.max(config.corner1.x,config.corner2.x))
        &&pos.getY()>=Math.floor(Math.min(config.corner1.y,config.corner2.y))&&pos.getY()<=Math.floor(Math.max(config.corner1.y,config.corner2.y))
        &&pos.getZ()>=Math.floor(Math.min(config.corner1.z,config.corner2.z))&&pos.getZ()<=Math.floor(Math.max(config.corner1.z,config.corner2.z));}
    public static boolean overlaps(net.minecraft.server.world.ServerWorld w,BlockPos a,BlockPos b){
        if((stage!=Stage.RUNNING&&stage!=Stage.RESULT)||config.corner1==null||config.corner2==null||!w.getRegistryKey().getValue().toString().equals(config.dimension))return false;
        var x=BlockPos.ofFloored(config.corner1.x,config.corner1.y,config.corner1.z);var y=BlockPos.ofFloored(config.corner2.x,config.corner2.y,config.corner2.z);
        return Math.max(a.getX(),b.getX())>=Math.min(x.getX(),y.getX())&&Math.min(a.getX(),b.getX())<=Math.max(x.getX(),y.getX())
            &&Math.max(a.getY(),b.getY())>=Math.min(x.getY(),y.getY())&&Math.min(a.getY(),b.getY())<=Math.max(x.getY(),y.getY())
            &&Math.max(a.getZ(),b.getZ())>=Math.min(x.getZ(),y.getZ())&&Math.min(a.getZ(),b.getZ())<=Math.max(x.getZ(),y.getZ());
    }
    private static String issue(MinecraftServer s){
        if(!validConfig)return "配置无法读取";if(config.corner1==null||config.corner2==null||config.spawns.size()<2)return "需设置 arena1、arena2 和至少两个出生点";
        if(!validPoint(config.corner1)||!validPoint(config.corner2)||config.spawns.stream().anyMatch(pt->!validPoint(pt)))return "场地点位无效";
        var w=s.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,Identifier.of(config.dimension)));if(w==null)return "维度不存在";
        for(var pt:config.spawns){if(!Double.isFinite(pt.x)||!Double.isFinite(pt.y)||!Double.isFinite(pt.z)||!inside(w,BlockPos.ofFloored(pt.x,pt.y,pt.z)))return "出生点不在场地内";
            var pos=BlockPos.ofFloored(pt.x,pt.y,pt.z);w.getChunk(pos);if(!w.getWorldBorder().contains(pos)||!w.isSpaceEmpty(new Box(pt.x-.3,pt.y,pt.z-.3,pt.x+.3,pt.y+1.8,pt.z+.3)))return "出生点被占用或越界";}
        var a=BlockPos.ofFloored(config.corner1.x(),config.corner1.y(),config.corner1.z());var b=BlockPos.ofFloored(config.corner2.x(),config.corner2.y(),config.corner2.z());
        if(PotDuel.overlaps(w,a,b)||PotArena.overlaps(w,a,b))return "场地与进行中的 Pot PVP 重叠";
        if(DuelArena.overlaps(w,a,b))return "场地与进行中的 UHC PvP 重叠";
        if(BedArena.overlaps(w,a,b))return "场地与进行中的起床战争重叠";
        if(TntRun.overlaps(w,a,b))return "场地与进行中的 TNT 跑酷重叠";
        if(Spleef.overlaps(w,a,b))return "场地与进行中的决一死战重叠";
        return "场地就绪";
    }
    private static boolean validPoint(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984;}
    public static boolean canEnter(ServerPlayerEntity p){if(stage==Stage.RUNNING||stage==Stage.RESULT){p.sendMessage(Text.literal("烫手山芋本局进行中，请稍后加入。"),false);return false;}if(queue.size()>=config.maxPlayers&&!queue.contains(p.getUuid())){p.sendMessage(Text.literal("参赛席位已满 · "+queue.size()+"/"+config.maxPlayers),false);return false;}return true;}
    public static void join(ServerPlayerEntity p){enroll(p);}
    public static boolean enroll(ServerPlayerEntity p){
        if(!member(p)||!p.isAlive()||!canEnter(p))return false;
        if(!issue(p.getServer()).equals("场地就绪")){p.sendMessage(Text.literal("场地待设置"),true);return false;}
        queue.add(p.getUuid());p.sendMessage(Text.literal("已参加 · "+queue.size()+"/"+config.maxPlayers),true);LobbyItems.sync(p);return true;
    }
    public static boolean requestStart(ServerPlayerEntity p){
        if(!canStartQueue(p))return false;
        return start(p.getServer());
    }
    private static void tell(MinecraftServer s,String text){var audience=new HashSet<>(queue);audience.addAll(players);for(UUID id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(text),true);}}
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING)return false;
        queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});
        if(queue.size()<config.minPlayers||queue.size()>config.maxPlayers)return false;
        String problem=issue(s);if(!problem.equals("场地就绪")){tell(s,problem);stage=Stage.WAITING;return false;}
        // Prepare durable recovery for everyone before clearing any inventory.
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);if(hasRecovery(p)){recover(p);if(hasRecovery(p)){tell(s,"存在尚未恢复的赛前档案，未开始。");stage=Stage.WAITING;return false;}}}
        // Closing first returns the screen cursor stack to the inventory that will be backed up.
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);}
        var written=new ArrayList<ServerPlayerEntity>();
        for(UUID id:queue){var p=s.getPlayerManager().getPlayer(id);if(!GameSessions.write(recovery(p),GameSessions.capture(p))){
            for(var saved:written)try{Files.deleteIfExists(recovery(saved));}catch(Exception ex){MWMod.LOGGER.error("Cannot remove unused hot potato recovery",ex);}
            tell(s,"档案备份失败，未开始");stage=Stage.WAITING;return false;
        }written.add(p);}
        players.clear();players.addAll(queue);alive.clear();alive.addAll(queue);queue.clear();stage=Stage.RUNNING;round=0;roundGlowBefore.clear();assistGlowBefore.clear();punchAfter.clear();assistApplied=false;holdAssist.clear();
        for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);roundGlowBefore.put(id,p.isGlowingLocal());}
        int i=0;for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);p.getInventory().clear();p.clearStatusEffects();p.changeGameMode(GameMode.ADVENTURE);p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);teleport(p,config.spawns.get(i++%config.spawns.size()));}
        LobbyItems.refresh(s);nextRound(s);return true;
    }
    private static void teleport(ServerPlayerEntity p,Point pt){var world=p.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,Identifier.of(config.dimension)));p.teleport(world,pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    private static ItemStack token(){var stack=new ItemStack(Items.BAKED_POTATO);stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("烫手山芋 · 近战击中别人传出"));var n=new NbtCompound();n.putBoolean("mg_bound",true);stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);return stack;}
    private static void speed(ServerPlayerEntity p,boolean carrying){
        var attribute=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attribute==null)return;
        int level=carrying?config.holderSpeed:config.runnerSpeed;if(carrying&&holdAssist.active(p.getServer().getTicks()))level=Math.max(level,config.assistSpeed);
        attribute.removeModifier(SPEED_ID);if(level>0)attribute.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID,level*.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void clearSpeed(ServerPlayerEntity p){var attribute=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attribute!=null)attribute.removeModifier(SPEED_ID);}
    private static void restoreGlow(ServerPlayerEntity p){
        Boolean before=assistGlowBefore.remove(p.getUuid());if(before!=null)p.setGlowing(before);
        before=roundGlowBefore.remove(p.getUuid());if(before!=null)p.setGlowing(before);
    }
    private static void endAssist(MinecraftServer s){
        for(var entry:assistGlowBefore.entrySet()){var p=s.getPlayerManager().getPlayer(entry.getKey());if(p!=null&&member(p))p.setGlowing(entry.getValue()||entry.getKey().equals(holder));}
        assistGlowBefore.clear();assistApplied=false;
        var carrier=holder==null?null:s.getPlayerManager().getPlayer(holder);if(carrier!=null&&member(carrier)&&alive.contains(holder))speed(carrier,true);
    }
    private static void assist(MinecraftServer s){
        if(assistApplied&&!holdAssist.active(s.getTicks()))endAssist(s);
        if(stage!=Stage.RUNNING||holder==null||!alive.contains(holder)||!holdAssist.available(s.getTicks(),config.assistThresholdSeconds*20))return;
        var carrier=s.getPlayerManager().getPlayer(holder);if(carrier==null||!member(carrier)||!carrier.isAlive()||carrier.isSpectator())return;
        if(!holdAssist.activate(s.getTicks(),config.assistThresholdSeconds*20,config.assistDurationSeconds*20))return;
        assistApplied=true;speed(carrier,true);
        if(config.assistRevealOthers)for(UUID id:alive){var p=s.getPlayerManager().getPlayer(id);if(p==null||id.equals(holder)||!member(p)||!p.isAlive()||p.isSpectator()||p.getServerWorld()!=carrier.getServerWorld())continue;
            // Snapshot only Entity's raw flag; LivingEntity adds potion glow without changing it.
            assistGlowBefore.put(id,p.isGlowingLocal());p.setGlowing(true);
        }
        carrier.sendMessage(Text.literal("追踪加速 · "+config.assistDurationSeconds+" 秒").formatted(Formatting.GOLD),true);
        carrier.getServerWorld().playSound(null,carrier.getBlockPos(),SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,SoundCategory.PLAYERS,.6f,1.8f);
    }
    private static void assign(MinecraftServer s,UUID next){
        holdAssist.clear();endAssist(s);holdAssist.assign(s.getTicks());
        for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);if(p==null)continue;for(int i=0;i<p.getInventory().size();i++){var data=p.getInventory().getStack(i).get(DataComponentTypes.CUSTOM_DATA);if(data!=null&&data.copyNbt().getBoolean("mg_bound"))p.getInventory().setStack(i,ItemStack.EMPTY);}if(alive.contains(id)){p.setGlowing(roundGlowBefore.getOrDefault(id,false));speed(p,id.equals(next));}p.currentScreenHandler.syncState();}
        holder=next;var p=s.getPlayerManager().getPlayer(next);if(p!=null){p.getInventory().setStack(0,token());GameSessions.selectSlot(p,0);p.setGlowing(true);p.currentScreenHandler.syncState();}
        passAfter=s.getTicks()+config.passCooldownTicks;
    }
    private static void nextRound(MinecraftServer s){
        if(alive.size()<=1){finish(s,alive.stream().findFirst().orElse(null),"对局结束");return;}
        round++;int seconds=HotPotatoRules.secondsForRound(config.roundSeconds,round,alive.size(),config.finalSeconds,config.roundDecreaseSeconds,config.minimumRoundSeconds);deadline=s.getTicks()+seconds*20;
        // The final duel starts from the configured spawn ring instead of hiding on opposite map edges.
        if(alive.size()==2){int i=0;for(UUID id:alive){var p=s.getPlayerManager().getPlayer(id);if(p!=null)teleport(p,config.spawns.get(i++%config.spawns.size()));}}
        var ids=new ArrayList<>(alive);assign(s,ids.get(s.getOverworld().random.nextInt(ids.size())));
        tell(s,(alive.size()==2?"最终对决":"第 "+round+" 轮")+" · "+seconds+" 秒");
    }
    public static boolean pass(ServerPlayerEntity from,ServerPlayerEntity to){
        var s=from.getServer();if(stage!=Stage.RUNNING)return false;
        if(s.getTicks()>=deadline){expire(s);return false;}
        if(!from.getUuid().equals(holder)||!alive.contains(from.getUuid())||!alive.contains(to.getUuid())||from==to||!HotPotatoRules.canPassAt(s.getTicks(),passAfter,deadline)
            ||!member(from)||!member(to)||!from.isAlive()||!to.isAlive()||from.isSpectator()||to.isSpectator()||!GameSessions.same(from,to)||from.squaredDistanceTo(to)>9
            ||!inside(from.getServerWorld(),from.getBlockPos())||!inside(to.getServerWorld(),to.getBlockPos())||!from.canInteractWithEntity(to,0)||!from.canSee(to))return false;
        assign(s,to.getUuid());from.sendMessage(Text.literal("已传出"),true);to.sendMessage(Text.literal("山芋在你手上 · 击中别人传出").formatted(Formatting.RED),true);
        from.getServerWorld().playSound(null,to.getBlockPos(),SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,SoundCategory.PLAYERS,.7f,1.2f);
        if(config.passKnockbackPercent>0){var delta=to.getPos().subtract(from.getPos());var horizontal=new Vec3d(delta.x,0,delta.z);if(horizontal.lengthSquared()>.0001){var push=horizontal.normalize().multiply(config.passKnockbackPercent/100.0);to.addVelocity(push.x,.08,push.z);to.velocityModified=true;to.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(to));}}
        return true;
    }
    /** Melee is a real server hit; only the potato's fuse eliminates players. */
    public static boolean attack(ServerPlayerEntity from,ServerPlayerEntity to){
        var s=from.getServer();if(stage!=Stage.RUNNING)return false;
        if(s.getTicks()>=deadline){expire(s);return false;}
        if(from==to||!alive.contains(from.getUuid())||!alive.contains(to.getUuid())||!member(from)||!member(to)
            ||!from.isAlive()||!to.isAlive()||from.isSpectator()||to.isSpectator()||!GameSessions.same(from,to)
            ||!inside(from.getServerWorld(),from.getBlockPos())||!inside(to.getServerWorld(),to.getBlockPos())
            ||from.squaredDistanceTo(to)>9||!from.canInteractWithEntity(to,0)||!from.canSee(to))return false;
        boolean carrying=from.getUuid().equals(holder);var stack=from.getMainHandStack();
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        boolean potato=stack.isOf(Items.BAKED_POTATO)&&data!=null&&data.copyNbt().getBoolean("mg_bound");
        if(!stack.isEmpty()&&!(carrying&&potato))return false;
        if(carrying){if(!pass(from,to))return false;}
        else if(s.getTicks()<punchAfter.getOrDefault(to.getUuid(),0))return false;
        punchAfter.put(to.getUuid(),s.getTicks()+10);
        var delta=from.getPos().subtract(to.getPos());
        if(!carrying){to.takeKnockback(.4,delta.x,delta.z);to.velocityModified=true;to.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(to));}
        // Vanilla's damage notification drives red flash, hurt sound and screen tilt on
        // the actual controlled client too, without inventing a health-damage death rule.
        to.animateDamage(0);to.tiltScreen(delta.x,delta.z);
        to.getServerWorld().sendEntityDamage(to,from.getDamageSources().playerAttack(from));
        return true;
    }
    private static void eliminate(ServerPlayerEntity p,String reason){
        eliminate(p.getServer(),p.getUuid(),reason,false);
    }
    private static void eliminate(MinecraftServer s,UUID id,String reason,boolean fuseExpired){
        if(stage!=Stage.RUNNING||!alive.remove(id))return;
        punchAfter.remove(id);
        boolean wasHolder=id.equals(holder);var p=s.getPlayerManager().getPlayer(id);
        if(wasHolder){holdAssist.clear();endAssist(s);}
        if(p!=null){clearSpeed(p);restoreGlow(p);
            if(fuseExpired){p.getServerWorld().spawnParticles(ParticleTypes.EXPLOSION,p.getX(),p.getY()+1,p.getZ(),1,0,0,0,0);p.getServerWorld().playSound(null,p.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,.8f,1.1f);}
            if(p.isAlive()){p.getInventory().clear();p.changeGameMode(GameMode.SPECTATOR);teleport(p,config.spawns.getFirst());}p.sendMessage(Text.literal("已淘汰 · "+reason),true);
        }
        if(HotPotatoRules.afterDeparture(alive.size(),wasHolder)==HotPotatoRules.Departure.FINISH){finish(s,alive.stream().findFirst().orElse(null),"本局结束");return;}
        if(fuseExpired){nextRound(s);return;}
        if(wasHolder){var ids=new ArrayList<>(alive);assign(s,ids.get(s.getOverworld().random.nextInt(ids.size())));tell(s,"持有者离开 · 倒计时继续");}
    }
    private static void expire(MinecraftServer s){
        if(stage==Stage.RUNNING&&s.getTicks()>=deadline){if(holder!=null&&alive.contains(holder))eliminate(s,holder,"倒计时归零",true);else finish(s,null,"本局取消");}
    }
    public static void recover(ServerPlayerEntity p){
        clearSpeed(p);restoreGlow(p);
        if(!Files.exists(recovery(p))||!p.isAlive())return;
        try{var n=GameSessions.read(recovery(p));GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p))Files.delete(recovery(p));}
        catch(Exception e){MWMod.LOGGER.error("Cannot recover hot potato inventory for {}",p.getUuid(),e);p.sendMessage(Text.literal("烫手山芋档案恢复失败，请联系管理员；备份文件已保留。"),false);}
    }
    public static void leave(ServerPlayerEntity p){
        queue.remove(p.getUuid());players.remove(p.getUuid());
        punchAfter.remove(p.getUuid());
        if(stage==Stage.RUNNING){expire(p.getServer());eliminate(p,"离开或断线");}
        clearSpeed(p);restoreGlow(p);recover(p);PrivateSidebar.clear(p);
    }
    private static void finish(MinecraftServer s,UUID winner,String reason){
        if(stage==Stage.RESULT)return;
        if(stage==Stage.WAITING&&players.isEmpty()&&queue.isEmpty())return;
        players.addAll(queue);stage=Stage.RESULT;
        resultText=winner==null?reason:"胜者："+(s.getPlayerManager().getPlayer(winner)==null?winner:s.getPlayerManager().getPlayer(winner).getName().getString());tell(s,resultText);
        holdAssist.clear();endAssist(s);
        for(UUID id:new ArrayList<>(players)){var p=s.getPlayerManager().getPlayer(id);if(p!=null){clearSpeed(p);restoreGlow(p);if(hasRecovery(p)){p.getInventory().clear();if(p.isAlive())p.changeGameMode(GameMode.SPECTATOR);}}}
        holder=null;passAfter=0;punchAfter.clear();deadline=s.getTicks()+config.resultSeconds*20;queue.clear();LobbyItems.refresh(s);
    }
    private static void returnToLobby(MinecraftServer s){
        holdAssist.clear();endAssist(s);var finished=new ArrayList<>(players);players.clear();queue.clear();alive.clear();holder=null;passAfter=0;deadline=0;round=0;stage=Stage.WAITING;
        for(UUID id:finished){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&member(p)){recover(p);PrivateSidebar.clear(p);if(p.isAlive())com.megawalls.menu.HubMenu.travel(p,GameSessions.HUB);}}
        roundGlowBefore.clear();assistGlowBefore.clear();punchAfter.clear();assistApplied=false;
    }
    public static void tick(MinecraftServer s){
        if(stage==Stage.RESULT&&s.getTicks()>=deadline){returnToLobby(s);return;}
        if(stage==Stage.WAITING&&s.getTicks()%20==0)queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});
        if(stage==Stage.RUNNING){
            expire(s);
            for(UUID id:players){var p=s.getPlayerManager().getPlayer(id);if(p!=null&&!alive.contains(id)&&p.isAlive()&&!inside(p.getServerWorld(),p.getBlockPos()))teleport(p,config.spawns.getFirst());}
            if(stage==Stage.RUNNING)for(UUID id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);if(p==null)eliminate(s,id,"离开或断线",false);else if(!member(p)||!p.isAlive()||p.isSpectator()||!inside(p.getServerWorld(),p.getBlockPos()))eliminate(p,"离开场地");if(stage!=Stage.RUNNING)break;}
            if(stage==Stage.RUNNING)assist(s);
        }
        if(s.getTicks()%20!=0)return;
        var audience=new HashSet<>(queue);audience.addAll(players);for(var p:s.getPlayerManager().getPlayerList())if(member(p))audience.add(p.getUuid());
        for(UUID id:audience){var p=s.getPlayerManager().getPlayer(id);if(p==null||!member(p))continue;
            String name=holder==null?"无":s.getPlayerManager().getPlayer(holder)==null?"离线":s.getPlayerManager().getPlayer(holder).getName().getString();
            int seconds=HotPotatoRules.remainingSeconds(s.getTicks(),deadline);
            if(stage==Stage.RESULT)PrivateSidebar.show(p,"烫手山芋",List.of(resultText,seconds+" 秒后返回大厅"));
            else if(stage==Stage.RUNNING){
                PrivateSidebar.show(p,"烫手山芋",List.of("第 "+round+" 轮 · "+seconds+" 秒","存活："+alive.size(),"持有者："+name));
                if(alive.contains(id)&&id.equals(holder)){String hint=holdAssist.active(s.getTicks())?"追踪加速 · "+HotPotatoRules.remainingSeconds(s.getTicks(),holdAssist.expiresAt())+" 秒":"山芋在你手上 · "+seconds+" 秒";p.sendMessage(Text.literal(hint).formatted(holdAssist.active(s.getTicks())?Formatting.GOLD:Formatting.RED),true);if(seconds<=5&&seconds>0)p.getServerWorld().playSound(null,p.getBlockPos(),SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,SoundCategory.PLAYERS,.5f,.6f+seconds*.12f);}
            }else PrivateSidebar.show(p,"烫手山芋",List.of(canStartQueue(p)?"右键开始游戏":"等待玩家","参加："+queue.size()+" / "+config.maxPlayers));
        }
    }
}
