package com.megawalls.network;

import com.megawalls.MWMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 网络包注册
 * C2S_UseSkill：客户端→服务端，请求释放技能
 * S2C_SkillSync：服务端→客户端，同步玩家技能数据
 */
public class NetworkHandler {
    // C2S：使用技能
    public static final CustomPayload.Id<C2S_UseSkill> USE_SKILL_ID =
            new CustomPayload.Id<>(MWMod.id("use_skill"));

    // S2C：同步技能数据
    public static final CustomPayload.Id<S2C_SkillSync> SKILL_SYNC_ID =
            new CustomPayload.Id<>(MWMod.id("skill_sync"));

    // C2S：选择职业
    public static final CustomPayload.Id<C2S_SelectClass> SELECT_CLASS_ID =
            new CustomPayload.Id<>(MWMod.id("select_class"));

    public static void register() {
        PayloadTypeRegistry.playS2C().register(S2C_EnderRift.ID, S2C_EnderRift.CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SkillPattern.ID,S2C_SkillPattern.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S_KitClick.ID,C2S_KitClick.CODEC);
        // The object-payload API already runs on the server thread. Preserve click/close ordering.
        ServerPlayNetworking.registerGlobalReceiver(C2S_KitClick.ID,(payload,context)->payload.apply(context.player()));
        // 注册 C2S 包
        PayloadTypeRegistry.playC2S().register(USE_SKILL_ID, C2S_UseSkill.CODEC);
        PayloadTypeRegistry.playC2S().register(SELECT_CLASS_ID, C2S_SelectClass.CODEC);
        // 注册 S2C 包
        PayloadTypeRegistry.playS2C().register(SKILL_SYNC_ID, S2C_SkillSync.CODEC);

        // 服务端接收使用技能请求
        ServerPlayNetworking.registerGlobalReceiver(USE_SKILL_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                int input = payload.slot();
                var item = player.getMainHandStack().getItem();
                if(input==3||input==4){com.megawalls.item.PlayerCompass.use(player,input==3);return;}
                if (input < 0 || input > 2) return;
                if (input == 1 && !(item instanceof net.minecraft.item.SwordItem || item instanceof net.minecraft.item.ShovelItem)) return;
                if (input == 2 && !(item instanceof net.minecraft.item.BowItem)) return;
                com.megawalls.skill.SkillManager.useSkill(player);
            });
        });

        // 服务端接收选择职业请求
        ServerPlayNetworking.registerGlobalReceiver(SELECT_CLASS_ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            int classId = payload.classId();
            context.server().execute(() -> {
                com.megawalls.menu.MenuHandler.selectClass(player, classId);
            });
        });
    }
}
