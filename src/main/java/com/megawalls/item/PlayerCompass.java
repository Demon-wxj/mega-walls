package com.megawalls.item;
import com.megawalls.game.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.text.Text;
import java.util.*;
public final class PlayerCompass {
    private static final Map<UUID,String> teams=new HashMap<>();
    private static final Map<UUID,UUID> targets=new HashMap<>();
    private static final Map<UUID,Integer> clicks=new HashMap<>();
    public static void forget(UUID id){teams.remove(id);targets.remove(id);clicks.remove(id);}
    public static void clear(){teams.clear();targets.clear();clicks.clear();}
    public static String selected(ServerPlayerEntity p){return teams.getOrDefault(p.getUuid(),"all");}
    public static String label(String team){return switch(team){case "red"->"红队";case "blue"->"蓝队";case "purple"->"紫队";case "yellow"->"黄队";default->"所有敌队";};}
    public static void use(ServerPlayerEntity p,boolean cycle){
        if(!p.getMainHandStack().isOf(Items.COMPASS))return;
        int now=p.getServer().getTicks();if(now-clicks.getOrDefault(p.getUuid(),-100)<4)return;clicks.put(p.getUuid(),now);
        if(!GameState.active(p)||GameState.phase(p)==1){p.sendMessage(Text.literal("§e追踪仅在战斗或职业练习中可用"),true);return;}
        if(cycle){
            var choices=new ArrayList<String>();choices.add("all");
            for(String team:MatchTeams.active(p.getServer()))if(p.getScoreboardTeam()==null||!team.equals(p.getScoreboardTeam().getName()))choices.add(team);
            String next=choices.get((choices.indexOf(selected(p))+1)%choices.size());teams.put(p.getUuid(),next);targets.remove(p.getUuid());clearNeedle(p);
            p.sendMessage(Text.literal("§e追踪队伍："+label(next)+" §7｜右键锁定最近敌人"),true);return;
        }
        ServerPlayerEntity best=null;double distance=Double.MAX_VALUE;
        for(var other:p.getServerWorld().getPlayers())if(eligible(p,other)&&p.squaredDistanceTo(other)<distance){best=other;distance=p.squaredDistanceTo(other);}
        if(best==null){targets.remove(p.getUuid());clearNeedle(p);p.sendMessage(Text.literal("§e"+label(selected(p))+" 没有可追踪敌人"),true);return;}
        targets.put(p.getUuid(),best.getUuid());update(p,best);
    }
    private static boolean eligible(ServerPlayerEntity p,ServerPlayerEntity other){return GameState.enemy(p,other)&&(selected(p).equals("all")||other.getScoreboardTeam()!=null&&selected(p).equals(other.getScoreboardTeam().getName()));}
    private static void clearNeedle(ServerPlayerEntity p){for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isOf(Items.COMPASS))stack.remove(DataComponentTypes.LODESTONE_TRACKER);}}
    private static void update(ServerPlayerEntity p,ServerPlayerEntity target){
        var tracking=new LodestoneTrackerComponent(Optional.of(GlobalPos.create(target.getWorld().getRegistryKey(),target.getBlockPos())),false);
        for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isOf(Items.COMPASS))stack.set(DataComponentTypes.LODESTONE_TRACKER,tracking);}
        if(p.getMainHandStack().isOf(Items.COMPASS))p.sendMessage(Text.literal("§a"+label(selected(p))+" → "+target.getName().getString()+" §e"+(int)p.distanceTo(target)+"格 §7｜左键切队伍"),true);
    }
    public static void tick(ServerPlayerEntity p){
        UUID id=targets.get(p.getUuid());if(id==null){clearNeedle(p);return;}var entity=p.getServerWorld().getPlayerByUuid(id);var target=entity instanceof ServerPlayerEntity sp?sp:null;
        if(target==null||!eligible(p,target)||GameState.phase(p)==1){targets.remove(p.getUuid());clearNeedle(p);return;}update(p,target);
    }
}
