package com.megawalls.network;
import com.megawalls.MWMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Bounded decoration only: damage and targeting are always resolved on the server. */
public record S2C_SkillPattern(Identifier dimension,int shape,int particle,Vec3d origin,Vec3d end,
                               double radius,float yaw,boolean essential,int anchorEntityId) implements CustomPayload {
    public S2C_SkillPattern(Identifier dimension,int shape,int particle,Vec3d origin,Vec3d end,double radius,float yaw,boolean essential){
        this(dimension,shape,particle,origin,end,radius,yaw,essential,-1);
    }
    public static final Id<S2C_SkillPattern> ID=new Id<>(MWMod.id("skill_pattern_v2"));
    public static final int RING=0,AURA=1,WINGS=2,LINE=3,CHARGE=4,CLAWS=5;
    // Timed cinematic recipes share the existing bounded, geometry-only payload.
    public static final int LIGHTNING=6,FIREBALL=7,FIRE_IMPACT=8,HEAL=9,ARROW_DRAW=10,
            ARROW_IMPACT=11,WEB_LEAP=12,WEB_IMPACT=13,TIDAL=14,CREEPER_FUSE=15,
            CREEPER_BLAST=16,SOUL_WINGS=17,SOUL_TRAIL=18,SOUL_IMPACT=19,
            IRON_PUNCH=20,FIRE_CHARGE=21,EAGLE_EYE=22,MOLE_BORE=23,
            WOLF_FORM=24,WOLF_HIT=25;
    private static final SimpleParticleType[] PARTICLES={ParticleTypes.CLOUD,ParticleTypes.SMOKE,ParticleTypes.HAPPY_VILLAGER,
            ParticleTypes.HEART,ParticleTypes.PORTAL,ParticleTypes.SOUL,ParticleTypes.FLAME,ParticleTypes.ENCHANT,
            ParticleTypes.WITCH,ParticleTypes.CRIT,ParticleTypes.BUBBLE,ParticleTypes.SPLASH,ParticleTypes.ELECTRIC_SPARK};
    public static int particleId(ParticleEffect effect){for(int i=0;i<PARTICLES.length;i++)if(effect==PARTICLES[i])return i;return -1;}
    public SimpleParticleType effect(){return PARTICLES[Math.clamp(particle,0,PARTICLES.length-1)];}
    public boolean valid(){return shape>=RING&&shape<=WOLF_HIT&&particle>=0&&particle<PARTICLES.length
            &&Double.isFinite(radius)&&radius>=0&&radius<=25&&Float.isFinite(yaw)
            &&anchorEntityId>=-1&&finite(origin)&&finite(end)&&origin.squaredDistanceTo(end)<=4096;}
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    public static final PacketCodec<RegistryByteBuf,S2C_SkillPattern> CODEC=PacketCodec.of((p,b)->{
        b.writeIdentifier(p.dimension);b.writeVarInt(p.shape);b.writeVarInt(p.particle);
        b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);
        b.writeDouble(p.end.x);b.writeDouble(p.end.y);b.writeDouble(p.end.z);
        b.writeDouble(p.radius);b.writeFloat(p.yaw);b.writeBoolean(p.essential);b.writeVarInt(p.anchorEntityId);
    },b->new S2C_SkillPattern(b.readIdentifier(),b.readVarInt(),b.readVarInt(),
            new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),
            b.readDouble(),b.readFloat(),b.readBoolean(),b.readVarInt()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
