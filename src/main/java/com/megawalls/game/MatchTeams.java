package com.megawalls.game;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import java.util.*;
public final class MatchTeams {
    public static final List<String> ALL=List.of("red","blue","purple","yellow");
    public static int count(MinecraftServer s){return MapConfig.get(s,"teams")==4?4:2;}
    public static List<String> active(MinecraftServer s){return ALL.subList(0,count(s));}
    public static int id(String name){return ALL.indexOf(name)+1;}
    public static String label(String name){return switch(name){case "red"->"红队";case "blue"->"蓝队";case "purple"->"紫队";case "yellow"->"黄队";default->"未分队";};}
    public static Formatting color(String name){return switch(name){case "red"->Formatting.RED;case "blue"->Formatting.BLUE;case "purple"->Formatting.DARK_PURPLE;case "yellow"->Formatting.YELLOW;default->Formatting.GRAY;};}
    public static String core(net.minecraft.entity.Entity entity){for(String team:ALL)if(entity.getCommandTags().contains("mw_wither_"+team))return team;return null;}
    public static int mode(MinecraftServer s,int count){
        if(count!=2&&count!=4||MatchFlow.running(s)||MapConfig.get(s,"wall_saved")==1)return 0;
        MapConfig.set(s,"teams",count);
        for(var p:s.getPlayerManager().getPlayerList()){
            if(!com.megawalls.minigame.GameSessions.mw(p))continue;
            p.removeCommandTag("mw_ready");
            if(p.getScoreboardTeam()!=null&&ALL.contains(p.getScoreboardTeam().getName())&&!active(s).contains(p.getScoreboardTeam().getName()))p.getScoreboard().clearTeam(p.getName().getString());
        }
        MatchFlow.announce(s,"§a已切换为 "+count+" 队模式，停用队伍已解除，请重新准备。");return 1;
    }
}
