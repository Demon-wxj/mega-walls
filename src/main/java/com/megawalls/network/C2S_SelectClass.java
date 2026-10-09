package com.megawalls.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S：客户端选择职业
 * 客户端在GUI菜单中点击职业按钮时发送，服务端执行职业装备选择
 */
public record C2S_SelectClass(int classId) implements CustomPayload {
    public static final PacketCodec<RegistryByteBuf, C2S_SelectClass> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeInt(value.classId()),
            buf -> new C2S_SelectClass(buf.readInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return NetworkHandler.SELECT_CLASS_ID;
    }
}
