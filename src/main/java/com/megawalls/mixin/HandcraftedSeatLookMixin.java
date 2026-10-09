package com.megawalls.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handcrafted 4.0.3 chairs: keep the seated body while allowing the head to turn. */
@Pseudo
@Mixin(targets = "earth.terrarium.handcrafted.common.entities.Seat", remap = false)
public abstract class HandcraftedSeatLookMixin {
    @Shadow(remap = false) private Box shape;
    @Unique private BlockState mw$seatState;
    @Unique private Box mw$seatShape;

    // Handcrafted 4.0.3 only discovers the shape in its position callback. The
    // spawn packet can precede the chair's chunk/block packet; an unmoving seat
    // then keeps a null or obsolete shape and uses the wrong passenger height.
    @Inject(method = {"tick", "method_5773"}, at = @At("HEAD"), remap = false, require = 1)
    private void mw$refreshClientSeatShape(CallbackInfo ci) {
        var seat = (Entity) (Object) this;
        if (!seat.getWorld().isClient) return;
        BlockState current = seat.getWorld().getBlockState(seat.getBlockPos());
        if (current != mw$seatState) {
            mw$seatState = current;
            mw$seatShape = null;
            try {
                // The optional mod is not a hard dependency. Resolve its own
                // public shape API only when the immutable block state changes.
                var method = current.getBlock().getClass().getMethod("getSeatSize", BlockState.class);
                mw$seatShape = (Box) method.invoke(current.getBlock(), current);
            } catch (NoSuchMethodException ignored) {
                // The supporting block has not arrived yet (or was removed).
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot read Handcrafted seat dimensions", error);
            }
        }
        if (mw$seatShape != null && shape != mw$seatShape) {
            shape = mw$seatShape;
            seat.setBoundingBox(shape.offset(seat.getBlockPos()));
        }
    }

    @Inject(method = "clampRotation", at = @At("HEAD"), cancellable = true, remap = false)
    private void mw$allowSeatedPlayerLook(Entity passenger, CallbackInfo ci) {
        if (passenger instanceof PlayerEntity && passenger.getVehicle() == (Object) this) {
            passenger.setBodyYaw(((Entity) (Object) this).getYaw());
            passenger.setHeadYaw(passenger.getYaw());
            ci.cancel();
        }
    }
}
