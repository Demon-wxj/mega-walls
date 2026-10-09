package com.megawalls.passive;
import com.megawalls.game.GameState;
import com.megawalls.player.MWPlayerData;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
public final class EnderRetention {
    private record Retained(List<ItemStack> slots,List<ItemStack> carried){}
    private static final Map<UUID,Retained> inventory=new HashMap<>();
    public static boolean capture(ServerPlayerEntity p){
        if(MWPlayerData.getServer(p).getClassId()!=5||!GameState.participant(p)||GameState.phase(p)!=2)return false;
        if(GameState.score(p.getScoreboard(),"#global","mw_wither_"+p.getScoreboardTeam().getName())<=0||p.getRandom().nextFloat()>=.39f)return false;
        var items=new ArrayList<ItemStack>();for(int i=0;i<p.getInventory().size();i++)items.add(p.getInventory().getStack(i).copy());
        var carried=new ArrayList<ItemStack>();
        var cursor=p.currentScreenHandler.getCursorStack();
        if(!cursor.isEmpty()){carried.add(cursor.copy());p.currentScreenHandler.setCursorStack(ItemStack.EMPTY);}
        if(p.playerScreenHandler!=p.currentScreenHandler){
            var ownCursor=p.playerScreenHandler.getCursorStack();
            if(!ownCursor.isEmpty()){carried.add(ownCursor.copy());p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);}
        }
        for(var pending:((com.megawalls.equipment.PendingKitOwner)p).mw$pendingKit())carried.add(pending.copy());
        com.megawalls.equipment.EquipmentHandler.clearPending(p);
        inventory.put(p.getUuid(),new Retained(items,carried));p.getInventory().clear();return true;
    }
    public static void restore(ServerPlayerEntity p){
        var retained=inventory.remove(p.getUuid());if(retained==null||p.isSpectator()||p.getCommandTags().contains("mw_eliminated"))return;
        var items=retained.slots();
        p.getInventory().clear();for(int i=0;i<Math.min(items.size(),p.getInventory().size());i++)p.getInventory().setStack(i,items.get(i));
        for(var carried:retained.carried()){
            if(com.megawalls.equipment.EquipmentHandler.isEquipmentItem(carried))com.megawalls.equipment.EquipmentHandler.returnOrReserve(p,carried);
            else p.giveItemStack(carried);
        }
        p.currentScreenHandler.sendContentUpdates();
    }
    public static void clear(){inventory.clear();}
}
