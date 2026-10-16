package com.megawalls.fun;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Observer-only entertainment animation. The player's real aim and movement stay unchanged. */
public final class FunSpin {
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private FunSpin() {}

    /** Called after vanilla entity tracking, so the cosmetic rotation is the last update this tick. */
    public static void tick(ServerPlayerEntity player) {
        ACTIVE.add(player.getUuid());
        int tick = player.getServer().getTicks();
        float yaw = MathHelper.wrapDegrees((tick % 5) * 72.0f);
        float pitch = (float) (Math.sin(tick * Math.PI / 3) * 55);
        send(player, yaw, pitch, yaw,true);
    }

    /** Restore the authoritative direction immediately when disabled, without changing player state. */
    public static void clear(ServerPlayerEntity player) {
        if (ACTIVE.remove(player.getUuid()))
            send(player, player.getYaw(), player.getPitch(), player.getHeadYaw(),false);
    }

    private static void send(ServerPlayerEntity player, float yaw, float pitch, float headYaw,boolean spinning) {
        var rotation = new EntityS2CPacket.Rotate(player.getId(), encode(yaw), encode(pitch), player.isOnGround());
        var head = new EntitySetHeadYawS2CPacket(player, encode(headYaw));
        for (var observer : PlayerLookup.tracking(player)) {
            // Exclude explicitly: PlayerLookup's contract does not guarantee whether self is present.
            if (observer == player) continue;
            observer.networkHandler.sendPacket(rotation);
            observer.networkHandler.sendPacket(head);
            if(ServerPlayNetworking.canSend(observer,FunSpinPayload.ID))
                ServerPlayNetworking.send(observer,new FunSpinPayload(player.getId(),player.getUuid(),yaw,pitch,spinning));
        }
    }

    private static byte encode(float degrees) {
        return (byte) MathHelper.floor(degrees * 256.0f / 360.0f);
    }
}
