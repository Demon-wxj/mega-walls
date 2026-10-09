package com.megawalls.player;
import com.megawalls.game.GameState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
public final class ClassSkins {
    private static final java.util.Map<java.util.UUID,Integer> last=new java.util.HashMap<>();
    public static void syncOthers(ServerPlayerEntity p,com.megawalls.network.S2C_SkillSync packet){
        int value=MWPlayerData.getServer(p).getClassId()*10+selected(p);
        if(java.util.Objects.equals(last.put(p.getUuid(),value),value))return;
        for(var other:p.getServer().getPlayerManager().getPlayerList())if(other!=p)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other,packet);
    }
    public static void init(){
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            for(var p:server.getPlayerManager().getPlayerList()){
                var data=MWPlayerData.getServer(p);data.setSkinId(selected(p));
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player,new com.megawalls.network.S2C_SkillSync(p.getUuid(),data.toNbt()));
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->last.remove(handler.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->last.clear());
    }
    public static int selected(ServerPlayerEntity p){return GameState.score(p.getScoreboard(),p.getName().getString(),"mw_skin");}
    public static int select(ServerPlayerEntity p,int id){
        if(id<0||id>2)return 0;
        var board=p.getScoreboard();var objective=board.getNullableObjective("mw_skin");
        if(objective==null)objective=board.addObjective("mw_skin",ScoreboardCriterion.DUMMY,Text.literal("职业外观"),ScoreboardCriterion.RenderType.INTEGER,false,null);
        board.getOrCreateScore(p,objective).setScore(id);
        com.megawalls.skill.SkillManager.syncToClient(p);
        p.sendMessage(Text.literal("§a末影人外观："+(id==0?"使用账号皮肤":id==1?"Enderman · 普通":"Hoops · 传奇")+"。选择末影人职业时生效。"),false);
        return 1;
    }
}
