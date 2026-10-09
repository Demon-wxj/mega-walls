package com.megawalls.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class MWPlacedBlockMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",at=@At("HEAD"),cancellable=true)
    private void duel$place(ItemPlacementContext context,BlockState state,CallbackInfoReturnable<Boolean> cir){
        if(context.getWorld() instanceof ServerWorld w){var p=context.getPlayer();boolean member=p instanceof net.minecraft.server.network.ServerPlayerEntity sp&&com.megawalls.minigame.ClassicDuel.member(sp)&&!com.megawalls.minigame.Isolation.editor(sp,com.megawalls.minigame.ClassicDuel.ID);
            if((member||com.megawalls.minigame.DuelArena.contains(w,context.getBlockPos()))&&(!(p instanceof net.minecraft.server.network.ServerPlayerEntity sp)||!com.megawalls.minigame.ClassicDuel.fighter(sp)||!com.megawalls.minigame.DuelArena.editable(w,context.getBlockPos())))cir.setReturnValue(false);
        }
    }
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",at=@At("RETURN"))
    private void mw$placed(ItemPlacementContext context,BlockState state,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValueZ()&&context.getWorld() instanceof ServerWorld world)
            com.megawalls.passive.MiningTreasure.placed(world,context.getBlockPos(),state);
    }
}
