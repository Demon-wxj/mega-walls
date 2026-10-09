package com.megawalls.scoreboard;
import com.megawalls.game.*;
import com.megawalls.minigame.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;
public final class ScoreboardHandler {
    private static final Text TITLE=Text.empty().append(Text.literal("超级战墙").formatted(Formatting.YELLOW,Formatting.BOLD))
            .append(Text.literal("  Mega Walls").formatted(Formatting.GOLD));
    private static int teamColor(String team){return switch(team){case "red"->0xFF8080;case "blue"->0x88B5FF;case "purple"->0xD7A1FF;case "yellow"->0xFFE48A;default->0xFFFFFF;};}
    private static Text amount(int value){return Text.literal(Integer.toString(Math.max(0,value))).formatted(Formatting.GREEN);}
    private static Text counter(String label,int first,int second){return Text.empty()
            .append(Text.literal(label+": ").formatted(Formatting.WHITE)).append(amount(first))
            .append(Text.literal("/").formatted(Formatting.GRAY)).append(amount(second));}
    private static Text clock(int seconds){return Text.literal(String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)).formatted(Formatting.GREEN);}
    public static List<Text> lines(ServerPlayerEntity p){
        var s=p.getServer();int phase=MatchFlow.phase(s);
        var lines=new ArrayList<Text>();
        if(phase>=1&&phase<=3){
            int left=Math.max(0,(MatchFlow.duration(s)-MatchFlow.timer(s)+19)/20);
            String label=phase==1?"开墙：":phase==2?"死斗：":"结束：";
            lines.add(Text.empty().append(Text.literal(label).formatted(Formatting.WHITE)).append(clock(left)));
        }
        String mine=p.getScoreboardTeam()==null?"":p.getScoreboardTeam().getName();
        lines.add(Text.empty().append(Text.literal("队伍：").styled(st->st.withColor(0xCCD6E0))).append(Text.literal(MatchTeams.ALL.contains(mine)?MatchTeams.label(mine):"未选择").styled(st->st.withColor(teamColor(mine)))));
        for(String team:MatchTeams.active(s)){
            int hp=GameState.score(s.getScoreboard(),"#global","mw_wither_"+team);
            Text status=phase==0?Text.literal(s.getPlayerManager().getPlayerList().stream().filter(q->GameState.participant(q)&&q.getCommandTags().contains("mw_ready")&&q.getScoreboardTeam().getName().equals(team)).count()+" 人").formatted(Formatting.GRAY)
                    :hp>0&&phase<=2?amount(hp):Text.literal("已毁").formatted(Formatting.RED);
            lines.add(Text.empty().append(Text.literal("■ "+MatchTeams.label(team)).styled(st->st.withColor(teamColor(team))))
                    .append(Text.literal(phase==0?"  ":"  凋零: ").styled(st->st.withColor(0xCCD6E0))).append(status).append(Text.literal(phase==0?"":" · "+MatchFlow.alive(s,MatchTeams.id(team))+"人").formatted(Formatting.WHITE)));
        }
        lines.add(Text.literal("  "));
        String name=p.getName().getString();
        lines.add(counter("击杀/助攻",GameState.score(s.getScoreboard(),name,"mw_kills"),GameState.score(s.getScoreboard(),name,"mw_assists")));
        lines.add(counter("最终击杀/助攻",GameState.score(s.getScoreboard(),name,"mw_final_kills"),GameState.score(s.getScoreboard(),name,"mw_final_assists")));
        return lines;
    }
    public static void updateScoreboard(ServerPlayerEntity p){
        if(!GameSessions.mw(p))return;
        PrivateSidebar.showText(p,TITLE,lines(p),true);
    }
    public static void removeScoreboard(ServerPlayerEntity p){PrivateSidebar.clear(p);}
}
