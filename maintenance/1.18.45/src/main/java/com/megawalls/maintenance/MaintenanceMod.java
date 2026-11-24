package com.megawalls.maintenance;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
public final class MaintenanceMod implements ModInitializer {
    @Override public void onInitialize() {
        com.megawalls.minigame.RescueTile.register();
        // A skull saved by vanilla across a restart can reload as a vanilla entity. Never let it change terrain.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity.getType()==net.minecraft.entity.EntityType.WITHER_SKULL&&entity.getCommandTags().contains("mg_source_bedwars")&&!(entity instanceof com.megawalls.minigame.BedWitherSkull))entity.discard();
        });
        PayloadTypeRegistry.playC2S().register(SettingsPayload.ID, SettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SettingsPayload.ID, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.ID, (packet, context) -> context.server().execute(() -> {
            var request = packet.data();
            if (request == null || request.getSize() > MatchTuning.RULES.size()+2) return;
            if(request.getSize()==1&&request.contains("visualMode",3)){VisualAudience.set(context.player(),request.getInt("visualMode"));return;}
            String message = request.getBoolean("apply") ? MatchTuning.apply(context.player(), request) : "";
            ServerPlayNetworking.send(context.player(), new SettingsPayload(MatchTuning.snapshot(context.player(), message)));
        }));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s)->VisualAudience.forget(h.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->VisualAudience.clear());
    }
}
