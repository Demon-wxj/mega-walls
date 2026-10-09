package com.megawalls.skill;

import com.megawalls.game.GameState;
import com.megawalls.game.MapConfig;
import com.megawalls.game.MatchFlow;
import com.megawalls.player.MWPlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** October 21, 2017 candidate: decide at consumption completion, never mutate the kit stack. */
public final class WerewolfPotionRules {
    private WerewolfPotionRules() {}
    public static final int WALL_WINDOW = 600;
    public static boolean restricted(int phase, int ticksUntilWalls, int combatElapsed) {
        return phase == 1 && ticksUntilWalls <= WALL_WINDOW
                || (phase == 2 || phase == 3) && combatElapsed <= WALL_WINDOW;
    }
    public static boolean suppressJump(ServerPlayerEntity player, ItemStack stack) {
        if (!GameState.active(player) || MWPlayerData.getServer(player).getClassId() != 13) return false;
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        var potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (data == null || !data.copyNbt().getBoolean("mw_kit") || potion == null) return false;
        boolean speed = false, jump = false;
        for (var effect : potion.getEffects()) {
            speed |= effect.getEffectType().equals(StatusEffects.SPEED);
            jump |= effect.getEffectType().equals(StatusEffects.JUMP_BOOST) && effect.getAmplifier() == 4;
        }
        var server = player.getServer();
        return speed && jump && restricted(GameState.phase(player),
                MapConfig.get(server,"prep") * 20 - MatchFlow.timer(server), MapConfig.get(server,"combat_elapsed"));
    }
}
