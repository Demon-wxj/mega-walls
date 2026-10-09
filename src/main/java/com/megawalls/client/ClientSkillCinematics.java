package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_SkillPattern;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

/** Shared procedural lightning/fireball templates and short cosmetic recipes for the other classes. */
public final class ClientSkillCinematics {
    private static final Identifier FILAMENT=MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW=MWMod.id("textures/effect/rift_glow.png");
    private static final Identifier FIRE=MWMod.id("textures/effect/skill_fireball.png");
    private static final ArrayDeque<Event> ACTIVE=new ArrayDeque<>();
    private static final java.util.Map<Integer,net.minecraft.entity.Entity> PROJECTILES=new java.util.HashMap<>();
    private static ClientWorld world;
    private static long ticks;
    private static int serial;
    private record Event(S2C_SkillPattern packet,long born,int life,int seed) {
        float age(float delta){return ticks-born+delta;}
    }
    private ClientSkillCinematics(){}

    public static void register(){WorldRenderEvents.AFTER_ENTITIES.register(ClientSkillCinematics::render);}
    public static void preload(MinecraftClient client){
        client.getTextureManager().getTexture(FILAMENT);
        client.getTextureManager().getTexture(GLOW);
        client.getTextureManager().getTexture(FIRE);
        SkillLayeredDetails.preload(client);
        ClientManifestWings.preload(client);
        ClientRendingClaws.preload(client);
        ClientClassSurfaces.preload(client);
    }
    public static void reset(){ACTIVE.clear();PROJECTILES.clear();world=null;ticks=0;serial=0;SkillVanillaParticles.beginTick();}
    static void trackProjectile(net.minecraft.entity.Entity entity){
        if(entity.getWorld()==world&&(PROJECTILES.size()<128||PROJECTILES.containsKey(entity.getId())))PROJECTILES.put(entity.getId(),entity);
    }
    public static void tick(MinecraftClient client){
        if(client.world!=world){reset();world=client.world;}
        if(world==null)return;
        ticks++;
        SkillVanillaParticles.beginTick();
        ACTIVE.removeIf(e->ticks-e.born>=e.life);
        PROJECTILES.values().removeIf(entity->entity.isRemoved()||entity.getWorld()!=world);
        for(var e:ACTIVE){var p=anchored(e.packet,1);if(p!=null)SkillVanillaParticles.emit(world,p,(int)(ticks-e.born),e.seed);}
    }
    public static void play(MinecraftClient client,S2C_SkillPattern p){
        if(client.world==null||client.player==null||!p.valid()||
                !client.world.getRegistryKey().getValue().equals(p.dimension())||
                Math.min(client.player.squaredDistanceTo(p.origin()),client.player.squaredDistanceTo(p.end()))>64*64)return;
        if(world!=client.world){reset();world=client.world;}
        if(ACTIVE.size()>=128)ACTIVE.removeFirst();
        int seed=++serial*747796405;
        // Packets place trailing motes only. The visible head follows its real networked entity.
        if(p.shape()!=S2C_SkillPattern.FIREBALL&&p.shape()!=S2C_SkillPattern.SOUL_TRAIL)
            ACTIVE.addLast(new Event(p,ticks,lifetime(p.shape()),seed));
        SkillVanillaParticles.emit(world,p,0,seed);
    }
    private static int lifetime(int shape){return switch(shape){
        case S2C_SkillPattern.LIGHTNING -> 16;
        case S2C_SkillPattern.WOLF_HIT -> 8;
        case S2C_SkillPattern.EAGLE_EYE -> 40;
        case S2C_SkillPattern.SOUL_WINGS -> 36;
        case S2C_SkillPattern.FIREBALL,S2C_SkillPattern.SOUL_TRAIL -> 4;
        case S2C_SkillPattern.MOLE_BORE -> 9;
        case S2C_SkillPattern.CREEPER_FUSE,S2C_SkillPattern.ARROW_DRAW -> 12;
        case S2C_SkillPattern.FIRE_IMPACT,S2C_SkillPattern.ARROW_IMPACT,S2C_SkillPattern.SOUL_IMPACT -> 24;
        case S2C_SkillPattern.FIRE_CHARGE -> 15;
        case S2C_SkillPattern.WEB_LEAP,S2C_SkillPattern.CREEPER_BLAST,
                S2C_SkillPattern.IRON_PUNCH -> 24;
        case S2C_SkillPattern.HEAL,S2C_SkillPattern.WEB_IMPACT,S2C_SkillPattern.WOLF_FORM -> 28;
        default -> 28;
    };}
    private static void render(WorldRenderContext context){
        if(context.matrixStack()==null||context.consumers()==null||world!=context.world()||(ACTIVE.isEmpty()&&PROJECTILES.isEmpty()))return;
        var entries=new ArrayList<Event>();
        Vec3d camera=context.camera().getPos();
        float delta=context.tickCounter().getTickDelta(false);
        for(var e:ACTIVE){var p=anchored(e.packet,delta);if(p!=null)entries.add(new Event(p,e.born,e.life,e.seed));}
        for(var entity:PROJECTILES.values()){
            if(entity.isRemoved())continue;
            Vec3d pos=new Vec3d(net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderX,entity.getX()),
                    net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderY,entity.getY()),
                    net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderZ,entity.getZ()));
            int shape=entity instanceof com.megawalls.skill.SkillModels.Fireball?S2C_SkillPattern.FIREBALL:S2C_SkillPattern.SOUL_TRAIL;
            var packet=new S2C_SkillPattern(world.getRegistryKey().getValue(),shape,0,pos,pos.add(entity.getVelocity().normalize()),.7,entity.getYaw(),true);
            entries.add(new Event(packet,ticks-(entity.age%16),Integer.MAX_VALUE,entity.getId()));
        }
        entries.sort(Comparator.comparingDouble((Event e)->e.packet.origin().squaredDistanceTo(camera)).reversed());
        for(var e:entries){
            float age=e.age(delta);
            if(age>=e.life)continue;
            var p=e.packet;
            if(Math.min(p.origin().squaredDistanceTo(camera),p.end().squaredDistanceTo(camera))>64*64)continue;
            if(context.frustum()!=null&& !context.frustum().isVisible(new Box(p.origin(),p.end()).expand(Math.max(3,p.radius()+2))))continue;
            boolean grouped=EnderRiftRenderOrder.begin(context.consumers());
            try{renderOne(context,e,age,camera);}
            finally{EnderRiftRenderOrder.finish(context.consumers(),grouped);}
        }
    }
    private static S2C_SkillPattern anchored(S2C_SkillPattern p,float delta){
        if(p.anchorEntityId()<0)return p;
        var entity=world.getEntityById(p.anchorEntityId());
        if(!(entity instanceof net.minecraft.entity.player.PlayerEntity)||entity.isRemoved()||!entity.isAlive()||entity.getPos().squaredDistanceTo(p.origin())>16*16)return null;
        Vec3d origin=new Vec3d(net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderX,entity.getX()),
                net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderY,entity.getY()),
                net.minecraft.util.math.MathHelper.lerp(delta,entity.lastRenderZ,entity.getZ()));
        var player=(net.minecraft.entity.player.PlayerEntity)entity;
        float bodyYaw=net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta,player.prevBodyYaw,player.bodyYaw);
        return new S2C_SkillPattern(p.dimension(),p.shape(),p.particle(),origin,p.end().add(origin.subtract(p.origin())),p.radius(),bodyYaw,p.essential());
    }
    private static void renderOne(WorldRenderContext context,Event e,float age,Vec3d camera){
        var p=e.packet; int kind=p.shape();
        // Projectile packets arrive every three ticks: keep the body opaque between samples.
        float fade=kind==S2C_SkillPattern.FIREBALL?Math.min(1,(e.life-age)/.7f):
                Math.min(1,age/2+.22f)*Math.min(1,(e.life-age)/5f);
        if(fade<=.001f)return;
        float[] color=palette(kind);
        float r=color[0]*fade,g=color[1]*fade,b=color[2]*fade;
        float phase=e.seed*.00000017f;
        var matrices=context.matrixStack();
        matrices.push();
        matrices.translate(p.origin().x-camera.x,p.origin().y-camera.y,p.origin().z-camera.z);
        if(kind==S2C_SkillPattern.FIREBALL){ClientFlameVortex.render(context,p,age,fade);matrices.pop();return;}
        if(kind==S2C_SkillPattern.WOLF_HIT){ClientRendingClaws.render(context,p,age,fade);matrices.pop();return;}
        if(kind==S2C_SkillPattern.WOLF_FORM)ClientRendingClaws.render(context,p,age,fade);
        float face=(float)Math.toDegrees(Math.atan2(camera.x-p.origin().x,camera.z-p.origin().z));
        // All ribbon recipes work in a vertical plane facing the observer. Ground circles
        // rotate that plane 90 degrees, so walls and terrain can depth-occlude them naturally.
        var v=context.consumers().getBuffer(RenderLayer.getEyes(FILAMENT));
        switch(kind){
            case S2C_SkillPattern.LIGHTNING -> {
                matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
                lightning(v,matrices.peek(),Math.max(2,Math.min(7,p.end().y-p.origin().y)),age,phase,r,g,b);
                matrices.pop();
                groundRing(v,matrices,(float)(Math.min(2,p.radius()+.4)*(.3f+age*.12f)),.07f,r,g,b);
            }
            case S2C_SkillPattern.FIREBALL,S2C_SkillPattern.SOUL_TRAIL -> {
                matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
                float scale=kind==S2C_SkillPattern.FIREBALL?.62f:.43f;
                for(int i=0;i<5;i++){
                    float a=phase+i*2.39996f+age*.42f;
                    float x=(float)Math.cos(a)*scale*(.55f+i*.08f);
                    float y=(float)Math.sin(a)*scale*(.55f+i*.08f);
                    streak(v,matrices.peek(),x,y,x+(float)Math.cos(a+.6)*.25f,y+(float)Math.sin(a+.6)*.25f,
                            .055f,r*.7f,g*.7f,b*.7f);
                }
                matrices.pop();
            }
            case S2C_SkillPattern.ARROW_IMPACT,
                 S2C_SkillPattern.SOUL_IMPACT,S2C_SkillPattern.CREEPER_BLAST,
                 S2C_SkillPattern.IRON_PUNCH,S2C_SkillPattern.WEB_IMPACT -> {
                float radius=(float)Math.min(p.radius(),.25+age*(kind==S2C_SkillPattern.WEB_IMPACT?.62:kind==S2C_SkillPattern.CREEPER_BLAST?.38:.22));
                groundRing(v,matrices,radius,.11f,r,g,b);
                groundRing(v,matrices,radius*.64f,.055f,r*.7f,g*.7f,b*.7f);
                if(kind==S2C_SkillPattern.WEB_IMPACT)web(v,matrices,radius,r,g,b);
                else rays(v,matrices,(float)(Math.min(3,p.radius())*(.3f+age*.045f)),kind==S2C_SkillPattern.IRON_PUNCH?12:20,age,phase,r,g,b);
            }
            case S2C_SkillPattern.HEAL,S2C_SkillPattern.TIDAL,
                 S2C_SkillPattern.CREEPER_FUSE,S2C_SkillPattern.WOLF_FORM -> {
                for(int i=0;i<3;i++){
                    float radius=kind==S2C_SkillPattern.TIDAL?(float)(p.radius()*(1-((age*.045f+i*.33f)%1))):
                            (float)Math.min(p.radius(),.55+((age*.13f+i*.33f)%1)*p.radius());
                    groundRing(v,matrices,radius,.04f+i*.015f,r*(1-i*.2f),g*(1-i*.2f),b*(1-i*.2f));
                }
                matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
                helix(v,matrices.peek(),age,phase,kind==S2C_SkillPattern.TIDAL?3:2,.65f,r,g,b);
                matrices.pop();
            }
            case S2C_SkillPattern.WEB_LEAP,S2C_SkillPattern.MOLE_BORE -> {
                groundRing(v,matrices,(float)Math.min(p.radius(),.5+age*.15f),.075f,r,g,b);
                rays(v,matrices,(float)Math.min(p.radius(),1.1f+age*.08f),kind==S2C_SkillPattern.WEB_LEAP?10:7,age,phase,r,g,b);
            }
            case S2C_SkillPattern.SOUL_WINGS -> {
                groundRing(v,matrices,1.1f+age*.04f,.045f,r,g,b);
            }
            case S2C_SkillPattern.ARROW_DRAW,S2C_SkillPattern.EAGLE_EYE,
                 S2C_SkillPattern.FIRE_CHARGE,S2C_SkillPattern.WOLF_HIT -> {
                matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
                if(kind==S2C_SkillPattern.WOLF_HIT)claws(v,matrices.peek(),age,r,g,b);
                else if(kind==S2C_SkillPattern.EAGLE_EYE){} // Filled eye and hot rim are drawn by the layered pass.
                else helix(v,matrices.peek(),age,phase,kind==S2C_SkillPattern.FIRE_CHARGE?3:1,.7f,r,g,b);
                matrices.pop();
            }
            default -> {}
        }
        // A separate soft-light pass gives every recipe an emissive core without relying on shaders.
        var glow=context.consumers().getBuffer(RenderLayer.getEyes(GLOW));
        matrices.push();matrices.multiply(context.camera().getRotation());
        float core=switch(kind){
            case S2C_SkillPattern.FIREBALL -> .18f;
            case S2C_SkillPattern.SOUL_TRAIL -> .6f;
            case S2C_SkillPattern.LIGHTNING -> .5f;
            case S2C_SkillPattern.FIRE_IMPACT,S2C_SkillPattern.CREEPER_BLAST -> .7f;
            case S2C_SkillPattern.EAGLE_EYE,S2C_SkillPattern.SOUL_WINGS -> 0;
            default -> .12f;
        };
        float height=switch(kind){
            case S2C_SkillPattern.LIGHTNING -> .2f;
            case S2C_SkillPattern.ARROW_IMPACT,S2C_SkillPattern.FIRE_IMPACT,
                 S2C_SkillPattern.CREEPER_BLAST,S2C_SkillPattern.IRON_PUNCH,
                 S2C_SkillPattern.WEB_IMPACT,S2C_SkillPattern.MOLE_BORE -> .38f;
            case S2C_SkillPattern.FIREBALL,S2C_SkillPattern.SOUL_TRAIL,
                 S2C_SkillPattern.SOUL_IMPACT -> 0;
            default -> 1.05f;
        };
        sprite(glow,matrices.peek(),0,height,core,core,r*.65f,g*.65f,b*.65f);
        sprite(glow,matrices.peek(),0,height,core*.38f,core*.38f,r,g,b);
        matrices.pop();
        if(kind==S2C_SkillPattern.FIREBALL||kind==S2C_SkillPattern.SOUL_TRAIL){
            Vec3d backward=p.origin().subtract(p.end());
            double length=backward.length();
            if(length>.0001){
                backward=backward.multiply(1/length);
                for(int i=1;i<=5;i++){
                    matrices.push();
                    double distance=i*.22+age*.05;
                    matrices.translate(backward.x*distance,backward.y*distance,backward.z*distance);
                    matrices.multiply(context.camera().getRotation());
                    float f=(6-i)/6f;
                    sprite(glow,matrices.peek(),0,0,.25f*f,.25f*f,r*f*.55f,g*f*.55f,b*f*.55f);
                    matrices.pop();
                }
            }
        }
        SkillLayeredDetails.render(context,p,age,e.life,e.seed,fade,color);
        matrices.pop();
    }
    private static float[] palette(int kind){return switch(kind){
        case S2C_SkillPattern.LIGHTNING -> new float[]{.58f,.46f,1};
        case S2C_SkillPattern.FIREBALL,S2C_SkillPattern.FIRE_IMPACT,S2C_SkillPattern.FIRE_CHARGE -> new float[]{1,.38f,.045f};
        case S2C_SkillPattern.HEAL -> new float[]{.15f,1,.36f};
        case S2C_SkillPattern.ARROW_DRAW,S2C_SkillPattern.ARROW_IMPACT -> new float[]{1,.72f,.24f};
        case S2C_SkillPattern.WEB_LEAP,S2C_SkillPattern.WEB_IMPACT -> new float[]{.68f,.82f,1};
        case S2C_SkillPattern.TIDAL -> new float[]{.09f,.78f,1};
        case S2C_SkillPattern.CREEPER_FUSE,S2C_SkillPattern.CREEPER_BLAST -> new float[]{.38f,1,.10f};
        case S2C_SkillPattern.SOUL_WINGS,S2C_SkillPattern.SOUL_TRAIL,S2C_SkillPattern.SOUL_IMPACT -> new float[]{.47f,.19f,.88f};
        case S2C_SkillPattern.IRON_PUNCH -> new float[]{.88f,.84f,.66f};
        case S2C_SkillPattern.EAGLE_EYE -> new float[]{1,.74f,.17f};
        case S2C_SkillPattern.MOLE_BORE -> new float[]{.76f,.42f,.16f};
        default -> new float[]{1,.16f,.23f};
    };}
    /** Forking bolt with a hot central trunk; height is the server-confirmed strike column. */
    private static void lightning(VertexConsumer v,MatrixStack.Entry m,double height,float age,float phase,float r,float g,float b){
        int steps=24;
        for(int trunk=0;trunk<3;trunk++){
        float shift=(trunk-1)*.32f,x=shift,y=(float)height;
        float flash=.8f+.2f*(float)Math.sin(age*1.8f+trunk);
        for(int i=1;i<=steps;i++){
            float t=i/(float)steps;
            float nx=shift*(1-t)+(float)(Math.sin(i*8.7+phase+trunk*3)*.25+Math.sin(i*17.1+age*.55+phase+trunk)*.15)*(1-t*.45f);
            float ny=(float)height*(1-t);
            streak(v,m,x,y,nx,ny,.25f,r*.38f,g*.45f,b*.7f);
            streak(v,m,x,y,nx,ny,.07f,b*flash,b*.92f*flash,b*flash);
            streak(v,m,x,y,nx,ny,.023f,b,b,b);
            if(i%4==1){
                float side=(i%8==1?1:-1);
                float bx=nx+side*(.32f+.08f*i),by=ny+.14f;
                streak(v,m,nx,ny,bx,by,.055f,r*.75f,g*.8f,b*.95f);
                streak(v,m,bx,by,bx+side*.22f,by-.3f,.026f,r*.7f,g*.7f,b*.85f);
            }
            x=nx;y=ny;
        }
        }
    }
    private static void groundRing(VertexConsumer v,MatrixStack matrices,float radius,float width,float r,float g,float b){
        if(radius<=0)return;
        matrices.push();matrices.translate(0,.035,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        var m=matrices.peek();
        for(int i=0;i<48;i++){
            float a=i*(float)Math.PI/24,c=(i+1)*(float)Math.PI/24;
            streak(v,m,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,
                    (float)Math.cos(c)*radius,(float)Math.sin(c)*radius,width,r,g,b);
        }
        matrices.pop();
    }
    private static void rays(VertexConsumer v,MatrixStack matrices,float radius,int count,float age,float phase,float r,float g,float b){
        matrices.push();matrices.translate(0,.045,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        var m=matrices.peek();
        for(int i=0;i<count;i++){
            float a=i*(float)(Math.PI*2/count)+phase*.03f;
            float inner=radius*.24f,outer=radius*(.62f+.25f*hash(i,age));
            streak(v,m,(float)Math.cos(a)*inner,(float)Math.sin(a)*inner,
                    (float)Math.cos(a)*outer,(float)Math.sin(a)*outer,.035f,r,g,b);
        }
        matrices.pop();
    }
    private static void web(VertexConsumer v,MatrixStack matrices,float radius,float r,float g,float b){
        matrices.push();matrices.translate(0,.055,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        var m=matrices.peek();
        for(int ring=1;ring<=5;ring++)for(int i=0;i<16;i++){
            float a=i*(float)Math.PI/8,c=(i+1)*(float)Math.PI/8,rad=radius*ring/5;
            // Sag each inter-spoke thread inward, like a silk web spreading under tension.
            float mid=(a+c)*.5f,inner=rad*.91f;
            streak(v,m,(float)Math.cos(a)*rad,(float)Math.sin(a)*rad,(float)Math.cos(mid)*inner,(float)Math.sin(mid)*inner,.031f,r,g,b);
            streak(v,m,(float)Math.cos(mid)*inner,(float)Math.sin(mid)*inner,(float)Math.cos(c)*rad,(float)Math.sin(c)*rad,.031f,r,g,b);
            if(ring==5)streak(v,m,0,0,(float)Math.cos(a)*rad,(float)Math.sin(a)*rad,.04f,r,g,b);
        }
        matrices.pop();
    }
    private static void helix(VertexConsumer v,MatrixStack.Entry m,float age,float phase,int strands,float radius,float r,float g,float b){
        for(int strand=0;strand<strands;strand++){
            float x=0,y=0;
            for(int i=0;i<=24;i++){
                float t=i/24f,a=t*(float)Math.PI*3+age*.19f+phase+strand*(float)(Math.PI*2/strands);
                float nx=(float)Math.sin(a)*radius*(.55f+.35f*t),ny=.1f+t*1.8f;
                if(i>0)streak(v,m,x,y,nx,ny,.028f,r,g,b);
                x=nx;y=ny;
            }
        }
    }
    private static void wings(VertexConsumer v,MatrixStack.Entry m,float age,float phase,float r,float g,float b){
        for(int side=-1;side<=1;side+=2)for(int feather=0;feather<5;feather++){
            float x=0,y=1.1f+feather*.09f;
            for(int i=1;i<=8;i++){
                float t=i/8f;
                float nx=side*(t*(1.5f+feather*.18f));
                float ny=1.1f+t*(.95f-feather*.2f)+(float)Math.sin(t*3+age*.14f+phase)*.07f;
                streak(v,m,x,y,nx,ny,.028f,r,g,b);x=nx;y=ny;
            }
        }
    }
    private static void eye(VertexConsumer v,MatrixStack.Entry m,float age,float r,float g,float b){
        for(int side=-1;side<=1;side+=2){
            float x=-1,y=1.6f;
            for(int i=1;i<=20;i++){
                float t=i/20f,nx=-1+2*t,ny=1.6f+side*(float)Math.sin(t*Math.PI)*(.3f+.03f*(float)Math.sin(age*.3f));
                streak(v,m,x,y,nx,ny,.037f,r,g,b);x=nx;y=ny;
            }
        }
        for(int i=0;i<20;i++){
            float a=i*(float)Math.PI/10,c=(i+1)*(float)Math.PI/10;
            streak(v,m,(float)Math.cos(a)*.22f,1.6f+(float)Math.sin(a)*.22f,
                    (float)Math.cos(c)*.22f,1.6f+(float)Math.sin(c)*.22f,.03f,r,g,b);
        }
    }
    private static void claws(VertexConsumer v,MatrixStack.Entry m,float age,float r,float g,float b){
        for(int i=0;i<3;i++){
            float x=-.6f+i*.4f;
            streak(v,m,x,1.65f,x+.38f,.55f,.085f,r,g,b);
            streak(v,m,x+.38f,.55f,x+.52f,.38f,.045f,r,g,b);
        }
    }
    private static void streak(VertexConsumer v,MatrixStack.Entry m,float x0,float y0,float x1,float y1,float width,float r,float g,float b){
        float dx=x1-x0,dy=y1-y0,length=(float)Math.sqrt(dx*dx+dy*dy);
        if(length<.00001f)return;
        float ox=-dy/length*width,oy=dx/length*width;
        for(int side=-1;side<=1;side+=2){
            vertex(v,m,x0-ox,y0-oy,.02f*side,0,.5f,r,g,b);
            vertex(v,m,x1-ox,y1-oy,.02f*side,0,.5f,r,g,b);
            vertex(v,m,x1+ox,y1+oy,.02f*side,1,.5f,r,g,b);
            vertex(v,m,x0+ox,y0+oy,.02f*side,1,.5f,r,g,b);
        }
    }
    private static void sprite(VertexConsumer v,MatrixStack.Entry m,float x,float y,float sx,float sy,float r,float g,float b){
        for(int side=-1;side<=1;side+=2){
            vertex(v,m,x-sx,y-sy,.025f*side,0,1,r,g,b);
            vertex(v,m,x+sx,y-sy,.025f*side,1,1,r,g,b);
            vertex(v,m,x+sx,y+sy,.025f*side,1,0,r,g,b);
            vertex(v,m,x-sx,y+sy,.025f*side,0,0,r,g,b);
        }
    }
    private static void atlasSprite(VertexConsumer v,MatrixStack.Entry m,float y,float size,int frame,float power){
        float u0=((frame%4)*256+4)/1024f,u1=((frame%4)*256+252)/1024f;
        float v0=((frame/4)*256+4)/1024f,v1=((frame/4)*256+252)/1024f;
        for(int side=-1;side<=1;side+=2){
            vertexAlpha(v,m,-size,y-size,.03f*side,u0,v1,1,1,1,power);
            vertexAlpha(v,m,size,y-size,.03f*side,u1,v1,1,1,1,power);
            vertexAlpha(v,m,size,y+size,.03f*side,u1,v0,1,1,1,power);
            vertexAlpha(v,m,-size,y+size,.03f*side,u0,v0,1,1,1,power);
        }
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float u,float w,float r,float g,float b){
        vertexAlpha(v,m,x,y,z,u,w,r,g,b,1);
    }
    private static void vertexAlpha(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float u,float w,float r,float g,float b,float a){
        v.vertex(m,x,y,z).color(Math.max(0,Math.min(1,r)),Math.max(0,Math.min(1,g)),Math.max(0,Math.min(1,b)),Math.max(0,Math.min(1,a)))
                .texture(u,w).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(m,0,0,z<0?-1:1);
    }
    private static float hash(int i,float salt){
        int h=i*374761393+(int)(salt*1000)*668265263;
        h=(h^(h>>>13))*1274126177;
        return (h&0x7fffffff)/(float)0x7fffffff;
    }
}
