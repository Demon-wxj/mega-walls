package com.megawalls.client;
import com.megawalls.network.S2C_SkillPattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Immediate geometry; no delayed tasks or entity references survive a world change. */
public final class ClientSkillEffects {
    private static net.minecraft.client.world.ClientWorld budgetWorld;
    private static long budgetTick=Long.MIN_VALUE;
    private static int used;
    public static void reset(){budgetWorld=null;budgetTick=Long.MIN_VALUE;used=0;ClientSkillCinematics.reset();}
    public static void tick(MinecraftClient client){
        if(budgetWorld!=client.world){reset();budgetWorld=client.world;}
        ClientSkillCinematics.tick(client);
    }
    public static void play(MinecraftClient client,S2C_SkillPattern p){
        if(client.world==null||client.player==null||!p.valid()||!client.world.getRegistryKey().getValue().equals(p.dimension()))return;
        if(p.shape()>=S2C_SkillPattern.LIGHTNING){
            if(budgetWorld!=client.world){reset();budgetWorld=client.world;}
            ClientSkillCinematics.play(client,p);return;
        }
        double distance=client.player.getPos().distanceTo(p.origin());if(distance>48)return;
        if(budgetWorld!=client.world||budgetTick!=client.world.getTime()){
            budgetWorld=client.world;budgetTick=client.world.getTime();used=0;
        }
        int detail=client.options.getParticles().getValue().ordinal();
        int count=p.shape()==S2C_SkillPattern.WINGS?24:p.shape()==S2C_SkillPattern.CHARGE?9:p.shape()==S2C_SkillPattern.AURA?12:24;
        if(detail>0)count=Math.max(4,count/(detail+1));
        if(distance>16)count=Math.max(4,count/2);if(distance>32)count=4;
        if(!p.essential())count=Math.min(count,Math.max(0,(detail==0?120:60)-used));
        used+=count;
        double yaw=Math.toRadians(p.yaw());var right=new Vec3d(Math.cos(yaw),0,Math.sin(yaw));
        var forward=new Vec3d(-Math.sin(yaw),0,Math.cos(yaw));
        for(int i=0;i<count;i++){
            double fraction=i/(double)Math.max(1,count-1),angle=i*Math.PI*2/Math.max(1,count);
            Vec3d at;
            switch(p.shape()){
                case S2C_SkillPattern.LINE -> at=p.origin().lerp(p.end(),fraction);
                case S2C_SkillPattern.WINGS -> {
                    int half=Math.max(1,count/2);double span=(i%half+1)*1.8/half,side=i<half?-1:1;
                    at=p.origin().subtract(forward.multiply(.35)).add(right.multiply(side*span)).add(0,1+Math.sin(span*1.4)*.8,0);
                }
                case S2C_SkillPattern.CHARGE -> at=p.origin().add(Math.cos(angle)*p.radius(),.3+(i%3)*.4,Math.sin(angle)*p.radius());
                case S2C_SkillPattern.CLAWS -> at=p.origin().add(right.multiply((i%3-1)*.18+fraction*.25)).add(0,1.3-fraction*.7,0);
                default -> at=p.origin().add(Math.cos(angle)*p.radius(),p.shape()==S2C_SkillPattern.AURA?.2+fraction*.7:.15,Math.sin(angle)*p.radius());
            }
            client.world.addParticle(p.effect(),p.essential(),at.x,at.y,at.z,0,.008,0);
        }
    }
}
