package com.megawalls.client;

import com.megawalls.minigame.LobbyItems;
import com.megawalls.network.C2S_KitClick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class KitCreativeInventory {
    private static boolean suppressListener;
    public static boolean suppressListener(){return suppressListener;}
    public static boolean hasKit(){
        var p=MinecraftClient.getInstance().player;
        if(p==null)return false;
        if(com.megawalls.minigame.LobbyItems.isBound(p.playerScreenHandler.getCursorStack()))return true;
        for(int i=0;i<p.getInventory().size();i++)if(com.megawalls.minigame.LobbyItems.isBound(p.getInventory().getStack(i)))return true;
        return false;
    }
    public static void flushPrediction(){
        var p=MinecraftClient.getInstance().player;if(p==null)return;
        suppressListener=true;
        try {p.playerScreenHandler.sendContentUpdates();}finally{suppressListener=false;}
    }
    public static void click(int slot,int button,SlotActionType type){
        var p=MinecraftClient.getInstance().player;
        var screen=p.playerScreenHandler;
        var cursor=screen.getCursorStack();
        ClientPlayNetworking.send(new C2S_KitClick(slot,button,type.ordinal(),
                com.megawalls.minigame.LobbyItems.isBound(cursor)?ItemStack.EMPTY:cursor.copy()));
        screen.onSlotClick(slot,button,type,p);
        // Record the prediction without CreativeInventoryListener echoing replacement packets.
        flushPrediction();
    }
    public static boolean canDeleteCursor(){
        var c=MinecraftClient.getInstance();
        return c.player!=null&&c.player.isAlive()&&!c.player.isSpectator()
                &&c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>
                &&ClientPlayNetworking.canSend(C2S_KitClick.ID);
    }
    public static void deleteCursor(){
        var p=MinecraftClient.getInstance().player;if(p==null)return;
        var screen=p.currentScreenHandler;
        ClientPlayNetworking.send(new C2S_KitClick(C2S_KitClick.DELETE_CURSOR,screen.syncId,
                SlotActionType.PICKUP.ordinal(),screen.getCursorStack().copy()));
        if(!LobbyItems.isBound(screen.getCursorStack()))screen.setCursorStack(ItemStack.EMPTY);
        flushPrediction();
    }
    public static void catalogueCursor(){
        var p=MinecraftClient.getInstance().player;if(p==null)return;
        var cursor=p.playerScreenHandler.getCursorStack();
        if(LobbyItems.isBound(cursor))return;
        ClientPlayNetworking.send(new C2S_KitClick(C2S_KitClick.CATALOGUE_CURSOR,0,
                SlotActionType.PICKUP.ordinal(),cursor.copy()));
        flushPrediction();
    }
    public static void delete(boolean all){
        var p=MinecraftClient.getInstance().player;if(p==null)return;
        ClientPlayNetworking.send(new C2S_KitClick(C2S_KitClick.DELETE_SLOT,0,
                (all?SlotActionType.QUICK_MOVE:SlotActionType.PICKUP).ordinal(),ItemStack.EMPTY));
        C2S_KitClick.deleteOrdinary(p,all);
        flushPrediction();
    }
}
