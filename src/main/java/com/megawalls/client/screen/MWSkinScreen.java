package com.megawalls.client.screen;

import com.megawalls.client.ClassSkinTextures;
import com.megawalls.player.MWPlayerData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class MWSkinScreen extends Screen {
    private final Screen parent;
    private int left, top, panelWidth, panelHeight, cardY, cardHeight, cell;
    private EpicButton ordinary, legendary, account;
    private int requested = -1, pending;

    public MWSkinScreen(Screen parent) { super(Text.literal("末影传承")); this.parent = parent; }
    @Override public boolean shouldPause() { return false; }

    @Override protected void init() {
        panelWidth = Math.min(560, width - 24);
        panelHeight = Math.min(360, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        cell = (panelWidth - 32) / 2;
        cardY = top + 46;
        cardHeight = panelHeight - 88;
        ordinary = addDrawableChild(new EpicButton(left + 18, cardY + cardHeight - 32, cell - 12, 26,
                Text.literal("装备普通"), b -> choose(1), EpicTheme.MUTED));
        legendary = addDrawableChild(new EpicButton(left + 26 + cell, cardY + cardHeight - 32, cell - 12, 26,
                Text.literal("装备传奇"), b -> choose(2), EpicTheme.PURPLE));
        account = addDrawableChild(new EpicButton(left + 12, top + panelHeight - 31, cell, 24,
                Text.literal("使用账号皮肤"), b -> choose(0), EpicTheme.GOLD));
        addDrawableChild(new EpicButton(left + 20 + cell, top + panelHeight - 31, cell, 24,
                Text.literal("返回"), b -> close(), EpicTheme.MUTED));
        update();
    }

    private void choose(int id) {
        if (client.player != null && pending == 0) { requested = id; pending = 60; client.player.networkHandler.sendChatCommand("mw skin " + id); update(); }
    }

    private void update() {
        var d = MWPlayerData.getClientLocal();
        int id = d == null ? 0 : d.getSkinId();
        ordinary.setMessage(Text.literal(id == 1 ? "已装备" : "装备普通"));
        legendary.setMessage(Text.literal(id == 2 ? "已装备" : "装备传奇"));
        account.setMessage(Text.literal(id == 0 ? "账号皮肤 · 当前" : "使用账号皮肤"));
        boolean allowed = client.player != null && d != null && d.getClassId() == 5 && com.megawalls.client.keybind.KeyBindings.inSession() && (d.getPhase() == 0 || d.getPhase() == 4) && pending == 0;
        ordinary.active = allowed && id != 1;
        legendary.active = allowed && id != 2;
        account.active = allowed && id != 0;
    }

    @Override public void tick() { if(pending > 0) pending--;var d=MWPlayerData.getClientLocal();if(d!=null&&d.getSkinId()==requested){requested=-1;pending=0;}update(); }
    @Override public void close() { client.setScreen(parent); }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        EpicTheme.background(c, width, height);
        EpicTheme.panel(c, left, top, panelWidth, panelHeight);
        EpicTheme.heading(c, textRenderer, "末影传承", "末影人职业外观", left + 12, top + 11);
        int firstX = left + 12, secondX = firstX + cell + 8;
        EpicTheme.panel(c, firstX, cardY, cell, cardHeight);
        EpicTheme.panel(c, secondX, cardY, cell, cardHeight);
        c.fill(secondX + 1, cardY + 1, secondX + cell - 1, cardY + 2, EpicTheme.PURPLE);
        c.drawCenteredTextWithShadow(textRenderer, "末影人", firstX + cell / 2, cardY + 10, EpicTheme.MUTED);
        c.drawCenteredTextWithShadow(textRenderer, "传奇末影", secondX + cell / 2, cardY + 10, EpicTheme.GOLD);
        int scale = Math.max(1, Math.min(5, (cardHeight - 62) / 32));
        int skinY = cardY + 23 + Math.max(0, (cardHeight - 60 - 32 * scale) / 2);
        EpicTheme.skin(c, ClassSkinTextures.texture(1), firstX + cell / 2, skinY, scale);
        EpicTheme.skin(c, ClassSkinTextures.texture(2), secondX + cell / 2, skinY, scale);
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.Drawable drawable) drawable.render(c, mx, my, delta);
    }
}
