package com.megawalls.client;

import com.megawalls.network.S2C_SkillPattern;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

/** The native particle material layer; all emission stays client-side and bounded. */
final class SkillVanillaParticles {
    private static final ParticleEffect GOLD_DUST=new DustParticleEffect(new org.joml.Vector3f(1f,.72f,.15f),.42f);
    private static final ParticleEffect SOUL_DUST=new DustParticleEffect(new org.joml.Vector3f(.61f,.22f,1f),.45f);
    private static final double[] WING_TIP_X={1.86,1.89,1.85,1.77,1.65,1.51,1.35};
    private static final double[] WING_TIP_Y={2.61,2.43,2.23,2.03,1.83,1.65,1.51};
    private static int remaining=360;
    static void beginTick(){remaining=360;}
    static void emit(ClientWorld world,S2C_SkillPattern p,int age,int seed){
        int kind=p.shape();
        boolean wing=kind==S2C_SkillPattern.EAGLE_EYE||kind==S2C_SkillPattern.SOUL_WINGS;
        if(age!=0&&age!=2&&age!=5&&!(wing&&(age==12||age==20)))return;
        if(kind==S2C_SkillPattern.FIREBALL&&age!=0)return;
        boolean trail=kind==S2C_SkillPattern.FIREBALL||kind==S2C_SkillPattern.SOUL_TRAIL||kind==S2C_SkillPattern.MOLE_BORE;
        int count=Math.min(remaining,wing?32:kind==S2C_SkillPattern.FIREBALL?6:kind==S2C_SkillPattern.WOLF_HIT?8:kind==S2C_SkillPattern.WOLF_FORM?12:
                kind==S2C_SkillPattern.EAGLE_EYE?16:trail?10:age==0?48:20);
        remaining-=count;
        var rng=net.minecraft.util.math.random.Random.create(seed+(long)age*7919);
        var state=world.getBlockState(BlockPos.ofFloored(p.origin()).down());
        if(state.isAir())state=Blocks.STONE.getDefaultState();
        for(int i=0;i<count;i++){
            double a=rng.nextDouble()*Math.PI*2;
            double radius=Math.min(p.radius(),3.2)*(.12+Math.sqrt(rng.nextDouble())*.73);
            double x=p.origin().x+Math.cos(a)*radius,z=p.origin().z+Math.sin(a)*radius;
            double y=p.origin().y+.08+rng.nextDouble()*.45;
            double vx=Math.cos(a)*(.06+rng.nextDouble()*.14),vz=Math.sin(a)*(.06+rng.nextDouble()*.14);
            double vy=.035+rng.nextDouble()*.17;
            ParticleEffect effect;
            switch(kind){
                case S2C_SkillPattern.LIGHTNING -> {
                    effect=i%5==0?ParticleTypes.END_ROD:ParticleTypes.ELECTRIC_SPARK;
                    if(i%3==0){x=p.origin().x+(rng.nextDouble()-.5)*.9;z=p.origin().z+(rng.nextDouble()-.5)*.9;y+=rng.nextDouble()*4;}
                }
                case S2C_SkillPattern.FIREBALL,S2C_SkillPattern.FIRE_CHARGE,S2C_SkillPattern.FIRE_IMPACT -> {
                    effect=i%7==0?ParticleTypes.LAVA:i%4==0?ParticleTypes.SMOKE:ParticleTypes.FLAME;
                    if(kind==S2C_SkillPattern.FIREBALL){
                        effect=i==5?ParticleTypes.SMOKE:ParticleTypes.FLAME;
                        var back=p.origin().subtract(p.end()).normalize();
                        x=p.origin().x+back.x*rng.nextDouble()*.8+(rng.nextDouble()-.5)*.5;
                        y=p.origin().y+back.y*.4+(rng.nextDouble()-.5)*.5;
                        z=p.origin().z+back.z*rng.nextDouble()*.8+(rng.nextDouble()-.5)*.5;
                        vx=back.x*.09;vy=back.y*.09+.02;vz=back.z*.09;
                    }
                }
                // Actual HEART feedback is already emitted by ZombieSkill only after health increased.
                case S2C_SkillPattern.HEAL -> {effect=ParticleTypes.HAPPY_VILLAGER;y+=rng.nextDouble()*1.4;vy=.025;}
                case S2C_SkillPattern.TIDAL -> {effect=i%3==0?ParticleTypes.BUBBLE_POP:ParticleTypes.SPLASH;vx=-vx;vz=-vz;vy=.12+rng.nextDouble()*.25;}
                case S2C_SkillPattern.WEB_IMPACT -> {effect=i%6==0?ParticleTypes.CLOUD:new BlockStateParticleEffect(ParticleTypes.BLOCK,Blocks.COBWEB.getDefaultState());vy*=.35;vx*=1.3;vz*=1.3;}
                case S2C_SkillPattern.WEB_LEAP -> {effect=i%4==0?ParticleTypes.CLOUD:ParticleTypes.END_ROD;vy*=.6;}
                case S2C_SkillPattern.IRON_PUNCH -> {effect=i%5==0?ParticleTypes.CRIT:new BlockStateParticleEffect(ParticleTypes.BLOCK,Blocks.IRON_BLOCK.getDefaultState());}
                case S2C_SkillPattern.MOLE_BORE -> {effect=new BlockStateParticleEffect(ParticleTypes.BLOCK,state);}
                case S2C_SkillPattern.CREEPER_BLAST,S2C_SkillPattern.CREEPER_FUSE -> {effect=i%4==0?ParticleTypes.SMOKE:ParticleTypes.HAPPY_VILLAGER;vy*=1.5;}
                case S2C_SkillPattern.SOUL_WINGS,S2C_SkillPattern.SOUL_IMPACT,S2C_SkillPattern.SOUL_TRAIL -> {effect=i%3==0?ParticleTypes.SOUL_FIRE_FLAME:ParticleTypes.PORTAL;y+=rng.nextDouble()*1.3;}
                case S2C_SkillPattern.EAGLE_EYE -> {effect=ParticleTypes.ENCHANT;y+=rng.nextDouble()*1.2;}
                case S2C_SkillPattern.WOLF_FORM -> {effect=i%6==0?ParticleTypes.ANGRY_VILLAGER:i%3==0?ParticleTypes.SMOKE:ParticleTypes.CRIT;y+=rng.nextDouble();}
                case S2C_SkillPattern.ARROW_DRAW,S2C_SkillPattern.ARROW_IMPACT -> {effect=i%4==0?ParticleTypes.ENCHANT:ParticleTypes.CRIT;y+=rng.nextDouble()*1.2;}
                default -> {effect=i%4==0?ParticleTypes.SMOKE:ParticleTypes.DAMAGE_INDICATOR;y+=rng.nextDouble();}
            }
            if(wing){
                boolean hunter=kind==S2C_SkillPattern.EAGLE_EYE;
                int feather=(i/2+age/2)%7;
                double t=.18+rng.nextDouble()*.8;
                double span,localY,localZ;
                if(hunter){
                    double rootX=.75+.075*feather,rootY=1.88+.032*feather;
                    span=rootX+(WING_TIP_X[feather]-rootX)*t;
                    localY=rootY+(WING_TIP_Y[feather]-rootY)*t;
                    localZ=-.24+(-.16-.021*feather)*(1-t)+(-.65-.025*feather)*t;
                    effect=i%13==0?ParticleTypes.END_ROD:i%7==0?ParticleTypes.ENCHANT:GOLD_DUST;
                }else{
                    int finger=(i/2)%3;
                    double tipX=finger==0?1.68:finger==1?1.90:1.41;
                    double tipY=finger==0?2.62:finger==1?1.90:.94;
                    span=.30+(tipX-.30)*t;localY=1.45+(tipY-1.45)*t;
                    localZ=-.30-.66*t;
                    effect=i%13==0?ParticleTypes.SOUL_FIRE_FLAME:i%7==0?ParticleTypes.PORTAL:SOUL_DUST;
                }
                double deployment=Math.clamp(age/7.0,0,1);deployment=deployment*deployment*(3-2*deployment);
                double localX=(i%2==0?1:-1)*span*(.55+.45*deployment);
                double yaw=Math.toRadians(p.yaw());
                x=p.origin().x+Math.cos(yaw)*localX-Math.sin(yaw)*localZ;
                z=p.origin().z+Math.sin(yaw)*localX+Math.cos(yaw)*localZ;
                y=p.origin().y+localY+(rng.nextDouble()-.5)*.065;
                vx*=.055;vz*=.055;vy=.005+rng.nextDouble()*.014;
            }
            if(kind==S2C_SkillPattern.WOLF_HIT){effect=ParticleTypes.CRIT;y+=.65;vx*=.45;vz*=.45;vy*=.5;}
            world.addParticle(effect,false,x,y,z,vx,vy,vz);
        }
    }
}
