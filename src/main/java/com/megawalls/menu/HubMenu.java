package com.megawalls.menu;

import com.google.gson.*;
import com.megawalls.MWMod;
import com.megawalls.game.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.item.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.nio.file.*;
import java.util.*;

/** Server-side item selector. Icons are buttons, never player-owned inventory items. */
public final class HubMenu {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LinkedHashMap<String, Destination> ENTRIES = new LinkedHashMap<>();
    private static boolean configValid = true;
    public record Destination(String id, String name, String icon, String dimension, double x, double y, double z, float yaw, float pitch, boolean configured, String onEnter) {
        public Destination(String id, String name, String icon, String dimension, double x, double y, double z, float yaw, float pitch, boolean configured) {
            this(id,name,icon,dimension,x,y,z,yaw,pitch,configured,null);
        }
        public Destination withTrigger(String function) { return new Destination(id,name,icon,dimension,x,y,z,yaw,pitch,configured,function); }
    }
    private static Path file(MinecraftServer s) { return s.getSavePath(WorldSavePath.ROOT).resolve("minigame-menu.json"); }
    private static List<Destination> defaults() {
        var result=new ArrayList<Destination>();
        for(var row:new String[][]{
            {"megawalls","超级战墙","iron_sword"}, {"classic_pvp","UHC PvP","diamond_sword"},
            {"pot_pvp","PVP Pot 药水对战","splash_potion"}, {"final_duel","决一死战","snowball"},
            {"tnt_run","TNT 跑酷","tnt"}, {"hot_potato","烫手山芋","baked_potato"},
            {"bedwars","起床战争","red_bed"}, {"flip7","七连翻","paper"}, {"clocktower","染钟楼谜团","book"}, {"sao_story","SAO 小剧情","enchanted_book"}, {"hub","总大厅","nether_star"}})
            result.add(new Destination(row[0],row[1],"minecraft:"+row[2],"minecraft:overworld",0,0,0,0,0,false));
        for(int i=0;i<result.size();i++){var e=result.get(i);if(TabletopBridge.destination(e.id()))result.set(i,new Destination(e.id(),e.name(),e.id().equals("flip7")?"tabletop:flip_deck":"tabletop:clock_box",e.dimension(),e.x(),e.y(),e.z(),e.yaw(),e.pitch(),e.configured()));}
        return result;
    }
    public static void register() {
        TabletopBridge.register();
        PayloadTypeRegistry.playS2C().register(OpenMWMenu.ID, OpenMWMenu.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(HubMenu::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { ENTRIES.clear(); EntryFunctions.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s) -> EntryFunctions.forget(handler.player.getUuid()));
        CommandRegistrationCallback.EVENT.register((d, registry, environment) -> {
            d.register(CommandManager.literal("hub").executes(c->travel(c.getSource().getPlayerOrThrow(),"hub")?1:0));
            d.register(CommandManager.literal("games").executes(c -> { open(c.getSource().getPlayerOrThrow()); return 1; })
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("id", StringArgumentType.word()).suggests((c,b)->{ENTRIES.keySet().forEach(b::suggest);return b.buildFuture();})
                        .executes(c -> set(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c,"id"), null, null))))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                    .then(CommandManager.argument("icon", net.minecraft.command.argument.IdentifierArgumentType.identifier())
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(c -> set(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c,"id"), net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(c,"icon").toString(), StringArgumentType.getString(c,"name")))))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("id", StringArgumentType.word()).executes(c -> {
                        String id=StringArgumentType.getString(c,"id"); var old=ENTRIES.remove(id); if(old==null)return 0;
                        if(!save(c.getSource().getServer())){ENTRIES.put(id,old);return 0;}return 1;
                    })))
                .then(CommandManager.literal("reload").executes(c->{load(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal(configValid?"小游戏菜单配置已加载。":"配置读取失败，请检查服务器日志。"),false);return configValid?1:0;}))
                .then(CommandManager.literal("trigger")
                    .then(CommandManager.argument("id",StringArgumentType.word()).suggests((c,b)->{ENTRIES.keySet().stream().filter(EntryFunctions::supported).forEach(b::suggest);return b.buildFuture();})
                        .executes(c -> trigger(c.getSource(),StringArgumentType.getString(c,"id"),null,false))
                        .then(CommandManager.literal("clear").executes(c -> trigger(c.getSource(),StringArgumentType.getString(c,"id"),null,true)))
                        .then(CommandManager.argument("function",net.minecraft.command.argument.IdentifierArgumentType.identifier())
                            .executes(c -> trigger(c.getSource(),StringArgumentType.getString(c,"id"),net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(c,"function").toString(),false)))))
                .then(CommandManager.literal("item")
                    .executes(c->{giveStar(c.getSource().getPlayerOrThrow());return 1;})
                    .then(CommandManager.argument("players", com.megawalls.command.GamePlayersArgument.players()).executes(c->{for(var p:EntityArgumentType.getPlayers(c.copyFor(c.getSource().withLevel(2)),"players"))giveStar(p);return 1;}))));
            d.register(CommandManager.literal("mw")
                .then(CommandManager.literal("menu").executes(c -> openMW(c.getSource().getPlayerOrThrow()))
                    .then(CommandManager.argument("players", com.megawalls.command.GamePlayersArgument.players())
                        .executes(c->{int count=0;for(var p:EntityArgumentType.getPlayers(c.copyFor(c.getSource().withLevel(2)),"players"))count+=openMW(p);return count;})))
                .then(CommandManager.literal("menubook")
                    .executes(c->{giveBook(c.getSource().getPlayerOrThrow());return 1;})
                    .then(CommandManager.argument("players", com.megawalls.command.GamePlayersArgument.players()).executes(c->{for(var p:EntityArgumentType.getPlayers(c.copyFor(c.getSource().withLevel(2)),"players"))giveBook(p);return 1;}))));
        });
    }
    private static boolean valid(Destination e) {
        return e != null && e.id()!=null && !e.id().equals("_current") && e.id().matches("[a-z0-9_-]{1,32}") && e.name()!=null && !e.name().isBlank() && e.name().length()<=48
            && Identifier.tryParse(e.icon()==null?"":e.icon())!=null && (Registries.ITEM.containsId(Identifier.of(e.icon())) || TabletopBridge.destination(e.id())&&TabletopBridge.optionalIcon(e.icon())) && !Identifier.of(e.icon()).equals(Identifier.ofVanilla("air"))
            && Identifier.tryParse(e.dimension()==null?"":e.dimension())!=null && Double.isFinite(e.x()) && Double.isFinite(e.y()) && Double.isFinite(e.z())
            && Math.abs(e.x())<=29999984 && Math.abs(e.z())<=29999984 && Float.isFinite(e.yaw()) && Float.isFinite(e.pitch()) && Math.abs(e.pitch())<=90 && EntryFunctions.valid(e.id(),e.onEnter());
    }
    private static void load(MinecraftServer s) {
        try {
            var next=new LinkedHashMap<String,Destination>();
            if(Files.exists(file(s))){for(var e:JSON.fromJson(Files.readString(file(s)),Destination[].class)){
                if(!valid(e)||next.putIfAbsent(e.id(),e)!=null||next.size()>21)throw new IllegalArgumentException("Invalid or duplicate menu destination");
            }}else for(var e:defaults())next.put(e.id(),e);
            if(!next.containsKey("hub"))next.put("hub",new Destination("hub","总大厅","minecraft:nether_star","minecraft:overworld",0,0,0,0,0,false));
            if(!next.containsKey("sao_story")&&next.size()<21)next.put("sao_story",new Destination("sao_story","SAO 小剧情","minecraft:enchanted_book","minecraft:overworld",0,0,0,0,0,false));
            ENTRIES.clear();ENTRIES.putAll(next);configValid=true;
        }catch(Exception e){configValid=false;MWMod.LOGGER.error("Cannot load minigame-menu.json; previous entries retained",e);}
    }
    private static boolean save(MinecraftServer s) {
        if(!configValid)return false;
        try {
            var temp=file(s).resolveSibling("minigame-menu.json.tmp");Files.writeString(temp,JSON.toJson(ENTRIES.values()));
            Files.move(temp,file(s),StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(Exception e){MWMod.LOGGER.error("Cannot save minigame-menu.json",e);return false;}
    }
    private static int set(ServerPlayerEntity p,String id,String icon,String name) {
        var old=ENTRIES.get(id);
        if(icon==null&&old==null){p.sendMessage(Text.literal("没有这个入口，请先用 /games add <ID> <物品ID> <名称> 添加。"),false);return 0;}
        if(old==null&&ENTRIES.size()>=21){p.sendMessage(Text.literal("总菜单最多支持 21 个入口。"),false);return 0;}
        var e=new Destination(id,name==null?old.name():name,icon==null?old.icon():icon,p.getWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch(),true,old==null?null:old.onEnter());
        if(!valid(e)){p.sendMessage(Text.literal("ID、图标或名称无效：ID 只允许小写字母、数字、_、-；图标使用物品 ID。"),false);return 0;}
        ENTRIES.put(id,e);if(!save(p.getServer())){if(old==null)ENTRIES.remove(id);else ENTRIES.put(id,old);p.sendMessage(Text.literal("保存失败，入口未更新；请检查服务器日志。"),false);return 0;}
        p.sendMessage(Text.literal("已保存入口「"+e.name()+"」到你当前的位置和朝向。"),false);return 1;
    }
    private static int trigger(ServerCommandSource source,String id,String function,boolean clear) {
        var old=ENTRIES.get(id);
        if(old==null||!EntryFunctions.supported(id)){source.sendError(Text.literal("仅支持 SAO 小剧情、七连翻、染钟楼谜团入口。"));return 0;}
        if(function==null&&!clear){source.sendFeedback(()->Text.literal(old.name()+"："+(EntryFunctions.bound(old.onEnter())?old.onEnter():"未绑定进入触发")),false);return 1;}
        String next=clear?null:function;
        if(!EntryFunctions.valid(id,next)){source.sendError(Text.literal("触发函数 ID 无效或过长。"));return 0;}
        ENTRIES.put(id,old.withTrigger(next));
        if(!save(source.getServer())){ENTRIES.put(id,old);source.sendError(Text.literal("保存失败，触发设置未更改。"));return 0;}
        source.sendFeedback(()->Text.literal(clear?"已取消进入触发。":"已绑定进入触发；设置地点并加载函数后生效。"),false);return 1;
    }
    public static void open(ServerPlayerEntity p) {
        if(!p.isAlive())return;
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, player) -> new Selector(syncId, inventory, List.copyOf(ENTRIES.values())),Text.literal("小游戏大厅 · 选择游戏")));
    }
    public static int openMW(ServerPlayerEntity p) {
        if(!p.isAlive())return 0;
        if(!ServerPlayNetworking.canSend(p,OpenMWMenu.ID)){p.sendMessage(Text.literal("客户端需要安装新版超级战墙 Mod 才能打开职业菜单。"),false);return 0;}
        p.closeHandledScreen();ServerPlayNetworking.send(p,new OpenMWMenu(0));return 1;
    }
    private static ItemStack icon(Destination e) {
        var stack=new ItemStack(Registries.ITEM.get(Identifier.of(TabletopBridge.destination(e.id())?TabletopBridge.icon(e.id(),e.icon()):e.icon())));
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(e.name()).formatted(Formatting.GOLD));
        if(TabletopBridge.destination(e.id()))stack.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal(TabletopBridge.installed()?e.configured()?"点击进入":EntryFunctions.bound(e.onEnter())?"暂未开放":"点击领取手机":"暂未开放").formatted(Formatting.GREEN))));
        else stack.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("点击传送至小游戏入口").formatted(Formatting.GREEN),Text.literal(e.configured()?"目的地已设置":e.id().equals("megawalls")?"未单独设置，可沿用超级战墙大厅点":"未设置传送点，暂不可进入").formatted(Formatting.GRAY))));return stack;
    }
    public static boolean travel(ServerPlayerEntity p,String id) {
        var e=ENTRIES.get(id);if(e==null||!configValid)return false;
        if(id.equals(com.megawalls.minigame.GameSessions.game(p))&&(com.megawalls.minigame.LobbyItems.roundPlayer(p)||id.equals("megawalls")&&MatchFlow.running(p.getServer())&&MatchFlow.roster(p)>0))return false;
        if(!p.isAlive()||p.hasVehicle()||p.hasPassengers())return false;
        if(EntryFunctions.bound(e.onEnter())){
            if(!e.configured()){p.sendMessage(Text.literal("场地待设置，请使用 /games set "+id),true);return false;}
            if(!EntryFunctions.ready(p,id,e.onEnter()))return false;
        }

        if(id.equals("hot_potato")&&!com.megawalls.minigame.HotPotato.canEnter(p))return false;
        if(id.equals("final_duel")&&!com.megawalls.minigame.Spleef.canEnter(p))return false;
        if(id.equals("tnt_run")&&!com.megawalls.minigame.TntRun.canEnter(p))return false;
        if(id.equals("pot_pvp")&&!com.megawalls.minigame.PotDuel.canEnter(p))return false;
        if(id.equals("bedwars")&&!com.megawalls.minigame.BedWars.canEnter(p))return false;
        if(id.equals("classic_pvp")&&!com.megawalls.minigame.ClassicDuel.canEnter(p))return false;
        if(!e.configured()) {
            if(TabletopBridge.destination(id)){
                if(!com.megawalls.minigame.GameSessions.switchTo(p,id))return false;
                p.closeHandledScreen();return TabletopBridge.enter(p);
            }
            else if(id.equals("hub")){var pos=p.getServer().getOverworld().getSpawnPos();e=new Destination("hub","总大厅",e.icon(),"minecraft:overworld",pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0,true);}
            else if(id.equals("megawalls")&&MapConfig.get(p.getServer(),"lobby_set")==1){var pos=MapConfig.point(p.getServer(),"lobby");e=new Destination(e.id(),e.name(),e.icon(),"minecraft:overworld",pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0,true);}
            else {p.sendMessage(Text.literal("入口尚未设置。站到目标位置输入 /games set "+id),false);return false;}
        }
        var world=p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(e.dimension())));
        var pos=BlockPos.ofFloored(e.x(),e.y(),e.z());
        if(world==null||world.isOutOfHeightLimit(pos)||!world.getWorldBorder().contains(pos)){p.sendMessage(Text.literal("目标维度不存在或传送点越界。"),false);return false;}
        world.getChunk(pos);
        var box=p.getBoundingBox().offset(e.x()-p.getX(),e.y()-p.getY(),e.z()-p.getZ());
        if(world.getBlockCollisions(p,box).iterator().hasNext()){p.sendMessage(Text.literal("传送点被方块挡住，请调整入口点位。"),false);return false;}
        if(!com.megawalls.minigame.GameSessions.switchTo(p,id))return false;
        if(TabletopBridge.destination(id)&&!TabletopBridge.enter(p))return false;
        p.closeHandledScreen();
        p.teleport(world,e.x(),e.y(),e.z(),Set.of(),e.yaw(),e.pitch());
        p.setVelocity(Vec3d.ZERO);p.fallDistance=0;
        if(id.equals("hot_potato")||id.equals("final_duel"))com.megawalls.minigame.LobbyItems.enter(p);
        if(id.equals("tnt_run"))com.megawalls.minigame.LobbyItems.enter(p);
        if(id.equals("classic_pvp"))com.megawalls.minigame.LobbyItems.enter(p);
        if(id.equals("pot_pvp"))com.megawalls.minigame.LobbyItems.enter(p);
        if(id.equals("bedwars"))com.megawalls.minigame.LobbyItems.enter(p);
        if(id.equals("megawalls"))com.megawalls.game.MatchFlow.joined(p);
        com.megawalls.minigame.LobbyItems.sync(p);
        EntryFunctions.run(p,id,e.onEnter());
        return true;
    }
    public static void giveStar(ServerPlayerEntity p) { com.megawalls.minigame.LobbyItems.sync(p); }
    public static boolean returnToEntry(ServerPlayerEntity p) {
        var e=ENTRIES.get(com.megawalls.minigame.GameSessions.game(p));if(e==null||!e.configured())return false;
        var w=p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(e.dimension())));if(w==null)return false;
        p.teleport(w,e.x(),e.y(),e.z(),Set.of(),e.yaw(),e.pitch());p.setVelocity(Vec3d.ZERO);p.fallDistance=0;return true;
    }
    private static void giveBook(ServerPlayerEntity p) {
        var page=Text.literal("超级战墙\n\n").append(Text.literal("[点击打开超级战墙菜单]").styled(style -> style.withColor(Formatting.DARK_GREEN).withFormatting(Formatting.UNDERLINE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/mw menu")))).append("\n\n选择职业、队伍与准备。\n\n下界之心右键打开小游戏总菜单。");
        var book=new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(net.minecraft.text.RawFilteredPair.of("超级战墙入口"),"小游戏大厅",0,List.of(net.minecraft.text.RawFilteredPair.of(page)),true));
        p.giveItemStack(book);
    }
    public static final class Selector extends GenericContainerScreenHandler {
        private final Map<Integer,String> buttons=new HashMap<>();
        private final UUID owner;
        private boolean used;
        public Selector(int syncId,PlayerInventory inventory,List<Destination> entries) {
            super(ScreenHandlerType.GENERIC_9X5,syncId,inventory,new SimpleInventory(45),5);owner=inventory.player.getUuid();
            for(int i=0;i<45;i++){var pane=new ItemStack(Items.GRAY_STAINED_GLASS_PANE);pane.set(DataComponentTypes.CUSTOM_NAME,Text.literal(" "));getInventory().setStack(i,pane);}
            for(int i=0;i<entries.size();i++){int slot=10+i/7*9+i%7;var entry=entries.get(i);getInventory().setStack(slot,icon(entry));buttons.put(slot,entry.id());}
            var close=new ItemStack(Items.BARRIER);close.set(DataComponentTypes.CUSTOM_NAME,Text.literal("关闭菜单"));getInventory().setStack(40,close);
        }
        @Override public boolean canUse(PlayerEntity p){return p.getUuid().equals(owner)&&p.isAlive();}
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
        @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
            if(!(player instanceof ServerPlayerEntity p)||!canUse(p)||p.currentScreenHandler!=this||used)return;
            if(action!=SlotActionType.PICKUP||button!=0)return;
            if(slot==40){used=true;p.closeHandledScreen();return;}
            var id=buttons.get(slot);if(id!=null){used=true;if(!travel(p,id))used=false;}
        }
    }
}

