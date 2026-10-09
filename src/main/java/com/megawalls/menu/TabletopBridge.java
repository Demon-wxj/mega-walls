package com.megawalls.menu;

import com.megawalls.minigame.GameSessions;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Optional integration: the tabletop Mod remains the sole owner of its game rules. */
public final class TabletopBridge {
    private TabletopBridge() {}
    public static boolean installed() { return FabricLoader.getInstance().isModLoaded("tabletop"); }
    public static boolean destination(String id) { return id.equals("flip7") || id.equals("clocktower"); }
    public static String destinationFor(String type) { return type.equals("flip") ? "flip7" : type.equals("clock") ? "clocktower" : ""; }
    public static boolean optionalIcon(String icon) { return icon.equals("tabletop:flip_deck") || icon.equals("tabletop:clock_box"); }
    public static String icon(String id, String requested) {
        var key=Identifier.tryParse(requested);
        if(key!=null && Registries.ITEM.containsId(key))return requested;
        return id.equals("flip7") ? "minecraft:paper" : "minecraft:book";
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if(installed())local.tabletop.Sessions.setAdmission((player,type) -> {
                String id=destinationFor(type);
                return !id.isEmpty() && GameSessions.switchTo(player,id);
            });
        });
    }
    public static boolean canEnter(ServerPlayerEntity player,String id) {
        if(!installed())return deny(player,"此入口需要安装桌间 Mod");
        if(!ServerPlayNetworking.canSend(player,local.tabletop.TabletopNet.View.ID))return deny(player,"客户端需要安装同版本桌间 Mod");
        if(previousGameBusy(player))return deny(player,"请先返回主大厅再进入桌游");
        Item phone=Registries.ITEM.get(Identifier.of("tabletop","phone"));
        return GameSessions.canReceiveItem(player,id,phone) || deny(player,"目标背包已满，请先腾出一个位置");
    }
    private static boolean previousGameBusy(ServerPlayerEntity player) {
        // These phases can move players during leave/finish cleanup, invalidating phone join distance.
        return switch(GameSessions.game(player)) {
            case GameSessions.MW -> com.megawalls.game.MatchFlow.phase(player.getServer())>=1&&com.megawalls.game.MatchFlow.phase(player.getServer())<=4;
            case com.megawalls.minigame.ClassicDuel.ID -> com.megawalls.minigame.ClassicDuel.running()||com.megawalls.minigame.ClassicDuel.stage()==com.megawalls.minigame.ClassicDuel.Stage.RESETTING;
            case com.megawalls.minigame.PotDuel.ID -> com.megawalls.minigame.PotDuel.running()||com.megawalls.minigame.PotDuel.stage()==com.megawalls.minigame.PotDuel.Stage.RESETTING;
            case com.megawalls.minigame.BedWars.ID -> com.megawalls.minigame.BedWars.stage()==com.megawalls.minigame.BedWars.Stage.RUNNING||com.megawalls.minigame.BedWars.stage()==com.megawalls.minigame.BedWars.Stage.RESETTING;
            case GameSessions.HOT -> com.megawalls.minigame.HotPotato.stage()==com.megawalls.minigame.HotPotato.Stage.RUNNING||com.megawalls.minigame.HotPotato.stage()==com.megawalls.minigame.HotPotato.Stage.RESULT;
            case com.megawalls.minigame.Spleef.ID -> com.megawalls.minigame.Spleef.stage()==com.megawalls.minigame.Spleef.Stage.RUNNING||com.megawalls.minigame.Spleef.stage()==com.megawalls.minigame.Spleef.Stage.RESULT||com.megawalls.minigame.Spleef.stage()==com.megawalls.minigame.Spleef.Stage.RESETTING;
            case com.megawalls.minigame.TntRun.ID -> com.megawalls.minigame.TntRun.stage()==com.megawalls.minigame.TntRun.Stage.RUNNING||com.megawalls.minigame.TntRun.stage()==com.megawalls.minigame.TntRun.Stage.RESETTING;
            default -> false;
        };
    }
    public static boolean enter(ServerPlayerEntity player) {
        if(!givePhone(player))return deny(player,"背包已满，请先腾出一个位置");
        player.sendMessage(Text.literal("手机已准备 · 右键打开"),true);
        return true;
    }
    /** Reserve the phone before lobby controls can fill the last empty inventory slot. */
    public static boolean prepareInventory(ServerPlayerEntity player,String next) {
        return !destination(next) || givePhone(player);
    }
    private static boolean givePhone(ServerPlayerEntity player) {
        Item phone=Registries.ITEM.get(Identifier.of("tabletop","phone"));
        for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i).isOf(phone))return true;
        int slot=player.getInventory().getEmptySlot();if(slot<0)return false;
        player.getInventory().setStack(slot,new ItemStack(phone));player.currentScreenHandler.syncState();return true;
    }
    public static boolean permitsSwitch(ServerPlayerEntity player,String next) {
        if(!installed())return true;
        String type=local.tabletop.Sessions.gameOf(player);
        return type.isEmpty() || destinationFor(type).equals(next) || deny(player,"请先在手机中离桌，再切换游戏");
    }
    private static boolean deny(ServerPlayerEntity player,String message) { player.sendMessage(Text.literal(message),true);return false; }
}
