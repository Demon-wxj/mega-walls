package com.megawalls.fun;

import com.megawalls.MWMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-selected attack direction, delivered only to the attacker. */
public record FunLookPayload(float yaw, float pitch) implements CustomPayload {
    public static final Id<FunLookPayload> ID = new Id<>(MWMod.id("fun_look"));
    public static final PacketCodec<RegistryByteBuf, FunLookPayload> CODEC = PacketCodec.of(
        (value, buf) -> { buf.writeFloat(value.yaw); buf.writeFloat(value.pitch); },
        buf -> new FunLookPayload(buf.readFloat(), buf.readFloat()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
