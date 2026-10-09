package com.megawalls.skill;

import com.megawalls.equipment.EquipmentHandler;
import com.megawalls.network.NetworkHandler;
import com.megawalls.network.S2C_SkillSync;
import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.skills.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能管理器（满级数据，无等级系统）
 */
public class SkillManager {
    private static final Map<Integer, Skill> skills = new HashMap<>();
    private static final Map<java.util.UUID, Integer> lastRequest = new HashMap<>();
    public static void clearRuntime() { lastRequest.clear(); com.megawalls.item.PlayerCompass.clear(); }
    public static void forget(java.util.UUID uuid) { lastRequest.remove(uuid); com.megawalls.passive.PassiveHandler.forget(uuid); com.megawalls.item.PlayerCompass.forget(uuid); SkillTasks.forget(uuid); SkillProjectiles.forget(uuid); HunterTracker.forget(uuid); WerewolfCombat.forget(uuid); }

    public static void init() {
        skills.put(1, new ZombieSkill());
        skills.put(2, new SkeletonSkill());
        skills.put(3, new SpiderSkill());
        skills.put(4, new SquidSkill());
        skills.put(5, new EndermanSkill());
        skills.put(6, new HerobrineSkill());
        skills.put(7, new CreeperSkill());
        skills.put(8, new DreadlordSkill());
        skills.put(9, new GolemSkill());
        skills.put(10, new BlazeSkill());
        skills.put(11, new HunterSkill());
        skills.put(12, new MoleSkill());
        skills.put(13, new WerewolfSkill());
    }

    public static Skill getSkill(int classId) {
        return skills.get(classId);
    }

    public static void useSkill(ServerPlayerEntity player) {
        if(!com.megawalls.minigame.GameSessions.mw(player))return;
        int now = player.getServer().getTicks();
        Integer last = lastRequest.get(player.getUuid());
        if (last != null && now >= last && now - last < 4) return;
        lastRequest.put(player.getUuid(), now);
        MWPlayerData data = MWPlayerData.getServer(player);
        int storedClass = com.megawalls.game.GameState.score(player.getScoreboard(), player.getName().getString(), "mw_class");
        if (storedClass >= 1 && storedClass <= 13) data.setClassId(storedClass);
        if (!player.isAlive() || player.isSpectator()) { player.sendMessage(Text.literal("§c观战或死亡时不能施放技能。"), true); return; }
        Skill skill = skills.get(data.getClassId());

        if (skill == null) {
            player.sendMessage(Text.literal("§c[超级战墙] 请先选择职业！"), false);
            return;
        }
        if (!com.megawalls.game.GameState.active(player)) {
            player.sendMessage(Text.literal("§e尚未进入战斗。按 M → 开始游戏 → 职业练习，或等待正式开局。"), false); return;
        }
        if (com.megawalls.game.GameState.phase(player) == 1) {
            player.sendMessage(Text.literal("§e准备阶段不能施放主动技能，请等待墙倒。"), true); return;
        }
        if (!com.megawalls.item.ItemUseHandler.isClassWeapon(player, player.getMainHandStack())) {
            player.sendMessage(Text.literal("§e请用任意剑右键、弓左键或按 V；鼹鼠也可用铲子。"), true); return;
        }
        if (data.getEnergy() < skill.getEnergyCost()) {
            // The skill HUD already shows energy. Repeated block-hits must not fill chat.
            return;
        }
        if (data.getSkillCooldown() > 0) {
            player.sendMessage(Text.literal("§c[超级战墙] 技能冷却中：" + (data.getSkillCooldown() / 20) + "秒"), false);
            return;
        }

        if (!skill.canExecute(player)) return;
        data.setEnergy(data.getEnergy() - skill.getEnergyCost());
        EnergyRules.spent(player);
        data.setSkillCooldown(skill.getCooldownTicks());
        data.saveServer(player);
        player.sendMessage(Text.literal("§a" + skill.getName() + " §e激活！"), false);
        player.sendMessage(Text.literal("§a技能激活成功！"), true);
        skill.execute(player);
        syncToClient(player);
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        MWPlayerData data = MWPlayerData.getServer(player);
        boolean changed = false;
        int phase = com.megawalls.game.GameState.phase(player);
        if (phase >= 1 && phase <= 3) player.removeCommandTag("mw_practice");
        boolean practice = com.megawalls.game.GameState.isPractice(player);
        if (data.getPhase() != phase || data.isPractice() != practice) { data.setPhase(phase); data.setPractice(practice); changed = true; }
        boolean active = com.megawalls.game.GameState.active(player);
        if (data.isInGame() != active) { data.setInGame(active); changed = true; }
        int selected = com.megawalls.game.GameState.score(player.getScoreboard(), player.getName().getString(), "mw_class");
        if (selected >= 1 && selected <= 13 && selected != data.getClassId()) {
            forget(player.getUuid());
            data.setClassId(selected); data.setEnergy(0); data.setSkillCooldown(0); changed = true;
        }

        if (data.getSkillCooldown() > 0) {
            data.setSkillCooldown(data.getSkillCooldown() - 1);
            changed = true;
        }
        int regeneration = EnergyRules.regeneration(data, phase, player.age);
        if (data.isInGame() && data.getEnergy() < 100 && regeneration > 0) {
            data.setEnergy(data.getEnergy() + regeneration);
            changed = true;
        }

        // Energy is synchronized independently; the client renders it without changing real XP.
        if (changed) {
            data.saveServer(player);
            syncToClient(player);
        }
        if (player.age % 5 == 0 && !changed) syncToClient(player);
    }

    public static void syncToClient(ServerPlayerEntity player) {
        MWPlayerData data = MWPlayerData.getServer(player);
        if(!com.megawalls.minigame.GameSessions.mw(player)){new MWPlayerData().saveServer(player);data=MWPlayerData.getServer(player);}
        data.setPhase(com.megawalls.game.GameState.phase(player));
        data.setPractice(com.megawalls.game.GameState.isPractice(player));
        data.setInGame(com.megawalls.game.GameState.active(player));
        data.setSkinId(com.megawalls.minigame.GameSessions.mw(player)?com.megawalls.player.ClassSkins.selected(player):0);
        data.setMatch(com.megawalls.game.MatchFlow.status(player));
        data.setSkillStatus(SkillStatus.snapshot(player));
        S2C_SkillSync packet = new S2C_SkillSync(player.getUuid(), data.toNbt());
        if(ServerPlayNetworking.canSend(player,NetworkHandler.SKILL_SYNC_ID))ServerPlayNetworking.send(player, packet);
        com.megawalls.player.ClassSkins.syncOthers(player,packet);
    }

    public static void setClass(ServerPlayerEntity player, int classId) {
        if(!com.megawalls.minigame.GameSessions.mw(player)||classId<0||classId>13)return;
        MWPlayerData data = MWPlayerData.getServer(player);
        EquipmentHandler.clearPending(player);
        com.megawalls.skill.Summons.forget(player.getUuid());
        com.megawalls.passive.LegacyBattle.forget(player.getUuid());
        forget(player.getUuid());
        var objective=com.megawalls.game.MapConfig.objective(player.getServer(),"mw_class");
        player.getScoreboard().getOrCreateScore(player,objective).setScore(classId);
        data.setClassId(classId);
        data.resetEpsGate();
        data.setEnergy(0);
        data.setSkillCooldown(0);
        data.saveServer(player);
        syncToClient(player);
        if (classId > 0) {
            player.sendMessage(Text.literal("§a[超级战墙]§e 职业装备已绑定，不可掉落/丢弃"), false);
        }
    }
}

