package com.megawalls.mixin;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.*;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerPlayerInteractionManager.class)
public abstract class MWSmeltingMixin {
    @Redirect(method="tryBreakBlock",at=@At(value="INVOKE",target="Lnet/minecraft/block/Block;afterBreak(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/item/ItemStack;)V"))
    private void mw$smelt(Block block,World world,PlayerEntity player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool){
        if(player instanceof ServerPlayerEntity p&&com.megawalls.game.GameState.active(p)&&com.megawalls.player.MWPlayerData.getServer(p).getClassId()==8&&state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES)){
            player.incrementStat(net.minecraft.stat.Stats.MINED.getOrCreateStat(block));player.addExhaustion(.005f);
            for(var drop:Block.getDroppedStacks(state,p.getServerWorld(),pos,entity,p,tool))Block.dropStack(world,pos,new ItemStack(Items.IRON_INGOT,drop.getCount()));
            if(com.megawalls.passive.MiningTreasure.mayGather(p,pos)&&player.getRandom().nextFloat()<.05f){Item[] armor={Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};Block.dropStack(world,pos,new ItemStack(armor[player.getRandom().nextInt(4)]));}
        }else block.afterBreak(world,player,pos,state,entity,tool);
    }
}
