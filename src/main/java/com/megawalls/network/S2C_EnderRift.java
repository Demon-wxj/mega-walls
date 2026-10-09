package com.megawalls.network;

import com.megawalls.MWMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** One successful teleport, two immutable world-space visual anchors. Never a gameplay request. */
public record S2C_EnderRift(Identifier dimension, UUID castId, UUID caster,
                            Vec3d origin, Vec3d destination, float originYaw, float destinationYaw,
                            long serverTick) implements CustomPayload {
    public static final Id<S2C_EnderRift> ID = new Id<>(MWMod.id("ender_rift_v1"));
    public static final int LIFETIME_TICKS = 24;
    public static final double VIEW_DISTANCE = 64;

    public boolean valid() {
        return finite(origin) && finite(destination) && origin.squaredDistanceTo(destination) <= 32 * 32
                && Float.isFinite(originYaw) && Float.isFinite(destinationYaw);
    }

    private static boolean finite(Vec3d p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 20_000_000;
    }

    public static final PacketCodec<RegistryByteBuf, S2C_EnderRift> CODEC = PacketCodec.of((p, b) -> {
        b.writeIdentifier(p.dimension); b.writeUuid(p.castId); b.writeUuid(p.caster);
        writeVector(b, p.origin); writeVector(b, p.destination);
        b.writeFloat(p.originYaw); b.writeFloat(p.destinationYaw); b.writeLong(p.serverTick);
    }, b -> new S2C_EnderRift(b.readIdentifier(), b.readUuid(), b.readUuid(),
            readVector(b), readVector(b), b.readFloat(), b.readFloat(), b.readLong()));

    private static void writeVector(RegistryByteBuf b, Vec3d p) {
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
    }

    private static Vec3d readVector(RegistryByteBuf b) {
        return new Vec3d(b.readDouble(), b.readDouble(), b.readDouble());
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
