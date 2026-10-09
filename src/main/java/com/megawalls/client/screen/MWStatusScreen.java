package com.megawalls.client.screen;

import com.megawalls.menu.ClassCatalog;
import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.SkillStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public final class MWStatusScreen extends Screen {
    private final Screen parent;
    private int scroll, maxScroll, left, top, panelWidth, panelHeight;
    private record Card(String title, String body, int accent) {}

    public MWStatusScreen(Screen parent) { super(Text.literal("战斗档案")); this.parent = parent; }

    @Override protected void init() {
        panelWidth = Math.min(620, width - 24);
        panelHeight = Math.min(440, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addDrawableChild(new EpicButton(left + 12, top + panelHeight - 31, panelWidth - 24, 24,
                Text.literal("返回菜单"), b -> close(), EpicTheme.MUTED));
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 22)));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 264 || key == 265 || key == 266 || key == 267 || key == 268 || key == 269) {
            int step = key == 266 || key == 267 ? Math.max(22, panelHeight - 90) : 22;
            scroll = key == 268 ? 0 : key == 269 ? maxScroll : Math.max(0, Math.min(maxScroll, scroll + (key == 264 || key == 267 ? step : -step)));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private List<Card> cards(MWPlayerData d) {
        var cards = new ArrayList<Card>();
        if (d == null || d.getClassId() == 0) {
            cards.add(new Card("尚未选择职业", "返回菜单选择职业后，查看技能与战斗状态。", EpicTheme.GOLD));
            return cards;
        }
        var info = ClassCatalog.get(d.getClassId());
        String activeState = d.getSkillCooldown() > 0 ? "冷却 " + SkillStatus.seconds(d.getSkillCooldown())
                : "能量 " + d.getEnergy() + " / 100";
        var n = d.getSkillStatus();
        if (n.getInt("duration") > 0) activeState += "  ·  生效剩余 " + SkillStatus.seconds(n.getInt("duration"));
        cards.add(new Card("主动 · " + info.skill(), activeState, EpicTheme.PURPLE));
        StringBuilder status = new StringBuilder();
        for (String key : new String[] { "passive1", "passive2", "gathering" }) {
            String value = n.getString(key);
            if (!value.isEmpty()) {
                if (!status.isEmpty()) status.append('\n');
                status.append(value);
            }
        }
        if (!status.isEmpty()) cards.add(new Card("被动与采集 · 当前状态", status.toString(), EpicTheme.GOLD));
        cards.add(new Card("主动机制", info.active(), EpicTheme.PURPLE));
        cards.add(new Card("职业特性", info.passive(), EpicTheme.GOLD));
        cards.add(new Card("追踪指南针", "右键锁定 · 左键切换敌队\n当前：" + n.getString("tracker"), EpicTheme.MUTED));
        cards.add(new Card("触发规则", "概率技能显示可触发时，仍需满足触发条件和概率。", EpicTheme.MUTED));
        return cards;
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        EpicTheme.background(c, width, height);
        EpicTheme.panel(c, left, top, panelWidth, panelHeight);
        var d = MWPlayerData.getClientLocal();
        String subtitle = d == null || d.getClassId() == 0 ? "职业与技能" : d.getClassName() + " · "
                + ClassCatalog.get(d.getClassId()).role() + "  /  40 HP";
        EpicTheme.heading(c, textRenderer, "战斗档案", subtitle, left + 12, top + 11);
        int bodyTop = top + 46, bodyBottom = top + panelHeight - 40;
        int inner = panelWidth - 32;
        var cards = cards(d);
        int total = 0;
        for (Card card : cards) total += 32 + textRenderer.wrapLines(Text.literal(card.body()), inner - 24).size() * 12;
        maxScroll = Math.max(0, total - 6 - (bodyBottom - bodyTop));
        scroll = Math.min(scroll, maxScroll);
        int y = bodyTop - scroll;
        c.enableScissor(left + 12, bodyTop, left + panelWidth - 12, bodyBottom);
        for (Card card : cards) {
            var lines = textRenderer.wrapLines(Text.literal(card.body()), inner - 24);
            int h = 26 + lines.size() * 12;
            if (y + h >= bodyTop && y < bodyBottom) {
                EpicTheme.panel(c, left + 12, y, inner, h);
                c.fill(left + 12, y + 6, left + 14, y + h - 6, card.accent());
                c.drawTextWithShadow(textRenderer, card.title(), left + 22, y + 8, card.accent());
                for (int i = 0; i < lines.size(); i++)
                    c.drawTextWithShadow(textRenderer, lines.get(i), left + 22, y + 22 + i * 12, 0xFFE0DAE8);
            }
            y += h + 6;
        }
        c.disableScissor();
        if (maxScroll > 0) {
            int track = bodyBottom - bodyTop, thumb = Math.max(16, track * track / (track + maxScroll));
            int sy = bodyTop + (track - thumb) * scroll / maxScroll;
            c.fill(left + panelWidth - 15, bodyTop, left + panelWidth - 13, bodyBottom, 0x552D263C);
            c.fill(left + panelWidth - 15, sy, left + panelWidth - 13, sy + thumb, EpicTheme.PURPLE);
        }
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.Drawable drawable) drawable.render(c, mx, my, delta);
    }
}
