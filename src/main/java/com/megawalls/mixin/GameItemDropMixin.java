package com.megawalls.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
@Mixin(net.minecraft.server.network.ServerPlayerEntity.class)
public abstract class GameItemDropMixin {
    @Inject(method="dropSelectedItem",at=@At("HEAD"),cancellable=true)
    private void game$keepControl(boolean entire,CallbackInfoReturnable<Boolean> cir){
        var p=(net.minecraft.server.network.ServerPlayerEntity)(Object)this;
        if(com.megawalls.minigame.LobbyItems.isControl(p.getMainHandStack())){p.currentScreenHandler.syncState();cir.setReturnValue(false);}
    }
    @Inject(method="dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void game$noControlEntity(ItemStack stack,boolean random,boolean retain,CallbackInfoReturnable<ItemEntity> cir){
        if(com.megawalls.minigame.LobbyItems.isControl(stack))cir.setReturnValue(null);
    }
    // PuzzlesLib captures death drops before spawnEntity and returns early; a RETURN
    // injection misses that path. Tag the newly-created entity before either branch.
    @ModifyExpressionValue(method="dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;"))
    private ItemEntity game$drop(ItemEntity item){
        com.megawalls.minigame.Isolation.dropped((net.minecraft.server.network.ServerPlayerEntity)(Object)this,item);
        return item!=null&&item.isRemoved()?null:item;
    }
}

