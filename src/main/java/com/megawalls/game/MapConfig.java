package com.megawalls.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.Identifier;
import net.minecraft.block.Blocks;
import net.minecraft.structure.StructurePlacementData;
import java.util.*;

/** Configuration lives in the save's scoreboard; /reload does not erase it. */
public final class MapConfig {
    public static final String[] POINTS={"redspawn","bluespawn","redwither","bluewither","wall1","wall2","center","lobby","purplespawn","yellowspawn","purplewither","yellowwither","wall3","wall4","wall5","wall6","wall7","wall8","redcastle1","redcastle2","bluecastle1","bluecastle2","purplecastle1","purplecastle2","yellowcastle1","yellowcastle2","arena1","arena2","redwitherzone1","redwitherzone2","bluewitherzone1","bluewitherzone2","purplewitherzone1","purplewitherzone2","yellowwitherzone1","yellowwitherzone2","training"};
    public static final String[] LABELS={"红队出生点","蓝队出生点","红队凋零","蓝队凋零","墙角点一","墙角点二","死斗中心","大厅返回点","紫队出生点","黄队出生点","紫队凋零","黄队凋零","蓝墙角一","蓝墙角二","紫墙角一","紫墙角二","黄墙角一","黄墙角二","红保护区角一","红保护区角二","蓝保护区角一","蓝保护区角二","紫保护区角一","紫保护区角二","黄保护区角一","黄保护区角二","战场边界角一","战场边界角二","红凋零区角一","红凋零区角二","蓝凋零区角一","蓝凋零区角二","紫凋零区角一","紫凋零区角二","黄凋零区角一","黄凋零区角二","训练场入口"};
    private static final Identifier WALL=Identifier.of("megawalls","match_wall");
    public static void init(MinecraftServer server){
        for(String name:new String[]{"mw_config","mw_roster","mw_offline","mw_reset_pending"}) objective(server,name);
        String[] keys={"prep","rage","battle","deathmatch","grace","radius","teams","wall_count","log_restore"};int[] defaults={600,390,2400,600,120,32,2,1,20};
        var board=server.getScoreboard();
        for(String name:MatchTeams.ALL){objective(server,"mw_wither_"+name);var team=board.getTeam(name);if(team==null)team=board.addTeam(name);team.setColor(MatchTeams.color(name));team.setFriendlyFireAllowed(false);}
        for(int i=0;i<keys.length;i++)if(board.getScore(ScoreHolder.fromName(keys[i]),objective(server,"mw_config"))==null)set(server,keys[i],defaults[i]);
        // Upgrade only the old factory preparation value; explicit non-default map settings survive.
        if(get(server,"restoration_rules")<20171021&&!MatchFlow.running(server)){
            if(get(server,"prep")==300){set(server,"prep",600);com.megawalls.MWMod.LOGGER.info("Mega Walls 2017 profile: factory preparation time 300 -> 600 seconds");}
            set(server,"restoration_rules",20171021);
        }
        // A save created by the procedural map builder can have the local pack
        // already marked as enabled, so Minecraft may not dispatch its load tag
        // on the first server boot.  Run the pack initializer once when its
        // phase objective is absent, then apply the SAO map setup for that save.
        if(board.getNullableObjective("mw_phase")==null&&server.getCommandFunctionManager().getFunction(Identifier.of("megawalls","init")).isPresent()){
            var source=server.getCommandSource().withLevel(4);
            server.getCommandManager().executeWithPrefix(source,"function megawalls:init");
        }
        String levelName=server.getSaveProperties().getLevelName();
        if((levelName.contains("SAO")||levelName.contains("岩谷堡"))&&server.getCommandFunctionManager().getFunction(Identifier.of("megawalls","map/sao_load")).isPresent()){
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4),"function megawalls:map/sao_load");
        }
    }
    public static ScoreboardObjective objective(MinecraftServer s,String name){
        var board=s.getScoreboard();var obj=board.getNullableObjective(name);
        return obj!=null?obj:board.addObjective(name,ScoreboardCriterion.DUMMY,Text.literal(name),ScoreboardCriterion.RenderType.INTEGER,false,null);
    }
    public static int get(MinecraftServer s,String key){return GameState.score(s.getScoreboard(),key,"mw_config");}
    public static void set(MinecraftServer s,String key,int value){s.getScoreboard().getOrCreateScore(ScoreHolder.fromName(key),objective(s,"mw_config")).setScore(value);}
    public static BlockPos point(MinecraftServer s,String name){return new BlockPos(get(s,name+"_x"),get(s,name+"_y"),get(s,name+"_z"));}
    public static int mask(MinecraftServer s){int bits=0;for(int i=0;i<Math.min(32,POINTS.length);i++)if(get(s,POINTS[i]+"_set")==1)bits|=1<<i;return bits;}
    public static int maskExtra(MinecraftServer s){int bits=0;for(int i=32;i<POINTS.length;i++)if(get(s,POINTS[i]+"_set")==1)bits|=1<<(i-32);return bits;}
    public static int select(ServerPlayerEntity p,String name){
        var s=p.getServer();init(s);
        if(MatchFlow.running(s)||get(s,"wall_saved")==1){p.sendMessage(Text.literal("§e请先结束并重置当前对局，再修改地图。"),false);return 0;}
        if(p.getServerWorld()!=s.getOverworld()){p.sendMessage(Text.literal("§e地图点位请统一设置在主世界。"),false);return 0;}
        if(name.matches("(red|blue|purple|yellow)protect[12]"))name=name.replace("protect","castle");
        int index=Arrays.asList(POINTS).indexOf(name);if(index<0)return 0;
        var pos=p.getBlockPos();set(s,name+"_x",pos.getX());set(s,name+"_y",pos.getY());set(s,name+"_z",pos.getZ());set(s,name+"_set",1);
        p.sendMessage(Text.literal("§a已设置"+LABELS[index]+"："+pos.toShortString()),false);return 1;
    }
    public static int walls(MinecraftServer s){return get(s,"wall_count")==4?MatchTeams.count(s):1;}
    public static java.util.List<String> required(MinecraftServer s){var names=new ArrayList<>(java.util.List.of("center","lobby"));for(String t:MatchTeams.active(s)){names.add(t+"spawn");names.add(t+"wither");}for(int i=1;i<=walls(s)*2;i++)names.add("wall"+i);if(get(s,"restore_bounds_required")==1){names.add("arena1");names.add("arena2");}return names;}
    public static boolean complete(MinecraftServer s){return required(s).stream().allMatch(n->get(s,n+"_set")==1);}
    public static BlockPos wallMin(MinecraftServer s){return wallMin(s,0);}
    public static Vec3i wallSize(MinecraftServer s){return wallSize(s,0);}
    public static BlockPos wallMin(MinecraftServer s,int i){var a=point(s,"wall"+(i*2+1));var b=point(s,"wall"+(i*2+2));return new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));}
    public static Vec3i wallSize(MinecraftServer s,int i){var a=point(s,"wall"+(i*2+1));var b=point(s,"wall"+(i*2+2));return new Vec3i(Math.abs(a.getX()-b.getX())+1,Math.abs(a.getY()-b.getY())+1,Math.abs(a.getZ()-b.getZ())+1);}
    public static String validate(MinecraftServer s){
        for(String n:required(s))if(get(s,n+"_set")!=1)return "地图缺少点位："+LABELS[Arrays.asList(POINTS).indexOf(n)];
        for(int i=0;i<walls(s);i++){var size=wallSize(s,i);long volume=(long)size.getX()*size.getY()*size.getZ();if(volume>262144)return "墙区域超过 262144 格，单面墙最多 262144 格。";
        var w=s.getOverworld();
        for(String name:required(s)){var pos=point(s,name);w.getChunk(pos);if(!w.getWorldBorder().contains(pos)||pos.getY()<w.getBottomY()||pos.getY()>=(w.getTopY()-1))return "地图点位未加载或越界："+name;}
        }
        for(String team:MatchTeams.active(s))if(point(s,team+"spawn").getSquaredDistance(point(s,team+"wither"))<9)return "出生点与凋零至少相隔 3 格："+team;
        if(get(s,"restore_bounds_required")==1){
            var a=point(s,"arena1");var b=point(s,"arena2");
            int margin=get(s,"journal_margin");if(margin==0)margin=16;
            if(margin<0||margin>64)return "恢复余量 journal_margin 必须为 0（默认16）到64格。";
            if((long)Math.abs(a.getX()-b.getX())+2L*margin>1024||(long)Math.abs(a.getZ()-b.getZ())+2L*margin>1024)return "战场边界过大：加上恢复余量后单边不能超过1024格。";
            for(String name:POINTS){
                if(name.equals("lobby")||name.equals("training")||name.startsWith("arena")||get(s,name+"_set")!=1)continue;
                var p=point(s,name);
                if(p.getX()<Math.min(a.getX(),b.getX())||p.getX()>Math.max(a.getX(),b.getX())||p.getZ()<Math.min(a.getZ(),b.getZ())||p.getZ()>Math.max(a.getZ(),b.getZ()))return "战场边界未包含点位："+name;
            }
        }
        return "";
    }
    public static boolean saveWall(MinecraftServer s){
        for(int i=0;i<walls(s);i++){var id=i==0?WALL:Identifier.of("megawalls","match_wall_"+i);var manager=s.getStructureTemplateManager();var template=manager.getTemplateOrBlank(id);
        template.saveFromWorld(s.getOverworld(),wallMin(s,i),wallSize(s,i),false,Blocks.STRUCTURE_VOID);
        if(!manager.saveTemplate(id))return false;}set(s,"saved_walls",walls(s));set(s,"wall_saved",1);return true;
    }
    public static boolean restoreWall(MinecraftServer s){
        if(get(s,"wall_saved")!=1)return true;
        for(int i=0;i<Math.max(1,get(s,"saved_walls"));i++){var min=wallMin(s,i);var size=wallSize(s,i);
        for(var pos:BlockPos.iterate(min,min.add(size).add(-1,-1,-1)))s.getOverworld().getChunk(pos);
        var template=s.getStructureTemplateManager().getTemplate(i==0?WALL:Identifier.of("megawalls","match_wall_"+i));if(template.isEmpty())return false;
        if(!template.get().place(s.getOverworld(),min,min,new StructurePlacementData(),s.getOverworld().random,3))return false;
        }set(s,"wall_saved",0);return true;
    }
    public static void teleport(ServerPlayerEntity p,String point){var pos=point(p.getServer(),point);p.teleport(p.getServer().getOverworld(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),p.getYaw(),p.getPitch());}
}
