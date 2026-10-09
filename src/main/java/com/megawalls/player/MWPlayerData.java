package com.megawalls.player;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 超级战墙玩家数据（满级，无等级系统）
 */
public class MWPlayerData {
    private static final Map<UUID, MWPlayerData> serverData = new HashMap<>();
    private static final Map<UUID, MWPlayerData> clientCache = new HashMap<>();
    private static MWPlayerData clientLocalPlayer;

    private int classId = 0;
    private NbtCompound skillStatus=new NbtCompound();
    public NbtCompound getSkillStatus(){return skillStatus;}
    public void setSkillStatus(NbtCompound n){skillStatus=n;}
    private NbtCompound match = new NbtCompound();
    public NbtCompound getMatch(){return match;}
    public void setMatch(NbtCompound value){match=value;}
    private int skinId = 0;
    public int getSkinId(){return skinId;}
    public void setSkinId(int value){skinId=Math.max(0,Math.min(2,value));}
    private int energy = 0;
    // Authoritative state survives reconnects and server-side game-profile snapshots.
    private int epsHits = 0;
    public int getEpsHits(){return epsHits;}
    public void resetEpsGate(){epsHits=0;}
    public void requireEpsHits(){epsHits=0;}
    public void recordEpsHit(){epsHits=Math.min(2,epsHits+1);}
    private int skillCooldown = 0;
    private boolean inGame = false;
    private int phase = 0;
    private boolean practice = false;

    public static MWPlayerData getServer(ServerPlayerEntity player) {
        return serverData.computeIfAbsent(player.getUuid(), k -> new MWPlayerData());
    }
    public void saveServer(ServerPlayerEntity player) { serverData.put(player.getUuid(), this); }
    public static MWPlayerData getClient(UUID uuid) { return clientCache.get(uuid); }
    public static void setClient(UUID uuid, MWPlayerData data) { clientCache.put(uuid, data); }
    public static MWPlayerData getClientLocal() { return clientLocalPlayer; }
    public static void setClientLocal(MWPlayerData data) { clientLocalPlayer = data; }
    public static void clearServer() { serverData.clear(); }
    public static void clearClient() { clientCache.clear(); clientLocalPlayer = null; }

    public static void initPlayer(ServerPlayerEntity player) {
        MWPlayerData data = getServer(player);
        if (data.classId == 0) {
            data.energy = 0;
            data.skillCooldown = 0;
            data.saveServer(player);
        }
    }

    public static MWPlayerData fromNbt(NbtCompound nbt) {
        MWPlayerData d = new MWPlayerData();
        d.classId = nbt.getInt("class_id");
        // Old profiles defaulted to two hits without combat. Start the new rule locked.
        d.epsHits=nbt.getInt("eps_gate_version")>=1&&nbt.contains("eps_hits")?Math.max(0,Math.min(2,nbt.getInt("eps_hits"))):0;
        d.skillStatus=nbt.getCompound("skill_status");
        d.match=nbt.getCompound("match");
        d.setSkinId(nbt.getInt("skin_id"));
        d.energy = nbt.getInt("energy");
        d.skillCooldown = nbt.getInt("skill_cd");
        d.inGame = nbt.getBoolean("in_game");
        d.phase = nbt.getInt("phase");
        d.practice = nbt.getBoolean("practice");
        return d;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("class_id", classId);
        nbt.putInt("eps_hits",epsHits);
        nbt.putInt("eps_gate_version",1);
        nbt.put("skill_status",skillStatus);
        nbt.put("match",match);
        nbt.putInt("skin_id",skinId);
        nbt.putInt("energy", energy);
        nbt.putInt("skill_cd", skillCooldown);
        nbt.putBoolean("in_game", inGame);
        nbt.putInt("phase", phase);
        nbt.putBoolean("practice", practice);
        return nbt;
    }

    public int getClassId() { return classId; }
    public void setClassId(int classId) { if(this.classId!=classId)resetEpsGate(); this.classId = classId; }
    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = Math.max(0, Math.min(100, energy)); }
    public int getSkillCooldown() { return skillCooldown; }
    public void setSkillCooldown(int skillCooldown) { this.skillCooldown = Math.max(0, skillCooldown); }
    public boolean isInGame() { return inGame; }
    public int getPhase() { return phase; }
    public void setPhase(int phase) { if(this.phase!=phase&&phase==1)resetEpsGate(); this.phase = phase; }
    public boolean isPractice() { return practice; }
    public void setPractice(boolean practice) { if(this.practice!=practice)resetEpsGate(); this.practice = practice; }
    public void setInGame(boolean inGame) { if(!inGame)resetEpsGate(); this.inGame = inGame; }

    public String getClassName() {
        return switch (classId) {
            case 1 -> "僵尸"; case 2 -> "骷髅"; case 3 -> "蜘蛛"; case 4 -> "鱿鱼";
            case 5 -> "末影人"; case 6 -> "海诺布"; case 7 -> "苦力怕"; case 8 -> "恐惧魔王";
            case 9 -> "铁傀儡"; case 10 -> "烈焰人"; case 11 -> "猎人"; case 12 -> "鼹鼠";
            case 13 -> "狼人"; default -> "无职业";
        };
    }
}
