package com.megawalls.maintenance;
import com.megawalls.minigame.GameSessions;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;
/** Only the optional Mega Walls ambience/skill particles pass here; abilities, projectiles and warnings are independent. */
public final class VisualAudience {
    private static final Map<UUID,Integer> modes=new HashMap<>();
    public static void set(ServerPlayerEntity p,int v){if(v>=0&&v<=3)modes.put(p.getUuid(),v);}
    public static void forget(UUID id){modes.remove(id);} public static void clear(){modes.clear();}
    public static boolean receives(ServerPlayerEntity p){return GameSessions.mw(p)&&modes.getOrDefault(p.getUuid(),2)>0;}
    public static int particles(ServerWorld world,ParticleEffect fx,double x,double y,double z,int count,double dx,double dy,double dz,double speed){
        int sent=0;for(var p:world.getPlayers()){int mode=modes.getOrDefault(p.getUuid(),2);
            if(!GameSessions.mw(p)||mode==0||p.squaredDistanceTo(x,y,z)>(mode==1?24*24:32*32))continue;
            if(world.spawnParticles(p,fx,false,x,y,z,mode==1&&count>1?Math.max(1,count/4):count,dx,dy,dz,speed))sent++;
        }return sent;
    }
    private VisualAudience(){}
}
