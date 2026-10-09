package com.megawalls.game;

import com.megawalls.player.MWPlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;

/** Scoped combat rules for active Mega Walls players; no world-wide combat configuration. */
public final class MWLegacyCombat {
    private static final String BLOCKING_TAG="mw_legacy_block";
    private MWLegacyCombat() {}

    public static boolean active(PlayerEntity player) {
        if(player instanceof ServerPlayerEntity serverPlayer)return GameState.active(serverPlayer);
        var data=MWPlayerData.getClient(player.getUuid());
        return player.getWorld().isClient&&data!=null&&data.isInGame()
                &&player.isAlive()&&!player.isSpectator();
    }

    public static boolean swordBlocking(PlayerEntity player) {
        return active(player)&&player.isUsingItem()&&player.getActiveItem().getItem() instanceof SwordItem;
    }

    public static boolean hasBlockingTag(ItemStack stack) {
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        return stack.getItem() instanceof SwordItem&&data!=null&&data.copyNbt().getBoolean(BLOCKING_TAG);
    }

    public static void markBlocking(ItemStack stack) {
        var data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        data.putBoolean(BLOCKING_TAG,true);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
    }

    public static void unmarkBlocking(ItemStack stack) {
        if(!hasBlockingTag(stack))return;
        var data=stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
        data.remove(BLOCKING_TAG);
        if(data.isEmpty())stack.remove(DataComponentTypes.CUSTOM_DATA);
        else stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
    }

    /** Also runs outside MW, so leaving a game cannot leave a blocking item active. */
    public static void tick(PlayerEntity player) {
        boolean active=active(player);
        if(!active&&hasBlockingTag(player.getActiveItem()))player.stopUsingItem();
        for(int i=0;i<player.getInventory().size();i++){
            var stack=player.getInventory().getStack(i);
            if(!active||!player.isUsingItem()||stack!=player.getActiveItem())unmarkBlocking(stack);
        }
        unmarkBlocking(player.currentScreenHandler.getCursorStack());
    }
}
