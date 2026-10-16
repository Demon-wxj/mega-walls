package com.megawalls.minigame;

/** Pure round decisions used by the authoritative server implementation. */
public final class HotPotatoRules {
    private HotPotatoRules() {}

    public enum Departure { FINISH, KEEP_CLOCK, REASSIGN }

    public static int secondsForRound(int[] schedule, int round, int alive, int finalSeconds) {
        int scheduled = schedule[Math.max(0, Math.min(round - 1, schedule.length - 1))];
        return alive == 2 ? Math.min(scheduled, finalSeconds) : scheduled;
    }

    /** Existing per-round settings are caps; later rounds keep shortening after the last cap. */
    public static int secondsForRound(int[] schedule,int round,int alive,int finalSeconds,int decrease,int minimum){
        int rounds=Math.max(1,round),seconds=schedule[0],configured=Math.min(rounds,schedule.length);
        for(int i=1;i<configured;i++)seconds=Math.max(minimum,Math.min(schedule[i],seconds-decrease));
        if(rounds>schedule.length)seconds=(int)Math.max(minimum,seconds-(long)decrease*(rounds-schedule.length));
        return alive==2?Math.min(seconds,finalSeconds):seconds;
    }

    public static int remainingSeconds(int now, int deadline) {
        return Math.max(0, (deadline - now + 19) / 20);
    }

    /** An input at the deadline belongs to the expired round, never to a new round. */
    public static boolean canPassAt(int now, int passAfter, int deadline) {
        return now >= passAfter && now < deadline;
    }

    /** Leaving cannot grant a fresh fuse. Only expiration advances the round. */
    public static Departure afterDeparture(int remainingAlive, boolean departedHolder) {
        if (remainingAlive <= 1) return Departure.FINISH;
        return departedHolder ? Departure.REASSIGN : Departure.KEEP_CLOCK;
    }

    /** Eligibility only; becoming ready never starts a match without player input. */
    public static boolean queueReadyToStart(int queueSize, int minimum, boolean arenaReady) {
        return arenaReady && queueSize >= minimum;
    }

    /** A single assist per uninterrupted possession; assignment always starts a new clock. */
    public static final class HoldAssist {
        private int assignedAt=-1, activeUntil;
        private boolean used;
        public void assign(int now){assignedAt=now;activeUntil=0;used=false;}
        public void clear(){assignedAt=-1;activeUntil=0;used=false;}
        public boolean available(int now,int thresholdTicks){return assignedAt>=0&&!used&&now-assignedAt>=thresholdTicks;}
        public boolean activate(int now,int thresholdTicks,int durationTicks){
            if(durationTicks<=0||!available(now,thresholdTicks))return false;
            used=true;activeUntil=now+durationTicks;return true;
        }
        public boolean active(int now){return assignedAt>=0&&used&&now<activeUntil;}
        public int expiresAt(){return activeUntil;}
    }
}
