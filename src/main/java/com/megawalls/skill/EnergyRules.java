package com.megawalls.skill;

import com.megawalls.player.MWPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

/** 2017 fall baseline. Blaze DM 8 EPS is provisional: contemporary March first-hand reports. */
public final class EnergyRules {
    public static int perHit(int cls, boolean arrow) {
        return switch (cls) {
            case 1, 8 -> 12;
            case 12 -> 10;
            case 2 -> arrow ? 20 : 0;
            case 3 -> 0;
            case 4, 9 -> 10;
            case 5 -> 20;
            case 6 -> 20;
            case 7 -> 20;
            case 10 -> 0;
            case 13 -> 8;
            default -> 0;
        };
    }
    public static int regeneration(int cls, int phase, int age) {
        if (age % 20 != 0) return 0;
        return switch (cls) {
            case 2 -> phase == 3 ? 1 : 0;
            case 3 -> phase == 3 ? 10 : 6;
            case 10 -> phase == 3 ? 8 : 5;
            case 11 -> 2;
            case 12 -> phase == 3 ? 2 : 0;
            default -> 0;
        };
    }
    public static boolean needsHits(int cls){return cls==3||cls==10;}
    public static int regeneration(MWPlayerData data,int phase,int age){
        if(needsHits(data.getClassId())&&data.getEpsHits()<2)return 0;
        return regeneration(data.getClassId(),phase,age);
    }
    /** Called only after an uncancelled, nonzero enemy melee/arrow hit, including lethal hits. */
    public static void hit(ServerPlayerEntity player,boolean arrow){
        if(!com.megawalls.game.GameState.active(player)||com.megawalls.game.GameState.phase(player)==1)return;
        var data=MWPlayerData.getServer(player);
        if(needsHits(data.getClassId())&&data.getEpsHits()<2){
            data.recordEpsHit();data.saveServer(player);SkillManager.syncToClient(player);
        }
        gain(player,perHit(data.getClassId(),arrow));
    }
    public static void spent(ServerPlayerEntity player){
        var data=MWPlayerData.getServer(player);
        if(needsHits(data.getClassId()))data.requireEpsHits();
    }
    public static void gain(ServerPlayerEntity player, int amount) {
        if (amount <= 0 || !com.megawalls.game.GameState.active(player)
                || com.megawalls.game.GameState.phase(player) == 1) return;
        grant(player,amount);
    }
    /** Gathering food is an explicit energy source available during preparation. */
    public static void food(ServerPlayerEntity player,int amount) {
        if(amount<=0||!com.megawalls.game.GameState.active(player))return;
        grant(player,Math.min(100,amount));
    }
    private static void grant(ServerPlayerEntity player,int amount) {
        var data = MWPlayerData.getServer(player);
        data.setEnergy(data.getEnergy() + amount);
        data.saveServer(player);
        SkillManager.syncToClient(player);
    }
    public static String describe(int cls) {
        String hits = "近战命中 +" + perHit(cls, false) + "；箭命中 +" + perHit(cls, true) + "。";
        return hits + switch (cls) {
            case 2 -> "死斗每秒 +1。";
            case 3 -> "解锁后每秒 +6；决战每秒 +10。开局、重生及每次施法后，均需有效近战/箭命中敌人两次才自动回能；职业练习同样适用。";
            case 10 -> "解锁后每秒 +5；决战每秒 +8。开局、重生及每次施法后，均需有效近战/箭命中敌人两次才自动回能；职业练习同样适用。";
            case 5 -> "命中回能；不自动回能。";
            case 11 -> "每秒 +2；决战不需要命中解锁。";
            case 12 -> "决战每秒 +2，无需命中解锁；职业食物也能回能。";
            case 13 -> "受敌人攻击 +2。";
            default -> "不自动回能。";
        };
    }
}
