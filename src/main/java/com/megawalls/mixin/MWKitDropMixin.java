package com.megawalls.mixin;
import net.minecraft.server.network.ServerPlayerEntity;
import com.megawalls.equipment.EquipmentHandler;
import com.megawalls.equipment.PendingKitOwner;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerEntity.class)
public abstract class MWKitDropMixin implements PendingKitOwner {
    @Unique private final List<ItemStack> mw$pendingKit=new ArrayList<>();
    @Override public List<ItemStack> mw$pendingKit(){return mw$pendingKit;}
    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void mw$savePending(NbtCompound nbt,CallbackInfo ci){
        var player=(ServerPlayerEntity)(Object)this;
        var entries=new NbtList();
        for(var stack:mw$pendingKit)if(!stack.isEmpty())entries.add(stack.encode(player.getRegistryManager()));
        if(!entries.isEmpty())nbt.put("mw_pending_kit",entries);else nbt.remove("mw_pending_kit");
    }
    @Inject(method="readCustomDataFromNbt",at=@At("TAIL"))
    private void mw$loadPending(NbtCompound nbt,CallbackInfo ci){
        var player=(ServerPlayerEntity)(Object)this;
        mw$pendingKit.clear();
        for(var entry:nbt.getList("mw_pending_kit",NbtElement.COMPOUND_TYPE))
            ItemStack.fromNbt(player.getRegistryManager(),entry).filter(stack->EquipmentHandler.isEquipmentItem(stack)&&EquipmentHandler.ownedBy(player,stack)).ifPresent(mw$pendingKit::add);
    }
    @Inject(method="dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void mw$noPublicKit(net.minecraft.item.ItemStack stack,boolean random,boolean retain,CallbackInfoReturnable<net.minecraft.entity.ItemEntity> cir){
        if(EquipmentHandler.isEquipmentItem(stack))cir.setReturnValue(null);
    }
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void mw$drop(boolean entire,CallbackInfoReturnable<Boolean> cir){
        var p=(ServerPlayerEntity)(Object)this;
        if(com.megawalls.minigame.PotDuel.inRound(p)||com.megawalls.minigame.ClassicDuel.inRound(p)||EquipmentHandler.isEquipmentItem(p.getMainHandStack())){p.currentScreenHandler.syncState();cir.setReturnValue(false);}
    }
}
