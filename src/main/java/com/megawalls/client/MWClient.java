package com.megawalls.client;

import com.megawalls.client.hud.SkillBarHud;
import com.megawalls.client.keybind.KeyBindings;
import com.megawalls.network.*;
import com.megawalls.player.MWPlayerData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class MWClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.megawalls.skill.SkillModels.ARROW,net.minecraft.client.render.entity.ArrowEntityRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.megawalls.skill.SkillModels.IRON,net.minecraft.client.render.entity.FallingBlockEntityRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.megawalls.skill.SkillModels.BOLT,net.minecraft.client.render.entity.LightningEntityRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.megawalls.skill.SkillModels.SKULL,SkillProjectileRenderers.Skull::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.megawalls.skill.SkillModels.FIREBALL,SkillProjectileRenderers.Fireball::new);
        KeyBindings.register();
        EnderRiftRenderer.register();
        ClientSkillCinematics.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            EnderRiftRenderer.preload(client);
            ClientSkillCinematics.preload(client);
        });
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public net.minecraft.util.Identifier getFabricId() { return com.megawalls.MWMod.id("ender_rift_reset"); }
                    @Override public void reload(net.minecraft.resource.ResourceManager manager) {
                        ClientEnderRifts.reset();
                        ClientSkillEffects.reset();
                    }
                });
        ClientPlayNetworking.registerGlobalReceiver(S2C_EnderRift.ID, (payload, context) ->
                ClientEnderRifts.play(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(S2C_SkillPattern.ID,(payload,context)->ClientSkillEffects.play(context.client(),payload));
        com.megawalls.client.screen.MWFunScreen.register();
        HudRenderCallback.EVENT.register(new SkillBarHud());
        ClientPlayNetworking.registerGlobalReceiver(com.megawalls.menu.OpenMWMenu.ID, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new com.megawalls.client.screen.MWMenuScreen())));
        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::tick);
        ClientTickEvents.END_CLIENT_TICK.register(SkillBarHud::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ClientSkillEffects::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ClientEnderRifts::tick);
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.SKILL_SYNC_ID, (payload, context) -> {
            context.client().execute(() -> {
                var data = payload.toPlayerData();
                MWPlayerData.setClient(payload.playerUuid(), data);
                if (context.player().getUuid().equals(payload.playerUuid())) MWPlayerData.setClientLocal(data);
            });
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MWPlayerData.clearClient();
            KeyBindings.reset();
            SkillBarHud.reset();
            ClientSkillEffects.reset();
            ClientEnderRifts.reset();
        });
    }
}
