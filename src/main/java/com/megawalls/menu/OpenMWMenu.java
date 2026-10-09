package com.megawalls.menu;

import com.megawalls.MWMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record OpenMWMenu(int page) implements CustomPayload {
    public static final Id<OpenMWMenu> ID = new Id<>(MWMod.id("open_mw_menu"));
    public static final PacketCodec<RegistryByteBuf, OpenMWMenu> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeInt(value.page()), buf -> new OpenMWMenu(buf.readInt()));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
