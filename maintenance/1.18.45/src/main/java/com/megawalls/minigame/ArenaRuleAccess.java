package com.megawalls.minigame;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
/** Look up each existing game's current config once; the original save/load routine still owns it. */
final class ArenaRuleAccess {
    private static VarHandle config(Class<?> game,Class<?> type){try{return MethodHandles.privateLookupIn(game,MethodHandles.lookup()).findStaticVarHandle(game,"config",type);}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    private static final class Bed { static final VarHandle CONFIG=config(BedWars.class,BedWars.Config.class); }
    private static final class Uhc { static final VarHandle CONFIG=config(ClassicDuel.class,ClassicDuel.Config.class); }
    static BedBowRules bed(){return (BedBowRules)(Object)(BedWars.Config)Bed.CONFIG.get();}
    static ClassicDuel.Config uhc(){return (ClassicDuel.Config)Uhc.CONFIG.get();}
    private ArenaRuleAccess(){}
}
