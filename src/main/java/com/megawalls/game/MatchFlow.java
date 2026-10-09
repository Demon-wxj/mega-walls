package com.megawalls.game;

import com.megawalls.player.MWPlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;
import java.util.*;

/** One authoritative phase clock for the v8 datapack. */
public final class MatchFlow {
    public static boolean canManage(net.minecraft.server.command.ServerCommandSource source){return true;}
    public static int requestStart(ServerPlayerEntity p){if(!com.megawalls.minigame.GameSessions.mw(p)||!p.getCommandTags().contains("mw_ready"))return 0;return start(p.getServer());}
    public static void depart(ServerPlayerEntity p){
        if(running(p.getServer())&&roster(p)>0){eliminate(p);announce(p.getServer(),p.getName().getString()+" 已退出本局。");}
        p.removeCommandTag("mw_ready");p.removeCommandTag("mw_practice");var d=MWPlayerData.getServer(p);d.setPractice(false);d.setEnergy(0);d.setSkillCooldown(0);com.megawalls.skill.SkillTasks.forget(p.getUuid());
    }
    public static int practice(ServerPlayerEntity p){
        if(!com.megawalls.minigame.GameSessions.mw(p)||running(p.getServer())||!p.isAlive()||p.isSpectator())return 0;
        var d=MWPlayerData.getServer(p);if(d.getClassId()==0){p.sendMessage(Text.literal("请先选择职业。"),false);return 0;}
        boolean enabled=!p.getCommandTags().contains("mw_practice");p.removeCommandTag("mw_ready");
        if(enabled){
            String point=MapConfig.get(p.getServer(),"training_set")==1?"training":p.getScoreboardTeam()!=null&&MatchTeams.ALL.contains(p.getScoreboardTeam().getName())?p.getScoreboardTeam().getName()+"spawn":"redspawn";
            if(MapConfig.get(p.getServer(),point+"_set")!=1){p.sendMessage(Text.literal("请先在战场设置中设置训练场入口。"),false);return 0;}
            MapConfig.teleport(p,point);p.addCommandTag("mw_practice");
            
        }else{p.removeCommandTag("mw_practice");com.megawalls.menu.HubMenu.returnToEntry(p);}
        d.setPractice(enabled);d.resetEpsGate();d.setEnergy(enabled?100:0);d.setSkillCooldown(0);com.megawalls.skill.SkillManager.syncToClient(p);return 1;
    }
    public static int duration(MinecraftServer s){return phase(s)==3&&MapConfig.get(s,"deathmatch_budget")>0?MapConfig.get(s,"deathmatch_budget"):MapConfig.get(s,phase(s)==1?"prep":phase(s)==2?"battle":"deathmatch")*20;}
    public static int matchId(MinecraftServer s){return MapConfig.get(s,"match_id");}
    public static void syncMembers(MinecraftServer s){for(var p:s.getPlayerManager().getPlayerList())if(com.megawalls.minigame.GameSessions.mw(p))com.megawalls.skill.SkillManager.syncToClient(p);}
    public static String phaseName(MinecraftServer s){return switch(phase(s)){case 1->"准备阶段";case 2->timer(s)<MapConfig.get(s,"rage")*20?"凋零暴怒":"普通攻防";case 3->"最终死斗";case 4->"本局结束";default->"等待准备";};}
    public static net.minecraft.nbt.NbtCompound status(ServerPlayerEntity p){
        var s=p.getServer();var n=new net.minecraft.nbt.NbtCompound();n.putString("stage",phaseName(s));n.putInt("phase",phase(s));n.putInt("left",running(s)?Math.max(0,(duration(s)-timer(s)+19)/20):0);
        n.putBoolean("in_session",com.megawalls.minigame.GameSessions.mw(p));n.putBoolean("map_locked",MapConfig.get(s,"wall_saved")==1);
        for(int id=1;id<=13;id++){var skill=com.megawalls.skill.SkillManager.getSkill(id);if(skill!=null)n.putInt("cooldown_"+id,skill.getCooldownTicks());}
        n.putInt("map",MapConfig.mask(s));n.putInt("map_extra",MapConfig.maskExtra(s));n.putBoolean("ready",p.getCommandTags().contains("mw_ready"));n.putInt("winner",MapConfig.get(s,"winner"));n.putString("team",p.getScoreboardTeam()==null?"未分队":MatchTeams.label(p.getScoreboardTeam().getName()));
        String issue=lobbyIssue(s);
        n.putString("team_id",p.getScoreboardTeam()==null?"":p.getScoreboardTeam().getName());n.putBoolean("can_manage",com.megawalls.minigame.GameSessions.mw(p));n.putString("start_issue",issue);n.putBoolean("can_start",issue.isEmpty()&&p.getCommandTags().contains("mw_ready"));
        n.putInt("wall_count",MapConfig.get(s,"wall_count"));n.putInt("teams",MatchTeams.count(s));n.putBoolean("map_ready",MapConfig.complete(s));n.putInt("map_total",MapConfig.required(s).size());n.putInt("map_set",(int)MapConfig.required(s).stream().filter(k->MapConfig.get(s,k+"_set")==1).count());
        int ready=0;for(String t:MatchTeams.active(s)){int count=0;for(var other:s.getPlayerManager().getPlayerList())if(GameState.participant(other)&&other.getScoreboardTeam().getName().equals(t)){count++;if(other.getCommandTags().contains("mw_ready"))ready++;}n.putInt(t+"_count",count);n.putInt(t+"_alive",alive(s,MatchTeams.id(t)));n.putInt(t+"_hp",GameState.score(s.getScoreboard(),"#global","mw_wither_"+t));}n.putInt("ready_count",ready);
        n.putInt("kills",GameState.score(s.getScoreboard(),p.getName().getString(),"mw_kills"));n.putInt("finals",GameState.score(s.getScoreboard(),p.getName().getString(),"mw_final_kills"));return n;
    }
    public static int phase(MinecraftServer s){return GameState.score(s.getScoreboard(),"#global","mw_phase");}
    public static boolean running(MinecraftServer s){return phase(s)>=1&&phase(s)<=3;}
    public static int timer(MinecraftServer s){return GameState.score(s.getScoreboard(),"#global","mw_timer");}
    public static void score(MinecraftServer s,String holder,String obj,int value){s.getScoreboard().getOrCreateScore(ScoreHolder.fromName(holder),MapConfig.objective(s,obj)).setScore(value);}
    public static int roster(ServerPlayerEntity p){return GameState.score(p.getScoreboard(),p.getName().getString(),"mw_roster");}
    public static int alive(MinecraftServer s,int team){int count=0;var obj=s.getScoreboard().getNullableObjective("mw_roster");if(obj!=null)for(var e:s.getScoreboard().getScoreboardEntries(obj))if(e.value()==team)count++;return count;}
    public static void announce(MinecraftServer s,String text){for(var p:s.getPlayerManager().getPlayerList())if(com.megawalls.minigame.GameSessions.mw(p))p.sendMessage(Text.literal(text),false);}
    /** One hidden, unlimited effect per life; no repeated command or tick refresh. */
    public static void giveNightVision(ServerPlayerEntity p){p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,StatusEffectInstance.INFINITE,0,true,false,false));}
    private static void phase(MinecraftServer s,int value){int before=phase(s);score(s,"#global","mw_phase",value);score(s,"#global","mw_timer",0);LogRegrowth.clear();if(before!=2||value!=3){com.megawalls.skill.SkillTasks.clear();com.megawalls.skill.SkillProjectiles.clear();}}
    public static int chooseTeam(ServerPlayerEntity p,String team){
        if(!com.megawalls.minigame.GameSessions.mw(p)){p.sendMessage(Text.literal("§e请先从小游戏入口进入超级战墙。"),true);return 0;}
        if(phase(p.getServer())!=0){p.sendMessage(Text.literal("§e请等待本局结束并重置后选择队伍。"),true);return 0;}
        if(team.equals("auto")){
            int least=Integer.MAX_VALUE;for(String t:MatchTeams.active(p.getServer())){int count=0;for(var other:p.getServer().getPlayerManager().getPlayerList())if(other!=p&&com.megawalls.minigame.GameSessions.mw(other)&&other.getScoreboardTeam()!=null&&other.getScoreboardTeam().getName().equals(t))count++;if(count<least){least=count;team=t;}}
        }
        if(!MatchTeams.active(p.getServer()).contains(team))return 0;
        var selected=p.getScoreboard().getTeam(team);if(selected==null)return 0;
        if(p.getScoreboardTeam()==selected)return 1;
        p.getScoreboard().addScoreHolderToTeam(p.getName().getString(),selected);p.removeCommandTag("mw_ready");
        p.sendMessage(Text.literal("已选择"+MatchTeams.label(team)).formatted(MatchTeams.color(team)),false);syncMembers(p.getServer());return 1;
    }
    public static int ready(ServerPlayerEntity p){
        if(!com.megawalls.minigame.GameSessions.mw(p)||phase(p.getServer())!=0||MWPlayerData.getServer(p).getClassId()<1||!p.isAlive()||p.isSpectator()){p.sendMessage(Text.literal("§e请在等待阶段加入超级战墙并选择职业；存活且退出观战后才能准备。"),true);return 0;}
        if(p.getScoreboardTeam()==null||!MatchTeams.active(p.getServer()).contains(p.getScoreboardTeam().getName()))if(chooseTeam(p,"auto")==0)return 0;
        p.removeCommandTag("mw_practice");MWPlayerData.getServer(p).setPractice(false);
        if(p.getCommandTags().contains("mw_ready"))p.removeCommandTag("mw_ready");else p.addCommandTag("mw_ready");
        p.sendMessage(Text.literal(p.getCommandTags().contains("mw_ready")?"§a已加入超级战墙":"§7已退出游戏"),false);syncMembers(p.getServer());return 1;
    }
    public static String startIssue(MinecraftServer s){
        String issue=lobbyIssue(s);return issue.isEmpty()?MapConfig.validate(s):issue;
    }
    /** Cheap roster/configuration check for HUD sync; full terrain validation only at start. */
    public static String lobbyIssue(MinecraftServer s){
        if(GameState.score(s.getScoreboard(),"#pack","mw_internal")<8)return "需要启用配套 v8 数据包。";
        if(running(s))return "对局正在进行。";
        if(phase(s)==4||MapConfig.get(s,"wall_saved")==1||MatchTerrainJournal.pending(s))return "请先重置上一局。";
        if(!MapConfig.complete(s))return "地图点位尚未设置完整。";
        var teams=new HashSet<String>();int count=0;for(var p:s.getPlayerManager().getPlayerList())if(GameState.participant(p)&&p.isAlive()&&!p.isSpectator()&&p.getCommandTags().contains("mw_ready")&&MWPlayerData.getServer(p).getClassId()>0){teams.add(p.getScoreboardTeam().getName());count++;}
        return count>=2&&teams.size()>=2?"":"至少两名玩家选择职业、加入不同队伍并准备。";
    }
    public static int start(MinecraftServer s){
        MapConfig.init(s);String issue=startIssue(s);if(!issue.isEmpty()){announce(s,"§e"+issue);return 0;}
        if(!MapConfig.saveWall(s)){announce(s,"§c墙体备份失败，未开始对局。");return 0;}
        if(!MatchTerrainJournal.begin(s)){announce(s,"§c"+MatchTerrainJournal.lastError()+"，未开始对局。请先重置。");return 0;}
        for(var e:new ArrayList<>(s.getScoreboard().getScoreboardEntries(MapConfig.objective(s,"mw_roster"))))score(s,e.owner(),"mw_roster",0);
        clearCombat();com.megawalls.skill.Summons.clear();
        MapConfig.set(s,"match_id",matchId(s)+1);MapConfig.set(s,"combat_elapsed",0);MapConfig.set(s,"deathmatch_budget",0);
        phase(s,1);MapConfig.set(s,"wall_cursor",0);MapConfig.set(s,"wall_segment",0);MapConfig.set(s,"winner",0);
        for(String team:MatchTeams.ALL){score(s,"#global","mw_wither_"+team,0);removeCore(s,team);}
        for(String team:MatchTeams.active(s)){if(s.getPlayerManager().getPlayerList().stream().noneMatch(p->GameState.participant(p)&&p.getCommandTags().contains("mw_ready")&&p.getScoreboardTeam().getName().equals(team)&&p.isAlive()&&!p.isSpectator()))continue;holdCoreChunk(s,team,true);score(s,"#global","mw_wither_"+team,1000);score(s,"#"+team+"_dead","mw_internal",0);spawnCore(s,team);}
        for(var p:s.getPlayerManager().getPlayerList()){
            if(!com.megawalls.minigame.GameSessions.mw(p))continue;
            if(!GameState.participant(p)||!p.isAlive()||p.isSpectator()||!p.getCommandTags().contains("mw_ready")){p.changeGameMode(GameMode.SPECTATOR);continue;}
            int team=MatchTeams.id(p.getScoreboardTeam().getName());score(s,p.getName().getString(),"mw_roster",team);score(s,p.getName().getString(),"mw_offline",0);
            p.removeCommandTag("mw_eliminated");p.removeCommandTag("wither_dead");p.removeCommandTag("mw_practice");
            for(String obj:new String[]{"mw_kills","mw_final_kills","mw_assists","mw_final_assists","mw_deaths"})score(s,p.getName().getString(),obj,0);
            p.changeGameMode(GameMode.SURVIVAL);p.clearStatusEffects();p.setHealth(p.getMaxHealth());p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);
            com.megawalls.equipment.EquipmentHandler.clearPending(p);command(p,"function megawalls:system/finalize_kit");com.megawalls.equipment.EquipmentHandler.bind(p);MapConfig.teleport(p,p.getScoreboardTeam().getName()+"spawn");giveNightVision(p);MWPlayerData.getServer(p).resetEpsGate();MWPlayerData.getServer(p).setEnergy(0);MWPlayerData.getServer(p).setSkillCooldown(0);
        }
        com.megawalls.minigame.LobbyItems.refresh(s);announce(s,"§a对局开始！准备 "+MapConfig.get(s,"prep")+" 秒，随后拆墙。");syncMembers(s);return 1;
    }
    private static void spawnCore(MinecraftServer s,String team){
        removeCore(s,team);var pos=MapConfig.point(s,team+"wither");var core=new WitherEntity(EntityType.WITHER,s.getOverworld());
        core.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);core.setAiDisabled(true);core.setNoGravity(true);core.setPersistent();core.addCommandTag("mw_wither_"+team);
        core.setCustomName(Text.literal(MatchTeams.label(team)+"凋零").formatted(MatchTeams.color(team)));core.setCustomNameVisible(true);s.getOverworld().spawnEntity(core);
    }
    private static void removeCore(MinecraftServer s,String team){for(var w:s.getWorlds())for(var e:w.iterateEntities())if(e instanceof WitherEntity&&e.getCommandTags().contains("mw_wither_"+team))e.discard();}
    private static void holdCoreChunk(MinecraftServer s,String team,boolean hold){
        var p=MapConfig.point(s,team+"wither");int x=p.getX()>>4,z=p.getZ()>>4;var w=s.getOverworld();String key=team+"_forced_by_match";
        if(hold&&!w.getForcedChunks().contains(ChunkPos.toLong(x,z))){w.setChunkForced(x,z,true);MapConfig.set(s,key,1);}
        if(!hold&&MapConfig.get(s,key)==1){w.setChunkForced(x,z,false);MapConfig.set(s,key,0);}
    }
    public static void eliminate(ServerPlayerEntity p){int value=roster(p);if(value>0)score(p.getServer(),p.getName().getString(),"mw_roster",-value);p.addCommandTag("mw_eliminated");}
    public static void joined(ServerPlayerEntity p){
        if(!com.megawalls.minigame.GameSessions.mw(p))return;
        if(!running(p.getServer())){if(phase(p.getServer())==0&&roster(p)==0){
            if(GameState.score(p.getScoreboard(),p.getName().getString(),"mw_reset_pending")==1){com.megawalls.equipment.EquipmentHandler.clearPending(p);clearRoundInventory(p);score(p.getServer(),p.getName().getString(),"mw_reset_pending",0);p.sendMessage(Text.literal("§a上一局已重置，比赛物品已清理。"),false);}
            p.removeCommandTag("mw_ready");p.removeCommandTag("mw_practice");p.removeCommandTag("mw_eliminated");p.removeCommandTag("wither_dead");MWPlayerData.getServer(p).setEnergy(0);MWPlayerData.getServer(p).setSkillCooldown(0);if(p.isSpectator())p.changeGameMode(GameMode.SURVIVAL);
        }return;}
        if(roster(p)<=0){p.addCommandTag("mw_eliminated");p.changeGameMode(GameMode.SPECTATOR);p.sendMessage(Text.literal("§7本局已开始，你正在观战。"),false);}
        else{int id=roster(p);if(id>MatchTeams.count(p.getServer())){eliminate(p);p.changeGameMode(GameMode.SPECTATOR);return;}var team=p.getScoreboard().getTeam(MatchTeams.ALL.get(id-1));if(team!=null)p.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);score(p.getServer(),p.getName().getString(),"mw_offline",0);p.sendMessage(Text.literal("§a已重连，继续本局。"),false);}
    }
    public static void tick(MinecraftServer s){
        if(GameState.score(s.getScoreboard(),"#pack","mw_internal")<8||!running(s))return;
        if(MatchTerrainJournal.failed(s)){finish(s,0);announce(s,"§c地图记录失败，对局已停止。请先排查并重置："+MatchTerrainJournal.lastError());return;}
        int time=timer(s)+1;score(s,"#global","mw_timer",time);int phase=phase(s);
        if(phase>=2)MapConfig.set(s,"combat_elapsed",MapConfig.get(s,"combat_elapsed")+1);
        if(phase==1&&time>=MapConfig.get(s,"prep")*20){
            if(clearWall(s)){phase(s,2);announce(s,"§6墙已倒塌，攻击敌方凋零！");}return;
        }
        if(time%20!=0)return;
        if(phase==1){int left=MapConfig.get(s,"prep")-time/20;if(left==30||left<=5)announce(s,"§e拆墙倒计时："+left+" 秒");}
        var online=new HashSet<String>();for(var p:s.getPlayerManager().getPlayerList())online.add(p.getName().getString());
        for(var e:new ArrayList<>(s.getScoreboard().getScoreboardEntries(MapConfig.objective(s,"mw_roster"))))if(e.value()>0){
            int absent=online.contains(e.owner())?0:GameState.score(s.getScoreboard(),e.owner(),"mw_offline")+1;
            score(s,e.owner(),"mw_offline",absent);
            if(absent>=MapConfig.get(s,"grace")){score(s,e.owner(),"mw_roster",-e.value());announce(s,"§7"+e.owner()+" 重连等待超时，退出本局。");}
        }
        if(phase>=2){int remaining=0,winner=0;for(String t:MatchTeams.active(s))if(alive(s,MatchTeams.id(t))>0){remaining++;winner=MatchTeams.id(t);}if(remaining<=1){finish(s,remaining==0?0:winner);return;}}
        if(phase==2){
            for(String team:MatchTeams.active(s)){
                String obj="mw_wither_"+team;int hp=GameState.score(s.getScoreboard(),"#global",obj);
                if(time%60==0&&hp>1)score(s,"#global",obj,hp-1);
                if(hp<=0&&GameState.score(s.getScoreboard(),"#"+team+"_dead","mw_internal")==0){score(s,"#"+team+"_dead","mw_internal",1);removeCore(s,team);announce(s,"§c"+MatchTeams.label(team)+"凋零死亡，该队无法复活！");}
            }
            if(time==MapConfig.get(s,"rage")*20)announce(s,"§e凋零暴怒结束，进入普通攻防。凋零仍存活。");
            if(MatchTeams.active(s).stream().allMatch(t->GameState.score(s.getScoreboard(),"#global","mw_wither_"+t)<=0)||time>=MapConfig.get(s,"battle")*20){
                for(String team:MatchTeams.active(s)){score(s,"#global","mw_wither_"+team,0);removeCore(s,team);}
                MapConfig.set(s,"deathmatch_budget",Math.max(20,(MapConfig.get(s,"battle")+MapConfig.get(s,"deathmatch"))*20-MapConfig.get(s,"combat_elapsed")));
                phase(s,3);announce(s,"§4所有凋零已死亡，进入死斗！无法复活；请前往墙倒后的中央区域，留在外围将获得饥饿 II。");syncMembers(s);
            }
        }
        for(var p:s.getPlayerManager().getPlayerList())if(com.megawalls.minigame.GameSessions.mw(p)&&roster(p)>0&&!p.isSpectator()){
            String team=p.getScoreboardTeam()==null?"":p.getScoreboardTeam().getName();
            if(GameState.score(s.getScoreboard(),"#global","mw_wither_"+team)<=0)p.addCommandTag("wither_dead");
            if(phase==1)p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE,40,1,true,false));
            if(phase(s)==3&&GameState.active(p)&&!DeathmatchZone.contains(p))
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER,40,1,true,false));
        }
        if(phase==3&&time>=duration(s))finish(s,0);
    }
    private static boolean clearWall(MinecraftServer s){
        int segment=MapConfig.get(s,"wall_segment");if(segment>=MapConfig.walls(s))return true;var min=MapConfig.wallMin(s,segment);var size=MapConfig.wallSize(s,segment);int volume=size.getX()*size.getY()*size.getZ(),cursor=MapConfig.get(s,"wall_cursor"),end=Math.min(volume,cursor+2048);
        for(int i=cursor;i<end;i++){int x=i%size.getX(),z=i/size.getX()%size.getZ(),y=i/(size.getX()*size.getZ());var pos=min.add(x,y,z);s.getOverworld().getChunk(pos);
            if(s.getOverworld().getBlockState(pos).isOf(Blocks.BARRIER))s.getOverworld().setBlockState(pos,Blocks.AIR.getDefaultState(),3);
            MapConfig.set(s,"wall_cursor",i+1);}
        if(end==volume){MapConfig.set(s,"wall_segment",segment+1);MapConfig.set(s,"wall_cursor",0);}return end==volume&&segment+1==MapConfig.walls(s);
    }
    public static void finish(MinecraftServer s,int winner){
        if(!running(s))return;
        clearCombat();
        phase(s,4);MapConfig.set(s,"winner",winner);com.megawalls.skill.Summons.clear();
        for(String team:MatchTeams.ALL){removeCore(s,team);holdCoreChunk(s,team,false);}
        announce(s,winner>=1&&winner<=4?MatchTeams.color(MatchTeams.ALL.get(winner-1))+MatchTeams.label(MatchTeams.ALL.get(winner-1))+"胜利！":"§e本局平局。");
        for(var p:s.getPlayerManager().getPlayerList()){
            if(!com.megawalls.minigame.GameSessions.mw(p))continue;MWPlayerData.getServer(p).setEnergy(0);MWPlayerData.getServer(p).setSkillCooldown(0);p.clearStatusEffects();p.extinguish();p.sendMessage(Text.literal("§6结算 · 击杀 "+GameState.score(p.getScoreboard(),p.getName().getString(),"mw_kills")+" / 最终击杀 "+GameState.score(p.getScoreboard(),p.getName().getString(),"mw_final_kills")+" · 按 M 查看本局"),false);}syncMembers(s);
    }
    private static void clearCombat() {
        com.megawalls.wither.WitherDefense.clear();GameEvents.clearRuntime();
        CombatCredit.clear();com.megawalls.passive.PassiveHandler.clear();
        com.megawalls.passive.LegacyBattle.clear();com.megawalls.skill.WerewolfCombat.clear();
        com.megawalls.skill.HunterTracker.clear();com.megawalls.skill.SkillManager.clearRuntime();
        com.megawalls.passive.EnderRetention.clear();
    }
    private static void clearRoundInventory(ServerPlayerEntity p){p.closeHandledScreen();p.getInventory().clear();com.megawalls.menu.HubMenu.giveStar(p);p.playerScreenHandler.sendContentUpdates();}
    public static int reset(MinecraftServer s){
        if(running(s))finish(s,0);
        boolean journal=MatchTerrainJournal.pending(s);
        boolean hadMatch=phase(s)==4||journal||MapConfig.get(s,"wall_saved")==1;
        var restored=MatchTerrainJournal.recordedPositions(s);
        if(journal){
            if(!MatchTerrainJournal.restore(s)){announce(s,"§c"+MatchTerrainJournal.lastError());return 0;}
            MapConfig.set(s,"wall_saved",0);
            RegionProtection.clearRestored(s.getOverworld(),restored);
            com.megawalls.passive.MiningTreasure.resetRestoredPositions(s.getOverworld(),restored);
        }else if(!MapConfig.restoreWall(s)){announce(s,"§e墙体还原失败：请加载墙区域后重试。");return 0;}
        clearCombat();
        if(!journal)com.megawalls.passive.MiningTreasure.clearChests(s);
        phase(s,0);MapConfig.set(s,"wall_segment",0);MapConfig.set(s,"wall_cursor",0);MapConfig.set(s,"winner",0);MapConfig.set(s,"combat_elapsed",0);MapConfig.set(s,"deathmatch_budget",0);
        for(var e:new ArrayList<>(s.getScoreboard().getScoreboardEntries(MapConfig.objective(s,"mw_roster")))){
            if(hadMatch&&e.value()!=0){score(s,e.owner(),"mw_reset_pending",1);for(String obj:new String[]{"mw_kills","mw_final_kills","mw_assists","mw_final_assists","mw_deaths"})score(s,e.owner(),obj,0);}
            score(s,e.owner(),"mw_roster",0);
        }
        for(var e:new ArrayList<>(s.getScoreboard().getScoreboardEntries(MapConfig.objective(s,"mw_offline"))))score(s,e.owner(),"mw_offline",0);
        for(var p:s.getPlayerManager().getPlayerList()){
            if(!com.megawalls.minigame.GameSessions.mw(p))continue;p.removeCommandTag("mw_ready");p.removeCommandTag("mw_practice");p.removeCommandTag("mw_eliminated");p.removeCommandTag("wither_dead");com.megawalls.equipment.EquipmentHandler.clearPending(p);if(GameState.score(s.getScoreboard(),p.getName().getString(),"mw_reset_pending")==1){clearRoundInventory(p);score(s,p.getName().getString(),"mw_reset_pending",0);}MWPlayerData.getServer(p).setEnergy(0);MWPlayerData.getServer(p).setSkillCooldown(0);p.changeGameMode(GameMode.SURVIVAL);p.clearStatusEffects();p.extinguish();p.setHealth(p.getMaxHealth());p.getHungerManager().setFoodLevel(20);p.getHungerManager().setSaturationLevel(5);if(MapConfig.get(s,"lobby_set")==1)MapConfig.teleport(p,"lobby");}
        syncMembers(s);announce(s,journal?"§a已回到大厅，恢复本局记录范围内的地形和容器。":"§a已回到大厅并还原分隔墙。旧局没有地形日志，其他破坏无法补恢复。");return 1;
    }
    public static void command(ServerPlayerEntity p,String command){p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(2).withSilent(),command);}
}
