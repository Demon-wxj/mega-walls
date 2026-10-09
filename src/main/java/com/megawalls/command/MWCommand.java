package com.megawalls.command;

import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.SkillManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

/**
 * 超级战墙命令系统（满级版，无升级命令）
 */
public class MWCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("mw")
                .then(CommandManager.literal("team").then(CommandManager.argument("team",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx,b)->{for(String t:com.megawalls.game.MatchTeams.active(ctx.getSource().getServer()))b.suggest(t);b.suggest("auto");return b.buildFuture();})
                    .executes(ctx->com.megawalls.game.MatchFlow.chooseTeam(ctx.getSource().getPlayerOrThrow(),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"team")))))
                .then(CommandManager.literal("teams").requires(com.megawalls.game.MatchFlow::canManage).then(CommandManager.argument("count",IntegerArgumentType.integer(2,4)).executes(ctx->com.megawalls.game.MatchTeams.mode(ctx.getSource().getServer(),IntegerArgumentType.getInteger(ctx,"count")))))
                .then(CommandManager.literal("walls").requires(com.megawalls.game.MatchFlow::canManage).then(CommandManager.argument("count",IntegerArgumentType.integer(1,4)).executes(ctx->{var s=ctx.getSource().getServer();int count=IntegerArgumentType.getInteger(ctx,"count");if((count!=1&&count!=4)||com.megawalls.game.MatchFlow.running(s)||com.megawalls.game.MapConfig.get(s,"wall_saved")==1)return 0;com.megawalls.game.MapConfig.set(s,"wall_count",count);return 1;})))
                .then(CommandManager.literal("ready").executes(ctx->com.megawalls.game.MatchFlow.ready(ctx.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("map").requires(com.megawalls.game.MatchFlow::canManage)
                    .then(CommandManager.argument("point",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx,b)->{for(String point:com.megawalls.game.MapConfig.POINTS)b.suggest(point);for(String team:com.megawalls.game.MatchTeams.ALL)for(int i=1;i<=2;i++)b.suggest(team+"protect"+i);return b.buildFuture();})
                    .executes(ctx->com.megawalls.game.MapConfig.select(ctx.getSource().getPlayerOrThrow(),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"point")))))
                .then(CommandManager.literal("stop").requires(com.megawalls.game.MatchFlow::canManage).executes(ctx->{com.megawalls.game.MatchFlow.finish(ctx.getSource().getServer(),0);return 1;}))
                .then(CommandManager.literal("reset").requires(com.megawalls.game.MatchFlow::canManage).executes(ctx->com.megawalls.game.MatchFlow.reset(ctx.getSource().getServer())))
                .then(CommandManager.literal("rules").requires(com.megawalls.game.MatchFlow::canManage)
                    .then(CommandManager.argument("rule",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx,b)->{for(String rule:new String[]{"prep","rage","battle","deathmatch","grace","radius","log_restore"})b.suggest(rule);return b.buildFuture();})
                    .then(CommandManager.argument("value",IntegerArgumentType.integer(5,3600)).executes(ctx->{
                        var s=ctx.getSource().getServer();if(com.megawalls.game.MatchFlow.running(s))return 0;
                        String key=com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"rule");
                        if(!java.util.List.of("prep","rage","battle","deathmatch","grace","radius","log_restore").contains(key))return 0;
                        com.megawalls.game.MapConfig.set(s,key,IntegerArgumentType.getInteger(ctx,"value"));ctx.getSource().sendFeedback(()->Text.literal("已更新规则："+key),false);return 1;
                    }))))
                .then(CommandManager.literal("skin").then(CommandManager.argument("id",IntegerArgumentType.integer(0,2))
                    .executes(ctx -> com.megawalls.player.ClassSkins.select(ctx.getSource().getPlayerOrThrow(),IntegerArgumentType.getInteger(ctx,"id")))))
                .then(CommandManager.literal("practice").executes(ctx->com.megawalls.game.MatchFlow.practice(ctx.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("recharge").executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrThrow();
                    if (!com.megawalls.game.GameState.isPractice(p)) { p.sendMessage(Text.literal("§e补满能量仅限职业练习。"), false); return 0; }
                    var data = MWPlayerData.getServer(p); data.setEnergy(100); data.setSkillCooldown(0);
                    SkillManager.syncToClient(p); p.sendMessage(Text.literal("§a能量已补满，冷却已清除。"), true); return 1;
                }))
                .then(CommandManager.literal("setup")
                        .then(CommandManager.argument("team", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, builder) -> { for(String team:com.megawalls.game.MatchTeams.active(ctx.getSource().getServer()))builder.suggest(team); return builder.buildFuture(); })
                                .executes(ctx -> {
                                    var player = ctx.getSource().getPlayerOrThrow();
                                    if (com.megawalls.game.GameState.phase(player) >= 1 && com.megawalls.game.GameState.phase(player) <= 3) return 0;
                                    String team = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "team");
                                    if (!com.megawalls.game.MatchTeams.active(player.getServer()).contains(team)) return 0;
                                    var manager = player.getServer().getCommandManager();
                                    manager.executeWithPrefix(ctx.getSource().withLevel(2), "kill @e[type=marker,tag=mw_spawn_" + team + "]");
                                    manager.executeWithPrefix(ctx.getSource().withLevel(2), "summon marker ~ ~ ~ {Tags:[\"mw_spawn_" + team + "\"]}");
                                    ctx.getSource().sendFeedback(() -> Text.literal("§a已设置 " + team + " 队出生点及凋零位置。"), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("start")
                        .requires(com.megawalls.game.MatchFlow::canManage)
                        .executes(ctx -> {
                            return ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? com.megawalls.game.MatchFlow.requestStart(p) : com.megawalls.game.MatchFlow.start(ctx.getSource().getServer());
                        }))
                .then(CommandManager.literal("help")
                        .executes(ctx -> { sendHelp(ctx.getSource()); return 1; }))
                .then(CommandManager.literal("setclass")
                        .then(CommandManager.argument("player", GamePlayersArgument.players())
                                .then(CommandManager.argument("classId", IntegerArgumentType.integer(0, 13))
                                        .executes(ctx -> {
                                            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx.copyFor(ctx.getSource().withLevel(2)), "player");
                                            int classId = IntegerArgumentType.getInteger(ctx, "classId");
                                            int changed=0;for (ServerPlayerEntity p : players) if(changeClass(p,classId))changed++;
                                            final int count=changed;
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已设置职业: " + count + "人 -> ID " + classId+"（对局中不允许换职业）"), true);
                                            return count;
                                        }))))
                .then(CommandManager.literal("giveenergy")
                        .then(CommandManager.argument("player", GamePlayersArgument.players())
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx.copyFor(ctx.getSource().withLevel(2)), "player");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            for (ServerPlayerEntity p : players) {
                                                MWPlayerData data = MWPlayerData.getServer(p);
                                                data.setEnergy(data.getEnergy() + amount);
                                                data.saveServer(p);
                                                SkillManager.syncToClient(p);
                                            }
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已给能量: " + players.size() + "人 +" + amount), true);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("player", GamePlayersArgument.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx.copyFor(ctx.getSource().withLevel(2)), "player");
                                    MWPlayerData data = MWPlayerData.getServer(p);
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§6=== " + p.getName().getString() + " ===\n" +
                                            "§e职业: §f" + data.getClassName() + " (ID:" + data.getClassId() + ")\n" +
                                            "§e能量: §f" + data.getEnergy() + "/100\n" +
                                            "§e冷却: §f" + (data.getSkillCooldown() / 20) + "秒\n" +
                                            "§e游戏中: §f" + data.isInGame()
                                    ), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("player", GamePlayersArgument.players())
                                .executes(ctx -> {
                                    Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx.copyFor(ctx.getSource().withLevel(2)), "player");
                                    int changed=0;for (ServerPlayerEntity p : players)if(changeClass(p,0))changed++;
                                    final int count=changed;
                                    ctx.getSource().sendFeedback(() -> Text.literal("§a已重置: " + count + "人（对局中不清除职业）"), true);
                                    return count;
                                })))
                .then(CommandManager.literal("callfunction")
                        .then(CommandManager.argument("function", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> {
                                    String function = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "function");
                                    ctx.getSource().getServer().getCommandManager().executeWithPrefix(ctx.getSource().withLevel(2), "function " + function);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§a已调用数据包函数: " + function), true);
                                    return 1;
                                })))
        );
    }

    private static boolean changeClass(ServerPlayerEntity p,int id){
        if(!com.megawalls.minigame.GameSessions.mw(p)||com.megawalls.game.MatchFlow.running(p.getServer())||!p.isAlive()||p.isSpectator())return false;
        if(id>0){com.megawalls.menu.MenuHandler.selectClass(p,id);return MWPlayerData.getServer(p).getClassId()==id;}
        p.closeHandledScreen();SkillManager.setClass(p,0);com.megawalls.equipment.EquipmentHandler.removeKit(p);
        for(String tag:new java.util.HashSet<>(p.getCommandTags()))if(tag.startsWith("mw_class_"))p.removeCommandTag(tag);
        p.removeCommandTag("mw_ready");p.removeCommandTag("mw_practice");SkillManager.syncToClient(p);return true;
    }

    private static void sendHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(
                "§6=== 超级战墙命令帮助 ===\n" +
                "§e/mw start§f - 开始游戏\n" +
                "§e/mw help§f - 帮助\n" +
                "§e/mw practice§f - 开关职业练习（无需地图）\n" +
                "§e/mw recharge§f - 练习中补满能量和冷却\n" +
                "§e/mw setclass <玩家> <ID>§f - 设置职业(0-13)\n" +
                "§e/mw giveenergy <玩家> <数>§f - 给能量\n" +
                "§e/mw info <玩家>§f - 查看信息\n" +
                "§e/mw reset <玩家>§f - 重置数据\n" +
                "§e/mw callfunction <函数>§f - 调用数据包函数\n" +
                "§6========================"
        ), false);
    }
}
