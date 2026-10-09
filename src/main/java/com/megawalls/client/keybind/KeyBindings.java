package com.megawalls.client.keybind;

import com.megawalls.network.C2S_UseSkill;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定
 * 默认技能键：V（可在设置中修改）
 * 按技能键时向服务端发送使用技能请求
 */
public class KeyBindings {
    public static KeyBinding useSkillKey;
    public static KeyBinding menuKey;
    private static boolean skillHeld, menuHeld, attackHandled, useHandled;
    private static int lastSkillTick = Integer.MIN_VALUE;

    public static String skillName() { return useSkillKey == null ? "V" : useSkillKey.getBoundKeyLocalizedText().getString(); }
    public static String menuName() { return menuKey == null ? "M" : menuKey.getBoundKeyLocalizedText().getString(); }

    public static boolean inSession() {
        var data = com.megawalls.player.MWPlayerData.getClientLocal();
        return data != null && (data.getMatch().contains("in_session")
                ? data.getMatch().getBoolean("in_session") : data.getPhase() >= 0);
    }

    public static void reset() { skillHeld = menuHeld = attackHandled = useHandled = false; lastSkillTick = Integer.MIN_VALUE; }

    public static void requestSkill(MinecraftClient client, int input) {
        if (client.player == null || client.player.age == lastSkillTick) return;
        if (!net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.megawalls.network.NetworkHandler.USE_SKILL_ID)) return;
        lastSkillTick = client.player.age;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new C2S_UseSkill(input));
    }

    /** Vanilla repeats item use while held; skill/compass inputs are press actions. */
    public static boolean consumeItemPress(MinecraftClient client, boolean attack) {
        if (!inSession() || client.player == null || client.currentScreen != null || client.player.isSpectator()) return false;
        if (attack ? attackHandled : useHandled) return false;
        if (attack) attackHandled = true; else useHandled = true;
        return true;
    }

    private static boolean drain(KeyBinding key) {
        boolean pressed = false;
        while (key.wasPressed()) pressed = true;
        return pressed;
    }

    public static void register() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.megawalls.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.megawalls"));
        useSkillKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.megawalls.use_skill",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.megawalls"
        ));
    }

    public static void tick(MinecraftClient client) {
        boolean menuPress = drain(menuKey), skillPress = drain(useSkillKey);
        if (!client.options.attackKey.isPressed()) attackHandled = false;
        if (!client.options.useKey.isPressed()) useHandled = false;
        if (menuPress && !menuHeld && client.player != null && client.currentScreen == null) {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.megawalls.network.NetworkHandler.USE_SKILL_ID))
                {if(inSession())client.setScreen(new com.megawalls.client.screen.MWMenuScreen());else client.player.networkHandler.sendChatCommand("games");}
            else client.player.sendMessage(Text.literal("当前服务器没有启用超级战墙。"), true);
        }
        if (skillPress && !skillHeld) {
            if (inSession() && client.player != null && client.player.isAlive() && !client.player.isSpectator()
                    && client.world != null && client.currentScreen == null
                    && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.megawalls.network.NetworkHandler.USE_SKILL_ID)) {
                // 向服务端发送使用技能请求
                requestSkill(client, 0);
            }
        }
        menuHeld = menuKey.isPressed();
        skillHeld = useSkillKey.isPressed();
    }
}
