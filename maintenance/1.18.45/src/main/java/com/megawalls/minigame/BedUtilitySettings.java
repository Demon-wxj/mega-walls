package com.megawalls.minigame;

public class BedUtilitySettings {
    public int magnetChance = 75;
    public int magnetDamage = 200;
    public int rescueSeconds = 5;
    public int rescueCooldown = 20;
    public int rescueFall = 0;
    public int wallWidth = 5;
    public int wallHeight = 3;
    public int wallDistance = 2;
    public int wallCooldown = 20;

    final boolean utilitiesValid() {
        return range(magnetChance, 0, 100) && range(magnetDamage, 100, 400)
                && range(rescueSeconds, 2, 30) && range(rescueCooldown, 0, 120)
                && range(rescueFall, 0, 100)
                && range(wallWidth, 3, 9) && wallWidth % 2 == 1 && range(wallHeight, 1, 6)
                && range(wallDistance, 1, 5) && range(wallCooldown, 0, 120);
    }

    private static boolean range(int value, int lo, int hi) {
        return value >= lo && value <= hi;
    }
}
