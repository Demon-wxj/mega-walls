package com.megawalls.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S：客户端请求释放技能
 * 客户端按技能键时发送，服务端验证后执行
 */
public record C2S_UseSkill(int slot) implements CustomPayload {
    public static final PacketCodec<RegistryByteBuf, C2S_UseSkill> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeInt(value.slot()),
            buf -> new C2S_UseSkill(buf.readInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return NetworkHandler.USE_SKILL_ID;
    }
}
