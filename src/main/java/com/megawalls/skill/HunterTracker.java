package com.megawalls.skill;

import com.megawalls.player.MWPlayerData;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 猎人鹰眼箭矢自动追踪处理器
 */
public class HunterTracker {
    // Server ticks, never wall-clock milliseconds.
    private static final Map<UUID, Long> eagleEyeEndTime = new HashMap<>();
    private static final Map<UUID,Map<UUID,ArrowEntity>> arrows=new HashMap<>();
    public static void register(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity instanceof ArrowEntity arrow&&arrow.getOwner() instanceof ServerPlayerEntity p
                    &&com.megawalls.game.GameState.active(p)&&MWPlayerData.getServer(p).getClassId()==11)
                arrows.computeIfAbsent(p.getUuid(),id->new HashMap<>()).put(arrow.getUuid(),arrow);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            if(entity instanceof ArrowEntity arrow&&arrow.getOwner() instanceof ServerPlayerEntity p){
                var owned=arrows.get(p.getUuid());if(owned!=null){owned.remove(arrow.getUuid());if(owned.isEmpty())arrows.remove(p.getUuid());}
            }
        });
    }

    // 14 seconds at 20 TPS.
    private static final long EAGLE_EYE_DURATION = 280;
    public static int remaining(ServerPlayerEntity p){return (int)Math.max(0,eagleEyeEndTime.getOrDefault(p.getUuid(),0L)-p.getServer().getTicks());}

    /**
     * 激活鹰眼
     */
    public static void clear() { eagleEyeEndTime.clear();arrows.clear(); }
    public static void forget(UUID uuid) { eagleEyeEndTime.remove(uuid);arrows.remove(uuid); }
    public static void activateEagleEye(ServerPlayerEntity player) {
        eagleEyeEndTime.put(player.getUuid(), (long)player.getServer().getTicks() + EAGLE_EYE_DURATION);
    }

    /**
     * 检查玩家是否有鹰眼效果
     */
    public static boolean hasEagleEye(ServerPlayerEntity player) {
        Long endTime = eagleEyeEndTime.get(player.getUuid());
        return com.megawalls.game.GameState.active(player) && endTime != null && (long)player.getServer().getTicks() < endTime;
    }

    /**
     * tick更新所有箭矢追踪
     */
    public static void tickArrows(ServerPlayerEntity player) {
        if (!hasEagleEye(player)) return;
        if (MWPlayerData.getServer(player).getClassId() != 11) return; // 只有猎人

        var owned=arrows.get(player.getUuid());if(owned==null)return;
        owned.values().removeIf(arrow->arrow.isRemoved()||arrow.getWorld()!=player.getWorld());
        var bounds=player.getBoundingBox().expand(50);
        for (ArrowEntity arrow : owned.values()) {
            if(arrow.getOwner()!=player||!arrow.isCritical()||arrow.isOnGround()||!arrow.getBoundingBox().intersects(bounds))continue;

            // 找到最近的敌人（30格内）
            ServerPlayerEntity target = findNearestEnemy(player, arrow.getPos(), 30);
            if (target == null) continue;

            // 计算朝向目标的方向
            Vec3d arrowPos = arrow.getPos();
            Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0);
            Vec3d offset = targetPos.subtract(arrowPos);

            // A fixed 30% turn overshoots nearby off-axis targets and can orbit them.
            // Use the remaining flight time to tighten the turn without changing arrow speed.
            Vec3d currentVel = arrow.getVelocity();
            double speed = currentVel.length();
            if(speed<.05||offset.lengthSquared()<1.0e-8)continue;
            double turn=Math.clamp(2.5*speed/offset.length(),.3,1);
            Vec3d desired=offset.normalize().multiply(speed);
            Vec3d mixed=currentVel.multiply(1-turn).add(desired.multiply(turn));
            Vec3d newVel=(mixed.lengthSquared()<1.0e-8?desired:mixed.normalize().multiply(speed));

            arrow.setVelocity(newVel);
            arrow.velocityModified = true;
            if(player.getServer().getTicks()%3==0)player.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                    arrow.getX(),arrow.getY(),arrow.getZ(),1,0,0,0,0);
        }
    }

    /**
     * 找到最近的敌人
     */
    private static ServerPlayerEntity findNearestEnemy(ServerPlayerEntity player, Vec3d from, double maxDist) {
        ServerPlayerEntity nearest = null;
        double nearestDist = maxDist;

        String playerTeam = getTeam(player);

        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (!com.megawalls.game.GameState.enemy(player, other)) continue;
            if (!other.isAlive()) continue;

            String otherTeam = getTeam(other);
            if (playerTeam != null && otherTeam != null && playerTeam.equals(otherTeam)) continue;

            double dist = from.distanceTo(other.getPos());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        return nearest;
    }

    private static String getTeam(ServerPlayerEntity player) {
        var team = player.getScoreboard().getScoreHolderTeam(player.getName().getString());
        return team != null ? team.getName() : null;
    }
}
