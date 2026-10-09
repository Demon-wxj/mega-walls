package com.megawalls.minigame;

import com.megawalls.menu.HubMenu;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Dedicated registry IDs plus owner/game/action tags. Vanilla lookalikes never act as buttons. */
public final class LobbyItems {
    private static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    private static final Map<UUID,Integer> used=new HashMap<>();
    private static final Set<UUID> spectators=new HashSet<>();
    public static boolean supported(String game){return Set.of(ClassicDuel.ID,PotDuel.ID,BedWars.ID,Spleef.ID,GameSessions.HOT,TntRun.ID).contains(game);}
    public static void register(){
        for(String action:List.of("join","leave","start","team","mode","hub","games"))
            ITEMS.put(action,Registry.register(Registries.ITEM,Identifier.of("megawalls","lobby_"+action),new Item(new Item.Settings().maxCount(1))));
        UseItemCallback.EVENT.register((p,w,hand)->{
            var stack=p.getStackInHand(hand);if(w.isClient||!isControl(stack))return TypedActionResult.pass(stack);
            if(p instanceof ServerPlayerEntity sp&&hand==Hand.MAIN_HAND)use(sp,stack);
            return TypedActionResult.success(stack);
        });
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(w.isClient||!isControl(p.getStackInHand(hand)))return ActionResult.PASS;
            if(p instanceof ServerPlayerEntity sp&&hand==Hand.MAIN_HAND)use(sp,p.getStackInHand(hand));
            return ActionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((p,w,hand,e,hit)->{
            if(w.isClient||!isControl(p.getStackInHand(hand)))return ActionResult.PASS;
            if(p instanceof ServerPlayerEntity sp&&hand==Hand.MAIN_HAND)use(sp,p.getStackInHand(hand));
            return ActionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTicks()%10==0)for(var p:s.getPlayerManager().getPlayerList())sync(p);});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{used.clear();spectators.clear();});
    }
    public static boolean isControl(ItemStack stack){return ITEMS.containsValue(stack.getItem());}
    /** Buttons and owner-bound kit stay in player inventory through every drop entry point. */
    public static boolean isBound(ItemStack stack){return isControl(stack)||com.megawalls.equipment.EquipmentHandler.isEquipmentItem(stack);}
    public static String action(ItemStack stack){return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString("lobby_action");}
    private static String label(String action){return switch(action){case "join"->"参加游戏";case "leave"->"退出游戏";case "start"->"开始游戏";case "team"->"选择队伍";case "mode"->"对局模式";case "hub"->"返回主大厅";default->"小游戏总菜单";};}
    private static boolean duel(String game){return game.equals(ClassicDuel.ID)||game.equals(PotDuel.ID);}
    private static boolean relay(String game){return switch(game){case ClassicDuel.ID->ClassicDuel.isRelay();case PotDuel.ID->PotDuel.isRelay();default->false;};}
    private static int duelTeamCount(String game,int team){return game.equals(ClassicDuel.ID)?ClassicDuel.teamCount(team):PotDuel.teamCount(team);}
    private static int duelSelectedTeam(String game,ServerPlayerEntity p){return game.equals(ClassicDuel.ID)?ClassicDuel.selectedTeam(p):PotDuel.selectedTeam(p);}
    private static Text controlName(ServerPlayerEntity p,String action){
        String name=label(action),game=GameSessions.game(p);
        if(action.equals("mode")&&duel(game))name+=" · "+(relay(game)?"4v4 车轮战":"1v1");
        if(action.equals("team")&&duel(game)&&relay(game))name+=" · 红 "+duelTeamCount(game,0)+"/4 · 紫 "+duelTeamCount(game,1)+"/4";
        return Text.literal(name).formatted(action.equals("leave")?Formatting.RED:Formatting.GOLD);
    }
    public static ItemStack create(ServerPlayerEntity p,String action){
        var stack=new ItemStack(ITEMS.get(action));var n=new NbtCompound();n.putString("lobby_action",action);n.putString("lobby_game",GameSessions.game(p));n.putUuid("lobby_owner",p.getUuid());
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));stack.set(DataComponentTypes.CUSTOM_NAME,controlName(p,action));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);return stack;
    }
    public static boolean authorized(ServerPlayerEntity p,ItemStack stack){
        if(!isControl(stack))return false;var n=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        return n.containsUuid("lobby_owner")&&n.getUuid("lobby_owner").equals(p.getUuid())&&n.getString("lobby_game").equals(GameSessions.game(p))&&ITEMS.get(n.getString("lobby_action"))==stack.getItem();
    }
    public static boolean queued(ServerPlayerEntity p){return ClassicDuel.queued(p)||PotDuel.queued(p)||BedWars.queued(p)||Spleef.queued(p)||HotPotato.queued(p)||TntRun.queued(p);}
    private static boolean waiting(String game){return switch(game){case ClassicDuel.ID->ClassicDuel.stage()==ClassicDuel.Stage.WAITING;case PotDuel.ID->PotDuel.stage()==PotDuel.Stage.WAITING;case BedWars.ID->BedWars.stage()==BedWars.Stage.WAITING;case Spleef.ID->Spleef.stage()==Spleef.Stage.WAITING;case GameSessions.HOT->HotPotato.stage()==HotPotato.Stage.WAITING;case TntRun.ID->TntRun.stage()==TntRun.Stage.WAITING;default->false;};}
    public static boolean running(String game){return switch(game){case ClassicDuel.ID->ClassicDuel.running();case PotDuel.ID->PotDuel.running();case BedWars.ID->BedWars.stage()==BedWars.Stage.RUNNING;case Spleef.ID->Spleef.stage()==Spleef.Stage.RUNNING||Spleef.stage()==Spleef.Stage.RESULT||Spleef.stage()==Spleef.Stage.RESETTING;case GameSessions.HOT->HotPotato.stage()==HotPotato.Stage.RUNNING||HotPotato.stage()==HotPotato.Stage.RESULT;case TntRun.ID->TntRun.stage()==TntRun.Stage.RUNNING||TntRun.stage()==TntRun.Stage.RESETTING;default->false;};}
    public static boolean roundPlayer(ServerPlayerEntity p){return ClassicDuel.inRound(p)||PotDuel.inRound(p)||BedWars.inRound(p)||Spleef.inRound(p)||HotPotato.inRound(p)||TntRun.inRound(p);}
    private static boolean ready(String game){return switch(game){case ClassicDuel.ID->ClassicDuel.canStartQueue();case PotDuel.ID->PotDuel.canStartQueue();case BedWars.ID->BedWars.canStartQueue();case Spleef.ID->Spleef.canStartQueue();case GameSessions.HOT->HotPotato.canStartQueue();case TntRun.ID->TntRun.canStartQueue();default->false;};}
    public static void enter(ServerPlayerEntity p){
        if(!supported(GameSessions.game(p)))return;
        if(running(GameSessions.game(p))&&!roundPlayer(p)){spectators.add(p.getUuid());p.changeGameMode(GameMode.SPECTATOR);}
        else p.changeGameMode(GameMode.ADVENTURE);
        sync(p);
    }
    public static void spectateAudience(MinecraftServer s,String game){for(var p:s.getPlayerManager().getPlayerList())if(GameSessions.game(p).equals(game)&&!roundPlayer(p)){spectators.add(p.getUuid());p.changeGameMode(GameMode.SPECTATOR);sync(p);}}
    public static void finishAudience(MinecraftServer s,String game){for(var p:s.getPlayerManager().getPlayerList())if(GameSessions.game(p).equals(game)&&spectators.remove(p.getUuid())){p.setCameraEntity(p);p.changeGameMode(GameMode.ADVENTURE);HubMenu.returnToEntry(p);sync(p);}}
    public static void leaving(ServerPlayerEntity p){spectators.remove(p.getUuid());used.remove(p.getUuid());p.setCameraEntity(p);remove(p);}
    public static void remove(ServerPlayerEntity p){for(int i=0;i<p.getInventory().size();i++)if(isControl(p.getInventory().getStack(i)))p.getInventory().setStack(i,ItemStack.EMPTY);}
    private static boolean matchRunning(ServerPlayerEntity p){return switch(GameSessions.game(p)){
        case GameSessions.MW->com.megawalls.game.MatchFlow.running(p.getServer());
        case GameSessions.HOT->HotPotato.stage()==HotPotato.Stage.RUNNING||HotPotato.stage()==HotPotato.Stage.RESULT;
        case TntRun.ID->TntRun.stage()==TntRun.Stage.RUNNING;
        default->running(GameSessions.game(p));
    };}
    public static void sync(ServerPlayerEntity p){
        if(!p.isAlive())return;boolean arenaGame=GameSessions.game(p).equals(Spleef.ID)||GameSessions.game(p).equals(GameSessions.HOT);boolean activeArena=arenaGame&&matchRunning(p);if(matchRunning(p)&&!arenaGame){if(p.getInventory().contains(LobbyItems::isControl)){remove(p);p.currentScreenHandler.syncState();}return;}String game=GameSessions.game(p);var actions=new LinkedHashSet<String>();if(!activeArena)actions.add("games");if(!game.equals(GameSessions.HUB))actions.add("hub");
        if(supported(game)&&queued(p)&&!running(game))actions.add("leave");
        if(supported(game)&&waiting(game)){
            if(game.equals(BedWars.ID))actions.add("team");else if(!queued(p))actions.add("join");
            if(duel(game)){actions.add("mode");if(relay(game))actions.add("team");}
            if(queued(p)){actions.add("leave");if(ready(game))actions.add("start");}
        }
        var existing=new HashSet<String>();boolean changed=false;
        for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(!isControl(stack))continue;String a=action(stack);
            if(!authorized(p,stack)||!actions.contains(a)||!existing.add(a)){p.getInventory().setStack(i,ItemStack.EMPTY);changed=true;}
            else if(!Objects.equals(stack.get(DataComponentTypes.CUSTOM_NAME),controlName(p,a))){stack.set(DataComponentTypes.CUSTOM_NAME,controlName(p,a));changed=true;}}
        for(String a:actions)if(!existing.contains(a)){int slot=activeArena&&a.equals("hub")&&p.getInventory().getStack(8).isEmpty()?8:p.getInventory().getEmptySlot();if(slot>=0){p.getInventory().setStack(slot,create(p,a));changed=true;}}
        if(changed)p.currentScreenHandler.syncState();
    }
    public static void refresh(MinecraftServer s){for(var p:s.getPlayerManager().getPlayerList())sync(p);}
    public static boolean use(ServerPlayerEntity p,ItemStack stack){
        if(!authorized(p,stack)||!p.isAlive()||matchRunning(p)&&!((GameSessions.game(p).equals(Spleef.ID)||GameSessions.game(p).equals(GameSessions.HOT))&&action(stack).equals("hub")))return false;int tick=p.getServer().getTicks();if(tick-used.getOrDefault(p.getUuid(),tick-10)<5)return false;used.put(p.getUuid(),tick);
        String a=action(stack),game=GameSessions.game(p);boolean ok=false;
        switch(a){
            case "games"->{HubMenu.open(p);ok=true;}
            case "hub"->ok=HubMenu.travel(p,GameSessions.HUB);
            case "team"->{if(waiting(game)){if(game.equals(BedWars.ID)){p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,player)->new TeamSelector(id,inv),Text.literal("选择队伍")));ok=true;}else if(duel(game)&&relay(game)){p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,player)->new DuelTeamSelector(id,inv,game),Text.literal("4v4 车轮战 · 选择队伍")));ok=true;}}}
            case "mode"->{if(duel(game)&&waiting(game)){p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,player)->new DuelModeSelector(id,inv,game),Text.literal("选择对局模式")));ok=true;}}
            case "join"->{if(game.equals(ClassicDuel.ID))ok=ClassicDuel.enroll(p);else if(game.equals(PotDuel.ID))ok=PotDuel.enroll(p);else if(game.equals(Spleef.ID))ok=Spleef.enroll(p);else if(game.equals(GameSessions.HOT))ok=HotPotato.enroll(p);else if(game.equals(TntRun.ID))ok=TntRun.enroll(p);}
            case "leave"->{if(queued(p)){if(game.equals(ClassicDuel.ID))ClassicDuel.leave(p);else if(game.equals(PotDuel.ID))PotDuel.leave(p);else if(game.equals(BedWars.ID))BedWars.leave(p);else if(game.equals(Spleef.ID))Spleef.leave(p);else if(game.equals(GameSessions.HOT))HotPotato.leave(p);else if(game.equals(TntRun.ID))TntRun.leave(p);p.sendMessage(Text.literal("已退出游戏"),false);ok=true;}}
            case "start"->{if(game.equals(ClassicDuel.ID))ok=ClassicDuel.requestStart(p);else if(game.equals(PotDuel.ID))ok=PotDuel.requestStart(p);else if(game.equals(BedWars.ID))ok=BedWars.requestStart(p);else if(game.equals(Spleef.ID))ok=Spleef.requestStart(p);else if(game.equals(GameSessions.HOT))ok=HotPotato.requestStart(p);else if(game.equals(TntRun.ID))ok=TntRun.requestStart(p);}
        }
        refresh(p.getServer());return ok;
    }
    private static final class DuelModeSelector extends GenericContainerScreenHandler {
        private final UUID owner;
        private final String game;
        DuelModeSelector(int id,net.minecraft.entity.player.PlayerInventory inv,String game){
            super(ScreenHandlerType.GENERIC_9X1,id,inv,new SimpleInventory(9),1);owner=inv.player.getUuid();this.game=game;
            boolean relay=relay(game);
            var solo=new ItemStack(Items.IRON_SWORD);solo.set(DataComponentTypes.CUSTOM_NAME,Text.literal("1v1 · 原规则"+(relay?"":"（当前）")).formatted(Formatting.GOLD));solo.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,!relay);getInventory().setStack(2,solo);
            var team=new ItemStack(Items.PURPLE_BANNER);team.set(DataComponentTypes.CUSTOM_NAME,Text.literal("4v4 车轮战 · 红队 / 紫队"+(relay?"（当前）":"")).formatted(Formatting.LIGHT_PURPLE));team.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,relay);getInventory().setStack(6,team);
        }
        @Override public boolean canUse(PlayerEntity p){return p instanceof ServerPlayerEntity&&p.getUuid().equals(owner)&&GameSessions.game((ServerPlayerEntity)p).equals(game)&&duel(game)&&waiting(game);}
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity p){
            if(!(p instanceof ServerPlayerEntity sp)||!canUse(p)||p.currentScreenHandler!=this||button!=0||type!=SlotActionType.PICKUP||slot!=2&&slot!=6)return;
            boolean ok=game.equals(ClassicDuel.ID)?ClassicDuel.setRelay(sp,slot==6):PotDuel.setRelay(sp,slot==6);
            if(ok){sp.sendMessage(Text.literal("当前对局模式："+(relay(game)?"4v4 车轮战":"1v1")),false);sp.closeHandledScreen();}refresh(sp.getServer());
        }
    }
    private static final class DuelTeamSelector extends GenericContainerScreenHandler {
        private final UUID owner;
        private final String game;
        private final ServerPlayerEntity player;
        private int displayedRed=-1,displayedPurple=-1,displayedSelected=-2;
        DuelTeamSelector(int id,net.minecraft.entity.player.PlayerInventory inv,String game){
            super(ScreenHandlerType.GENERIC_9X1,id,inv,new SimpleInventory(9),1);owner=inv.player.getUuid();this.game=game;player=(ServerPlayerEntity)inv.player;updateTeams();
        }
        private void updateTeams(){
            int red=duelTeamCount(game,0),purple=duelTeamCount(game,1),selected=duelSelectedTeam(game,player);
            if(red==displayedRed&&purple==displayedPurple&&selected==displayedSelected)return;
            displayedRed=red;displayedPurple=purple;displayedSelected=selected;
            for(int team=0;team<2;team++){
                int count=team==0?red:purple;var stack=new ItemStack(team==0?Items.RED_WOOL:Items.PURPLE_WOOL);
                String status=selected==team?" · 已选择":count>=4?" · 已满":" · 点击加入";
                stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal((team==0?"红队":"紫队")+" · "+count+"/4"+status).formatted(team==0?Formatting.RED:Formatting.LIGHT_PURPLE));stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,selected==team);getInventory().setStack(team==0?2:6,stack);
            }
        }
        @Override public void sendContentUpdates(){if(player!=null&&canUse(player))updateTeams();super.sendContentUpdates();}
        @Override public boolean canUse(PlayerEntity p){return p instanceof ServerPlayerEntity sp&&p.getUuid().equals(owner)&&GameSessions.game(sp).equals(game)&&duel(game)&&waiting(game)&&relay(game);}
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity p){
            if(!(p instanceof ServerPlayerEntity sp)||!canUse(p)||p.currentScreenHandler!=this||button!=0||type!=SlotActionType.PICKUP||slot!=2&&slot!=6)return;
            boolean ok=game.equals(ClassicDuel.ID)?ClassicDuel.selectTeam(sp,slot==2?0:1):PotDuel.selectTeam(sp,slot==2?0:1);
            if(ok)sp.closeHandledScreen();else updateTeams();refresh(sp.getServer());
        }
    }
    private static final class TeamSelector extends GenericContainerScreenHandler {
        private final UUID owner;
        TeamSelector(int id,net.minecraft.entity.player.PlayerInventory inv){super(ScreenHandlerType.GENERIC_9X1,id,inv,new SimpleInventory(9),1);owner=inv.player.getUuid();
            Item[] icons={Items.RED_WOOL,Items.BLUE_WOOL,Items.GREEN_WOOL,Items.YELLOW_WOOL};
            for(int i=0;i<BedWars.teamCount();i++){String team=BedWars.TEAMS[i];var stack=new ItemStack(icons[i]);stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(BedWars.teamLabel(team)+" · "+BedWars.chosenCount(team)+" 人"));getInventory().setStack(i*2,stack);}}
        @Override public boolean canUse(PlayerEntity p){return p instanceof ServerPlayerEntity sp&&p.getUuid().equals(owner)&&BedWars.member(sp)&&BedWars.stage()==BedWars.Stage.WAITING;}
        @Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity p){if(p instanceof ServerPlayerEntity sp&&canUse(p)&&p.currentScreenHandler==this&&button==0&&type==SlotActionType.PICKUP&&slot>=0&&slot%2==0&&slot/2<BedWars.teamCount()){
            if(BedWars.enroll(sp,BedWars.TEAMS[slot/2]))sp.closeHandledScreen();refresh(sp.getServer());}}
    }
}
