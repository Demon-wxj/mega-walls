package com.megawalls.client.hud;

import com.megawalls.client.keybind.KeyBindings;
import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.SkillManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import java.util.Locale;
import java.util.UUID;

/** Server-synchronized skill feedback above the vanilla health/armor area. */
public class SkillBarHud implements HudRenderCallback {
    private static UUID owner;
    private static int lastClass, flashTicks;
    private static boolean wasReady;

    public static boolean hasClass(MWPlayerData d) {
        return d != null && d.getClassId() >= 1 && d.getClassId() <= 13
                && (d.getMatch().contains("in_session") ? d.getMatch().getBoolean("in_session") : d.getPhase() >= 0);
    }

    public static boolean ready(MWPlayerData d) {
        return hasClass(d) && d.getPhase() != 1 && (d.isInGame() || d.isPractice())
                && d.getEnergy() >= 100 && d.getSkillCooldown() == 0 && d.getSkillStatus().getInt("duration") == 0;
    }

    /** Ceiling prevents a still-active one-tick cooldown being displayed as zero. */
    public static String remaining(int ticks) { return Math.max(1, (ticks + 19) / 20) + "秒"; }

    public static void reset() { owner = null; lastClass = flashTicks = 0; wasReady = false; }

    public static void tick(MinecraftClient mc) {
        var d = MWPlayerData.getClientLocal();
        if (mc.player == null || !mc.player.isAlive() || mc.player.isSpectator() || !hasClass(d)) { reset(); return; }
        boolean current = ready(d);
        if (!mc.player.getUuid().equals(owner) || lastClass != d.getClassId()) {
            // Rejoining/changing class establishes a new baseline, not another charge event.
            owner = mc.player.getUuid(); lastClass = d.getClassId(); wasReady = current; flashTicks = 0;
            return;
        }
        if (flashTicks > 0) flashTicks--;
        if (current && !wasReady) {
            flashTicks = 20;
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 1.25f);
        }
        if (!current) flashTicks = 0;
        wasReady = current;
    }

    @Override public void onHudRender(DrawContext c, RenderTickCounter ticks) {
        var mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.isAlive() && !mc.player.isSpectator() && !mc.options.hudHidden && mc.currentScreen == null)
            renderCompact(c, MWPlayerData.getClientLocal());
    }

    public static String label(MWPlayerData d) {
        var skill = SkillManager.getSkill(d.getClassId()); if (skill == null) return "";
        var n = d.getSkillStatus();
        String state = d.getPhase() == 1 ? "准备中" : !d.isInGame() && !d.isPractice() ? "待战"
                : n.getInt("duration") > 0 ? "生效 " + remaining(n.getInt("duration"))
                : d.getSkillCooldown() > 0 ? "冷却 " + remaining(d.getSkillCooldown())
                : n.getInt("eps_hits_remaining") > 0 ? "回能需命中 " + n.getInt("eps_hits_remaining") + " 次"
                : ready(d) ? "就绪 [" + KeyBindings.skillName() + "]" : d.getEnergy() + "/100";
        return "§6" + skill.getName() + " §e" + state + " §8│ §b" + n.getString("hud_passive1") + " §8│ §b" + n.getString("hud_passive2");
    }

    public static void renderCompact(DrawContext c, MWPlayerData d) {
        if (!hasClass(d)) return;
        var mc = MinecraftClient.getInstance(); if (mc.player == null) return;
        // 1.21's LayeredDrawer raises each vanilla HUD layer by 200, then pops
        // its matrix before HudRenderCallback. Drawing here at Z=0 lets the
        // chat background and experience bar clip later text and borders.
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 4000);
        try {
            renderForeground(c, d, mc);
        } finally {
            c.getMatrices().pop();
        }
    }

    private static void renderForeground(DrawContext c, MWPlayerData d, MinecraftClient mc) {
        int width = mc.getWindow().getScaledWidth(), height = mc.getWindow().getScaledHeight();
        var line = Text.literal(label(d)); int textWidth = mc.textRenderer.getWidth(line);
        float scale = Math.min(.75f, (width - 24f) / Math.max(1, textWidth));
        // Account for vanilla's additional health and absorption rows before placing either line.
        int rows = (int) Math.ceil((Math.max(mc.player.getMaxHealth(), mc.player.getHealth()) + Math.ceil(mc.player.getAbsorptionAmount())) / 20d);
        int rowHeight = Math.max(3, 10 - (rows - 2));
        // Vanilla actionbar starts at height-68; keep our two lines above it,
        // including when a recent skill activation message is still fading.
        int bottom = Math.max(28, Math.min(height - 78, height - 49 - (rows - 1) * rowHeight - 2));
        c.getMatrices().push(); c.getMatrices().translate(width / 2f, bottom - 9 * scale, 0); c.getMatrices().scale(scale, scale, 1);
        c.drawTextWithShadow(mc.textRenderer, line, -textWidth / 2, 0, 0xFFFFFFFF); c.getMatrices().pop();

        String health = String.format(Locale.ROOT, "♥ %.1f / %.0f HP", mc.player.getHealth(), mc.player.getMaxHealth());
        if (mc.player.getAbsorptionAmount() > 0) health += String.format(Locale.ROOT, "  §e+%.1f 吸收", mc.player.getAbsorptionAmount());
        int healthWidth = mc.textRenderer.getWidth(health);
        float healthScale = Math.min(.75f, (width - 24f) / Math.max(1, healthWidth));
        c.getMatrices().push(); c.getMatrices().translate(width / 2f, bottom - 20 * healthScale, 0); c.getMatrices().scale(healthScale, healthScale, 1);
        c.drawTextWithShadow(mc.textRenderer, health, -healthWidth / 2, 0, mc.player.getHealth() <= 10 ? 0xFFFF7878 : 0xFFFFC6C6); c.getMatrices().pop();

        if (ready(d) && mc.player.getJumpingMount() == null && mc.interactionManager != null && mc.interactionManager.hasExperienceBar()) {
            int x = width / 2 - 91, y = height - 33;
            int gap=mc.textRenderer.getWidth(Integer.toString(d.getEnergy()))/2+3;
            // The vanilla level number crosses the bar. Keep that central text unobscured.
            if (flashTicks > 0) {
                int shine = Math.min(181, (20 - flashTicks) * 10);
                int start=x+shine,end=x+Math.min(182,shine+3);
                if(end<=width/2-gap||start>=width/2+gap)c.fill(start,y+1,end,y+6,0xBBFFF4BF);
            }
        }
    }
}
