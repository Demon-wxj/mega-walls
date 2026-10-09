package com.megawalls.fun;

import com.megawalls.MWMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: desired settings; S2C: authoritative settings and whether to open the screen. */
public record FunPayload(int flags, int reach, int speed, boolean open) implements CustomPayload {
    public FunPayload(int flags, int reach, boolean open) { this(flags, reach, 1, open); }
    public static final Id<FunPayload> ID = new Id<>(MWMod.id("fun_settings_v2"));
    public static final PacketCodec<RegistryByteBuf, FunPayload> CODEC = PacketCodec.of(
        (value, buf) -> { buf.writeInt(value.flags); buf.writeInt(value.reach); buf.writeInt(value.speed); buf.writeBoolean(value.open); },
        buf -> new FunPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
