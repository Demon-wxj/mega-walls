package com.megawalls.nametag;

import com.megawalls.menu.ClassCatalog;
import com.megawalls.player.MWPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NameTagHandler {
    private static final Map<UUID, String> last = new HashMap<>();
    public static Text label(ServerPlayerEntity player) {
        if(!com.megawalls.minigame.GameSessions.mw(player))return null;
        int id = MWPlayerData.getServer(player).getClassId();
        if (id < 1 || id > 13) return null;
        var team = player.getScoreboardTeam();
        String name = team == null ? "" : team.getName();
        Formatting color = com.megawalls.game.MatchTeams.color(name);
        String teamLabel = com.megawalls.game.MatchTeams.label(name);
        return Text.empty().append(Text.literal("[" + teamLabel + "] ").formatted(color))
                .append(Text.literal("[" + ClassCatalog.get(id).name() + "] ").formatted(Formatting.GOLD))
                .append(Text.literal(player.getGameProfile().getName()).formatted(Formatting.WHITE));
    }
    public static void createNameTag(ServerPlayerEntity player) { updateNameTag(player); }
    public static void updateNameTag(ServerPlayerEntity player) {
        if (player.age % 10 != 0) return;
        Text label = label(player);
        String value = label == null ? "" : label.getString();
        if (value.equals(last.put(player.getUuid(), value))) return;
        player.getServer().getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
    }
    public static void removeNameTag(ServerPlayerEntity player) { last.remove(player.getUuid()); }
    public static void clear() { last.clear(); }
    public static void registerLegacyCleanup() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity display) {
                String text = display.getText().getString().replaceAll("§.", "");
                if (text.matches("\\[(红|蓝|绿|黄|无)\\] [A-Za-z0-9_]{1,16} (无职业|僵尸|骷髅|蜘蛛|鱿鱼|末影人|海诺布|苦力怕|恐惧魔王|铁傀儡|烈焰人|猎人|鼹鼠|狼人)\\n[0-9.]+❤")) display.discard();
            }
        });
    }
}
