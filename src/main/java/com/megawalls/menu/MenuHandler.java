package com.megawalls.menu;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * 下界之心打开服务器小游戏总菜单；职业选择继续由超级战墙菜单负责。
 */
public class MenuHandler {

    private static final String[] CLASS_NAMES = {"僵尸", "骷髅", "蜘蛛", "鱿鱼", "末影人", "海诺布", "苦力怕",
            "恐惧魔王", "铁傀儡", "烈焰人", "猎人", "鼹鼠", "狼人"};
    private static final String[] CLASS_KEYS = {"zombie", "skeleton", "spider", "squid", "enderman",
            "herobrine", "creeper", "dreadlord", "golem", "blaze", "hunter", "mole", "werewolf"};

    public static void register() {}

    /**
     * 服务端：选择职业（由C2S_SelectClass网络包调用）
     */
    public static void selectClass(ServerPlayerEntity player, int classId) {
        if(!com.megawalls.minigame.GameSessions.mw(player)){player.sendMessage(Text.literal("请先从总菜单进入超级战墙。"),false);return;}
        int phase = com.megawalls.game.GameState.phase(player);
        if (phase >= 1 && phase <= 3) {
            player.sendMessage(Text.literal("§c对局已开始，请在下一局开始前选择职业。"), false);
            return;
        }
        if (classId < 1 || classId > 13) {
            player.sendMessage(Text.literal("§c无效的职业ID"), false);
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            player.sendMessage(Text.literal("§e请返回大厅并退出观战后选择职业。"), false);
            return;
        }
        if (com.megawalls.player.MWPlayerData.getServer(player).getClassId() == classId
                && com.megawalls.game.GameState.score(player.getScoreboard(), player.getName().getString(), "mw_class") == classId) {
            com.megawalls.skill.SkillManager.syncToClient(player);
            return;
        }
        String key = CLASS_KEYS[classId - 1];
        String name = CLASS_NAMES[classId - 1];
        if (player.getServer().getCommandFunctionManager().getFunction(net.minecraft.util.Identifier.of("megawalls", "classes/" + key + "/select")).isEmpty()) {
            player.sendMessage(Text.literal("§c没有加载配套数据包，无法发放职业武器。请安装新版数据包后 /reload。"), false);
            return;
        }

        player.removeCommandTag("mw_ready");

        // 1. 设置Mod内部职业数据（技能系统依赖）
        com.megawalls.skill.SkillManager.setClass(player, classId);

        // 2. 设置计分板职业ID
        player.getServer().getCommandManager().executeWithPrefix(
                player.getCommandSource().withLevel(2).withSilent(),
                "scoreboard players set @s mw_class " + classId);
        // 3. 调用数据包的职业装备选择
        player.getServer().getCommandManager().executeWithPrefix(
                player.getCommandSource().withLevel(2).withSilent(),
                "function megawalls:classes/" + key + "/select");

        com.megawalls.equipment.EquipmentHandler.bind(player);
        com.megawalls.skill.SkillManager.syncToClient(player);
        player.sendMessage(Text.literal("§a已选择 " + name), false);
    }
}
