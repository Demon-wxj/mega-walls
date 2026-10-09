package com.megawalls.mixin;

import com.megawalls.network.C2S_UseSkill;
import com.megawalls.network.NetworkHandler;
import com.megawalls.client.keybind.KeyBindings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual attack/use actions cover air, blocks and entities without consuming bow use. */
@Mixin(MinecraftClient.class)
public abstract class MWSkillInputMixin {
    private boolean mw$compass(boolean attack){
        var c=(MinecraftClient)(Object)this;
        if(c.player==null||c.currentScreen!=null||c.player.isSpectator()||!KeyBindings.inSession()||!c.player.getMainHandStack().isOf(Items.COMPASS)||!ClientPlayNetworking.canSend(NetworkHandler.USE_SKILL_ID))return false;
        if(KeyBindings.consumeItemPress(c,attack))ClientPlayNetworking.send(new C2S_UseSkill(attack?3:4));return true;
    }
    private void mw$request(boolean attack) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        if (client.player == null || client.currentScreen != null || client.player.isSpectator()) return;
        Item item = client.player.getMainHandStack().getItem();
        var data = com.megawalls.player.MWPlayerData.getClientLocal();
        boolean eligible = attack ? item instanceof BowItem : item instanceof SwordItem
                || item instanceof ShovelItem && data != null && data.getClassId() == 12;
        if (eligible && ClientPlayNetworking.canSend(NetworkHandler.USE_SKILL_ID) && KeyBindings.consumeItemPress(client,attack))
            KeyBindings.requestSkill(client, attack ? 2 : 1);
    }
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable=true)
    private void mw$attack(CallbackInfoReturnable<Boolean> cir) { if(mw$compass(true)){cir.setReturnValue(false);return;} mw$request(true); }
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable=true)
    private void mw$use(CallbackInfo ci) { if(mw$compass(false)){ci.cancel();return;} mw$request(false); }
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void mw$compassNoMining(boolean breaking,CallbackInfo ci){
        var c=(MinecraftClient)(Object)this;
        if(c.player!=null&&!c.player.isSpectator()&&KeyBindings.inSession()&&c.player.getMainHandStack().isOf(Items.COMPASS)&&ClientPlayNetworking.canSend(NetworkHandler.USE_SKILL_ID))ci.cancel();
    }
}
