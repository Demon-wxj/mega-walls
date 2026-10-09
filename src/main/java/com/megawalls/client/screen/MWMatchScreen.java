package com.megawalls.client.screen;

import com.megawalls.game.MapConfig;
import com.megawalls.player.MWPlayerData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Server-authorized match controls, styled as the command room of the lobby. */
public class MWMatchScreen extends Screen {
    private final Screen parent;
    private final boolean mapPage;
    private int page, pending, left, top, panelWidth, panelHeight, pageSize = 8;
    private final List<ButtonWidget> mapButtons = new ArrayList<>();
    private ButtonWidget start, stop, reset, mode, walls, map;

    public MWMatchScreen(Screen parent) { this(parent, false); }
    public MWMatchScreen(Screen parent, boolean mapPage) {
        super(Text.literal(mapPage ? "战场布置" : "对局指挥"));
        this.parent = parent;
        this.mapPage = mapPage;
    }

    private NbtCompound data() {
        var d = MWPlayerData.getClientLocal();
        return d == null ? new NbtCompound() : d.getMatch();
    }

    private void send(String command) {
        if (pending == 0 && client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
            pending = 8;
            update();
        }
    }

    private EpicButton button(String label, int x, int y, int w, int h, int accent, Runnable action) {
        return addDrawableChild(new EpicButton(x, y, w, h, Text.literal(label), b -> action.run(), accent));
    }

    private int pages() { return (MapConfig.POINTS.length + pageSize - 1) / pageSize; }

    private void confirm(String title, String message, String command) {
        client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(accepted -> {
            client.setScreen(this);
            if (accepted) send(command);
        }, Text.literal(title), Text.literal(message)));
    }

    @Override protected void init() {
        mapButtons.clear();
        start = stop = reset = mode = walls = map = null;
        panelWidth = Math.min(560, width - 24);
        panelHeight = Math.min(270, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int x = left + 12, inner = panelWidth - 24;
        int col = (inner - 8) / 2, third = (inner - 16) / 3;
        int bottom = top + panelHeight;
        if (mapPage) {
            pageSize = Math.max(2, Math.min(8, ((panelHeight - 114) / 29) * 2));
            page = Math.max(0, Math.min(page, pages() - 1));
            for (int j = 0; j < pageSize; j++) {
                final int i = page * pageSize + j;
                if (i >= MapConfig.POINTS.length) break;
                var pointButton = button(MapConfig.LABELS[i], x + (j % 2) * (col + 8),
                        top + 46 + (j / 2) * 29, col, 26, EpicTheme.PURPLE,
                        () -> send("mw map " + MapConfig.POINTS[i]));
                pointButton.setTooltip(Tooltip.of(Text.literal(MapConfig.POINTS[i].contains("castle")
                        ? "保护原建筑；可放置并拆除本局方块。站在两个对角点设置。"
                        : MapConfig.POINTS[i].contains("witherzone")
                        ? "凋零区不可放置或破坏。站在两个对角点设置。"
                        : "站在目标位置点击，记录当前位置。")));
                mapButtons.add(pointButton);
            }
            button("上一页", x, bottom - 61, third, 24, EpicTheme.MUTED,
                    () -> { page--; clearAndInit(); }).active = page > 0;
            walls = button("", x + third + 8, bottom - 61, third, 24, EpicTheme.GOLD,
                    () -> send("mw walls " + (data().getInt("wall_count") == 4 ? 1 : 4)));
            button("下一页", x + 2 * (third + 8), bottom - 61, third, 24, EpicTheme.MUTED,
                    () -> { page++; clearAndInit(); }).active = page + 1 < pages();
        } else {
            mode = button("", x, top + 54, col, 28, EpicTheme.PURPLE,
                    () -> send("mw teams " + (data().getInt("teams") == 4 ? 2 : 4)));
            map = button("布置战场", x + col + 8, top + 54, col, 28, EpicTheme.GOLD,
                    () -> client.setScreen(new MWMatchScreen(this, true)));
            start = button("开始对局", x, top + 90, inner, 32, EpicTheme.GOLD, () -> send("mw start"));
            stop = button("结束本局", x, top + 130, col, 28, EpicTheme.PURPLE, () -> confirm("结束当前对局？", "本局将结束并进入结算。", "mw stop"));
            reset = button("重置 · 恢复地图", x + col + 8, top + 130, col, 28, EpicTheme.MUTED, () -> confirm("重置对局？", "结束本局、恢复建筑并让玩家返回大厅。", "mw reset"));
        }
        button("返回", x, bottom - 31, inner, 24, EpicTheme.MUTED, this::close);
        update();
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public void tick() { if (pending > 0) pending--; update(); }

    private void update() {
        var n = data();
        int phase = n.getInt("phase");
        boolean waiting = phase == 0, editable = waiting && !n.getBoolean("map_locked"), admin = n.getBoolean("can_manage") && pending == 0;
        if (mode != null) {
            mode.setMessage(Text.literal(n.getInt("teams") == 4 ? "四队 · 切换双队" : "双队 · 切换四队"));
            mode.active = admin && editable;
        }
        if (map != null) map.active = admin && editable;
        if (walls != null) {
            walls.setMessage(Text.literal(n.getInt("wall_count") == 4 ? "四面分隔墙" : "单面分隔墙"));
            walls.active = admin && editable;
        }
        if (start != null) {
            start.active = admin && waiting && n.getBoolean("can_start");
            stop.active = admin && phase >= 1 && phase <= 3;
            reset.active = admin;
        }
        for (int j = 0; j < mapButtons.size(); j++) {
            int i = page * pageSize + j;
            var b = mapButtons.get(j);
            b.active = admin && editable;
            int mask=n.getInt(i<32?"map":"map_extra");
            b.setMessage(Text.literal(((mask & (1 << (i<32?i:i-32))) != 0 ? "✓ " : "○ ") + MapConfig.LABELS[i]));
        }
        String lockReason = !n.getBoolean("can_manage") ? "请先进入超级战墙" : !waiting || n.getBoolean("map_locked") ? "请先结束并重置上一局" : pending > 0 ? "正在发送设置" : "点击切换设置";
        if (mode != null) mode.setTooltip(Tooltip.of(Text.literal(lockReason)));
        if (map != null) map.setTooltip(Tooltip.of(Text.literal(lockReason)));
        if (start != null) start.setTooltip(Tooltip.of(Text.literal(n.getString("start_issue").isEmpty() ? "至少两队参加并准备后开始对局" : n.getString("start_issue"))));
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        EpicTheme.background(c, width, height);
        EpicTheme.panel(c, left, top, panelWidth, panelHeight);
        var n = data();
        String subtitle = mapPage ? "点位 " + (page + 1) + " / " + pages() + " · 站在目标处设置"
                : n.getString("stage") + "  /  地图 " + n.getInt("map_set") + " / " + n.getInt("map_total");
        EpicTheme.heading(c, textRenderer, mapPage ? "战场布置" : "对局指挥", subtitle, left + 12, top + 11);
        if (!mapPage) {
            String issue = n.getString("start_issue");
            if (!n.getBoolean("can_manage")) issue = "请先进入超级战墙";
            else if (issue.isEmpty()) issue = "各队已准备 · 战场等待开启";
            var lines = textRenderer.wrapLines(Text.literal(issue), panelWidth - 28);
            int limit = Math.max(1, (panelHeight - 205) / 11 + 1);
            for (int i = 0; i < Math.min(limit, lines.size()); i++)
                c.drawTextWithShadow(textRenderer, lines.get(i), left + 14, top + 169 + i * 11, EpicTheme.MUTED);
        }
        for (var child : children())
            if (child instanceof net.minecraft.client.gui.Drawable drawable) drawable.render(c, mx, my, delta);
    }
}
