package com.megawalls.equipment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
/** Kit items never enter public death loot. Normal loot stays vanilla. */
public final class EquipmentHandler {
    public static final String EQUIPMENT_TAG="mw_kit";
    public static void register() {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((p,w,hand)->{
            var held=p.getStackInHand(hand);var equipment=net.minecraft.item.Equipment.fromStack(held);
            if(equipment!=null){
                var slot=equipment.getSlotType();
                if(slot==net.minecraft.entity.EquipmentSlot.HEAD||slot==net.minecraft.entity.EquipmentSlot.CHEST
                    ||slot==net.minecraft.entity.EquipmentSlot.LEGS||slot==net.minecraft.entity.EquipmentSlot.FEET){
                    var worn=p.getEquippedStack(slot).get(DataComponentTypes.CUSTOM_DATA);
                    if(worn!=null&&worn.copyNbt().getBoolean(EQUIPMENT_TAG))return net.minecraft.util.TypedActionResult.fail(held);
                }
            }
            return net.minecraft.util.TypedActionResult.pass(held);
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p,w,hand,entity,hit)->
            entity instanceof net.minecraft.entity.decoration.ArmorStandEntity && isEquipmentItem(p.getStackInHand(hand))
                ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) -> {
            if(alive) for(var stack:((PendingKitOwner)oldPlayer).mw$pendingKit())
                ((PendingKitOwner)player).mw$pendingKit().add(stack.copy());
            clearPending(oldPlayer);
        });
    }
    public static boolean isEquipmentItem(ItemStack stack) {
        if(stack==null||stack.isEmpty())return false;
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        return data!=null&&data.copyNbt().getBoolean(EQUIPMENT_TAG) || data!=null&&data.copyNbt().getBoolean("mg_bound");
    }
    public static void markAsEquipment(ItemStack stack) {
        if(stack==null||stack.isEmpty())return;
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        var nbt=data==null?new NbtCompound():data.copyNbt();nbt.putBoolean(EQUIPMENT_TAG,true);
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(nbt));
    }
    public static boolean ownedBy(ServerPlayerEntity player,ItemStack stack) {
        if(!isEquipmentItem(stack))return true;
        String owner=stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getString("mw_owner");
        return owner.isEmpty()||owner.equals(player.getUuidAsString());
    }
    /** Only worn MW armor is immovable. Weapons and unequipped kit keep normal inventory sorting. */
    public static boolean isLockedArmor(net.minecraft.entity.player.PlayerEntity player,net.minecraft.screen.slot.Slot slot) {
        if(slot==null||slot.inventory!=player.getInventory()||slot.getIndex()<36||slot.getIndex()>39)return false;
        var data=slot.getStack().get(DataComponentTypes.CUSTOM_DATA);
        return data!=null&&data.copyNbt().getBoolean(EQUIPMENT_TAG);
    }
    public static void bind(ServerPlayerEntity player) {
        for(int i=0;i<player.getInventory().size();i++) {
            var stack=player.getInventory().getStack(i);if(!isEquipmentItem(stack))continue;
            if(!ownedBy(player,stack)){player.getInventory().setStack(i,ItemStack.EMPTY);continue;}
            var nbt=stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            if(!nbt.contains("mw_owner")){nbt.putString("mw_owner",player.getUuidAsString());stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(nbt));}
            normalizeKit(stack);
        }
        normalizeKit(player.currentScreenHandler.getCursorStack());
        normalizeKit(player.playerScreenHandler.getCursorStack());
    }
    /** Armor locking is enforced by slot rules in both game modes, not an extra stat-changing enchantment. */
    private static void normalizeKit(ItemStack stack) {
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        if(data==null||!data.copyNbt().getBoolean(EQUIPMENT_TAG))return;
        // Cosmetic only: preserve actual enchantments and combat attributes.
        if(!Boolean.TRUE.equals(stack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)))
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);
        // User-selected exception to the March 2017 shop-pickaxe durability rule.
        if(stack.isOf(net.minecraft.item.Items.DIAMOND_PICKAXE)&&!stack.contains(DataComponentTypes.UNBREAKABLE))
            stack.set(DataComponentTypes.UNBREAKABLE,new net.minecraft.component.type.UnbreakableComponent(true));
        var enchants=stack.get(DataComponentTypes.ENCHANTMENTS);
        if(enchants!=null&&enchants.getEnchantments().stream().anyMatch(e->e.matchesKey(net.minecraft.enchantment.Enchantments.BINDING_CURSE)))
            net.minecraft.enchantment.EnchantmentHelper.apply(stack,builder->builder.remove(e->e.matchesKey(net.minecraft.enchantment.Enchantments.BINDING_CURSE)));
    }
    /** Inserts without PlayerInventory's creative-mode overflow deletion. */
    private static void insertSafely(ServerPlayerEntity player, ItemStack stack) {
        var inventory=player.getInventory();
        for(int pass=0;pass<2&&!stack.isEmpty();pass++)for(int i=0;i<36&&!stack.isEmpty();i++){
            var target=inventory.getStack(i);
            if(pass==0&&!target.isEmpty()&&ItemStack.areItemsAndComponentsEqual(stack,target)){
                int moved=Math.min(stack.getCount(),Math.min(target.getMaxCount(),inventory.getMaxCountPerStack())-target.getCount());
                if(moved>0){target.increment(moved);stack.decrement(moved);}
            }else if(pass==1&&target.isEmpty()){
                int moved=Math.min(stack.getCount(),Math.min(stack.getMaxCount(),inventory.getMaxCountPerStack()));
                inventory.setStack(i,stack.split(moved));
            }
        }
        inventory.markDirty();
    }
    public static void returnOrReserve(ServerPlayerEntity player, ItemStack stack) {
        if(stack.isEmpty()||!ownedBy(player,stack))return;
        insertSafely(player,stack);
        if(!stack.isEmpty()){
            ((PendingKitOwner)player).mw$pendingKit().add(stack.copy());
            stack.setCount(0);
            player.sendMessage(net.minecraft.text.Text.literal("背包已满，职业物品已暂存；腾出空位后会自动放回。"),true);
        }
        player.currentScreenHandler.sendContentUpdates();
    }
    public static void restorePending(ServerPlayerEntity player) {
        if(!player.isAlive()||player.isSpectator())return;
        var pending=((PendingKitOwner)player).mw$pendingKit();
        if(pending.isEmpty())return;
        for(var iterator=pending.iterator();iterator.hasNext();){
            var stack=iterator.next();
            if(!ownedBy(player,stack)){iterator.remove();continue;}
            normalizeKit(stack);insertSafely(player,stack);
            if(stack.isEmpty())iterator.remove();
        }
        player.currentScreenHandler.sendContentUpdates();
    }
    /** Call before a fresh kit or another game takes ownership of the player's inventory. */
    public static void clearPending(ServerPlayerEntity player) {
        ((PendingKitOwner)player).mw$pendingKit().clear();
    }
    public static void removeKit(ServerPlayerEntity player) {
        clearPending(player);
        for(int i=0;i<player.getInventory().size();i++)if(isEquipmentItem(player.getInventory().getStack(i)))player.getInventory().setStack(i,ItemStack.EMPTY);
        if(isEquipmentItem(player.currentScreenHandler.getCursorStack()))player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        if(isEquipmentItem(player.playerScreenHandler.getCursorStack()))player.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
    }
    public static boolean matchDeath(ServerPlayerEntity p) {
        return com.megawalls.game.GameState.participant(p)&&com.megawalls.game.MatchFlow.running(p.getServer());
    }
    public static void tickDroppedEquipment(net.minecraft.server.MinecraftServer server) {
        if(server.getTicks()%20==0)for(var player:server.getPlayerManager().getPlayerList())if(com.megawalls.minigame.GameSessions.mw(player)){bind(player);restorePending(player);}
    }
    /** Compatibility name: never mark ordinary acquired items as kit. */
    public static void markAllPlayerEquipment(ServerPlayerEntity player) {bind(player);}
}
