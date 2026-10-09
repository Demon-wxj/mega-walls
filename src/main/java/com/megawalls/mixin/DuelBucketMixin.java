package com.megawalls.mixin;
import com.megawalls.minigame.*;
import net.minecraft.item.BucketItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BucketItem.class)
public abstract class DuelBucketMixin {
    @Inject(method="placeFluid",at=@At("HEAD"),cancellable=true)
    private void duel$bucket(PlayerEntity player,World world,BlockPos pos,BlockHitResult hit,CallbackInfoReturnable<Boolean> cir){if(world instanceof ServerWorld w){boolean member=player instanceof ServerPlayerEntity p&&ClassicDuel.member(p)&&!Isolation.editor(p,ClassicDuel.ID);if((member||DuelArena.contains(w,pos))&&(!(player instanceof ServerPlayerEntity p)||!ClassicDuel.fighter(p)||!DuelArena.editable(w,pos)))cir.setReturnValue(false);}}
}
