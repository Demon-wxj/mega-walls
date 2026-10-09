package com.megawalls.rules;

import com.megawalls.minigame.SpleefRules;

/** Tests stale shots, boundaries and draw precedence without starting Minecraft. */
public final class SpleefChecks {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    public static void main(String[] args){
        check(SpleefRules.settings(2,10,10,600,40,4,5),"default settings");
        check(!SpleefRules.settings(1,10,10,300,40,4,5),"single player cannot start");
        check(!SpleefRules.settings(10,2,10,300,40,4,5),"max below min");
        check(!SpleefRules.settings(2,10,10,0,40,4,5),"no immediate duration");
        check(!SpleefRules.settings(2,10,10,300,40,0,5),"no packet-rate snowballs");
        check(!SpleefRules.settings(2,11,10,600,40,4,5),"eleventh player not configurable");
        check(SpleefRules.settings(10,10,10,600,40,4,5),"ten-player minimum remains configurable");
        check(SpleefRules.capacity(10,10)==10,"ten complete spawn positions fill the game");
        check(SpleefRules.capacity(32,32)==10,"stale capacity still caps at ten");
        check(SpleefRules.capacity(10,3)==3,"fewer spawns reduce available slots");
        check(SpleefRules.settings(2,10,0,600,40,4,5)&&SpleefRules.settings(2,10,-1,600,40,4,5),"unused legacy countdown no longer invalidates saved config");
        check(SpleefRules.bounded(256,1,256),"largest supported floor");
        check(SpleefRules.bounded(90,29,105),"saved SAO arena includes empty height without exceeding the scan budget");
        check(SpleefRules.bounded(256,16,256),"maximum bounded scan volume");
        check(!SpleefRules.bounded(256,17,256),"reject excessive scan volume");
        check(!SpleefRules.bounded(16,65,16),"reject unbounded vertical scan");
        for(long stale:new long[]{-1,0,6,8})check(!SpleefRules.projectile(stale,7,true,true,true),"stale or ordinary shot "+stale);
        check(SpleefRules.projectile(7,7,true,true,true),"live current shot");
        check(!SpleefRules.projectile(7,7,false,true,true),"eliminated thrower");
        check(!SpleefRules.projectile(7,7,true,false,true),"foreign dimension");
        check(!SpleefRules.projectile(7,7,true,true,false),"non-snow/manual construction protected");
        check(SpleefRules.projectileAge(0)&&SpleefRules.projectileAge(100),"bounded shot lifetime");
        check(!SpleefRules.projectileAge(-1)&&!SpleefRules.projectileAge(101),"reject aged or invalid shot");
        check(!SpleefRules.eliminated(0,64,0,0,9,0,9,63.5,true),"northwest boundary remains playable");
        check(!SpleefRules.eliminated(9.99,63.5,9.99,0,9,0,9,63.5,true),"last platform edge and exact height safe");
        check(SpleefRules.eliminated(10,64,4,0,9,0,9,63.5,true),"east edge leaves platform");
        check(SpleefRules.eliminated(4,63.49,4,0,9,0,9,63.5,true),"below loseY eliminated");
        check(SpleefRules.eliminated(4,64,4,0,9,0,9,63.5,false),"dimension transfer eliminated");
        check(SpleefRules.outcome(0,false)==-1,"simultaneous fall draw");
        check(SpleefRules.outcome(1,false)==1,"last survivor wins");
        check(SpleefRules.outcome(1,true)==1,"last survivor beats timeout draw");
        check(SpleefRules.outcome(2,true)==-1,"multiple survivors timeout draw");
        check(SpleefRules.outcome(2,false)==0,"two survivors continue");
        System.out.println("SPLEEF_CHECKS_OK "+checks);
    }
}
