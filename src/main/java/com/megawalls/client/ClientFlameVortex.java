package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_SkillPattern;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** A volumetric flame-dragon ornament coiling around the real projectile; entirely cosmetic. */
final class ClientFlameVortex {
    private static final Identifier LINE = MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW = MWMod.id("textures/effect/rift_glow.png");
    private static final Identifier ORB = MWMod.id("textures/effect/skill_magma_orb.png");
    private static final Identifier FLAME = MWMod.id("textures/effect/skill_flame.png");
    private static final double TAU = Math.PI * 2;
    private static final int STEPS = 56;
    private static final double HEAD_AXIAL = .78;
    private static final double NECK_AXIAL = .56;
    private static final double TAIL_AXIAL = -1.43;

    private ClientFlameVortex() {}

    // The caller's matrix is already translated to the projectile's interpolated origin.
    static void render(WorldRenderContext c, S2C_SkillPattern p, float age, float fade) {
        if (fade <= 0 || c.consumers() == null || c.matrixStack() == null) return;
        Vec3d forward = p.end().subtract(p.origin());
        if (forward.lengthSquared() < .00001) forward = new Vec3d(0, 0, -1);
        else forward = forward.normalize();
        Vec3d helper = Math.abs(forward.y) > .94 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = forward.crossProduct(helper).normalize();
        Vec3d up = right.crossProduct(forward).normalize();
        Vec3d camera = c.camera().getPos().subtract(p.origin());
        Vec3d view = camera.lengthSquared() > .00001 ? camera.normalize() : forward.multiply(-1);
        MinecraftClient client = MinecraftClient.getInstance();
        double time = client.world == null ? age : client.world.getTime() + c.tickCounter().getTickDelta(false);
        // Global time prevents a new cosmetic packet from restarting the visible spin.
        double phase = time * .105;
        MatrixStack matrices = c.matrixStack();

        // A real tubular surface supplies the silhouette; the flowing fire remains on top of it.
        dragonBody(c, matrices.peek(), forward, right, up, camera, view, phase, time, fade, false);

        matrices.push();
        matrices.multiply(c.camera().getRotation());
        VertexConsumer orb = c.consumers().getBuffer(RenderLayer.getEntityNoOutline(ORB));
        atlasQuad(orb, matrices.peek(), Vec3d.ZERO, new Vec3d(1, 0, 0), new Vec3d(0, 1, 0),
                .82f, .82f, (float)(time * 1.65), fade, false);
        matrices.pop();

        dragonBody(c, matrices.peek(), forward, right, up, camera, view, phase, time, fade, true);
        dragonHead(c, matrices.peek(), forward, right, up, camera, view, phase, time, fade);

        VertexConsumer glow = c.consumers().getBuffer(RenderLayer.getEyes(GLOW));
        // A compact ember envelope surrounds the same fixed ZERO as the native fireball.
        for (int i = 0; i < 28; i++) {
            double travel = fract(i * .61803398875 + time * .013);
            Vec3d center = curve(forward,right,up,phase+i*.38,travel)
                    .add(outward(right,up,phase+i*.38,travel).multiply(.07+.10*travel));
            double size = .017 + (1 - travel) * .024;
            double power = fade * (1 - travel) * .64 * coreVisibility(center,view);
            billboard(glow, matrices.peek(), center, camera, forward,
                    (float)size, (float)(size * 2.6), (float)power, (float)(power * .48), (float)(power * .065));
        }
        VertexConsumer line = c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for (int step = 0; step < 32; step++) {
            double t = .12 + .84 * step / 32.0, nt = .12 + .84 * (step + 1) / 32.0;
            Vec3d a = curve(forward,right,up,phase + Math.PI,t);
            Vec3d b = curve(forward,right,up,phase + Math.PI,nt);
            float f = fade * (float)((1-t)*smooth(.12,.28,t)*coreVisibility(a,view)) * .28f;
            strip(line, matrices.peek(), a,b,camera,.030f*(float)(1-t),.030f*(float)(1-nt),
                    f,f*.35f,f*.014f,f*.95f,f*.30f,f*.01f);
        }
    }

    private static Vec3d curve(Vec3d forward, Vec3d right, Vec3d up, double phase, double t) {
        double angle = phase + t * TAU * 1.22;
        // The body crosses from in front of ZERO to behind it: the ball is inside the coil.
        // Only the final short tail contracts; the chest never moves onto the ball's centre.
        double radius = (.77 + .045 * Math.sin(t * 18 - phase * .8)) * coilEnvelope(t);
        return forward.multiply(NECK_AXIAL + (TAIL_AXIAL - NECK_AXIAL) * t)
                .add(right.multiply(Math.cos(angle) * radius))
                .add(up.multiply(Math.sin(angle) * radius));
    }

    private static float bodyWidth(double t, double time) {
        return (float)((.145 + .018*Math.sin(t*25-time*.21)) * Math.pow(1-t,.63) + .006);
    }

    private static Vec3d outward(Vec3d right,Vec3d up,double phase,double t){
        double angle=phase+t*TAU*1.22;
        return right.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle)));
    }

    private static void dragonBody(WorldRenderContext c,MatrixStack.Entry m,Vec3d forward,
                                    Vec3d right,Vec3d up,Vec3d camera,Vec3d view,
                                    double phase,double time,float fade,boolean front){
        VertexConsumer solid=c.consumers().getBuffer(RenderLayer.getEntityNoOutline(ORB));
        for(int i=0;i<STEPS;i++){
            double t=i/(double)STEPS,nt=(i+1)/(double)STEPS;
            Vec3d a=curve(forward,right,up,phase,t),b=curve(forward,right,up,phase,nt);
            if((a.add(b).dotProduct(view)>=0)!=front)continue;
            tube(solid,m,a,b,outward(right,up,phase,t),bodyWidth(t,time),bodyWidth(nt,time),
                    time*1.6+t*12,fade*.84f*coreVisibility(a.add(b).multiply(.5),view),1,.76f,.48f,10);
        }
        VertexConsumer flames=c.consumers().getBuffer(RenderLayer.getEntityNoOutline(FLAME));
        // Overlapping flame vanes create a shaggy dragon mane, not round billboard beads.
        for(int i=0;i<32;i++){
            double t=.018+i*.0295;
            Vec3d center=curve(forward,right,up,phase,t);
            if((center.dotProduct(view)>=0)!=front)continue;
            Vec3d tangent=curve(forward,right,up,phase,Math.min(1,t+.012)).subtract(center).normalize();
            Vec3d radial=outward(right,up,phase,t);
            float size=(float)Math.pow(1-t,.63);
            for(int vane=0;vane<2;vane++){
                // The curve tangent points towards the tail: flame tongues must sweep aft.
                Vec3d direction=radial.multiply(.75+vane*.20).add(tangent.multiply(.95)).normalize();
                Vec3d pos=center.add(radial.multiply(bodyWidth(t,time)*.68));
                flameAlong(flames,m,pos,camera,direction,right,.19f*size,.31f*size,
                        (float)(time*2+i*.72+vane*5),fade*(vane==0?.66f:.30f)*coreVisibility(pos,view));
            }
        }
        VertexConsumer line=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int i=0;i<STEPS;i++){
            double t=i/(double)STEPS,nt=(i+1)/(double)STEPS;
            Vec3d radial=outward(right,up,phase,t),nextRadial=outward(right,up,phase,nt);
            Vec3d a=curve(forward,right,up,phase,t),b=curve(forward,right,up,phase,nt);
            if((a.add(b).dotProduct(view)>=0)!=front)continue;
            float size=(float)Math.pow(1-t,.55),next=(float)Math.pow(1-nt,.55);
            float visibleA=coreVisibility(a,view),visibleB=coreVisibility(b,view);
            strip(line,m,a,b,camera,.22f*size,.22f*next,fade*.62f*size*visibleA,fade*.09f*size*visibleA,0,
                    fade*.62f*next*visibleB,fade*.09f*next*visibleB,0);
            // A curved hot dorsal ridge follows the outside of the thick body.
            Vec3d ridgeA=a.add(radial.multiply(bodyWidth(t,time)*.82));
            Vec3d ridgeB=b.add(nextRadial.multiply(bodyWidth(nt,time)*.82));
            strip(line,m,ridgeA,ridgeB,camera,.045f*size,.045f*next,
                    fade*size*visibleA,fade*.65f*size*visibleA,fade*.10f*size*visibleA,
                    fade*next*visibleB,fade*.65f*next*visibleB,fade*.10f*next*visibleB);
        }
    }

    private static void dragonHead(WorldRenderContext c,MatrixStack.Entry m,Vec3d forward,
                                   Vec3d right,Vec3d up,Vec3d camera,Vec3d view,
                                   double phase,double time,float fade){
        // A small dragon crest is part of the outer coil. Its mouth faces velocity;
        // the fireball remains at ZERO in the middle, never attached to this muzzle.
        Vec3d crown=outward(right,up,phase,0);
        Vec3d side=forward.crossProduct(crown).normalize();
        Vec3d head=curve(forward,right,up,phase,0).add(forward.multiply(HEAD_AXIAL-NECK_AXIAL));
        fade*=Math.max(.20f,coreVisibility(head,view));
        VertexConsumer solid=c.consumers().getBuffer(RenderLayer.getEntityNoOutline(ORB));
        ellipsoid(solid,m,head,forward,side,crown,.23,.15,.14,time*1.6,fade,1,.86f,.60f);
        ellipsoid(solid,m,head.add(forward.multiply(.22)).add(crown.multiply(.084)),
                forward,side,crown,.18,.111,.063,time*1.6+3,fade,1,.83f,.49f);
        ellipsoid(solid,m,head.add(forward.multiply(.216)).subtract(crown.multiply(.060)),
                forward,side,crown,.174,.093,.039,time*1.6+6,fade,1,.69f,.32f);
        // Short swept horn tips preserve the head silhouette without a second large focal point.
        for(int sign=-1;sign<=1;sign+=2){
            Vec3d root=head.add(side.multiply(sign*.105)).add(crown.multiply(.085)).subtract(forward.multiply(.08));
            Vec3d tip=root.add(crown.multiply(.14)).add(side.multiply(sign*.03)).subtract(forward.multiply(.16));
            tube(solid,m,root,tip,side,.046f,.003f,time*1.6+sign*2,fade,1,.90f,.67f,7);
        }
        VertexConsumer flame=c.consumers().getBuffer(RenderLayer.getEntityNoOutline(FLAME));
        for(int i=0;i<4;i++){
            double at=i*.055;
            Vec3d pos=head.subtract(forward.multiply(at)).add(crown.multiply(.12+at*.10));
            flameAlong(flame,m,pos,camera,crown.subtract(forward.multiply(1.1)).normalize(),side,
                    .12f,.24f+(i%2)*.03f,(float)(time*2+i*2.7),fade*.73f);
        }
        VertexConsumer line=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int sign=-1;sign<=1;sign+=2){
            Vec3d eye=head.add(forward.multiply(.108)).add(side.multiply(sign*.141)).add(crown.multiply(.050));
            Vec3d eyeEnd=eye.add(forward.multiply(.072)).subtract(crown.multiply(.016));
            strip(line,m,eye,eyeEnd,camera,.014f,.008f,fade,fade*.98f,fade*.62f,fade,fade*.87f,fade*.34f);
            Vec3d brow=eye.subtract(forward.multiply(.024)).add(crown.multiply(.035));
            strip(line,m,brow,eyeEnd.add(crown.multiply(.024)),camera,.027f,.010f,
                    fade,fade*.42f,fade*.015f,fade,fade*.64f,fade*.08f);
            Vec3d jaw=head.add(forward.multiply(.384)).add(side.multiply(sign*.066)).subtract(crown.multiply(.036));
            strip(line,m,jaw,head.add(forward.multiply(.09)).add(side.multiply(sign*.102)).subtract(crown.multiply(.036)),
                    camera,.015f,.010f,fade,fade*.75f,fade*.16f,fade*.7f,fade*.24f,fade*.01f);
        }
    }

    private static void flameAlong(VertexConsumer v,MatrixStack.Entry m,Vec3d point,Vec3d camera,
                                    Vec3d direction,Vec3d fallback,float sx,float sy,float frame,float alpha){
        Vec3d facing=camera.subtract(point).normalize();
        Vec3d across=direction.crossProduct(facing);
        if(across.lengthSquared()<.00001)across=fallback;else across=across.normalize();
        Vec3d along=facing.crossProduct(across).normalize();
        atlasQuad(v,m,point,across,along,sx,sy,frame,alpha,false);
    }

    private static void tube(VertexConsumer v,MatrixStack.Entry m,Vec3d a,Vec3d b,Vec3d reference,
                             float radiusA,float radiusB,double frame,float alpha,float r,float g,float blue,int sides){
        Vec3d axis=b.subtract(a).normalize();
        Vec3d x=reference.subtract(axis.multiply(reference.dotProduct(axis)));
        if(x.lengthSquared()<.00001)x=axis.crossProduct(new Vec3d(0,0,1));
        if(x.lengthSquared()<.00001)x=axis.crossProduct(new Vec3d(1,0,0));
        x=x.normalize();Vec3d y=axis.crossProduct(x).normalize();
        int tile=Math.floorMod((int)frame,16);float u=tile%4*.25f,w=tile/4*.25f;
        for(int i=0;i<sides;i++){
            double angle=i*TAU/sides,next=(i+1)*TAU/sides;
            Vec3d n0=x.multiply(Math.cos(angle)).add(y.multiply(Math.sin(angle)));
            Vec3d n1=x.multiply(Math.cos(next)).add(y.multiply(Math.sin(next)));
            // Use the opaque central field of the animated magma frame, preserving surface volume.
            float uu=u+.06f+i/(float)sides*.13f,un=u+.06f+(i+1)/(float)sides*.13f;
            float shade=(float)(.62+.38*(.5+.5*Math.cos(angle-1)));
            vertex(v,m,a.add(n0.multiply(radiusA)),uu,w+.072f,r*shade,g*shade,blue*shade,alpha,n0);
            vertex(v,m,b.add(n0.multiply(radiusB)),uu,w+.178f,r*shade,g*shade,blue*shade,alpha,n0);
            vertex(v,m,b.add(n1.multiply(radiusB)),un,w+.178f,r*shade,g*shade,blue*shade,alpha,n1);
            vertex(v,m,a.add(n1.multiply(radiusA)),un,w+.072f,r*shade,g*shade,blue*shade,alpha,n1);
        }
    }

    private static void ellipsoid(VertexConsumer v,MatrixStack.Entry m,Vec3d center,Vec3d forward,
                                  Vec3d side,Vec3d up,double length,double width,double height,
                                  double frame,float alpha,float r,float g,float b){
        int tile=Math.floorMod((int)frame,16);float u=tile%4*.25f,w=tile/4*.25f;
        for(int lat=0;lat<7;lat++)for(int lon=0;lon<12;lon++){
            double p0=-Math.PI/2+lat*Math.PI/7,p1=-Math.PI/2+(lat+1)*Math.PI/7;
            double a0=lon*TAU/12,a1=(lon+1)*TAU/12;
            Vec3d[] n={sphereVector(forward,side,up,p0,a0),sphereVector(forward,side,up,p0,a1),
                    sphereVector(forward,side,up,p1,a1),sphereVector(forward,side,up,p1,a0)};
            for(int corner=0;corner<4;corner++){
                Vec3d nn=n[corner];
                Vec3d point=center.add(forward.multiply(nn.dotProduct(forward)*length))
                        .add(side.multiply(nn.dotProduct(side)*width)).add(up.multiply(nn.dotProduct(up)*height));
                float shade=(float)(.65+.35*(nn.dotProduct(up)*.5+.5));
                float uu=u+.063f+(lon+(corner==1||corner==2?1:0))*.124f/12;
                float vv=w+.065f+(lat+(corner>=2?1:0))*.12f/7;
                vertex(v,m,point,uu,vv,r*shade,g*shade,b*shade,alpha,nn);
            }
        }
    }

    private static Vec3d sphereVector(Vec3d f,Vec3d s,Vec3d u,double pitch,double yaw){
        return f.multiply(Math.cos(pitch)*Math.cos(yaw)).add(s.multiply(Math.cos(pitch)*Math.sin(yaw)))
                .add(u.multiply(Math.sin(pitch)));
    }

    /** Camera-facing ribbon with independent endpoint widths and a reversed back face. */
    private static void strip(VertexConsumer v, MatrixStack.Entry m, Vec3d a, Vec3d b, Vec3d camera,
                              float widthA, float widthB, float ar, float ag, float ab,
                              float br, float bg, float bb) {
        Vec3d tangent = b.subtract(a);
        Vec3d side = tangent.crossProduct(camera.subtract(a.add(b).multiply(.5)));
        if (side.lengthSquared() < .0000001) return;
        side = side.normalize();
        Vec3d a0 = a.subtract(side.multiply(widthA)), a1 = a.add(side.multiply(widthA));
        Vec3d b0 = b.subtract(side.multiply(widthB)), b1 = b.add(side.multiply(widthB));
        Vec3d normal = side.crossProduct(tangent).normalize();
        vertex(v,m,a0,0,.5f,ar,ag,ab,1,normal); vertex(v,m,b0,0,.5f,br,bg,bb,1,normal);
        vertex(v,m,b1,1,.5f,br,bg,bb,1,normal); vertex(v,m,a1,1,.5f,ar,ag,ab,1,normal);
        Vec3d back=normal.multiply(-1);
        vertex(v,m,a1,1,.5f,ar,ag,ab,1,back); vertex(v,m,b1,1,.5f,br,bg,bb,1,back);
        vertex(v,m,b0,0,.5f,br,bg,bb,1,back); vertex(v,m,a0,0,.5f,ar,ag,ab,1,back);
    }

    private static void atlasQuad(VertexConsumer v, MatrixStack.Entry m, Vec3d center, Vec3d right,
                                  Vec3d up, float sx, float sy, float frame, float alpha, boolean bothSides) {
        int tile = Math.floorMod((int)frame,16);
        float u0=tile%4*.25f+.004f, v0=tile/4*.25f+.004f;
        float u1=u0+.242f, v1=v0+.242f;
        quad(v,m,center,right,up,sx,sy,u0,v0,u1,v1,1,1,1,alpha,bothSides);
    }

    private static void billboard(VertexConsumer v, MatrixStack.Entry m, Vec3d center, Vec3d camera,
                                  Vec3d along, float sx, float sy, float r, float g, float b) {
        Vec3d facing = camera.subtract(center).normalize();
        Vec3d right = along.crossProduct(facing);
        if (right.lengthSquared() < .00001) right = new Vec3d(1,0,0);
        else right = right.normalize();
        Vec3d up = facing.crossProduct(right).normalize();
        quad(v,m,center,right,up,sx,sy,0,0,1,1,r,g,b,1,true);
    }

    private static void quad(VertexConsumer v, MatrixStack.Entry m, Vec3d center, Vec3d right, Vec3d up,
                             float sx,float sy,float u0,float v0,float u1,float v1,
                             float r,float g,float b,float alpha,boolean bothSides) {
        Vec3d x=right.multiply(sx), y=up.multiply(sy);
        Vec3d a=center.subtract(x).subtract(y), b0=center.add(x).subtract(y);
        Vec3d d=center.subtract(x).add(y), c=center.add(x).add(y);
        Vec3d normal=right.crossProduct(up).normalize();
        vertex(v,m,a,u0,v1,r,g,b,alpha,normal);vertex(v,m,b0,u1,v1,r,g,b,alpha,normal);
        vertex(v,m,c,u1,v0,r,g,b,alpha,normal);vertex(v,m,d,u0,v0,r,g,b,alpha,normal);
        if(bothSides){
            Vec3d back=normal.multiply(-1);
            vertex(v,m,d,u0,v0,r,g,b,alpha,back);vertex(v,m,c,u1,v0,r,g,b,alpha,back);
            vertex(v,m,b0,u1,v1,r,g,b,alpha,back);vertex(v,m,a,u0,v1,r,g,b,alpha,back);
        }
    }

    private static void vertex(VertexConsumer v,MatrixStack.Entry m,Vec3d p,float u,float w,
                               float r,float g,float b,float alpha,Vec3d normal){
        v.vertex(m,(float)p.x,(float)p.y,(float)p.z)
                .color(clamp(r),clamp(g),clamp(b),clamp(alpha)).texture(u,w)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(m,(float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    private static double fract(double value){return value-Math.floor(value);}
    private static double smooth(double low,double high,double value){
        double t=Math.max(0,Math.min(1,(value-low)/(high-low)));
        return t*t*(3-2*t);
    }
    private static double coilEnvelope(double t){
        return 1-.92*smooth(.73,1,t);
    }
    private static float coreVisibility(Vec3d point,Vec3d view){
        double depth=point.dotProduct(view);
        if(depth<=0)return 1;
        double projected=Math.sqrt(Math.max(0,point.lengthSquared()-depth*depth));
        // Thin only the foreground overlap, retaining an unobstructed view of the native orb.
        return (float)(.12+.88*smooth(.30,.63,projected));
    }
}
