package com.megawalls.maintenance.client;
import com.megawalls.maintenance.SettingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public final class MaintenanceClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.megawalls.minigame.RescueTile.BLOCK, net.minecraft.client.render.RenderLayer.getTranslucent());
        ClientEffectTuning.load();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((h,s,c)->ClientEffectTuning.announce());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context->ClientEffectTuning.beginFrame());
        ClientTickEvents.END_CLIENT_TICK.register(ClientEffectTuning::tick);
        ClientPlayNetworking.registerGlobalReceiver(SettingsPayload.ID, (packet, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof MatchTuningScreen screen) screen.receive(packet.data());
        }));
    }
}
