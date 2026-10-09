package com.megawalls.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MWNameTagMixin {
    @org.spongepowered.asm.mixin.injection.Redirect(method = "render", at = @At(value="INVOKE",target="Lnet/minecraft/entity/Entity;getDisplayName()Lnet/minecraft/text/Text;"))
    private Text megawalls$nativeLabel(Entity entity) {
        Text original=entity.getDisplayName();
        if (!(entity instanceof PlayerEntity player)) return original;
        var network = MinecraftClient.getInstance().getNetworkHandler();
        if (network == null) return original;
        var entry = network.getPlayerListEntry(player.getUuid());
        return entry != null && entry.getDisplayName() != null ? entry.getDisplayName() : original;
    }
}
