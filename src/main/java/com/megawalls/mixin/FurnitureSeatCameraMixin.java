package com.megawalls.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stationary furniture uses the same immediate local camera yaw as walking. */
@Mixin(ClientPlayerEntity.class)
public abstract class FurnitureSeatCameraMixin {
    private static final Identifier MW_TABLETOP_CHAIR = Identifier.of("tabletop", "tabletop_chair");

    @Inject(method = "getYaw(F)F", at = @At("HEAD"), cancellable = true)
    private void mw$seatedView(float tickDelta, CallbackInfoReturnable<Float> cir) {
        var player = (ClientPlayerEntity) (Object) this;
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        boolean furniture = switch (vehicle.getClass().getName()) {
            case "earth.terrarium.handcrafted.common.entities.Seat",
                 "com.berksire.furniture.core.entity.ChairEntity",
                 "com.yuushya.entity.ChairEntity",
                 "net.satisfy.meadow.core.entity.ChairEntity",
                 "org.betterx.bclib.furniture.entity.EntityChair" -> true;
            default -> vehicle instanceof ArmorStandEntity stand && stand.isMarker()
                    && MW_TABLETOP_CHAIR.equals(Registries.BLOCK.getId(
                    vehicle.getWorld().getBlockState(vehicle.getBlockPos()).getBlock()));
        };
        // Mounted vanilla players interpolate model head angles, which furniture updates
        // only on ticks (or clamps independently). Do not use those angles for local input.
        if (furniture) cir.setReturnValue(player.getYaw());
    }
}
