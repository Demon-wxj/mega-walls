package com.megawalls.minigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.Text;
import java.util.*;
/** A detached scoreboard is sent only to its recipient, never registered as a global sidebar. */
public final class PrivateSidebar {
    private static final Map<UUID,ScoreboardObjective> shown=new HashMap<>();
    private static final Map<UUID,String> last=new HashMap<>();
    private static final Map<UUID,String> titles=new HashMap<>();
    private static final Map<UUID,Integer> sizes=new HashMap<>();
    public static void show(ServerPlayerEntity p,String title,List<String> lines) {
        showGame(p,title,lines);
    }
    public static void showGame(ServerPlayerEntity p,String title,List<String> lines){
        var result=new ArrayList<Text>();for(String line:lines)result.add(style(line));
        showText(p,Text.literal(title).formatted(net.minecraft.util.Formatting.GOLD,net.minecraft.util.Formatting.BOLD),result,true);
    }
    private static Text style(String line){
        var F=net.minecraft.util.Formatting.GRAY;int split=line.indexOf('：');
        if(line.contains("红队"))F=net.minecraft.util.Formatting.RED;else if(line.contains("蓝队"))F=net.minecraft.util.Formatting.BLUE;else if(line.contains("绿队"))F=net.minecraft.util.Formatting.GREEN;else if(line.contains("紫队"))F=net.minecraft.util.Formatting.LIGHT_PURPLE;else if(line.contains("黄队"))F=net.minecraft.util.Formatting.YELLOW;
        if(F!=net.minecraft.util.Formatting.GRAY){int color=switch(F){case RED->0xFF8080;case BLUE->0x88B5FF;case GREEN->0x8FE6A0;case LIGHT_PURPLE->0xD7A1FF;default->0xFFE48A;};return Text.literal(line).styled(st->st.withColor(color));}
        return split<0?Text.literal(line).formatted(net.minecraft.util.Formatting.AQUA):Text.empty().append(Text.literal(line.substring(0,split+1)).styled(st->st.withColor(0xCCD6E0))).append(Text.literal(line.substring(split+1)).formatted(net.minecraft.util.Formatting.WHITE));
    }
    public static void showText(ServerPlayerEntity p,Text title,List<Text> lines,boolean hideNumbers){
        lines=new ArrayList<>(lines);
        if(lines.size()>12)lines=new ArrayList<>(lines.subList(0,12));
        lines.add(Text.literal(" "));
        lines.add(Text.literal("Xteamy").formatted(net.minecraft.util.Formatting.GOLD,net.minecraft.util.Formatting.BOLD));
        lines.add(Text.empty().append(Text.literal("bilibili").styled(st->st.withColor(0xFF91BD))).append(Text.literal(" 房间号：").formatted(net.minecraft.util.Formatting.WHITE)).append(Text.literal("88888").formatted(net.minecraft.util.Formatting.AQUA)));
        hideNumbers=true;
        var id=p.getUuid();String titleKey=title.toString()+hideNumbers;
        String value=titleKey+lines;if(value.equals(last.get(id)))return;
        var obj=shown.get(id);
        boolean fresh=obj==null||!titleKey.equals(titles.get(id))||sizes.getOrDefault(id,-1)!=lines.size();
        if(fresh){
            clear(p);
            var board=new Scoreboard();obj=board.addObjective("mg_private",ScoreboardCriterion.DUMMY,title,ScoreboardCriterion.RenderType.INTEGER,false,null);
            shown.put(id,obj);titles.put(id,titleKey);sizes.put(id,lines.size());
            p.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(obj,0));
        }
        last.put(id,value);
        for(int i=0;i<lines.size();i++)p.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket("line"+i,obj.getName(),lines.size()-i,
                Optional.of(lines.get(i)),hideNumbers?Optional.of(net.minecraft.scoreboard.number.BlankNumberFormat.INSTANCE):Optional.empty()));
        if(fresh)p.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR,obj));
    }
    public static void clear(ServerPlayerEntity p){var id=p.getUuid();var old=shown.remove(id);last.remove(id);titles.remove(id);sizes.remove(id);if(old!=null){p.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR,null));p.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(old,1));}}
    public static void forget(UUID id){shown.remove(id);last.remove(id);titles.remove(id);sizes.remove(id);}
    public static void clearAll(){shown.clear();last.clear();titles.clear();sizes.clear();}
}
