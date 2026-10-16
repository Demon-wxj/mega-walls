package com.megawalls.mixin;

import com.megawalls.minigame.GameSessions;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppress new vanilla advancement rewards/chat/toasts in map games, retaining saved progress. */
@Mixin(PlayerAdvancementTracker.class)
public abstract class GameAdvancementMixin {
    @Shadow private ServerPlayerEntity owner;
    @Inject(method="grantCriterion",at=@At("HEAD"),cancellable=true)
    private void mapGames$noAdvancements(AdvancementEntry advancement,String criterion,CallbackInfoReturnable<Boolean> cir){
        if(owner!=null&&(!GameSessions.game(owner).equals(GameSessions.HUB)||owner.getCommandTags().contains("mapreview_actor")))cir.setReturnValue(false);
    }
}
