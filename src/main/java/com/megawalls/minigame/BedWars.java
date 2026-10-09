package com.megawalls.minigame;

import com.google.gson.*;
import com.megawalls.MWMod;
import com.megawalls.menu.HubMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import java.nio.file.*;
import java.util.*;

/** A separate server-authoritative copper/iron/gold match, never a Mega Walls team. */
public final class BedWars {
    public static final String ID="bedwars";
    public static final String[] TEAMS={"red","blue","green","yellow"};
    private static final String[] NAMES={"红队","蓝队","绿队","黄队"};
    private static final Formatting[] COLORS={Formatting.RED,Formatting.BLUE,Formatting.GREEN,Formatting.YELLOW};
    public enum Stage {WAITING,COUNTDOWN,RUNNING,RESETTING}
    public record Point(double x,double y,double z,float yaw,float pitch){public BlockPos pos(){return BlockPos.ofFloored(x,y,z);}}
    public static class Base {public Point spawn,bed,shop;}
    public record Generator(String type,Point point){}
    public static class Config {
        public String dimension="minecraft:overworld";public Point corner1,corner2;
        public Map<String,Base> bases=new LinkedHashMap<>();public List<Generator> generators=new ArrayList<>();
        public int teams=4,minPlayers=2,maxPerTeam=4,countdownSeconds=10,respawnSeconds=5,shieldTicks=40,maxSeconds=3600,bedLossSeconds=1800,bronzeTicks=20,ironTicks=200,goldTicks=400;
        public Config(){for(var t:TEAMS)bases.put(t,new Base());}
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static boolean collectingDrops;
    private static Config config=new Config();private static boolean valid=true;private static Stage stage=Stage.WAITING;
    private static final Set<UUID> queue=new LinkedHashSet<>(),alive=new LinkedHashSet<>();
    private static final Map<UUID,String> roster=new LinkedHashMap<>(),chosen=new HashMap<>();
    private static final Map<UUID,Integer> lives=new HashMap<>();
    private static final Map<UUID,Integer> respawns=new HashMap<>(),shields=new HashMap<>();
    private static final Set<UUID> reposition=new HashSet<>();
    private static final Set<String> beds=new HashSet<>();private static String round="";private static int started;private static boolean deathmatch;
    public static Stage stage(){return stage;}public static String roundId(){return round;}
    public static boolean deathmatch(){return stage==Stage.RUNNING&&deathmatch;}
    public static boolean member(ServerPlayerEntity p){return GameSessions.game(p).equals(ID);}
    public static boolean inRound(ServerPlayerEntity p){return stage==Stage.RUNNING&&roster.containsKey(p.getUuid());}
    public static boolean fighter(ServerPlayerEntity p){return inRound(p)&&member(p)&&alive.contains(p.getUuid())&&!respawns.containsKey(p.getUuid())&&p.isAlive()&&!p.isSpectator()&&BedArena.contains(p.getServerWorld(),p.getBlockPos());}
    public static String team(ServerPlayerEntity p){return roster.get(p.getUuid());}
    public static int color(ServerPlayerEntity p){int i=Arrays.asList(TEAMS).indexOf(team(p));return new int[]{0xB02E26,0x3C44AA,0x5E7C16,0xFED83D}[Math.max(0,i)];}
    public static boolean sameTeam(ServerPlayerEntity a,ServerPlayerEntity b){return team(a)!=null&&team(a).equals(team(b));}
    private static Path file(MinecraftServer s){return s.getSavePath(WorldSavePath.ROOT).resolve("bedwars.json");}
    private static Path recovery(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("bedwars-recovery").resolve(p.getUuidAsString()+".dat");}
    public static boolean hasRecovery(ServerPlayerEntity p){return Files.exists(recovery(p));}
    private static ServerWorld world(MinecraftServer s){return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(config.dimension)));}
    private static Point point(ServerPlayerEntity p){return new Point(p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());}
    private static boolean finite(Point p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<29999984&&Math.abs(p.z)<29999984&&Math.abs(p.y)<2048&&Float.isFinite(p.yaw)&&Float.isFinite(p.pitch);}
    private static boolean values(Config c){return c!=null&&Identifier.tryParse(c.dimension)!=null&&(c.teams==2||c.teams==4)&&c.bases!=null&&Arrays.stream(TEAMS).allMatch(t->c.bases.get(t)!=null)&&c.generators!=null&&c.generators.size()<=64&&c.generators.stream().allMatch(g->g!=null&&Set.of("bronze","iron","gold").contains(g.type)&&finite(g.point))&&c.minPlayers>=2&&c.minPlayers<=32&&c.maxPerTeam>=1&&c.maxPerTeam<=8&&c.minPlayers<=c.teams*c.maxPerTeam&&c.respawnSeconds>=1&&c.respawnSeconds<=30&&c.shieldTicks>=0&&c.shieldTicks<=200&&c.maxSeconds>=60&&c.maxSeconds<=7200&&c.bedLossSeconds>=60&&c.bedLossSeconds<c.maxSeconds&&c.bronzeTicks>=1&&c.ironTicks>=1&&c.goldTicks>=1&&c.bronzeTicks<=12000&&c.ironTicks<=12000&&c.goldTicks<=12000;}
    private static void load(MinecraftServer s){try{var next=Files.exists(file(s))?JSON.fromJson(Files.readString(file(s)),Config.class):new Config();if(!values(next))throw new IllegalArgumentException("Invalid bedwars settings");config=next;valid=true;}catch(Exception e){valid=false;MWMod.LOGGER.error("Invalid bedwars.json",e);}}
    private static int save(MinecraftServer s,String old){try{if(!values(config))throw new IllegalArgumentException("Invalid bedwars settings");var tmp=file(s).resolveSibling("bedwars.json.tmp");Files.writeString(tmp,JSON.toJson(config));Files.move(tmp,file(s),StandardCopyOption.REPLACE_EXISTING);valid=true;return 1;}catch(Exception e){config=JSON.fromJson(old,Config.class);MWMod.LOGGER.error("Cannot save bedwars config",e);return 0;}}
    private static boolean configuredInside(Point p){if(!finite(p)||!finite(config.corner1)||!finite(config.corner2))return false;var a=config.corner1.pos();var b=config.corner2.pos();var q=p.pos();return q.getX()>=Math.min(a.getX(),b.getX())&&q.getX()<=Math.max(a.getX(),b.getX())&&q.getY()>=Math.min(a.getY(),b.getY())&&q.getY()<=Math.max(a.getY(),b.getY())&&q.getZ()>=Math.min(a.getZ(),b.getZ())&&q.getZ()<=Math.max(a.getZ(),b.getZ());}
    public static boolean mappedAt(ServerWorld w,BlockPos p){return finite(config.corner1)&&finite(config.corner2)&&w.getRegistryKey().getValue().toString().equals(config.dimension)
        &&p.getX()>=Math.min(config.corner1.pos().getX(),config.corner2.pos().getX())&&p.getX()<=Math.max(config.corner1.pos().getX(),config.corner2.pos().getX())
        &&p.getY()>=Math.min(config.corner1.pos().getY(),config.corner2.pos().getY())&&p.getY()<=Math.max(config.corner1.pos().getY(),config.corner2.pos().getY())
        &&p.getZ()>=Math.min(config.corner1.pos().getZ(),config.corner2.pos().getZ())&&p.getZ()<=Math.max(config.corner1.pos().getZ(),config.corner2.pos().getZ());}
    private static boolean editor(ServerPlayerEntity p){return stage==Stage.WAITING&&!p.isSpectator();}
    public static String issue(MinecraftServer s){
        if(!valid||!BedShop.valid())return "配置或商店无法读取";
        if(!finite(config.corner1)||!finite(config.corner2))return "请设置 arena1、arena2";
        var w=world(s);if(w==null)return "维度不存在";var a=config.corner1.pos();var b=config.corner2.pos();
        if(Math.abs(a.getX()-b.getX())>1023||Math.abs(a.getZ()-b.getZ())>1023||w.isOutOfHeightLimit(a)||w.isOutOfHeightLimit(b))return "场地单边上限 1024 格，Y 必须在世界高度内";
        if(PotDuel.overlaps(w,a,b)||TntRun.overlaps(w,a,b)||HotPotato.overlaps(w,a,b)||Spleef.overlaps(w,a,b)||DuelArena.overlaps(w,a,b))return "与其它正在运行的场地重叠";
        var positions=new HashSet<BlockPos>();
        for(int i=0;i<config.teams;i++){var base=config.bases.get(TEAMS[i]);
            if(!configuredInside(base.spawn)||!configuredInside(base.bed)||!configuredInside(base.shop))return NAMES[i]+" 缺少范围内的 spawn、bed 或 shop";
            for(var pt:List.of(base.spawn,base.bed,base.shop)){w.getChunk(pt.pos());if(com.megawalls.game.RegionProtection.contains(w,pt.pos())||DuelArena.contains(w,pt.pos()))return "点位与其它游戏保护区域重叠";}
            var foot=bedFoot(w,base.bed.pos());if(foot==null||!positions.add(foot)||!configuredInside(new Point(bedOther(w,foot).getX(),bedOther(w,foot).getY(),bedOther(w,foot).getZ(),0,0)))return NAMES[i]+" 床缺失、重复或床另一半越界";
            if(w.getBlockState(base.spawn.pos().down()).isAir()||!w.isSpaceEmpty(new Box(base.spawn.x-.3,base.spawn.y,base.spawn.z-.3,base.spawn.x+.3,base.spawn.y+1.8,base.spawn.z+.3)))return NAMES[i]+" 出生点不安全";
        }
        if(!config.generators.stream().map(Generator::type).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("bronze","iron","gold")))return "至少各设置一个铜、铁、金资源点";
        for(var g:config.generators)if(!configuredInside(g.point))return "资源点越界";
        return "场地就绪";
    }
    private static BlockPos bedFoot(ServerWorld w,BlockPos p){var st=w.getBlockState(p);if(!(st.getBlock() instanceof BedBlock))return null;var foot=st.get(BedBlock.PART)==BedPart.FOOT?p:p.offset(st.get(BedBlock.FACING).getOpposite());var other=foot.offset(st.get(BedBlock.FACING));var f=w.getBlockState(foot);var h=w.getBlockState(other);return f.isOf(st.getBlock())&&h.isOf(st.getBlock())&&f.get(BedBlock.PART)==BedPart.FOOT&&h.get(BedBlock.PART)==BedPart.HEAD&&f.get(BedBlock.FACING)==h.get(BedBlock.FACING)?foot:null;}
    private static BlockPos bedOther(ServerWorld w,BlockPos foot){return foot.offset(w.getBlockState(foot).get(BedBlock.FACING));}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{lives.clear();reposition.clear();queue.clear();alive.clear();roster.clear();chosen.clear();respawns.clear();shields.clear();beds.clear();round="";deathmatch=false;stage=Stage.WAITING;BedSpecial.clear();BedArena.clear();load(s);BedShop.load(s);if(BedArena.exists(s)){stage=Stage.RESETTING;BedArena.load(s);}clearEntities(s);});
        ServerTickEvents.END_SERVER_TICK.register(BedWars::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{finish(s,"服务器关闭，本局取消。");if(stage==Stage.RESETTING)BedArena.restore(s,Integer.MAX_VALUE);});
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,keep)->{if(member(p)){p.changeGameMode(GameMode.SPECTATOR);reposition.add(p.getUuid());}});
        ServerLivingEntityEvents.AFTER_DEATH.register((e,source)->{if(e instanceof ServerPlayerEntity p&&inRound(p)&&alive.contains(p.getUuid())){shields.remove(p.getUuid());BedSpecial.forget(p.getUuid());if(beds.contains(team(p))){respawns.put(p.getUuid(),p.getServer().getTicks()+config.respawnSeconds*20);p.sendMessage(Text.literal(config.respawnSeconds+" 秒后重生"),false);}else{alive.remove(p.getUuid());tell(p.getServer(),p.getName().getString()+" 最终死亡！");}}});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,source,amount)->{
            if(!(e instanceof ServerPlayerEntity p)||!member(p))return true;
            if(source.isOf(DamageTypes.GENERIC_KILL))return true;
            if(!fighter(p)||p.getServer().getTicks()<shields.getOrDefault(p.getUuid(),0))return false;
            var attacker=Isolation.owner(source.getAttacker());if(attacker!=null){if(!fighter(attacker)||attacker!=p&&sameTeam(attacker,p))return false;shields.remove(attacker.getUuid());}
            BedSpecial.damaged(p);return true;
        });
        UseEntityCallback.EVENT.register((p,w,hand,e,hit)->{if(!(p instanceof ServerPlayerEntity sp)||editor(sp))return ActionResult.PASS;if(e.getCommandTags().contains("bw_shop")){if(hand==Hand.MAIN_HAND&&e instanceof VillagerEntity v)BedShop.open(sp,v);return ActionResult.SUCCESS;}return member(sp)?ActionResult.FAIL:ActionResult.PASS;});
        AttackEntityCallback.EVENT.register((p,w,hand,e,hit)->{if(e.getCommandTags().contains("bw_shop"))return ActionResult.FAIL;if(p instanceof ServerPlayerEntity sp&&member(sp)){if(e instanceof ServerPlayerEntity target&&fighter(sp)&&fighter(target)&&!sameTeam(sp,target)){shields.remove(sp.getUuid());return ActionResult.PASS;}return ActionResult.FAIL;}return ActionResult.PASS;});
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,state,be)->{if(!(p instanceof ServerPlayerEntity sp))return true;if(member(sp)&&!editor(sp)){breakBlock(sp,pos);return false;}return !mappedAt(sp.getServerWorld(),pos)||editor(sp);});
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(!(p instanceof ServerPlayerEntity sp)||p.getStackInHand(hand).isOf(Items.NETHER_STAR))return ActionResult.PASS;
            if(editor(sp))return ActionResult.PASS;
            if(!member(sp))return (mappedAt(sp.getServerWorld(),hit.getBlockPos())||mappedAt(sp.getServerWorld(),hit.getBlockPos().offset(hit.getSide())))&&!editor(sp)?ActionResult.FAIL:ActionResult.PASS;
            if(!fighter(sp)||hand!=Hand.MAIN_HAND)return ActionResult.FAIL;
            if(!(p.getStackInHand(hand).getItem() instanceof BlockItem)&&!BedArena.contains(sp.getServerWorld(),hit.getBlockPos()))return ActionResult.FAIL;
            var stack=p.getStackInHand(hand);var special=BedSpecial.use(sp,hand,hit);if(special!=ActionResult.PASS)return special;
            var state=w.getBlockState(hit.getBlockPos());if(state.getBlock() instanceof BedBlock&&!(stack.getItem() instanceof BlockItem&&p.isSneaking()))return ActionResult.FAIL;
            if((state.getBlock() instanceof ChestBlock||state.getBlock() instanceof EnderChestBlock)&&!p.isSneaking()){
                if(!BedArena.capture(sp.getServerWorld(),hit.getBlockPos()))return ActionResult.FAIL;
                for(var d:Direction.Type.HORIZONTAL){var q=hit.getBlockPos().offset(d);if(w.getBlockState(q).getBlock() instanceof ChestBlock&&!BedArena.capture(sp.getServerWorld(),q))return ActionResult.FAIL;}return ActionResult.PASS;
            }
            if(stack.getItem() instanceof BlockItem block){var context=new ItemPlacementContext(sp,hand,stack,hit);var target=context.getBlockPos();var second=block.getBlock() instanceof BedBlock?target.offset(p.getHorizontalFacing()):block.getBlock() instanceof DoorBlock||block.getBlock() instanceof TallPlantBlock?target.up():null;if(!BedArena.contains(sp.getServerWorld(),target)||second!=null&&!BedArena.contains(sp.getServerWorld(),second))return ActionResult.FAIL;return BedArena.authorized(()->block.place(context));}
            if(stack.isOf(Items.FLINT_AND_STEEL)){var q=hit.getBlockPos().offset(hit.getSide());if(w.isAir(q)&&BedArena.set(sp.getServerWorld(),q,Blocks.FIRE.getDefaultState())){stack.damage(1,sp,EquipmentSlot.MAINHAND);return ActionResult.SUCCESS;}return ActionResult.FAIL;}
            if(state.hasBlockEntity()||stack.getItem() instanceof BucketItem)return ActionResult.FAIL;return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((p,w,hand)->{var stack=p.getStackInHand(hand);if(!(p instanceof ServerPlayerEntity sp)||editor(sp)||!member(sp)||stack.isOf(Items.NETHER_STAR))return TypedActionResult.pass(stack);if(!fighter(sp)||hand!=Hand.MAIN_HAND||stack.getItem() instanceof BucketItem)return TypedActionResult.fail(stack);var result=BedSpecial.use(sp,hand,null);return result==ActionResult.PASS?TypedActionResult.pass(stack):result==ActionResult.SUCCESS?TypedActionResult.success(stack):TypedActionResult.fail(stack);});
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{var p=Isolation.owner(e);if(!(e instanceof ServerPlayerEntity)&&p!=null&&fighter(p)&&e.getCommandTags().stream().noneMatch(t->t.startsWith("bw_round_")))stamp(e);});
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(collectingDrops&&stage==Stage.RUNNING&&e instanceof ItemEntity item&&BedArena.contains(w,e.getBlockPos())&&e.getCommandTags().stream().noneMatch(t->t.startsWith("mg_item_")||t.startsWith("mg_source_"))){var n=item.getStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();if(!n.contains("mg_item_game")||n.getString("mg_item_game").equals(ID))stamp(e);}});
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(!(e instanceof ServerPlayerEntity)&&e.getCommandTags().contains("mg_source_"+ID)&&!current(e))e.discard();});
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{
            if(!(e instanceof net.minecraft.entity.projectile.PersistentProjectileEntity arrow)||!(w instanceof ServerWorld world))return;
            if(!deathmatch()||!(arrow.getOwner() instanceof ServerPlayerEntity owner)||!fighter(owner)||!current(arrow)
                ||!owner.getMainHandStack().isOf(Items.BOW)&&!owner.getOffHandStack().isOf(Items.BOW))return;
            BedShop.enableWitherBows(owner);
            var skull=new BedWitherSkull(net.minecraft.entity.EntityType.WITHER_SKULL,world);
            skull.setOwner(owner);skull.refreshPositionAndAngles(arrow.getX(),arrow.getY(),arrow.getZ(),arrow.getYaw(),arrow.getPitch());skull.setVelocity(arrow.getVelocity());
            stamp(skull);arrow.discard();world.spawnEntity(skull);
        });
        commands();
    }
    private static void commands(){CommandRegistrationCallback.EVENT.register((d,r,e)->{
        var root=CommandManager.literal("bw").executes(c->{c.getSource().sendFeedback(()->Text.literal("起床战争 /bw join | team <颜色/auto> | leave | status；设置：arena1/arena2、spawn/bed/shop <颜色>、generator、rule、start/stop/reset、shopreload"),false);return 1;});
        root.then(CommandManager.literal("join").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),ID)?1:0));
        root.then(CommandManager.literal("leave").executes(c->HubMenu.travel(c.getSource().getPlayerOrThrow(),GameSessions.HUB)?1:0));
        root.then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("起床战争 "+stage+" / 排队 "+queue.size()+" / 存活 "+alive.size()+" / "+(stage==Stage.RESETTING?(BedArena.failed()?"恢复失败，检查日志后 /bw reset":"恢复中"):stage==Stage.RUNNING?"比赛中":issue(c.getSource().getServer()))),false);return 1;}));
        var team=CommandManager.literal("team");for(String t:List.of("red","blue","green","yellow","auto"))team.then(CommandManager.literal(t).executes(c->enroll(c.getSource().getPlayerOrThrow(),t)?1:0));root.then(team);
        for(String name:List.of("arena1","arena2","spawn","bed","shop")){var node=CommandManager.literal(name);if(name.startsWith("arena"))node.executes(c->setPoint(c.getSource().getPlayerOrThrow(),name,null));else for(String t:TEAMS)node.then(CommandManager.literal(t).executes(c->setPoint(c.getSource().getPlayerOrThrow(),name,t)));root.then(node);}
        var gen=CommandManager.literal("generator");var add=CommandManager.literal("add");for(String t:List.of("bronze","iron","gold"))add.then(CommandManager.literal(t).executes(c->setPoint(c.getSource().getPlayerOrThrow(),"generator",t)));gen.then(add).then(CommandManager.literal("clear").executes(c->{if(stage!=Stage.WAITING)return 0;var old=JSON.toJson(config);config.generators.clear();return save(c.getSource().getServer(),old);}));root.then(gen);
        root.then(CommandManager.literal("rule").then(CommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word()).then(CommandManager.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,12000)).executes(c->{if(stage!=Stage.WAITING)return 0;String old=JSON.toJson(config);int v=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value");switch(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")){case "teams"->config.teams=v;case "min"->config.minPlayers=v;case "teamSize"->config.maxPerTeam=v;case "respawn"->config.respawnSeconds=v;case "shield"->config.shieldTicks=v;case "time"->config.maxSeconds=v;case "bedLoss"->config.bedLossSeconds=v;case "bronze"->config.bronzeTicks=v;case "iron"->config.ironTicks=v;case "gold"->config.goldTicks=v;default->{return 0;}}int result=save(c.getSource().getServer(),old);c.getSource().sendFeedback(()->Text.literal(result==1?"规则已保存":"参数无效或保存失败"),false);return result;}))));
        root.then(CommandManager.literal("start").executes(c->requestStart(c.getSource().getPlayerOrThrow())?1:0));
        root.then(CommandManager.literal("stop").executes(c->{finish(c.getSource().getServer(),"本局已取消。");return 1;}));
        root.then(CommandManager.literal("reset").executes(c->{var s=c.getSource().getServer();finish(s,"场地正在重置。");if(stage==Stage.RESETTING&&BedArena.failed())BedArena.load(s);return 1;}));
        root.then(CommandManager.literal("shopreload").executes(c->stage==Stage.WAITING&&BedShop.load(c.getSource().getServer())?1:0));d.register(root);
    });}
    private static int setPoint(ServerPlayerEntity p,String kind,String t){if(stage!=Stage.WAITING)return 0;String dim=p.getWorld().getRegistryKey().getValue().toString();if((config.corner1!=null||config.corner2!=null||!config.generators.isEmpty()||config.bases.values().stream().anyMatch(b->b.spawn!=null||b.bed!=null||b.shop!=null))&&!config.dimension.equals(dim)){p.sendMessage(Text.literal("所有点位必须在同一维度。"),false);return 0;}
        var pt=point(p);if(kind.equals("bed")){var hit=p.raycast(p.getBlockInteractionRange(),1,false);if(!(hit instanceof BlockHitResult bh)||bedFoot(p.getServerWorld(),bh.getBlockPos())==null){p.sendMessage(Text.literal("准星对准完整床再执行。"),false);return 0;}var foot=bedFoot(p.getServerWorld(),bh.getBlockPos());pt=new Point(foot.getX(),foot.getY(),foot.getZ(),0,0);}
        String old=JSON.toJson(config);config.dimension=dim;switch(kind){case "arena1"->config.corner1=pt;case "arena2"->config.corner2=pt;case "spawn"->config.bases.get(t).spawn=pt;case "bed"->config.bases.get(t).bed=pt;case "shop"->config.bases.get(t).shop=pt;case "generator"->config.generators.add(new Generator(t,pt));}int result=save(p.getServer(),old);p.sendMessage(Text.literal(result==1?"已保存 "+kind+" "+Objects.toString(t,""):"保存失败"),false);return result;}
    public static boolean canEnter(ServerPlayerEntity p){return true;}
    public static int teamCount(){return config.teams;}
    public static String teamLabel(String team){int i=Arrays.asList(TEAMS).indexOf(team);return i<0?"未选择":NAMES[i];}
    public static long chosenCount(String team){return queue.stream().filter(id->team.equals(chosen.get(id))).count();}
    public static boolean queued(ServerPlayerEntity p){return member(p)&&queue.contains(p.getUuid());}
    public static boolean canStartQueue(){return stage==Stage.WAITING&&queue.size()>=config.minPlayers&&queue.stream().map(chosen::get).filter(Objects::nonNull).distinct().count()>=2;}
    public static void join(ServerPlayerEntity p){enroll(p,"auto");}
    public static boolean enroll(ServerPlayerEntity p,String team){
        if(!member(p)||!p.isAlive()||stage!=Stage.WAITING)return false;
        if(team.equals("auto")){team=TEAMS[0];for(int i=1;i<config.teams;i++)if(chosenCount(TEAMS[i])<chosenCount(team))team=TEAMS[i];}
        if(Arrays.asList(TEAMS).indexOf(team)<0||Arrays.asList(TEAMS).indexOf(team)>=config.teams)return false;
        if(!team.equals(chosen.get(p.getUuid()))&&chosenCount(team)>=config.maxPerTeam){p.sendMessage(Text.literal("该队已满，请选择其他队伍。"),false);return false;}
        queue.add(p.getUuid());chosen.put(p.getUuid(),team);p.changeGameMode(GameMode.ADVENTURE);p.sendMessage(Text.literal("已加入 "+teamLabel(team)),false);LobbyItems.refresh(p.getServer());return true;
    }
    public static boolean requestStart(ServerPlayerEntity p){
        if(!queued(p)||stage!=Stage.WAITING||!p.isAlive()||p.isSpectator())return false;String problem=issue(p.getServer());
        if(!canStartQueue()||!problem.equals("场地就绪")){p.sendMessage(Text.literal("暂不能开始："+(!canStartQueue()?"至少两名玩家参加且分属两支队伍":problem)),false);return false;}
        return start(p.getServer());
    }
    private static void tell(MinecraftServer s,String message){var audience=new HashSet<>(queue);audience.addAll(roster.keySet());for(var id:audience){var p=s.getPlayerManager().getPlayer(id);if(p!=null)p.sendMessage(Text.literal(message),false);}}
    public static void teleportBase(ServerPlayerEntity p){if(fighter(p))teleport(p,config.bases.get(team(p)).spawn);}
    private static void teleport(ServerPlayerEntity p,Point pt){p.teleport(world(p.getServer()),pt.x,pt.y,pt.z,Set.of(),pt.yaw,pt.pitch);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);}
    public static boolean start(MinecraftServer s){
        if(stage!=Stage.WAITING)return false;queue.removeIf(id->{var p=s.getPlayerManager().getPlayer(id);return p==null||!member(p)||!p.isAlive();});if(!canStartQueue()||!issue(s).equals("场地就绪")){stage=Stage.WAITING;return false;}
        for(var id:queue){var p=s.getPlayerManager().getPlayer(id);recover(p);if(hasRecovery(p))return false;}
        for(var id:queue){var p=s.getPlayerManager().getPlayer(id);p.closeHandledScreen();if(!GameSessions.write(recovery(p),GameSessions.capture(p)))return false;}
        if(!BedArena.begin(s,world(s),config.corner1.pos(),config.corner2.pos()))return false;
        roster.clear();for(var id:queue)roster.put(id,chosen.get(id));
        round=UUID.randomUUID().toString();stage=Stage.RUNNING;started=s.getTicks();deathmatch=false;beds.clear();beds.addAll(roster.values());alive.clear();alive.addAll(roster.keySet());queue.clear();respawns.clear();shields.clear();BedSpecial.clear();
        for(int i=0;i<config.teams;i++){String name="bw_"+TEAMS[i];var t=s.getScoreboard().getTeam(name);if(t==null)t=s.getScoreboard().addTeam(name);t.setColor(COLORS[i]);t.setFriendlyFireAllowed(false);t.setDisplayName(Text.literal(NAMES[i]));}
        for(var id:roster.keySet()){var p=s.getPlayerManager().getPlayer(id);s.getScoreboard().addScoreHolderToTeam(p.getName().getString(),s.getScoreboard().getTeam("bw_"+team(p)));p.getEnderChestInventory().clear();spawn(p);}
        for(int i=0;i<config.teams;i++){var pt=config.bases.get(TEAMS[i]).shop;var v=new VillagerEntity(EntityType.VILLAGER,world(s));v.refreshPositionAndAngles(pt.x,pt.y,pt.z,pt.yaw,0);v.setAiDisabled(true);v.setInvulnerable(true);v.setSilent(true);v.setPersistent();v.setCustomName(Text.literal(NAMES[i]+"商店").formatted(COLORS[i]));v.setCustomNameVisible(true);v.addCommandTag("bw_shop");stamp(v);world(s).spawnEntity(v);}
        LobbyItems.spectateAudience(s,ID);LobbyItems.refresh(s);
        tell(s,"起床战争开始");return true;
    }
    private static ServerPlayerEntity respawn(ServerPlayerEntity old){var p=old.getServer().getPlayerManager().respawnPlayer(old,false,Entity.RemovalReason.KILLED);p.networkHandler.player=p;return p;}
    private static void spawn(ServerPlayerEntity p){lives.merge(p.getUuid(),1,Integer::sum);respawns.remove(p.getUuid());p.getInventory().clear();var tool=new ItemStack(Items.WOODEN_PICKAXE);stampStack(tool);p.getInventory().setStack(0,tool);GameSessions.selectSlot(p,0);p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);p.setHealth(20);p.setAbsorptionAmount(0);p.setGlowing(false);p.extinguish();p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);teleport(p,config.bases.get(team(p)).spawn);shields.put(p.getUuid(),p.getServer().getTicks()+config.shieldTicks);p.currentScreenHandler.syncState();}
    public static boolean breakBlock(ServerPlayerEntity p,BlockPos pos){if(!fighter(p)||!p.canInteractWithBlockAt(pos,0)||!BedArena.contains(p.getServerWorld(),pos))return false;var w=p.getServerWorld();var foot=bedFoot(w,pos);
        if(foot!=null){String victim=null;for(var t:beds)if(config.bases.get(t).bed.pos().equals(foot)){victim=t;break;}if(victim==null||victim.equals(team(p))||!bedReachable(p,pos))return false;var other=bedOther(w,foot);if(!BedArena.capture(w,foot)||!BedArena.capture(w,other))return false;BedArena.set(w,foot,Blocks.AIR.getDefaultState());BedArena.set(w,other,Blocks.AIR.getDefaultState());beds.remove(victim);tell(p.getServer(),victim+" 队的床被 "+p.getName().getString()+" 摧毁！");return true;}
        if(!BedArena.added(w,pos))return false;var state=w.getBlockState(pos);boolean broken;collectingDrops=true;try{broken=BedArena.authorized(()->w.breakBlock(pos,true,p));}finally{collectingDrops=false;}if(broken)p.getMainHandStack().postMine(w,state,pos,p);return broken;
    }
    /** Bed attacks treat container model gaps as cover without changing normal interaction shapes. */
    private static boolean bedReachable(ServerPlayerEntity p,BlockPos pos){
        var start=p.getEyePos();
        var end=start.add(p.getRotationVec(1).multiply(p.getBlockInteractionRange()));
        var hit=p.getServerWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p){
            @Override public VoxelShape getBlockShape(BlockState state,BlockView world,BlockPos blockPos){
                return state.getBlock() instanceof ChestBlock||state.getBlock() instanceof EnderChestBlock
                    ?VoxelShapes.fullCube():super.getBlockShape(state,world,blockPos);
            }
        });
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
    }
    public static boolean shopAllowed(ServerPlayerEntity p,VillagerEntity v){return fighter(p)&&v.isAlive()&&v.getWorld()==p.getWorld()&&v.getCommandTags().contains("bw_shop")&&current(v)&&p.squaredDistanceTo(v)<=36;}
    public static void stamp(Entity e){e.addCommandTag("mg_source_"+ID);e.addCommandTag("bw_round_"+round);var owner=Isolation.owner(e);if(owner!=null)e.addCommandTag("bw_life_"+lives.getOrDefault(owner.getUuid(),0));if(e instanceof ItemEntity item){e.addCommandTag("mg_item_"+ID);stampStack(item.getStack());}}
    public static void stampStack(ItemStack stack){var n=stack.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();n.putString("bw_round",round);n.putString("mg_item_game",ID);stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(n));}
    public static boolean current(Entity e){return stage==Stage.RUNNING&&!round.isEmpty()&&e.getCommandTags().contains("bw_round_"+round)&&e.getWorld() instanceof ServerWorld w&&BedArena.contains(w,e.getBlockPos());}
    public static boolean projectileValid(Entity e){var p=Isolation.owner(e);return current(e)&&p!=null&&fighter(p)&&p.getWorld()==e.getWorld()&&e.getCommandTags().contains("bw_life_"+lives.getOrDefault(p.getUuid(),0));}
    public static boolean permits(Entity e,ServerPlayerEntity target){var p=Isolation.owner(e);return projectileValid(e)&&fighter(target)&&p!=null&&(p==target||!sameTeam(p,target));}
    public static boolean pickup(ServerPlayerEntity p,ItemEntity item){
        var n=item.getStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
        boolean roundItem=!n.getString("bw_round").isEmpty()||item.getCommandTags().stream().anyMatch(t->t.startsWith("bw_round_"));
        if(!member(p)&&!roundItem&&!n.getString("mg_item_game").equals(ID)&&!item.getCommandTags().contains("mg_item_"+ID))return true;
        if(!roundItem&&member(p)&&(stage==Stage.WAITING||p.isCreative()))return true;
        for(String tag:item.getCommandTags())if(tag.startsWith("bw_round_")&&!tag.equals("bw_round_"+round))return false;
        return fighter(p)&&item.getWorld()==p.getWorld()&&BedArena.contains(p.getServerWorld(),item.getBlockPos())
                &&n.getString("bw_round").equals(round)&&n.getString("mg_item_game").equals(ID);
    }
    public static void recover(ServerPlayerEntity p){if(!hasRecovery(p)||!p.isAlive())return;try{var n=GameSessions.read(recovery(p));GameSessions.restore(p,n);p.setGlowing(n.getBoolean("glowing"));if(GameSessions.checkpoint(p))Files.delete(recovery(p));}catch(Exception e){MWMod.LOGGER.error("Bedwars player recovery retained",e);}}
    public static void leave(ServerPlayerEntity p){reposition.remove(p.getUuid());queue.remove(p.getUuid());chosen.remove(p.getUuid());alive.remove(p.getUuid());respawns.remove(p.getUuid());shields.remove(p.getUuid());roster.remove(p.getUuid());BedSpecial.forget(p.getUuid());recover(p);PrivateSidebar.clear(p);}
    private static void finish(MinecraftServer s,String message){if(stage==Stage.RESETTING||stage==Stage.WAITING&&queue.isEmpty()&&roster.isEmpty())return;tell(s,message);stage=Stage.RESETTING;round="";deathmatch=false;BedSpecial.clear();clearEntities(s);var all=new HashSet<>(roster.keySet());all.addAll(queue);queue.clear();alive.clear();respawns.clear();shields.clear();for(var id:all){var p=s.getPlayerManager().getPlayer(id);if(p!=null){if(!p.isAlive())p=respawn(p);recover(p);PrivateSidebar.clear(p);HubMenu.travel(p,GameSessions.HUB);}}roster.clear();chosen.clear();beds.clear();reposition.clear();lives.clear();LobbyItems.finishAudience(s,ID);if(BedArena.active())BedArena.beginReset();else stage=Stage.WAITING;}
    private static void clearEntities(MinecraftServer s){for(var w:s.getWorlds()){var remove=new ArrayList<Entity>();for(var e:w.iterateEntities())if(!(e instanceof ServerPlayerEntity)&&(e.getCommandTags().contains("mg_source_"+ID)||e.getCommandTags().contains("mg_item_"+ID)))remove.add(e);remove.forEach(Entity::discard);}}
    private static void generate(MinecraftServer s,int tick){var w=world(s);for(var g:config.generators){int interval=switch(g.type){case "bronze"->config.bronzeTicks;case "iron"->config.ironTicks;default->config.goldTicks;};if(tick%interval!=0)continue;var pt=g.point;w.getChunk(pt.pos());int count=w.getEntitiesByClass(ItemEntity.class,new Box(pt.pos()).expand(2),i->BedShop.money(i.getStack(),g.type)).stream().mapToInt(i->i.getStack().getCount()).sum();if(count>=64)continue;var item=new ItemEntity(w,pt.x,pt.y,pt.z,BedShop.currency(g.type,1));item.setVelocity(Vec3d.ZERO);item.setPickupDelay(10);stamp(item);w.spawnEntity(item);}}
    private static void enterDeathmatch(MinecraftServer s,boolean timed){
        if(deathmatch||stage!=Stage.RUNNING)return;
        if(timed){
            var w=world(s);
            for(String team:new ArrayList<>(beds)){
                var foot=bedFoot(w,config.bases.get(team).bed.pos());
                if(foot==null)continue;
                var other=bedOther(w,foot);
                if(!BedArena.capture(w,foot)||!BedArena.capture(w,other)||!BedArena.set(w,foot,Blocks.AIR.getDefaultState())||!w.isAir(other)&&!BedArena.set(w,other,Blocks.AIR.getDefaultState())){
                    finish(s,"床清除失败，对局已停止并开始地图恢复。");return;
                }
            }
            beds.clear();
        }
        deathmatch=true;
        for(var p:s.getPlayerManager().getPlayerList())if(inRound(p)&&member(p))BedShop.enableWitherBows(p);
        tell(s,timed?"§4床保护时间结束：所有床消失，进入最终死斗！死亡后不再复活。":"§4所有床已被摧毁，进入最终死斗！死亡后不再复活。");
    }
    public static void tick(MinecraftServer s){for(var id:new ArrayList<>(reposition)){var p=s.getPlayerManager().getPlayer(id);if(p!=null){if(inRound(p))teleport(p,config.bases.get(team(p)).spawn);else{recover(p);HubMenu.travel(p,GameSessions.HUB);}}reposition.remove(id);}if(stage==Stage.RESETTING){if(BedArena.restore(s,256))stage=Stage.WAITING;return;}if(s.getTicks()%20==0)sidebar(s);if(stage!=Stage.RUNNING)return;
        if(BedArena.failed()){finish(s,"场地记录失败，本局取消并保留恢复日志。");return;}
        if(!deathmatch&&(beds.isEmpty()||s.getTicks()-started>=config.bedLossSeconds*20)){
            enterDeathmatch(s,!beds.isEmpty());if(stage!=Stage.RUNNING)return;
        }
        for(var id:new ArrayList<>(alive)){var p=s.getPlayerManager().getPlayer(id);if(p==null){alive.remove(id);continue;}if(respawns.containsKey(id)){if(!beds.contains(team(p))){alive.remove(id);respawns.remove(id);if(!p.isAlive())p=respawn(p);p.changeGameMode(GameMode.SPECTATOR);}else if(s.getTicks()>=respawns.get(id)){if(!p.isAlive())p=respawn(p);spawn(p);}continue;}if(p.isAlive()&&!BedArena.contains(p.getServerWorld(),p.getBlockPos()))p.damage(p.getDamageSources().genericKill(),Float.MAX_VALUE);}
        var remaining=new HashSet<String>();for(var id:alive)remaining.add(roster.get(id));if(remaining.size()<=1){finish(s,remaining.isEmpty()?"起床战争平局。":"起床战争胜者："+remaining.iterator().next()+" 队！");return;}if(s.getTicks()-started>=config.maxSeconds*20){finish(s,"时间耗尽，起床战争平局。");return;}
        generate(s,s.getTicks()-started);BedSpecial.tick(s);
    }
    private static void sidebar(MinecraftServer s){for(var p:s.getPlayerManager().getPlayerList())if(member(p)){
        var lines=new ArrayList<String>();
        lines.add("队伍："+teamLabel(stage==Stage.RUNNING?team(p):chosen.get(p.getUuid())));
        lines.add("人数："+(stage==Stage.RUNNING?alive.size():queue.size())+" / "+(config.teams*config.maxPerTeam));
        for(int i=0;i<config.teams;i++){String t=TEAMS[i];long n=stage==Stage.RUNNING?alive.stream().filter(id->t.equals(roster.get(id))).count():chosenCount(t);lines.add(NAMES[i]+"："+n+" 人"+(stage==Stage.RUNNING?(beds.contains(t)?" · 床 ✔":" · 床 ✘"):""));}
        if(stage==Stage.RUNNING)lines.add(deathmatch?"结束："+Math.max(0,config.maxSeconds-(s.getTicks()-started)/20)+" 秒":"清床："+Math.max(0,config.bedLossSeconds-(s.getTicks()-started)/20)+" 秒");
        PrivateSidebar.showGame(p,"起床战争",lines);
    }}
}
