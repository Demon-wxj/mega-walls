package com.megawalls;

import com.megawalls.command.MWCommand;
import com.megawalls.equipment.EquipmentHandler;
import com.megawalls.item.ItemUseHandler;
import com.megawalls.menu.MenuHandler;
import com.megawalls.nametag.NameTagHandler;
import com.megawalls.network.NetworkHandler;
import com.megawalls.passive.PassiveHandler;
import com.megawalls.player.MWPlayerData;
import com.megawalls.scoreboard.ScoreboardHandler;
import com.megawalls.skill.HunterTracker;
import com.megawalls.skill.SkillManager;
import com.megawalls.wither.WitherHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MWMod implements ModInitializer {
    public static final String MOD_ID = "megawalls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[超级战墙] Mod 初始化中...");

        com.megawalls.command.GamePlayersArgument.register();
        NetworkHandler.register();
        com.megawalls.minigame.LobbyItems.register();
        com.megawalls.minigame.Isolation.register();
        com.megawalls.minigame.HotPotato.register();
        com.megawalls.minigame.Spleef.register();
        com.megawalls.minigame.TntRun.register();
        com.megawalls.minigame.ClassicDuel.register();
        com.megawalls.minigame.PotDuel.register();
        com.megawalls.minigame.BedWars.register();
        com.megawalls.fun.FunMode.register();
        com.megawalls.skill.SkillModels.init();
        com.megawalls.player.ClassSkins.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(com.megawalls.game.MapConfig::init);
        SkillManager.init();
        NameTagHandler.registerLegacyCleanup();
        com.megawalls.game.GameEvents.register();
        com.megawalls.game.MatchTerrainJournal.register();
        com.megawalls.game.PlayerVitals.register();
        com.megawalls.wither.WitherDefense.register();
        com.megawalls.game.RegionProtection.register();
        com.megawalls.game.LogRegrowth.register();
        com.megawalls.skill.WerewolfCombat.register();
        EquipmentHandler.register();
        ItemUseHandler.register();
        MenuHandler.register();
        com.megawalls.menu.HubMenu.register();
        com.megawalls.skill.Summons.register();
        HunterTracker.register();
        PassiveHandler.register();
        com.megawalls.passive.MiningTreasure.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MWCommand.register(dispatcher);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            com.megawalls.game.PlayerVitals.health(player);
            MWPlayerData.initPlayer(player);
            com.megawalls.game.MatchFlow.joined(player);
            SkillManager.syncToClient(player);
            NameTagHandler.createNameTag(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            com.megawalls.minigame.GameSessions.checkpoint(player);
            NameTagHandler.removeNameTag(player);
            com.megawalls.nametag.PlayerHealthDisplay.forget(player.getUuid());
            SkillManager.forget(player.getUuid());
            com.megawalls.passive.LegacyBattle.forget(player.getUuid());
            com.megawalls.skill.Summons.forget(player.getUuid());
            ScoreboardHandler.removeScoreboard(player);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.megawalls.game.PlayerVitals.flush();
            com.megawalls.game.MatchFlow.tick(server);
            com.megawalls.wither.WitherDefense.tick(server);
            com.megawalls.skill.SkillTasks.tick(server);
            com.megawalls.skill.SkillProjectiles.tick(server);
            com.megawalls.skill.Summons.tick(server);
            // 装备掉落保护：每tick一次（放在玩家循环外，避免重复执行）
            EquipmentHandler.tickDroppedEquipment(server);
            com.megawalls.nametag.PlayerHealthDisplay.tick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!com.megawalls.minigame.GameSessions.mw(player)) continue;
                SkillManager.tickPlayer(player);
                com.megawalls.passive.LegacyBattle.tick(player);
                NameTagHandler.updateNameTag(player);
                HunterTracker.tickArrows(player);
                // 每20tick（1秒）调用被动tick和更新计分板
                if (server.getTicks() % 20 == 0) {
                    com.megawalls.game.PlayerVitals.health(player);
                    com.megawalls.item.PlayerCompass.tick(player);
                    PassiveHandler.tickSecond(player);
                    ScoreboardHandler.updateScoreboard(player);
                }
            }
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            com.megawalls.skill.SkillTasks.clear();
            com.megawalls.skill.SkillProjectiles.clear();
            com.megawalls.skill.Summons.clear();
            com.megawalls.passive.LegacyBattle.clear();
            com.megawalls.passive.EnderRetention.clear();
            MWPlayerData.clearServer();
            SkillManager.clearRuntime();
            PassiveHandler.clear();
            com.megawalls.item.PlayerCompass.clear();com.megawalls.game.PlayerVitals.clear();
            NameTagHandler.clear();
            com.megawalls.nametag.PlayerHealthDisplay.clearAll();
            com.megawalls.skill.WerewolfCombat.clear();
            HunterTracker.clear();
        });

        LOGGER.info("[超级战墙] Mod 初始化完成！");
    }
}
