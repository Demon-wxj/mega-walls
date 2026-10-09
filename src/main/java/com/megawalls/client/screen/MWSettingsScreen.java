package com.megawalls.client.screen;

import com.megawalls.client.keybind.KeyBindings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.text.Text;

/** A small, reachable entry to the real Minecraft key binding settings. */
public final class MWSettingsScreen extends Screen {
    private final Screen parent;
    private int left, top, panelWidth, panelHeight;

    public MWSettingsScreen(Screen parent) { super(Text.literal("操作设置")); this.parent = parent; }

    @Override protected void init() {
        panelWidth = Math.min(440, width - 24);
        panelHeight = Math.min(220, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addDrawableChild(new EpicButton(left + 12, top + panelHeight - 64, panelWidth - 24, 26,
                Text.literal("打开按键绑定设置"), b -> client.setScreen(new KeybindsScreen(this, client.options)), EpicTheme.GOLD));
        addDrawableChild(new EpicButton(left + 12, top + panelHeight - 31, panelWidth - 24, 24,
                Text.literal("返回超级战墙"), b -> close(), EpicTheme.MUTED));
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        EpicTheme.background(c, width, height);
        EpicTheme.panel(c, left, top, panelWidth, panelHeight);
        EpicTheme.heading(c, textRenderer, "操作设置", "修改后自动使用新的按键", left + 12, top + 11);
        c.drawTextWithShadow(textRenderer, "打开菜单：" + KeyBindings.menuName(), left + 16, top + 51, EpicTheme.GOLD);
        c.drawTextWithShadow(textRenderer, "释放技能：" + KeyBindings.skillName(), left + 16, top + 68, EpicTheme.GOLD);
        var lines = textRenderer.wrapLines(Text.literal("在按键绑定列表找到“超级战墙”。红色表示与其他操作冲突，请改用空闲按键。剑右键、弓左键也可施法；按住不会重复释放。"), panelWidth - 32);
        int maxLines = Math.max(0, (panelHeight - 154) / 11);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) c.drawTextWithShadow(textRenderer, lines.get(i), left + 16, top + 91 + i * 11, EpicTheme.MUTED);
        // Screen.render also applies the vanilla background blur. The themed
        // background and text are already drawn; render only the controls here.
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.Drawable drawable) drawable.render(c, mx, my, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
