package com.megawalls.skill;

/** User-selected September 2026 tuning, separate from historical restoration claims. */
public final class ClassBalanceRules {
    private ClassBalanceRules() {}
    public static final int BLAZE_WINDUP = 30;
    public static final int ENDERMAN_WEAKNESS = 60;
    public static final double SPIDER_UP = 1.25;
    public static final double SPIDER_FORWARD = 1.8;
    public static final int MOLE_WINDUP = 1;
    public static final int MOLE_SEGMENTS_PER_TICK = 4;
    public static final float SQUID_DAMAGE = 3;
    public static final double SQUID_RADIUS = 5;
    public static final float SQUID_HEAL_RATIO = .7f, SQUID_HEAL_CAP = 7;
    public static final int SQUID_REFUND_PER_TARGET = 10, SQUID_REFUND_CAP = 30;
    public static String squidDescription() {
        return "拉拽 " + (int)SQUID_RADIUS + " 格内敌人并造成 " + (int)SQUID_DAMAGE
                + " HP 真实伤害；恢复实际生命及吸收伤害的 70%，最多 " + (int)SQUID_HEAL_CAP
                + " HP；有效命中每人返还 " + SQUID_REFUND_PER_TARGET + " 能量，单次最多 " + SQUID_REFUND_CAP + "。";
    }
    public static boolean teleportWeakness(double distanceSquared, boolean obstructed) {
        return obstructed && distanceSquared > 100;
    }
}
