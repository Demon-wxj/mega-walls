package com.megawalls.client.screen;

import com.megawalls.fun.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public final class MWFunScreen extends Screen {
    private static int localFlags;
    private static int localSpeed = 1;
    private int flags, reach, speed;
    private boolean pending;
    private MWFunScreen(FunPayload state) {
        super(Text.literal("个人娱乐面板")); flags = state.flags(); reach = state.reach(); speed = state.speed();
    }
    public static void register() {
        com.megawalls.client.FunSpinClient.register();
        ClientPlayNetworking.registerGlobalReceiver(FunPayload.ID, (state, context) -> context.client().execute(() -> {
            localFlags = state.flags();
            localSpeed = state.speed();
            if (state.open()) context.client().setScreen(new MWFunScreen(state));
            else if (context.client().currentScreen instanceof MWFunScreen screen) {
                screen.flags = state.flags(); screen.reach = state.reach(); screen.speed = state.speed();
                screen.pending = false; screen.clearAndInit();
            }
        }));
        // Keep the old payload channel readable for protocol compatibility, but never turn the camera.
        ClientPlayNetworking.registerGlobalReceiver(FunLookPayload.ID, (look, context) -> {});
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { localFlags = 0; localSpeed = 1; });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            var p = client.player;
            if (p == null || !p.isAlive() || p.isSpectator() || client.currentScreen != null || p.hasVehicle()) return;
            boolean moving = client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed() || client.options.rightKey.isPressed();
            // Vanilla airborne steering does not scale with MOVEMENT_SPEED. Keep the selected
            // entertainment speed during hopping as well, using ordinary collision-aware movement.
            if ((localFlags & FunMode.HOP) != 0 && localSpeed > 1 && moving && !p.getAbilities().flying
                    && !p.isTouchingWater() && !p.isInLava() && !p.isClimbing() && !client.options.sneakKey.isPressed()) {
                double forward = (client.options.forwardKey.isPressed() ? 1 : 0) - (client.options.backKey.isPressed() ? 1 : 0);
                double strafe = (client.options.leftKey.isPressed() ? 1 : 0) - (client.options.rightKey.isPressed() ? 1 : 0);
                double length = Math.hypot(forward, strafe);
                if (length > 0) {
                    double angle = Math.toRadians(p.getYaw()), amount = 0.215 * localSpeed / length;
                    p.setVelocity((strafe * Math.cos(angle) - forward * Math.sin(angle)) * amount,
                        p.getVelocity().y, (forward * Math.cos(angle) + strafe * Math.sin(angle)) * amount);
                }
            }
            if ((localFlags & FunMode.HOP) != 0 && moving && p.isOnGround() && !p.getAbilities().flying
                    && !p.isTouchingWater() && !p.isClimbing() && !client.options.sneakKey.isPressed()) p.jump();
        });
    }
    private void update(int newFlags, int newReach, int newSpeed) {
        if (pending || !ClientPlayNetworking.canSend(FunPayload.ID)) return;
        pending = true;
        for (var child : children()) if (child instanceof ButtonWidget button) button.active = false;
        ClientPlayNetworking.send(new FunPayload(newFlags, newReach, newSpeed, false));
    }
    private String toggle(String name, int bit) { return name + ((flags & bit) != 0 ? " [开启]" : " [关闭]"); }
    private void button(String label, String tip, int column, int row, Runnable action) {
        int x = width / 2 - 144 + column * 148, y = Math.max(28, height / 2 - 96) + row * 29;
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, 140, 22).tooltip(Tooltip.of(Text.literal(tip))).build());
    }
    @Override protected void init() {
        button(toggle("杀戮光环", FunMode.AURA), "暴力模式：每 tick 自动攻击最多 8 个范围内可见敌人，不等待武器冷却；保留队伍和受伤无敌时间规则。", 0, 1, () -> update(flags ^ FunMode.AURA, reach, speed));
        button(toggle("超级搭路", FunMode.BRIDGE), "主手或副手拿完整方块，按移动路径消耗物品铺路；真实跳跃跨过整格后向上铺一层。", 1, 1, () -> update(flags ^ FunMode.BRIDGE, reach, speed));
        button(toggle("空中行走", FunMode.AIR), "空格上升、Shift 下降；飞行时不执行地面连跳。", 0, 2, () -> update(flags ^ FunMode.AIR, reach, speed));
        button("攻击距离 " + (reach == 3 ? "原版" : "+" + (reach - 3) + " 格"), "点击循环原版、+3、+6、+9 格，手动攻击也生效。", 1, 2, () -> update(flags, reach == 12 ? 3 : reach + 3, speed));
        button("移动速度 " + speed + " 倍", "点击循环 1、2、4、8 倍，同时影响步行与飞行。", 0, 3, () -> update(flags, reach, speed == 8 ? 1 : speed * 2));
        button(toggle("自动连跳", FunMode.HOP), "按移动键时落地自动跳起；Shift 暂停连跳。", 1, 3, () -> update(flags ^ FunMode.HOP, reach, speed));
        button(toggle("旋转转头", FunMode.SPIN), "其他玩家看到你快速旋转摆头；自己的镜头、瞄准和移动方向保持正常。", 0, 4, () -> update(flags ^ FunMode.SPIN, reach, speed));
        button("一键旋转跳打", "开启光环、连跳、旋转和 8 倍移动，攻击距离 +6 格；关闭飞行以便连跳。", 1, 4, () -> update((flags | FunMode.AURA | FunMode.HOP | FunMode.SPIN) & ~FunMode.AIR, 9, 8));
        button("全部关闭", "恢复默认攻击距离、速度及原有飞行权限。", 0, 5, () -> update(0, 3, 1));
        button("返回游戏", "关闭面板，已开启的功能继续生效。", 1, 5, this::close);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xEE101521);
        int y = Math.max(28, height / 2 - 96);
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, y - 14, 0xFFFFCC66);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("仅本人设置 · 不发送全服通知"), width / 2, y + 4, 0xFFB8C9DE);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("悬停查看说明 · 动作仍对其他玩家可见"), width / 2, y + 178, 0xFF9AAFC5);
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.Drawable drawable) drawable.render(c, mx, my, delta);
    }
}
