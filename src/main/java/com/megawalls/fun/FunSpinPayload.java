package com.megawalls.fun;

import com.megawalls.MWMod;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Public cosmetic pose of an entity already tracked by the receiving observer. */
public record FunSpinPayload(int entityId,UUID entityUuid,float yaw,float pitch,boolean enabled) implements CustomPayload {
    public static final Id<FunSpinPayload> ID=new Id<>(MWMod.id("fun_spin_pose"));
    public static final PacketCodec<RegistryByteBuf,FunSpinPayload> CODEC=PacketCodec.of(
        (value,buf)->{buf.writeVarInt(value.entityId);buf.writeUuid(value.entityUuid);buf.writeFloat(value.yaw);buf.writeFloat(value.pitch);buf.writeBoolean(value.enabled);},
        buf->new FunSpinPayload(buf.readVarInt(),buf.readUuid(),buf.readFloat(),buf.readFloat(),buf.readBoolean()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
