package com.megawalls.maintenance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record SettingsPayload(NbtCompound data) implements CustomPayload {
    public static final Id<SettingsPayload> ID = new Id<>(Identifier.of("megawalls", "match_settings_v1"));
    public static final PacketCodec<RegistryByteBuf, SettingsPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeNbt(value.data()), buf -> new SettingsPayload(buf.readNbt()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
