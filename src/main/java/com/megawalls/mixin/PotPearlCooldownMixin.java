package com.megawalls.mixin;

import com.megawalls.minigame.PotDuel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(EnderPearlItem.class)
public abstract class PotPearlCooldownMixin {
    @Redirect(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V"))
    private void pot$cooldown(ItemCooldownManager manager,Item item,int ticks,World world,PlayerEntity player,Hand hand) {
        var data=player.getStackInHand(hand).get(DataComponentTypes.CUSTOM_DATA);
        if(player instanceof ServerPlayerEntity p && PotDuel.fighter(p))ticks=PotDuel.pearlCooldownTicks();
        else if(world.isClient && data!=null && data.copyNbt().contains("mw_pot_pearl_ticks"))ticks=data.copyNbt().getInt("mw_pot_pearl_ticks");
        manager.set(item,ticks);
    }
}
