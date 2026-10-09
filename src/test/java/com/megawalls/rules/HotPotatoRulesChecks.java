package com.megawalls.rules;

import com.megawalls.minigame.HotPotatoRules;

/** Regressions for fuse boundaries and departures; requires no Minecraft bootstrap. */
public final class HotPotatoRulesChecks {
    private static int checks;
    private static void expect(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        int[] schedule={120,100,80,60,45};
        int[] expected={120,100,80,60,45,45,45};
        for(int i=1;i<=expected.length;i++)expect(HotPotatoRules.secondsForRound(schedule,i,9,45)==expected[i-1],"round "+i);
        expect(HotPotatoRules.secondsForRound(schedule,1,2,45)==45,"two-player first match uses final fuse");
        expect(HotPotatoRules.secondsForRound(schedule,4,3,45)==60,"three players keep configured round");
        expect(HotPotatoRules.secondsForRound(new int[]{90,60,45,30,20},2,6,15)==60,"existing schedule retained");
        expect(HotPotatoRules.secondsForRound(new int[]{8,8,8,8,8},1,2,15)==8,"final never lengthens configured fuse");

        int fuse=1000,lock=920;
        expect(!HotPotatoRules.canPassAt(919,lock,fuse),"new recipient cannot return tag during lock");
        expect(HotPotatoRules.canPassAt(920,lock,fuse),"tag allowed exactly at cooldown boundary");
        expect(HotPotatoRules.canPassAt(999,lock,fuse),"last pre-expiry tick is valid");
        expect(!HotPotatoRules.canPassAt(1000,lock,fuse),"expiration wins same-tick attack");
        expect(!HotPotatoRules.canPassAt(1001,lock,fuse),"late input cannot escape explosion");
        expect(!HotPotatoRules.canPassAt(999,1005,fuse),"reassigned holder during final second cannot bypass lock");
        expect(HotPotatoRules.remainingSeconds(980,fuse)==1,"one second before explosion");
        expect(HotPotatoRules.remainingSeconds(981,fuse)==1,"ceil partial second");
        expect(HotPotatoRules.remainingSeconds(999,fuse)==1,"display one until actual expiry");
        expect(HotPotatoRules.remainingSeconds(1000,fuse)==0,"display zero at actual expiry");
        expect(HotPotatoRules.remainingSeconds(1020,fuse)==0,"no negative time");
        expect(HotPotatoRules.afterDeparture(7,false)==HotPotatoRules.Departure.KEEP_CLOCK,"uninvolved disconnect preserves round");
        expect(HotPotatoRules.afterDeparture(7,true)==HotPotatoRules.Departure.REASSIGN,"holder disconnect transfers ownership without new round");
        expect(HotPotatoRules.afterDeparture(2,false)==HotPotatoRules.Departure.KEEP_CLOCK,"three to two mid-round does not grant new fuse");
        expect(HotPotatoRules.afterDeparture(1,false)==HotPotatoRules.Departure.FINISH,"one survivor ends match");
        expect(HotPotatoRules.afterDeparture(1,true)==HotPotatoRules.Departure.FINISH,"holder leave with one survivor ends match");
        expect(HotPotatoRules.afterDeparture(0,true)==HotPotatoRules.Departure.FINISH,"no survivor is clean draw");
        expect(!HotPotatoRules.queueReadyToStart(1,2,true),"minimum players");
        expect(!HotPotatoRules.queueReadyToStart(9,2,false),"valid map required");
        expect(HotPotatoRules.queueReadyToStart(2,2,true),"ready roster permits a requested start");
        var assist=new HotPotatoRules.HoldAssist();
        expect(!assist.available(999,400),"no assist outside possession");
        assist.assign(100);
        expect(!assist.available(499,400),"assist unavailable before uninterrupted threshold");
        expect(assist.available(500,400),"assist available exactly at threshold");
        expect(assist.activate(500,400,120),"first assist activation");
        expect(assist.active(619),"assist active until last duration tick");
        expect(!assist.active(620),"assist ends exactly at duration boundary");
        expect(!assist.activate(700,400,120),"same possession cannot renew assist repeatedly");
        assist.assign(800);
        expect(!assist.available(1000,400),"transfer resets uninterrupted holding time");
        expect(!assist.active(801),"transfer ends previous assist");
        expect(assist.activate(1200,400,120),"next possession has its own assist");
        assist.clear();
        expect(!assist.available(2000,400)&&!assist.active(1201),"elimination or leave clears assist state");
        assist.assign(2000);
        expect(!assist.activate(2400,400,0),"zero duration cannot consume assist");
        System.out.println("HOT_POTATO_RULES_CHECKS_PASSED="+checks);
    }
}
