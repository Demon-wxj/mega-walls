package com.megawalls.skill;

/** Late-2017 candidates; source/confidence are recorded in the restoration delivery notes. */
public final class RestorationSkillRules {
    private RestorationSkillRules() {}
    public static final int CREEPER_WINDUP = 80;
    public static final float CREEPER_DAMAGE = 10;
    public static final float CREEPER_WILLPOWER_HP = 14;
    public static final int ENDERMAN_COOLDOWN = 120;
    public static final int SOUL_CHARGE_COOLDOWN = 200;
    public static final int AGILE_DURATION = 140;
    public static final int AGILE_RECOVERY = 80;
    public static final int REJUVENATE_COOLDOWN = 1000;
    public static final int IRON_HEART_COOLDOWN = 400;
    public static final float EXPLOSIVE_ARROW_DAMAGE = 6;
    public static final float BLAZE_DIRECT_DAMAGE = 10;
    public static final double DROP_SHOCK_RADIUS = 4;
    public static final double LYCANTHROPY_RADIUS = 5;
    public static final float LYCANTHROPY_SPLASH = 1;

    public static float dropShockDamage(float actualFallLoss) {
        return !Float.isFinite(actualFallLoss) || actualFallLoss < 4 ? 0 : Math.min(14, actualFallLoss * 2);
    }

    /** Paid combat effects survive the normal transition into deathmatch, but never other phases. */
    public static boolean samePhaseLifetime(int initial, int current) {
        return initial == current || initial == 2 && current == 3;
    }
}
