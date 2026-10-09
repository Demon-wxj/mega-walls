package com.megawalls.minigame;

/** Side-effect-free rules shared by the server and deterministic JVM checks. */
public final class SpleefRules {
    public static final int MAX_PLAYERS=10;
    public static final int MAX_SCAN_BLOCKS=1048576;
    public static final int MAX_SNOW_BLOCKS=65536;
    private SpleefRules() {}
    public static boolean settings(int min,int max,int countdown,int duration,int grace,int shotDelay,int result) {
        return min>=2&&max>=min&&max<=MAX_PLAYERS
            &&duration>=30&&duration<=3600&&grace>=0&&grace<=200
            &&shotDelay>=2&&shotDelay<=20&&result>=1&&result<=30;
    }
    public static int capacity(int configuredMax,int spawnCount){return Math.max(0,Math.min(MAX_PLAYERS,Math.min(configuredMax,spawnCount)));}
    public static boolean bounded(int dx,int dy,int dz) {
        return dx>=1&&dy>=1&&dz>=1&&dx<=256&&dy<=64&&dz<=256
            &&(long)dx*dy*dz<=MAX_SCAN_BLOCKS;
    }
    public static boolean projectile(long shotRound,long currentRound,boolean playing,boolean currentDimension,boolean snapshotSnow) {
        return shotRound>0&&shotRound==currentRound&&playing&&currentDimension&&snapshotSnow;
    }
    public static boolean projectileAge(int age){return age>=0&&age<=100;}
    public static boolean eliminated(double x,double y,double z,int minX,int maxX,int minZ,int maxZ,double loseY,boolean sameDimension) {
        return !sameDimension||y<loseY||x<minX||x>=maxX+1.0||z<minZ||z>=maxZ+1.0;
    }
    /** -1 means draw, 0 unresolved, 1 single survivor wins. */
    public static int outcome(int alive,boolean timedOut) {
        if(alive<1)return -1;
        if(alive==1)return 1;
        return timedOut?-1:0;
    }
}
