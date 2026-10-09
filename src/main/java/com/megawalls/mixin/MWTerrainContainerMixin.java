package com.megawalls.mixin;

import com.megawalls.game.MatchTerrainJournal;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class MWTerrainContainerMixin {
    @Inject(method="interactBlock",at=@At("HEAD"),cancellable=true)
    private void mw$recordContainer(ServerPlayerEntity player,World world,ItemStack stack,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> cir){
        if(world instanceof ServerWorld w&&!MatchTerrainJournal.beforeContainer(w,hit.getBlockPos()))cir.setReturnValue(ActionResult.FAIL);
    }
}
