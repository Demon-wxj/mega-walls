package com.megawalls.mixin;

import com.megawalls.player.MWPlayerData;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse the vanilla bar visually, without modifying either side's actual experience. */
@Mixin(InGameHud.class)
public abstract class MWEnergyBarMixin {
    private static MWPlayerData mw$data(ClientPlayerEntity p){
        var d=MWPlayerData.getClientLocal();return p.isAlive()&&!p.isSpectator()&&com.megawalls.client.hud.SkillBarHud.hasClass(d)?d:null;
    }
    @Redirect(method="renderExperienceBar",at=@At(value="FIELD",target="Lnet/minecraft/client/network/ClientPlayerEntity;experienceProgress:F"))
    private float mw$progress(ClientPlayerEntity player){var d=mw$data(player);return d==null?player.experienceProgress:d.getEnergy()/100f;}
    @Redirect(method="renderExperienceLevel",at=@At(value="FIELD",target="Lnet/minecraft/client/network/ClientPlayerEntity;experienceLevel:I"))
    private int mw$level(ClientPlayerEntity player){var d=mw$data(player);return d==null?player.experienceLevel:d.getEnergy();}
}
