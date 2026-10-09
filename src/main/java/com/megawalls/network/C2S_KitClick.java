package com.megawalls.network;

import com.megawalls.equipment.EquipmentHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/** Creative inventory sends intent for owned kit, never authoritative replacement kit stacks. */
public record C2S_KitClick(int slot, int button, int action, ItemStack ordinaryCursor) implements CustomPayload {
    public static final int DELETE_SLOT=-1000;
    public static final int CATALOGUE_CURSOR=-1001;
    public static final int DELETE_CURSOR=-1002;
    private record Budget(int start,int count){}
    private static final java.util.Map<ServerPlayerEntity,Budget> budgets=new java.util.WeakHashMap<>();
    public static final Id<C2S_KitClick> ID = new Id<>(com.megawalls.MWMod.id("kit_click"));
    public static final PacketCodec<RegistryByteBuf,C2S_KitClick> CODEC = PacketCodec.of(
        (p,b)->{b.writeInt(p.slot);b.writeInt(p.button);b.writeInt(p.action);ItemStack.OPTIONAL_PACKET_CODEC.encode(b,p.ordinaryCursor);},
        b->new C2S_KitClick(b.readInt(),b.readInt(),b.readInt(),ItemStack.OPTIONAL_PACKET_CODEC.decode(b)));
    @Override public Id<? extends CustomPayload> getId(){return ID;}

    public void apply(ServerPlayerEntity player) {
        var screen=player.playerScreenHandler;
        if(!player.isAlive()||player.isSpectator()) return;
        int now=player.getServer().getTicks();
        var budget=budgets.get(player);
        if(budget==null||now<budget.start||now-budget.start>=20)budget=new Budget(now,0);
        if(budget.count>=128)return;
        budgets.put(player,new Budget(budget.start,budget.count+1));
        // REI may be used in any handled screen. This operation can only remove
        // the sender's current ordinary cursor, never write a slot or create items.
        if(slot==DELETE_CURSOR){
            var current=player.currentScreenHandler;
            if(button==current.syncId&&action==SlotActionType.PICKUP.ordinal()
                    &&!com.megawalls.minigame.LobbyItems.isBound(current.getCursorStack())
                    &&ItemStack.areEqual(current.getCursorStack(),ordinaryCursor))
                current.setCursorStack(ItemStack.EMPTY);
            current.syncState();return;
        }
        if(!player.isCreative()||player.currentScreenHandler!=screen)return;
        if(slot==CATALOGUE_CURSOR){
            if(button==0&&action==SlotActionType.PICKUP.ordinal()&&validOrdinary(player,ordinaryCursor)
                    &&!com.megawalls.minigame.LobbyItems.isBound(screen.getCursorStack()))
                screen.setCursorStack(ordinaryCursor.copy());
            screen.syncState();return;
        }
        if(slot==DELETE_SLOT){
            if(button==0&&ordinaryCursor.isEmpty()
                    &&(action==SlotActionType.PICKUP.ordinal()||action==SlotActionType.QUICK_MOVE.ordinal()))
                deleteOrdinary(player,action==SlotActionType.QUICK_MOVE.ordinal());
            screen.syncState();return;
        }
        if(action<0||action>=SlotActionType.values().length||slot!=-999&&(slot<5||slot>=screen.slots.size())) {screen.syncState();return;}
        var type=SlotActionType.values()[action];
        boolean valid=switch(type){
            case PICKUP,QUICK_MOVE,THROW -> button==0||button==1;
            case SWAP -> button>=0&&button<9||button==40;
            case CLONE -> button==2;
            case PICKUP_ALL -> button==0||button==1;
            case QUICK_CRAFT -> button>=0&&button<=10&&ScreenHandler.unpackQuickCraftStage(button)<=2;
        };
        if(!valid||!validOrdinary(player,ordinaryCursor)) {screen.syncState();return;}
        // Creative catalogue items may exist only on the client cursor. Import only
        // ordinary creative items; the server's bound cursor always takes precedence.
        if(!com.megawalls.minigame.LobbyItems.isBound(screen.getCursorStack()))screen.setCursorStack(ordinaryCursor.copy());
        EquipmentHandler.bind(player);
        screen.onSlotClick(slot,button,type,player);
        screen.syncState(); // vanilla cursor-only updates are ignored by CreativeInventoryScreen
    }
    private static boolean validOrdinary(ServerPlayerEntity player,ItemStack stack){
        return !com.megawalls.minigame.LobbyItems.isBound(stack)&&stack.getCount()<=stack.getMaxCount()
                &&stack.isItemEnabled(player.getWorld().getEnabledFeatures());
    }
    /** Delete creative contents without creating drops or removing game controls and kit. */
    public static void deleteOrdinary(net.minecraft.entity.player.PlayerEntity player,boolean all){
        var screen=player.playerScreenHandler;
        if(!com.megawalls.minigame.LobbyItems.isBound(screen.getCursorStack()))screen.setCursorStack(ItemStack.EMPTY);
        if(all)for(int i=0;i<player.getInventory().size();i++)
            if(!com.megawalls.minigame.LobbyItems.isBound(player.getInventory().getStack(i)))
                player.getInventory().setStack(i,ItemStack.EMPTY);
    }
}
