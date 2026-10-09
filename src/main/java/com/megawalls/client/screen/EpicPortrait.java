package com.megawalls.client.screen;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A stationary UI portrait; never registered as a world entity or a network player. */
public final class EpicPortrait {
    private static final Team PORTRAIT_TEAM = new Team(new Scoreboard(), "mw_ui_portrait");
    private static final Quaternionf UPRIGHT = new Quaternionf().rotationZ((float) Math.PI);
    private static final Quaternionf CAMERA = new Quaternionf();
    private static PortraitPlayer portrait;
    private static float targetYaw, targetPitch, lookYaw, lookPitch;
    private static long lastFrameNanos;

    static {
        // This private scoreboard has no relationship with the active world's teams.
        PORTRAIT_TEAM.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        // Also release the world if the menu closes before a disconnect or dimension change.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (portrait != null && (client.world != portrait.getWorld() || client.player == null
                    || !portrait.matches(PlayerIdentity.profile()))) clear();
        });
    }

    private EpicPortrait() {}

    /** Drop the portrait when the owning screen is removed. Safe to call repeatedly. */
    public static void clear() {
        portrait = null;
        targetYaw = targetPitch = lookYaw = lookPitch = 0;
        lastFrameNanos = 0;
        PlayerIdentity.clear();
    }

    /**
     * Draw within GUI-scaled coordinates. Scale is pixels per entity unit and is
     * capped to the provided rectangle, so the complete figure remains visible.
     */
    public static void render(DrawContext context, int x1, int y1, int x2, int y2, int scale,
                              float mouseX, float mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            clear();
            return;
        }
        GameProfile profile = PlayerIdentity.profile();
        if (portrait != null && (portrait.getWorld() != client.world || !portrait.matches(profile))) clear();
        if (x2 - x1 < 12 || y2 - y1 < 12 || scale <= 0) return;
        if (portrait == null) portrait = new PortraitPlayer(client.world, profile);

        int fit = Math.max(1, (int) Math.min(scale,
                Math.min((x2 - x1 - 8) / 1.2f, (y2 - y1 - 8) / 2.05f)));
        float centerX = (x1 + x2) * 0.5f, centerY = (y1 + y2) * 0.5f;
        float headY = centerY - fit * 0.6375f;
        targetYaw = MathHelper.clamp((float) Math.toDegrees(Math.atan(
                (centerX - mouseX) / Math.max(40.0f, (x2 - x1) * 0.65f))), -40.0f, 40.0f);
        targetPitch = MathHelper.clamp((float) Math.toDegrees(Math.atan(
                (mouseY - headY) / Math.max(60.0f, (y2 - y1) * 0.7f))), -15.0f, 15.0f);
        long now = System.nanoTime();
        double elapsed = lastFrameNanos == 0 ? 1.0 / 60.0
                : MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000.0, 0.0, 0.1);
        lastFrameNanos = now;
        float blend = (float) -Math.expm1(-elapsed / 0.12);
        lookYaw += (targetYaw - lookYaw) * blend;
        lookPitch += (targetPitch - lookPitch) * blend;
        portrait.setYaw(180.0f + lookYaw);
        portrait.prevYaw = portrait.getYaw();
        portrait.headYaw = portrait.prevHeadYaw = portrait.getYaw();
        portrait.bodyYaw = portrait.prevBodyYaw = 180.0f + lookYaw * 0.2f;
        portrait.setPitch(lookPitch);
        portrait.prevPitch = portrait.getPitch();

        var dispatcher = client.getEntityRenderDispatcher();
        Quaternionf previousRotation = new Quaternionf(dispatcher.getRotation());
        context.enableScissor(x1, y1, x2, y2);
        context.getMatrices().push();
        // The vanilla inventory helper uses z=50 for its small model. A large tilted
        // head can cross the GUI background's z=0 depth plane; keep this preview forward.
        context.getMatrices().translate(0.0f, 0.0f, 200.0f);
        try {
            // The body stays upright. Only this isolated entity's head and small body
            // yaw follow the cursor; matching previous angles avoids secondary interpolation.
            float entityScale = portrait.getScale();
            Vector3f offset = new Vector3f(0.0f, portrait.getHeight() * 0.5f + 0.0625f * entityScale, 0.0f);
            InventoryScreen.drawEntity(context, centerX, centerY, fit / entityScale,
                    offset, UPRIGHT, CAMERA, portrait);
        } finally {
            dispatcher.setRotation(previousRotation);
            context.getMatrices().pop();
            context.disableScissor();
        }
    }

    private static final class PortraitPlayer extends OtherClientPlayerEntity {
        private PortraitPlayer(ClientWorld world, GameProfile profile) {
            super(world, profile);
            setOnGround(true);
            setSilent(true);
        }

        private boolean matches(GameProfile profile) {
            return getUuid().equals(profile.getId()) && getGameProfile().getName().equals(profile.getName());
        }

        @Override public SkinTextures getSkinTextures() { return PlayerIdentity.skin(); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isPartVisible(PlayerModelPart part) { return true; }
        @Override public Team getScoreboardTeam() { return PORTRAIT_TEAM; }
    }
}
