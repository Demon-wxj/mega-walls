package com.megawalls.network;

import com.megawalls.player.MWPlayerData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/**
 * S2C：服务端同步玩家技能数据到客户端
 * 包含职业ID、能量、冷却、等级，用于客户端HUD显示
 */
public record S2C_SkillSync(UUID playerUuid, NbtCompound data) implements CustomPayload {
    public static final PacketCodec<RegistryByteBuf, S2C_SkillSync> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeUuid(value.playerUuid());
                buf.writeNbt(value.data());
            },
            buf -> new S2C_SkillSync(buf.readUuid(), buf.readNbt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return NetworkHandler.SKILL_SYNC_ID;
    }

    public MWPlayerData toPlayerData() {
        return MWPlayerData.fromNbt(data);
    }
}
