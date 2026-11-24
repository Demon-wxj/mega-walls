package com.megawalls.maintenance;

import com.megawalls.game.MapConfig;
import com.megawalls.game.MatchFlow;
import com.megawalls.minigame.GameSessions;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Per-world rules, persisted by the existing scoreboard save. */
public final class MatchTuning {
    public record Rule(String key, String label, String unit, int min, int max, int step, int initial, String help) {
        public boolean valid(int value) { return value >= min && value <= max; }
    }
    public static final List<Rule> RULES = List.of(
        new Rule("prep", "落墙前准备", "秒", 5, 3600, 30, 600, "从开局到落墙的准备时间。"),
        new Rule("rage", "凋零暴怒", "秒", 0, 7200, 30, 390, "从落墙开始计时；0 表示关闭暴怒。"),
        new Rule("battle", "最晚进入死斗", "秒", 5, 7200, 60, 2400, "落墙后最多进行多久攻防；凋零全部死亡会提前进入死斗。"),
        new Rule("deathmatch", "死斗持续时间", "秒", 5, 7200, 30, 600, "默认从进入死斗重新计时，不再把剩余攻防时间加进来。"),
        new Rule("wither_decay", "凋零每次自然掉血", "HP", 0, 100, 1, 5, "每次扣除的记分板血量；0 表示关闭自然掉血。"),
        new Rule("wither_interval", "凋零掉血间隔", "秒", 1, 120, 1, 3, "只在落墙后的攻防阶段生效；默认每 3 秒掉 5 HP。"),
        new Rule("wither_floor", "自然掉血保留末血", "0关/1开", 0, 1, 1, 1, "1：保留最后 1 HP，需攻击击杀；0：可以自然衰减死亡。"),
        new Rule("dm_carry", "死斗继承攻防余时", "0关/1开", 0, 1, 1, 0, "0：死斗时长固定；1：沿用旧版，把未用完的攻防时间加到死斗。"),
        new Rule("grace", "掉线重连等待", "秒", 5, 600, 15, 120, "超时后退出本局。"),
        new Rule("radius", "死斗中心半径", "格", 8, 256, 4, 32, "未设置由墙围成的中心区域时使用的后备半径。"),
        new Rule("log_restore", "准备期原木恢复", "秒", 1, 300, 5, 20, "仅准备期恢复原木，落墙后停止。"),
        new Rule("core_hp","队伍凋零初始血量","HP",1,10000,50,1000,"每队凋零开局的血量。"),
        new Rule("core_skull","头颅伤害","HP",0,80,1,6,"0 关闭发射；其他值为命中伤害。"),
        new Rule("core_skull_gap","头颅发射间隔","刻",5,400,5,30,"20 刻为 1 秒。"),
        new Rule("core_reach","头颅及暴怒索敌距离","格",4,48,1,30,"只锁定视线内的敌对玩家。"),
        new Rule("core_wave","冲击波伤害","HP",0,80,1,15,"0 关闭蓄力和冲击波。"),
        new Rule("core_wave_r","冲击波半径","格",1,20,1,8,"受击后蓄力释放；不穿过实体墙。"),
        new Rule("core_charge","冲击波蓄力","刻",1,200,5,60,"显示给进攻方的预警时间。"),
        new Rule("core_wave_gap","冲击波冷却","刻",10,2400,20,400,"从一次释放后开始计算。"),
        new Rule("core_rage_gap","暴怒技能间隔","刻",5,600,5,50,"仅在凋零暴怒期触发。"),
        new Rule("core_bolt","雷击伤害","HP",0,80,1,8,"0 将雷击从暴怒抽选中移除。"),
        new Rule("core_fire","灼烧命中伤害","HP",0,80,1,3,"0 关闭灼烧技能。"),
        new Rule("core_flame_t","灼烧持续","秒",0,30,1,5,"0 只造成命中伤害。"),
        new Rule("core_slow","寒霜命中伤害","HP",0,80,1,5,"0 关闭寒霜技能。"),
        new Rule("core_slow_t","缓慢持续","秒",0,30,1,5,"0 不附加缓慢。"),
        new Rule("core_push","击退命中伤害","HP",0,80,1,4,"0 关闭击退技能。"),
        new Rule("core_push_pct","击退力度","%",0,200,10,100,"0 不附加击退；同时调节冲击波与暴怒击退。")
    );
    public static void init(MinecraftServer server) {
        var objective = MapConfig.objective(server, "mw_config");
        for (Rule rule : RULES) {
            if (server.getScoreboard().getScore(ScoreHolder.fromName(rule.key()), objective) == null)
                MapConfig.set(server, rule.key(), rule.initial());
        }
    }
    private static final java.util.Map<String,Rule> BY_KEY=RULES.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Rule::key,r->r));
    public static Rule rule(String key) { return BY_KEY.get(key); }
    public static int value(MinecraftServer server, String key) {
        Rule rule = rule(key);
        return Math.clamp(MapConfig.get(server, key), rule.min(), rule.max());
    }
    public static int decayInterval(MinecraftServer server) { return value(server, "wither_interval") * 20; }
    public static int decay(MinecraftServer server, int hp) {
        if (hp <= 0) return 0;
        return Math.max(value(server, "wither_floor"), hp - value(server, "wither_decay"));
    }
    public static int deathmatchBudget(MinecraftServer server) {
        int extra = value(server, "dm_carry") == 1 ? Math.max(0, value(server, "battle") * 20 - MapConfig.get(server, "combat_elapsed")) : 0;
        return value(server, "deathmatch") * 20 + extra;
    }
    public static NbtCompound snapshot(ServerPlayerEntity player, String message) {
        MinecraftServer server = player.getServer();
        init(server);
        NbtCompound nbt = new NbtCompound();
        for (Rule rule : RULES) nbt.putInt(rule.key(), value(server, rule.key()));
        nbt.putInt("revision", MapConfig.get(server, "settings_revision"));
        nbt.putBoolean("editable", GameSessions.mw(player) && !MatchFlow.running(server));
        nbt.putString("message", message);
        return nbt;
    }
    /** Validate the whole request before applying any change. */
    public static String apply(ServerPlayerEntity player, NbtCompound request) {
        MinecraftServer server = player.getServer();
        init(server);
        if (!GameSessions.mw(player)) return "请先进入超级战墙再修改参数。";
        if (MatchFlow.running(server)) return "本局正在进行，请结束后修改；已保留服务器原参数。";
        if (request.getInt("revision") != MapConfig.get(server, "settings_revision")) return "其他玩家已修改参数，请刷新后再保存。";
        for (String key : request.getKeys()) {
            if (key.equals("revision") || key.equals("apply")) continue;
            Rule rule = rule(key);
            if (rule == null || !request.contains(key, 3) || !rule.valid(request.getInt(key))) return "参数无效，未保存任何修改。";
        }
        for (Rule rule : RULES) if (!request.contains(rule.key(), 3)) return "参数不完整，请刷新后重试。";
        if (request.getInt("rage") > request.getInt("battle")) return "凋零暴怒时长不能超过攻防时长。";
        for (Rule rule : RULES) MapConfig.set(server, rule.key(), request.getInt(rule.key()));
        MapConfig.set(server, "settings_revision", MapConfig.get(server, "settings_revision") + 1);
        MatchFlow.syncMembers(server);
        return "已保存至当前世界，下一局使用这些参数。";
    }
}
