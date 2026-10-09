package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_SkillPattern;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** World-space seals, turbulent fire layers and moving motes, independent of shader packs. */
final class SkillLayeredDetails {
    private static final Identifier LINE=MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW=MWMod.id("textures/effect/rift_glow.png");
    private static final Identifier ORB=MWMod.id("textures/effect/skill_magma_orb.png");
    private static final Identifier FLAME=MWMod.id("textures/effect/skill_flame.png");
    private static final Identifier SEAL=MWMod.id("textures/effect/skill_sigil.png");
    private static final Identifier EYE=MWMod.id("textures/effect/skill_eye_flow.png");
    static void preload(MinecraftClient client){for(var id:new Identifier[]{LINE,GLOW,ORB,FLAME,SEAL,EYE})client.getTextureManager().getTexture(id);}

    // Called with the matrix already translated to the packet origin.
    static void render(WorldRenderContext c,S2C_SkillPattern p,float age,int life,int seed,float fade,float[] color){
        var m=c.matrixStack();int kind=p.shape();float radius=(float)Math.min(4.5,p.radius());
        float open=Math.min(1,.35f+age*.19f),face=(float)Math.toDegrees(Math.atan2(c.camera().getPos().x-p.origin().x,c.camera().getPos().z-p.origin().z));
        boolean fire=kind==S2C_SkillPattern.FIREBALL||kind==S2C_SkillPattern.FIRE_CHARGE||kind==S2C_SkillPattern.FIRE_IMPACT;
        if(kind==S2C_SkillPattern.HEAL||kind==S2C_SkillPattern.FIRE_CHARGE){
            seal(c,m,Math.max(.8f,radius)*open,age*(kind==S2C_SkillPattern.HEAL?1.8f:-2.5f),.065f,fade*.58f,color);
            if(kind==S2C_SkillPattern.FIRE_CHARGE)seal(c,m,Math.max(.4f,radius*.52f)*open,-age*4,.09f,fade*.4f,color);
        }
        if(kind==S2C_SkillPattern.HEAL||kind==S2C_SkillPattern.FIRE_CHARGE){
            for(int layer=0;layer<(kind==S2C_SkillPattern.HEAL?1:2);layer++){
                m.push();m.translate(0,.5+layer*.85,0);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age*(layer%2==0?2:-3)));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-72+layer*8));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age*(layer%2==0?2.5f:-2.5f)));
                float rad=(1.1f-layer*.13f)*open;
                quad(c.consumers().getBuffer(RenderLayer.getEntityNoOutline(SEAL)),m.peek(),rad,rad,0,0,1,1,color[0],color[1],color[2],fade*.38f);
                m.pop();
            }
            var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            for(int i=0;i<6;i++){
                float a=i*(float)Math.PI/3+age*.055f,b=a+2.2f;
                ribbon3d(v,m.peek(),(float)Math.cos(a)*1.2f,.08f,(float)Math.sin(a)*1.2f,
                        (float)Math.cos(b)*.75f,2.3f,(float)Math.sin(b)*.75f,.017f,color[0]*fade*.6f,color[1]*fade*.6f,color[2]*fade*.6f);
            }
        }
        if(kind==S2C_SkillPattern.TIDAL){
            var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            // Three broad, moving wave fronts rather than a flat stack of light rings.
            for(int k=0;k<3;k++){
                float rad=radius*(.5f+k*.22f)*(1-Math.min(.78f,age*.032f));
                for(int i=0;i<72;i++){
                    float a=i*(float)Math.PI/36,b=(i+1)*(float)Math.PI/36;
                    float h=.25f+(float)Math.sin(a*3+age*.23f+k)*.22f;
                    ribbon3d(v,m.peek(),(float)Math.cos(a)*rad,h,(float)Math.sin(a)*rad,
                            (float)Math.cos(b)*rad,.25f+(float)Math.sin(b*3+age*.23f+k)*.22f,(float)Math.sin(b)*rad,
                            .22f,fade*.10f,fade*.7f,fade);
                }
            }
        }
        if(kind==S2C_SkillPattern.SOUL_WINGS||kind==S2C_SkillPattern.EAGLE_EYE)
            ClientManifestWings.render(c,p,age,fade);
        ClientClassSurfaces.render(c,p,age,fade);
        if(kind==S2C_SkillPattern.WOLF_HIT||kind==S2C_SkillPattern.SOUL_IMPACT){
            m.push();m.translate(0,kind==S2C_SkillPattern.WOLF_HIT?1.1:.4,0);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
            var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            float stretch=Math.min(1,.4f+age*.25f);
            if(kind==S2C_SkillPattern.WOLF_HIT){
                for(int side=-1;side<=1;side+=2){
                    stroke(v,m.peek(),-1.15f*stretch,-side*.9f*stretch,1.15f*stretch,side*.9f*stretch,.18f,fade,fade*.025f,fade*.07f);
                    stroke(v,m.peek(),-1.35f*stretch,-side*1.07f*stretch,1.35f*stretch,side*1.07f*stretch,.026f,fade,fade*.85f,fade*.8f);
                }
            }else{
                // Broken offset segments leave a purple-black cut, not a solid opaque screen.
                for(int i=0;i<18;i++){
                    float x=-1.8f+i*.2f,y=x*.65f+(float)Math.sin(i*5.7f)*.05f;
                    stroke(v,m.peek(),x,y,x+.22f,y+.143f,.23f,fade*.18f,fade*.005f,fade*.32f);
                    stroke(v,m.peek(),x,y+.12f,x+.22f,y+.263f,.045f,fade*.7f,fade*.1f,fade);
                    stroke(v,m.peek(),x,y-.12f,x+.22f,y+.023f,.025f,fade*.95f,fade*.32f,fade);
                }
            }
            m.pop();
        }
        if(kind==S2C_SkillPattern.EAGLE_EYE){
            float pulse=.76f+.24f*(float)Math.sin(age*1.7f);
            float blink=.93f+.07f*(float)Math.sin(age*.8f);
            m.push();m.translate(0,2.2,0);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));m.scale(.67f,.78f,.8f);
            atlas(c.consumers().getBuffer(RenderLayer.getEntityNoOutline(EYE)),m.peek(),2.1f*open,.68f*open*blink,age*1.4f,fade*.9f);
            var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            for(int side=-1;side<=1;side+=2)for(int i=0;i<60;i++){
                float x=-2.1f+i*.07f,nx=x+.07f;
                float y=side*(float)Math.pow(Math.max(0,1-Math.abs(x)/2.1f),1.8)*.59f*blink;
                float ny=side*(float)Math.pow(Math.max(0,1-Math.abs(nx)/2.1f),1.8)*.59f*blink;
                stroke(v,m.peek(),x*open,y*open,nx*open,ny*open,.16f,fade*pulse,fade*.55f*pulse,fade*.06f*pulse);
                stroke(v,m.peek(),x*open,y*open,nx*open,ny*open,.036f,fade,fade*.95f,fade*.70f);
                float jitter=.024f*(float)Math.sin(i*2.1+age*2.3);
                stroke(v,m.peek(),x*open,y*open+jitter,nx*open,ny*open+jitter,.018f,fade,fade*.77f,fade*.21f);
            }
            for(int i=0;i<48;i++){
                float a=i*(float)Math.PI/24,b=(i+1)*(float)Math.PI/24;
                stroke(v,m.peek(),(float)Math.cos(a)*.39f,(float)Math.sin(a)*.51f,(float)Math.cos(b)*.39f,(float)Math.sin(b)*.51f,.035f,fade,fade*.68f,fade*.16f);
                if(i%6<4)stroke(v,m.peek(),(float)Math.cos(a+age*.08)*.48f,(float)Math.sin(a+age*.08)*.57f,(float)Math.cos(b+age*.08)*.48f,(float)Math.sin(b+age*.08)*.57f,.014f,fade,fade*.86f,fade*.45f);
            }
            float[] dx={0,.16f,0,-.16f,0},dy={.37f,0,-.37f,0,.37f};
            for(int i=0;i<4;i++)stroke(v,m.peek(),dx[i],dy[i],dx[i+1],dy[i+1],.045f,fade,fade*.96f,fade*.76f);
            for(int side=-1;side<=1;side+=2){
                stroke(v,m.peek(),side*1.7f,0,side*(2.35f+pulse*.12f),0,.03f,fade,fade*.70f,fade*.20f);
                stroke(v,m.peek(),0,side*.48f,0,side*(.79f+pulse*.04f),.024f,fade,fade*.88f,fade*.51f);
            }
            m.pop();
        }
        if(kind==S2C_SkillPattern.WOLF_FORM){
            m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
            var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            for(int orbit=0;orbit<2;orbit++)for(int i=0;i<48;i++){
                float a=i*(float)Math.PI/24+age*(orbit==0?.035f:-.05f),b=a+(float)Math.PI/24;
                float radiusA=(orbit==0?1.12f:.79f)*open;
                float x=(float)Math.cos(a)*radiusA,y=1.42f+(float)Math.sin(a)*radiusA;
                stroke(v,m.peek(),x,y,(float)Math.cos(b)*radiusA,1.42f+(float)Math.sin(b)*radiusA,.038f,fade,fade*.07f,fade*.16f);
                if(i%2==0){
                    float tooth=radiusA+.18f;
                    stroke(v,m.peek(),x,y,(float)Math.cos(a+.045f)*tooth,1.42f+(float)Math.sin(a+.045f)*tooth,.028f,fade,fade*.15f,fade*.23f);
                }
            }
            m.pop();
        }
        if(fire){
            if(kind==S2C_SkillPattern.FIRE_IMPACT){
                // Direct-hit fireball: compact collision flare, no large ground-area telegraph.
                for(int i=0;i<10;i++){
                    float a=i*2.39996f,spread=.12f+Math.min(.55f,age*.04f);
                    flame(c,m,(float)Math.cos(a)*spread,(float)Math.sin(a)*spread,.05f*(i%3),.3f,.48f,age*1.6f+i,fade);
                }
            }
            if(kind==S2C_SkillPattern.FIRE_CHARGE){
                float rad=kind==S2C_SkillPattern.FIRE_IMPACT?Math.min(radius,.65f+age*.22f):1.3f;
                for(int i=0;i<24;i++){
                    float a=i*(float)Math.PI/12+age*.035f;
                    flame(c,m,(float)Math.cos(a)*rad,.26f+(i%3)*.08f,(float)Math.sin(a)*rad,.43f,.7f+(i%4)*.12f,age*1.5f+i*1.7f,fade);
                }
                var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
                for(int k=0;k<2;k++)for(int i=0;i<72;i++){
                    float a=i*(float)Math.PI/36,b=(i+1)*(float)Math.PI/36;
                    float h=.25f+(float)Math.pow(Math.max(0,Math.sin(a+age*.1f+k*Math.PI)),4)*1.5f;
                    float nh=.25f+(float)Math.pow(Math.max(0,Math.sin(b+age*.1f+k*Math.PI)),4)*1.5f;
                    ribbon3d(v,m.peek(),(float)Math.cos(a)*rad,h,(float)Math.sin(a)*rad,(float)Math.cos(b)*rad,nh,(float)Math.sin(b)*rad,.038f,fade,fade*.6f,fade*.08f);
                }
            }
            if(kind!=S2C_SkillPattern.FIRE_IMPACT){
                float y=kind==S2C_SkillPattern.FIRE_CHARGE?1.3f:0;
                m.push();m.translate(0,y,0);m.multiply(c.camera().getRotation());
                atlas(c.consumers().getBuffer(RenderLayer.getEntityNoOutline(ORB)),m.peek(),.79f,.79f,age*2+seed%16,fade);
                m.pop();
                for(int i=0;i<9;i++){
                    float a=i*2.39996f+age*.11f;
                    flame(c,m,(float)Math.cos(a)*.48f,y+(float)Math.sin(a)*.48f,.08f*(i%3),.36f,.55f,age*1.6f+i*1.7f,fade*.82f);
                }
                if(kind==S2C_SkillPattern.FIREBALL){
                    Vec3d back=p.origin().subtract(p.end()).normalize();
                    for(int i=1;i<=6;i++)flame(c,m,(float)back.x*i*.24f,(float)back.y*i*.24f+.12f*(float)Math.sin(age+i),(float)back.z*i*.24f,.48f-i*.05f,.64f-i*.065f,age*2+i*2,fade*(1-i*.12f));
                }
            }
        }
        // Fixed-seed motes move continuously at render time, giving sub-tick fluid motion.
        int count=kind==S2C_SkillPattern.FIREBALL||kind==S2C_SkillPattern.SOUL_TRAIL?26:kind==S2C_SkillPattern.LIGHTNING?110:76;
        var glow=c.consumers().getBuffer(RenderLayer.getEyes(GLOW));
        for(int i=0;i<count;i++){
            float a=noise(seed,i)*6.283185f,s=noise(seed^919,i),t=age/20f;
            float rad=(.25f+s*Math.max(.8f,radius))*(.5f+t*.8f);
            float x=(float)Math.cos(a)*rad,z=(float)Math.sin(a)*rad;
            float y=.12f+(1-s)*t*(3.4f+noise(seed^327,i)*3)-t*t*1.8f;
            if(kind==S2C_SkillPattern.EAGLE_EYE){
                x=(noise(seed^81,i)-.5f)*4.3f*open;z=(s-.5f)*.20f;y=2.2f+((i%2==0?1:-1)*(float)Math.pow(Math.max(0,1-Math.abs(x)/2.1f),1.8)*.59f+(noise(seed^311,i)-.5f)*age*.025f)*.78f;x*=.67f;
                double angle=Math.toRadians(face);float oldX=x;x=(float)(oldX*Math.cos(angle)+z*Math.sin(angle));z=(float)(-oldX*Math.sin(angle)+z*Math.cos(angle));
            }
            if(kind==S2C_SkillPattern.LIGHTNING&&i%4==0){rad*=.2f;x*=.3f;z*=.3f;y=noise(seed^52,i)*5.5f;}
            if(kind==S2C_SkillPattern.FIREBALL||kind==S2C_SkillPattern.SOUL_TRAIL){x*=.6f;z*=.6f;y-=.35f;}
            if(y<.06)y=.06f;
            float size=.022f+noise(seed^727,i)*.065f;
            float power=fade*(.55f+noise(seed^13,i)*.45f);
            m.push();m.translate(x,y,z);m.multiply(c.camera().getRotation());
            quad(glow,m.peek(),size,size*(i%5==0?3:1),0,0,1,1,color[0]*power,color[1]*power,color[2]*power,1);
            if(i%7==0)quad(glow,m.peek(),size*2.6f,size*.28f,0,0,1,1,power,power*.87f,power*.72f,1);
            m.pop();
        }
    }
    private static void seal(WorldRenderContext c,MatrixStack m,float radius,float spin,float y,float opacity,float[] color){
        m.push();m.translate(0,y,0);m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
        quad(c.consumers().getBuffer(RenderLayer.getEntityNoOutline(SEAL)),m.peek(),radius,radius,0,0,1,1,color[0],color[1],color[2],opacity);
        m.pop();
    }
    private static float wingHeight(float u,float row,float age){
        return 1.2f+(float)Math.sin(u*Math.PI*.92f)*(row*1.2f+.35f)
                +u*(.15f+row*.35f)+(float)Math.sin(u*5.5f-age*.23f+row*1.4f)*u*.2f;
    }
    private static void flame(WorldRenderContext c,MatrixStack m,float x,float y,float z,float sx,float sy,float frame,float fade){
        m.push();m.translate(x,y,z);m.multiply(c.camera().getRotation());
        atlas(c.consumers().getBuffer(RenderLayer.getEntityNoOutline(FLAME)),m.peek(),sx,sy,frame,fade);m.pop();
    }
    private static void atlas(VertexConsumer v,MatrixStack.Entry m,float sx,float sy,float frame,float alpha){
        int f=Math.floorMod((int)frame,16);float u=(f%4)*.25f,w=(f/4)*.25f;
        quad(v,m,sx,sy,u+.004f,w+.004f,u+.246f,w+.246f,1,1,1,alpha);
    }
    private static float noise(int seed,int i){int h=seed^i*374761393;h=(h^(h>>>13))*1274126177;return(h&0x7fffffff)/(float)0x7fffffff;}
    private static void stroke(VertexConsumer v,MatrixStack.Entry m,float x0,float y0,float x1,float y1,float width,float r,float g,float b){
        float dx=x1-x0,dy=y1-y0,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.0001)return;
        float ox=-dy/len*width,oy=dx/len*width;
        vertex(v,m,x0-ox,y0-oy,.035f,0,.5f,r,g,b,1);vertex(v,m,x1-ox,y1-oy,.035f,0,.5f,r,g,b,1);
        vertex(v,m,x1+ox,y1+oy,.035f,1,.5f,r,g,b,1);vertex(v,m,x0+ox,y0+oy,.035f,1,.5f,r,g,b,1);
    }
    private static void ribbon3d(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float xx,float yy,float zz,float w,float r,float g,float b){
        // Vertical strips expose wave height and remain visible from the arena floor.
        vertex(v,m,x,y-w,z,0,.5f,r,g,b,1);vertex(v,m,xx,yy-w,zz,0,.5f,r,g,b,1);
        vertex(v,m,xx,yy+w,zz,1,.5f,r,g,b,1);vertex(v,m,x,y+w,z,1,.5f,r,g,b,1);
        vertex(v,m,x,y+w,z,1,.5f,r,g,b,1);vertex(v,m,xx,yy+w,zz,1,.5f,r,g,b,1);
        vertex(v,m,xx,yy-w,zz,0,.5f,r,g,b,1);vertex(v,m,x,y-w,z,0,.5f,r,g,b,1);
    }
    private static void quad(VertexConsumer v,MatrixStack.Entry m,float sx,float sy,float u0,float v0,float u1,float v1,float r,float g,float b,float a){
        vertex(v,m,-sx,-sy,.025f,u0,v1,r,g,b,a);vertex(v,m,sx,-sy,.025f,u1,v1,r,g,b,a);
        vertex(v,m,sx,sy,.025f,u1,v0,r,g,b,a);vertex(v,m,-sx,sy,.025f,u0,v0,r,g,b,a);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float u,float w,float r,float g,float b,float a){
        v.vertex(m,x,y,z).color(Math.min(1,r),Math.min(1,g),Math.min(1,b),Math.min(1,a)).texture(u,w)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,0,1);
    }
}
