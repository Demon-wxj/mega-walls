package com.megawalls.skill;
import com.megawalls.network.S2C_SkillPattern;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.particle.*;
import net.minecraft.sound.*;
import net.minecraft.util.math.Vec3d;

public final class SkillEffects {
    /** One geometry packet per observer instead of a particle packet per vertex. */
    public static void pattern(ServerPlayerEntity p,int shape,ParticleEffect effect,Vec3d origin,Vec3d end,double radius,boolean essential){
        int kind=S2C_SkillPattern.particleId(effect);
        if(kind<0){p.getServerWorld().spawnParticles(effect,origin.x,origin.y+.25,origin.z,8,radius*.4,.2,radius*.4,.02);return;}
        var event=new S2C_SkillPattern(p.getWorld().getRegistryKey().getValue(),shape,kind,origin,end,radius,p.getYaw(),essential);
        for(var observer:p.getServerWorld().getPlayers()){
            if(!com.megawalls.minigame.GameSessions.mw(observer)||observer.squaredDistanceTo(origin)>48*48||!ServerPlayNetworking.canSend(observer,S2C_SkillPattern.ID))continue;
            if(observer!=p&&p.getServerWorld().raycast(new net.minecraft.world.RaycastContext(observer.getEyePos(),origin.add(0,.6,0),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,observer))
                    .getType()!=net.minecraft.util.hit.HitResult.Type.MISS)continue;
            ServerPlayNetworking.send(observer,event);
        }
    }
    /** Purely decorative event; world depth decides visibility and no skill result depends on clients. */
    public static void cinematic(ServerPlayerEntity owner,int shape,Vec3d origin,Vec3d end,double radius){
        boolean attached=origin.squaredDistanceTo(owner.getPos())<.01&&switch(shape){
            case S2C_SkillPattern.FIRE_CHARGE,S2C_SkillPattern.CREEPER_FUSE,S2C_SkillPattern.EAGLE_EYE,
                    S2C_SkillPattern.WOLF_FORM,S2C_SkillPattern.SOUL_WINGS,S2C_SkillPattern.HEAL -> true;
            default -> false;
        };
        var event=new S2C_SkillPattern(owner.getWorld().getRegistryKey().getValue(),shape,
                S2C_SkillPattern.particleId(ParticleTypes.CLOUD),origin,end,radius,owner.getYaw(),true,attached?owner.getId():-1);
        if(!event.valid()||shape<S2C_SkillPattern.LIGHTNING)return;
        for(var observer:owner.getServerWorld().getPlayers()){
            if(!com.megawalls.minigame.GameSessions.mw(observer)||
                    Math.min(observer.squaredDistanceTo(origin),observer.squaredDistanceTo(end))>64*64||
                    !ServerPlayNetworking.canSend(observer,S2C_SkillPattern.ID))continue;
            ServerPlayNetworking.send(observer,event);
        }
    }
    public static void shadowWings(ServerPlayerEntity p){pattern(p,S2C_SkillPattern.WINGS,ParticleTypes.SOUL,p.getPos(),p.getPos(),1.8,false);}
    public static void charge(ServerPlayerEntity p,int elapsed,int total){
        double progress=elapsed/(double)Math.max(1,total);
        pattern(p,S2C_SkillPattern.CHARGE,ParticleTypes.FLAME,p.getPos(),p.getPos(),1-progress*.45,true);
        if(elapsed%10==0){
            p.sendMessage(net.minecraft.text.Text.literal("§6烈焰蓄力 §e"+String.format(java.util.Locale.ROOT,"%.1f",(total-elapsed)/20d)+"秒"),true);
            if(elapsed%20==0)sound(p,SoundEvents.ENTITY_BLAZE_AMBIENT,.25f,.8f+(float)progress*.4f);
        }
    }
    public static void wave(ServerPlayerEntity p,ParticleEffect effect,double radius,boolean inward){
        var origin=p.getPos();
        for(int step=0;step<3;step++){
            final double r=radius*(inward?(3-step)/3d:(step+1)/3d);
            SkillTasks.later(p,step*2,()->pattern(p,S2C_SkillPattern.RING,effect,origin,origin,r,false));
        }
    }
    public static void tether(ServerPlayerEntity from,ServerPlayerEntity to,ParticleEffect effect){
        var a=from.getPos().add(0,1,0);var b=to.getPos().add(0,1,0);
        pattern(from,S2C_SkillPattern.LINE,effect,a,b,0,false);
    }
    public static void aura(ServerPlayerEntity p,ParticleEffect effect,double radius){pattern(p,S2C_SkillPattern.AURA,effect,p.getPos(),p.getPos(),radius,false);}
    public static void ring(ServerPlayerEntity p,double radius,boolean smoke){pattern(p,S2C_SkillPattern.RING,smoke?ParticleTypes.SMOKE:ParticleTypes.CLOUD,p.getPos(),p.getPos(),radius,smoke);}
    public static void explosion(ServerPlayerEntity p,double radius){
        ring(p,radius,false);
        p.getServerWorld().spawnParticles(ParticleTypes.EXPLOSION,p.getX(),p.getY()+.4,p.getZ(),1,0,0,0,0);
        sound(p,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),.8f,.9f);
    }
    public static void claws(ServerPlayerEntity p,Vec3d hit){pattern(p,S2C_SkillPattern.CLAWS,ParticleTypes.CRIT,hit,hit,.4,false);}
    public static void sound(ServerPlayerEntity p,SoundEvent sound,float volume,float pitch){
        p.getServerWorld().playSound(null,p.getX(),p.getY(),p.getZ(),sound,SoundCategory.PLAYERS,volume,pitch);
    }
}
