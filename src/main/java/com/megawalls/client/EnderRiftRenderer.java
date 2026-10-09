package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_EnderRift;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;

/** Flowing plasma over a starfield in a horizontal tear, using standard depth-tested Minecraft shaders. */
public final class EnderRiftRenderer {
    private static final Identifier NEBULA = MWMod.id("textures/effect/ender_starfield.png");
    private static final Identifier PLASMA = MWMod.id("textures/effect/rift_plasma.png");
    private static final Identifier FILAMENT = MWMod.id("textures/effect/rift_filament_glow.png");
    private static final Identifier GLOW = MWMod.id("textures/effect/rift_glow.png");
    private static final int SEGMENTS = 96;
    private static final int RINGS = 1;
    private static final float HEIGHT = 1.22f;
    private static final float WIDTH = 2.10f;
    // Reused on the render thread, including between endpoints. No per-vertex object allocations.
    private static final float[] MX = new float[(RINGS + 1) * (SEGMENTS + 1)];
    private static final float[] MY = new float[MX.length];
    private static final float[] MU = new float[MX.length], MV = new float[MX.length];
    private static final float[] PX = new float[MX.length], PY = new float[MX.length];
    private record Endpoint(Vec3d foot, float yaw, float age, boolean arrival, long seed) {}

    private EnderRiftRenderer() {}
    public static void register() { WorldRenderEvents.AFTER_ENTITIES.register(EnderRiftRenderer::render); }

    static void preload(MinecraftClient client) {
        // Decode/upload the 64-frame atlas before play, not during the first teleport.
        // Once registered, Minecraft also owns its reload and disposal lifecycle.
        var textures=client.getTextureManager();
        textures.getTexture(NEBULA); textures.getTexture(PLASMA);
        textures.getTexture(FILAMENT); textures.getTexture(GLOW);
    }

    private static void render(WorldRenderContext context) {
        if (context.matrixStack() == null || context.consumers() == null) return;
        var client = MinecraftClient.getInstance();
        if (client.world != context.world() || client.player == null) return;
        float delta = context.tickCounter().getTickDelta(false);
        var endpoints = new ArrayList<Endpoint>();
        for (var cast : ClientEnderRifts.active()) {
            float age = cast.age() + delta;
            if (age >= S2C_EnderRift.LIFETIME_TICKS) continue;
            endpoints.add(new Endpoint(cast.event.origin(), cast.event.originYaw(), age, false, cast.event.castId().getLeastSignificantBits()));
            endpoints.add(new Endpoint(cast.event.destination(), cast.event.destinationYaw(), age, true, cast.event.castId().getLeastSignificantBits()));
        }
        // Emissive translucent geometry does not write depth: emit far endpoints before near ones.
        endpoints.sort(Comparator.comparingDouble((Endpoint e) -> e.foot.squaredDistanceTo(context.camera().getPos())).reversed());
        boolean drew = false;
        for (var e : endpoints) {
            // Iris batches otherwise unordered world callbacks. Each endpoint needs its own
            // base -> plasma -> rim -> motes edges; one shared group would introduce a cycle.
            boolean grouped = EnderRiftRenderOrder.begin(context.consumers());
            try { drew |= drawEndpoint(context, e.foot, e.yaw, e.age, e.arrival, e.seed); }
            finally { EnderRiftRenderOrder.finish(context.consumers(), grouped); }
        }
        if (drew) ClientEnderRifts.rendered();
    }

    private static boolean drawEndpoint(WorldRenderContext context, Vec3d foot, float yaw, float age, boolean arrival, long seed) {
        Vec3d camera = context.camera().getPos();
        if (foot.squaredDistanceTo(camera) > 64 * 64) return false;
        if (context.frustum() != null && !context.frustum().isVisible(new Box(foot.add(-3.5, -.6, -3.5), foot.add(3.5, 3.4, 3.5)))) return false;
        float open = ease(Math.min(1, (age + .45f) / 3f));
        float close = ease(Math.min(1, (S2C_EnderRift.LIFETIME_TICKS - age) / 6f));
        float width = WIDTH * (.25f + .75f * (float)Math.sqrt(open * close));
        float height = HEIGHT * open * close;
        float opacity = Math.min(1, close * 1.4f);
        // A near-camera fade prevents a flat surface covering the first-person view on arrival.
        // Third-person observers retain the complete portal. No camera/FOV/aim changes.
        double yawRad = Math.toRadians(yaw);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3d center = foot.add(0, 1.30, 0).subtract(forward.multiply(.38));
        double planeDistance = Math.abs(camera.subtract(center).dotProduct(forward));
        double horizontal = camera.subtract(center).dotProduct(new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad)));
        if (Math.abs(horizontal) < 2.4 && Math.abs(camera.y - center.y) < 1.55)
            opacity *= (float) Math.max(.05, Math.min(1, (planeDistance - .12) / .6));
        float phase = (float) ((seed & 65535) / 65535.0 * Math.PI * 2);
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        var entry = matrices.peek();
        var consumers = context.consumers();
        // Iris routes translucent-emissive through SpiderEyes/additive blending, which loses dark
        // interiors against daylight. NoOutline preserves alpha blending and does not write depth.
        prepareSurface(width, height, age, phase);
        nebula(consumers.getBuffer(RenderLayer.getEntityNoOutline(NEBULA)), entry, opacity);
        var plasma = consumers.getBuffer(RenderLayer.getEyes(PLASMA));
        plasma(plasma, entry, age, opacity);
        // getBuffer can flush the previous fallback layer; never retain a consumer across switches.
        var light = consumers.getBuffer(RenderLayer.getEyes(FILAMENT));
        rim(light, entry, width, height, age, phase, .19f, opacity, opacity, opacity);
        crawlingArcs(light, entry, width, height, age, phase, opacity);
        branches(light, entry, width, height, age, phase, opacity);
        var glow = consumers.getBuffer(RenderLayer.getEyes(GLOW));
        stars(glow, entry, width, height, age, phase, opacity);
        wisps(glow, entry, width, height, age, phase, opacity, arrival);
        // A brief bright seam accompanies opening and the final collapse.
        float seam = (float) (Math.exp(-age * .8) + Math.pow(1 - close, 2) * .8) * opacity;
        if (seam > .005) billboard(glow, entry, 0, 0, .09f, width * 1.2f, .025f + .06f * seam, .5f * seam, .65f * seam, seam);
        matrices.pop();
        return true;
    }

    private static void prepareSurface(float width, float height, float age, float phase) {
        float turn = phase + age * .004f;
        float cos = (float)Math.cos(turn), sin = (float)Math.sin(turn);
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (i % SEGMENTS) * (float)(Math.PI * 2 / SEGMENTS);
            float[] boundary = point(angle, 1, 1, 1, age, phase);
            for (int ring = 0; ring <= RINGS; ring++) {
                int n = ring * (SEGMENTS + 1) + i;
                float radius = ring / (float)RINGS;
                float x = boundary[0] * radius, y = boundary[1] * radius;
                MX[n] = x * width; MY[n] = y * height;
                // A continuous fluid field is baked at pixel resolution in 64 atlas frames.
                // Adjacent frames blend on the GPU; no per-frame CPU isocontour extraction.
                PX[n] = (x / 2.30f + .5f); PY[n] = (y / 2.30f + .5f);
                MU[n] = .5f + .34f * (x*cos-y*sin);
                MV[n] = .5f + .34f * (x*sin+y*cos);
            }
        }
    }

    private static void nebula(VertexConsumer v, MatrixStack.Entry m, float opacity) {
        for (int i=0;i<SEGMENTS;i++) {
            int b=i+SEGMENTS+1;
            surfaceVertex(v,m,i,opacity); surfaceVertex(v,m,b,opacity);
            surfaceVertex(v,m,b+1,opacity); surfaceVertex(v,m,i+1,opacity);
        }
    }

    private static void surfaceVertex(VertexConsumer v, MatrixStack.Entry m, int n, float opacity) {
        vertex(v,m,MX[n],MY[n],0,MU[n],MV[n],.46f,.21f,.60f,opacity);
    }

    private static void plasma(VertexConsumer v, MatrixStack.Entry m, float age, float opacity) {
        float frame = Math.min(63, Math.max(0,age/S2C_EnderRift.LIFETIME_TICKS*63));
        int current=(int)frame;
        plasmaFrame(v,m,current,opacity*(1-(frame-current)));
        if(current<63) plasmaFrame(v,m,current+1,opacity*(frame-current));
    }

    private static void plasmaFrame(VertexConsumer v, MatrixStack.Entry m, int frame, float strength) {
        if(strength<.001f) return;
        for(int i=0;i<SEGMENTS;i++) {
            int b=i+SEGMENTS+1;
            for(int side=-1;side<=1;side+=2) {
                if(side==1) {
                    plasmaVertex(v,m,i,frame,side,strength); plasmaVertex(v,m,b,frame,side,strength);
                    plasmaVertex(v,m,b+1,frame,side,strength); plasmaVertex(v,m,i+1,frame,side,strength);
                } else {
                    plasmaVertex(v,m,i+1,frame,side,strength); plasmaVertex(v,m,b+1,frame,side,strength);
                    plasmaVertex(v,m,b,frame,side,strength); plasmaVertex(v,m,i,frame,side,strength);
                }
            }
        }
    }

    private static void plasmaVertex(VertexConsumer v, MatrixStack.Entry m, int n, int frame, int side, float strength) {
        // Two texel gutters stop bilinear filtering sampling the adjacent frame.
        float u=((frame%8)*512+2+PX[n]*507)/4096f;
        float w=((frame/8)*512+2+PY[n]*507)/4096f;
        vertex(v,m,MX[n],MY[n],.021f*side,u,w,strength,strength,strength,1);
    }

    private static float noise(float x, float y) {
        int ix=(int)Math.floor(x), iy=(int)Math.floor(y);
        float u=ease(x-ix), w=ease(y-iy);
        float a=hash(ix,iy), b=hash(ix+1,iy), c=hash(ix,iy+1), d=hash(ix+1,iy+1);
        return (a+(b-a)*u)*(1-w)+(c+(d-c)*u)*w;
    }

    private static float[] point(float angle, float radius, float width, float height, float age, float phase) {
        float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
        float tear = 1 + .065f * (float)Math.sin(angle * 11 + phase + age * .36f)
                + .032f * (float)Math.sin(angle * 23 - age * .5f)
                + .020f * (float)Math.sin(angle * 41 + phase - age * .7f);
        // Four pointed tips and concave shoulders, like a horizontal tear in a stretched membrane.
        return new float[]{Math.copySign((float)Math.pow(Math.abs(x),2.1),x)*width*tear*radius,
                Math.copySign((float)Math.pow(Math.abs(y),2.1),y)*height*tear*radius};
    }

    private static void crawlingArcs(VertexConsumer v, MatrixStack.Entry m, float width, float height, float age, float phase, float opacity) {
        for (int arc=0;arc<13;arc++) {
            float start=phase+arc*2.399963f+age*(arc%2==0?.042f:-.037f);
            float span=.32f+hash(arc,91)*.65f;
            float[] prev=null;
            for(int step=0;step<=22;step++) {
                float progress=step/22f, angle=start+span*progress;
                float lift=(.055f+.13f*noise(angle*11+age*.23f,arc*3.1f))* (float)Math.sin(progress*Math.PI);
                float jitter=(noise(angle*37-age*.8f,arc*5.4f)-.5f)*.10f;
                float[] p=point(angle,1,width,height,age,phase);
                p[0]+=(float)Math.cos(angle)*(lift+jitter); p[1]+=(float)Math.sin(angle)*(lift+jitter);
                if(prev!=null) {
                    float power=opacity*(float)Math.sin(progress*Math.PI)*(.5f+.5f*noise(arc*7,age*.8f));
                    ribbon(v,m,prev[0],prev[1],p[0],p[1],.054f,.09f,power,power,power);
                }
                prev=p;
            }
        }
    }

    private static void rim(VertexConsumer v, MatrixStack.Entry m, float width, float height, float age, float phase,
                            float thickness, float r, float g, float b) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * (float) (Math.PI * 2 / SEGMENTS), a1 = (i + 1) * (float) (Math.PI * 2 / SEGMENTS);
            float[] p0 = point(a0, 1, width, height, age, phase), p1 = point(a1, 1, width, height, age, phase);
            // The pointed outline is not a circular parameterization. Offsetting along cos/sin
            // folds quads at the tips; stay on each actual radial ray and keep its inner edge > 0.
            float length0=(float)Math.sqrt(p0[0]*p0[0]+p0[1]*p0[1]);
            float length1=(float)Math.sqrt(p1[0]*p1[0]+p1[1]*p1[1]);
            float scale0=Math.min(.8f,thickness/Math.max(.00001f,length0));
            float scale1=Math.min(.8f,thickness/Math.max(.00001f,length1));
            float dx0=p0[0]*scale0, dy0=p0[1]*scale0;
            float dx1=p1[0]*scale1, dy1=p1[1]*scale1;
            // One continuous strip samples a soft blue halo and white hot core.
            for(int side=-1;side<=1;side+=2) {
                float sx0=dx0*side, sy0=dy0*side, sx1=dx1*side, sy1=dy1*side;
                vertex(v,m,p0[0]+sx0,p0[1]+sy0,.036f*side,0,.5f,r,g,b,1);
                vertex(v,m,p1[0]+sx1,p1[1]+sy1,.036f*side,0,.5f,r,g,b,1);
                vertex(v,m,p1[0]-sx1,p1[1]-sy1,.036f*side,1,.5f,r,g,b,1);
                vertex(v,m,p0[0]-sx0,p0[1]-sy0,.036f*side,1,.5f,r,g,b,1);
            }
        }
    }

    private static void branches(VertexConsumer v, MatrixStack.Entry m, float width, float height, float age, float phase, float opacity) {
        for (int i = 0; i < 22; i++) {
            float angle = phase + i * 2.399963f;
            float pulse = .4f + .6f * (float) Math.pow(Math.max(0, Math.sin(age * .35f + i * 1.3f)), 2);
            float[] p = point(angle, 1, width, height, age, phase);
            float x = p[0], y = p[1];
            for (int j = 0; j < 4; j++) {
                float length = .08f + .025f * (float) Math.sin(i * 3 + j);
                float direction = angle + .65f * (float) Math.sin(i * 7 + j * 9 + age * .12f);
                float nx = x + (float) Math.cos(direction) * length, ny = y + (float) Math.sin(direction) * length;
                float power = pulse * opacity * (1 - j / 4f);
                ribbon(v, m, x, y, nx, ny, .058f, .08f, power, power, power);
                x = nx; y = ny;
            }
        }
    }

    private static void stars(VertexConsumer v, MatrixStack.Entry m, float width, float height, float age, float phase, float opacity) {
        for (int i = 0; i < 44; i++) {
            float radius = (float) Math.sqrt(hash(i, 17)) * .89f;
            float angle = hash(i, 73) * (float) (Math.PI * 2) + phase + age * (.004f + .006f * (1 - radius));
            float[] p = point(angle, radius, width, height, age, phase);
            float twinkle = (.35f + .30f * (float) Math.sin(age * .19 + i * 5.73)) * opacity;
            float size = .005f + hash(i, 29) * .011f;
            float z = .045f + hash(i, 63) * .075f;
            billboard(v, m, p[0], p[1], z, size * 2, size * 2, .60f * twinkle, .75f * twinkle, twinkle);
            if (i % 9 == 0) {
                billboard(v, m, p[0], p[1], z + .002f, size * .3f, size * 4, .75f * twinkle, .82f * twinkle, twinkle);
                billboard(v, m, p[0], p[1], z + .004f, size * 3, size * .3f, .75f * twinkle, .82f * twinkle, twinkle);
            }
        }
    }

    private static void wisps(VertexConsumer v, MatrixStack.Entry m, float width, float height, float age, float phase, float opacity, boolean arrival) {
        for (int i = 0; i < 26; i++) {
            float progress = (age * .052f + hash(i, 41)) % 1;
            float radial = arrival ? .86f + progress * .75f : 1.6f - progress * .7f;
            float angle = phase + i * 2.399963f + progress * (arrival ? .7f : -.7f);
            float[] p = point(angle, radial, width, height, age, phase);
            float power = (float) Math.sin(progress * Math.PI) * opacity;
            float size = .022f + hash(i, 6) * .02f;
            billboard(v, m, p[0], p[1], .12f + .15f * (float) Math.sin(angle), size, size * 1.8f,
                    .48f * power, .27f * power, .9f * power);
        }
    }

    private static void ribbon(VertexConsumer v, MatrixStack.Entry m, float x0, float y0, float x1, float y1,
                               float z, float thickness, float r, float g, float b) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < .00001) return;
        for (int side = -1; side <= 1; side += 2) {
            float ox = -dy / length * thickness * side, oy = dx / length * thickness * side;
            vertex(v,m,x0-ox,y0-oy,z*side,0,.5f,r,g,b,1);
            vertex(v,m,x1-ox,y1-oy,z*side,0,.5f,r,g,b,1);
            vertex(v,m,x1+ox,y1+oy,z*side,1,.5f,r,g,b,1);
            vertex(v,m,x0+ox,y0+oy,z*side,1,.5f,r,g,b,1);
        }
    }

    private static void billboard(VertexConsumer v, MatrixStack.Entry m, float x, float y, float z, float sx, float sy,
                                  float r, float g, float b) {
        for (int side = -1; side <= 1; side += 2) {
            float x0 = x - sx * side, x1 = x + sx * side;
            vertex(v,m,x0,y-sy,z*side,0,1,r,g,b,1);
            vertex(v,m,x1,y-sy,z*side,1,1,r,g,b,1);
            vertex(v,m,x1,y+sy,z*side,1,0,r,g,b,1);
            vertex(v,m,x0,y+sy,z*side,0,0,r,g,b,1);
        }
    }

    private static void vertex(VertexConsumer v, MatrixStack.Entry m, float x, float y, float z, float u, float w,
                               float r, float g, float b, float a) {
        v.vertex(m, x, y, z).color(r, g, b, a).texture(u, w).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m, 0, 0, z < 0 ? -1 : 1);
    }
    private static float ease(float x) { return x * x * (3 - 2 * x); }
    private static float hash(int value, int salt) {
        int n = value * 374761393 + salt * 668265263; n = (n ^ (n >>> 13)) * 1274126177;
        return (n & 0x7fffffff) / (float) 0x7fffffff;
    }
}
