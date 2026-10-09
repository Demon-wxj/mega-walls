package com.megawalls.skill;

import com.megawalls.minigame.GameSessions;
import com.megawalls.network.S2C_EnderRift;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Cosmetic broadcast after teleport success; no entities, delayed server tasks, or combat changes. */
public final class EnderRiftEffects {
    private EnderRiftEffects() {}

    public static void teleported(ServerPlayerEntity player, Vec3d origin, float originYaw) {
        var world = player.getServerWorld();
        Vec3d destination = player.getPos();
        var event = new S2C_EnderRift(world.getRegistryKey().getValue(), UUID.randomUUID(), player.getUuid(),
                origin, destination, originYaw, player.getYaw(), world.getTime());
        double range = S2C_EnderRift.VIEW_DISTANCE * S2C_EnderRift.VIEW_DISTANCE;
        for (var observer : world.getPlayers()) {
            // Either endpoint can be in view. Terrain occlusion is handled by depth-tested rendering,
            // so moving around a corner during the short effect does not lose an already-sent endpoint.
            if (GameSessions.mw(observer) && (observer.squaredDistanceTo(origin) <= range
                    || observer.squaredDistanceTo(destination) <= range)
                    && ServerPlayNetworking.canSend(observer, S2C_EnderRift.ID)) {
                ServerPlayNetworking.send(observer, event);
            }
        }
        world.playSound(null, origin.x, origin.y + 1, origin.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, .48f, 1.55f);
        world.playSound(null, destination.x, destination.y + 1, destination.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, .6f, .72f);
    }
}
