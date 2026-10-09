package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_SkillPattern;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Three staggered curved tears, with irregular ruptured edges and peeled fragments. */
final class ClientRendingClaws {
    private static final Identifier REND=MWMod.id("textures/effect/claw_rend.png");
    private static final Identifier LINE=MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW=MWMod.id("textures/effect/rift_glow.png");
    static void preload(MinecraftClient client){client.getTextureManager().getTexture(REND);}
    static void render(WorldRenderContext c,S2C_SkillPattern packet,float age,float fade){
        if(packet.shape()==S2C_SkillPattern.WOLF_HIT){quickHit(c,packet,age,fade);return;}
        fade*=Math.clamp((19-age)/6f,0,1);
        if(fade<=.001f)return;
        var m=c.matrixStack();m.push();
        // The blade plane is perpendicular to the attacker's facing, not the spectator's camera.
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-packet.yaw()));
        m.translate(0,0,.06);
        var fill=c.consumers().getBuffer(RenderLayer.getEntityNoOutline(REND));
        for(int blade=0;blade<3;blade++){
            float end=smooth((age-blade*.55f)/3.2f);
            for(int i=0;i<64;i++){
                float t=i/64f,u=Math.min((i+1)/64f,end);if(t>=end)break;
                float[] a=point(blade,t),b=point(blade,u);
                float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.00001)continue;
                float nx=-dy/len,ny=dx/len;
                float la=edgeWidth(blade,t,age,-1),ra=edgeWidth(blade,t,age,1);
                float lb=edgeWidth(blade,u,age,-1),rb=edgeWidth(blade,u,age,1);
                surface(fill,m.peek(),a[0]-nx*la,a[1]-ny*la,a[0]+nx*ra,a[1]+ny*ra,
                        b[0]-nx*lb,b[1]-ny*lb,b[0]+nx*rb,b[1]+ny*rb,t,u,fade*.95f);
            }
        }
        var edges=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int blade=0;blade<3;blade++){
            float end=smooth((age-blade*.55f)/3.2f);
            for(int i=0;i<64;i++){
                float t=i/64f,u=Math.min((i+1)/64f,end);if(t>=end)break;
                float[] a=point(blade,t),b=point(blade,u);
                float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.00001)continue;
                float nx=-dy/len,ny=dx/len;
                for(int side=-1;side<=1;side+=2){
                    float wa=edgeWidth(blade,t,age,side),wb=edgeWidth(blade,u,age,side);
                    float ax=a[0]+side*nx*wa,ay=a[1]+side*ny*wa,bx=b[0]+side*nx*wb,by=b[1]+side*ny*wb;
                    line(edges,m.peek(),ax,ay,bx,by,.082f,fade*.92f,fade*.013f,fade*.045f,.045f);
                    float hot=side==1?.91f:.36f;
                    line(edges,m.peek(),ax,ay,bx,by,side==1?.021f:.010f,fade,fade*hot,fade*hot*.88f,.05f);
                    if(i%9==3&&t>.12f&&t<.87f){
                        float crack=.16f+noise(i+blade*31,18)*.22f;
                        float cx=ax+nx*side*crack-dx/len*.06f,cy=ay+ny*side*crack-dy/len*.06f;
                        line(edges,m.peek(),ax,ay,cx,cy,.023f,fade,fade*.035f,fade*.11f,.055f);
                        line(edges,m.peek(),cx,cy,cx+nx*side*.065f+dx/len*.10f,cy+ny*side*.065f+dy/len*.10f,.009f,fade*.8f,fade*.06f,fade*.13f,.055f);
                    }
                }
            }
        }
        // Detached angular chips carry the dark interior away from the cut instead of another crossing slash.
        // Immediate providers may flush the previous layer: finish the entire body pass
        // before acquiring the emissive edge consumer, never retain it across switches.
        for(int pass=0;pass<2;pass++){
        var debris=c.consumers().getBuffer(pass==0?RenderLayer.getEntityTranslucent(LINE):RenderLayer.getEyes(LINE));
        for(int i=0;i<27;i++){
            int blade=i%3,side=i%2==0?1:-1;float t=.13f+noise(i,72)*.71f;
            if(t>smooth((age-blade*.55f)/3.2f))continue;
            float[] a=point(blade,t),b=point(blade,Math.min(1,t+.02f));
            float dx=b[0]-a[0],dy=b[1]-a[1],len=(float)Math.sqrt(dx*dx+dy*dy);
            float nx=-dy/len,ny=dx/len,travel=Math.max(0,age-3)*(.025f+noise(i,83)*.033f);
            float offset=edgeWidth(blade,t,age,side)+.045f+travel;
            float x=a[0]+nx*side*offset,y=a[1]+ny*side*offset-travel*travel*.17f;
            float angle=(float)Math.atan2(dy,dx)+side*(.2f+travel*1.5f);
            float size=.045f+noise(i,48)*.09f;
            float vx=(float)Math.cos(angle)*size,vy=(float)Math.sin(angle)*size;
            float wx=-vy*.53f,wy=vx*.53f;
            if(pass==0)shard(debris,m.peek(),x,y,vx,vy,wx,wy,fade*(1-Math.min(.75f,travel*.45f)));
            else line(debris,m.peek(),x-vx+wx,y-vy+wy,x+vx*1.7f,y+vy*1.7f,.010f,fade*.88f,fade*.05f,fade*.13f,.09f);
        }
        }
        var glow=c.consumers().getBuffer(RenderLayer.getEyes(GLOW));
        for(int i=0;i<42;i++){
            int blade=i%3;float t=noise(i,37),speed=.4f+noise(i,93)*1.4f;
            float[] pos=point(blade,t);
            float travel=Math.max(0,age-2)*.075f;
            float direction=noise(i,119)*6.283185f;
            float x=pos[0]+(float)Math.cos(direction)*travel*speed;
            float y=pos[1]+(float)Math.sin(direction)*travel*speed-travel*travel*.4f;
            float size=.014f+noise(i,19)*.035f;
            quad(glow,m.peek(),x,y,size,size*(i%6==0?4:1),.07f,fade,fade*(i%7==0?.85f:.09f),fade*.16f);
        }
        // The leading point flashes while the material opens, then yields to the red afterimage.
        if(age<7)for(int blade=0;blade<3;blade++){
            float[] head=point(blade,smooth((age-blade*.55f)/3.2f));
            float flash=fade*Math.max(0,1-age/7);
            quad(glow,m.peek(),head[0],head[1],.31f,.31f,.085f,flash,flash*.4f,flash*.3f);
            quad(glow,m.peek(),head[0],head[1],.28f,.035f,.09f,flash,flash,flash);
        }
        m.pop();
    }
    /** Small, thin impact marks; the full rupturing material is reserved for ability activation. */
    private static void quickHit(WorldRenderContext c,S2C_SkillPattern packet,float age,float fade){
        var m=c.matrixStack();m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-packet.yaw()));
        // Negative local Z is toward the incoming attacker, placing the fine mark
        // on the near surface of the victim instead of hiding it inside the torso.
        m.translate(0,.64,-.38);m.scale(.44f,.36f,1);
        var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int blade=0;blade<3;blade++){
            float end=smooth((age-blade*.2f)/1.7f);
            for(int i=0;i<24;i++){
                float t=i/24f,u=Math.min(end,(i+1)/24f);if(t>=end)break;
                float[] a=point(blade,t),b=point(blade,u);
                float taper=.25f+.75f*(float)Math.sin(t*Math.PI);
                line(v,m.peek(),a[0],a[1],b[0],b[1],.065f*taper,fade*.85f,fade*.035f,fade*.085f,.035f);
                line(v,m.peek(),a[0],a[1],b[0],b[1],.026f*taper,fade,fade*.73f,fade*.68f,.04f);
            }
        }
        m.pop();
    }
    private static float[] point(int blade,float t){
        float startY=blade==0?2.48f:blade==1?2.88f:2.68f;
        float length=blade==0?2.15f:blade==1?2.72f:2.16f;
        return new float[]{-1.72f+blade*.70f+1.38f*t+.29f*(float)Math.sin(t*Math.PI),
                startY-length*t+.17f*(float)Math.sin(t*Math.PI)};
    }
    private static float width(int blade,float t,float age){
        float envelope=(float)Math.pow(Math.max(0,t),.55)*(float)Math.pow(Math.max(0,1-t),.84)*2.5f;
        return (blade==1?.235f:blade==0?.16f:.18f)*envelope*(1-Math.max(0,age-9)*.023f);
    }
    private static float edgeWidth(int blade,float t,float age,int side){
        float cell=t*64,local=cell-(float)Math.floor(cell);int n=(int)Math.floor(cell);
        float a=noise(n+blade*89+side*53,91),b=noise(n+1+blade*89+side*53,91);
        return width(blade,t,age)*(.69f+.44f*(a+(b-a)*local));
    }
    private static void shard(VertexConsumer v,MatrixStack.Entry m,float x,float y,float vx,float vy,float wx,float wy,float alpha){
        float ax=x+vx*1.7f,ay=y+vy*1.7f,bx=x-vx+wx,by=y-vy+wy,cx=x-vx-wx,cy=y-vy-wy;
        for(int side=-1;side<=1;side+=2){
            float z=.085f*side;
            vert(v,m,ax,ay,z,.5f,.5f,.075f,.009f,.02f,alpha);
            vert(v,m,side>0?bx:cx,side>0?by:cy,z,.5f,.5f,.075f,.009f,.02f,alpha);
            vert(v,m,side>0?cx:bx,side>0?cy:by,z,.5f,.5f,.075f,.009f,.02f,alpha);
            vert(v,m,ax,ay,z,.5f,.5f,.075f,.009f,.02f,alpha);
        }
    }
    private static float smooth(float v){float t=Math.clamp(v,0,1);return t*t*(3-2*t);}
    private static float noise(int i,int seed){int h=(i+seed)*374761393;h=(h^(h>>>13))*1274126177;return(h&0x7fffffff)/(float)0x7fffffff;}
    private static void surface(VertexConsumer v,MatrixStack.Entry m,float alx,float aly,float arx,float ary,float blx,float bly,float brx,float bry,float t,float u,float alpha){
        vert(v,m,alx,aly,0,0,t,1,1,1,alpha);vert(v,m,blx,bly,0,0,u,1,1,1,alpha);
        vert(v,m,brx,bry,0,1,u,1,1,1,alpha);vert(v,m,arx,ary,0,1,t,1,1,1,alpha);
        vert(v,m,arx,ary,-.006f,1,t,1,1,1,alpha);vert(v,m,brx,bry,-.006f,1,u,1,1,1,alpha);
        vert(v,m,blx,bly,-.006f,0,u,1,1,1,alpha);vert(v,m,alx,aly,-.006f,0,t,1,1,1,alpha);
    }
    private static void line(VertexConsumer v,MatrixStack.Entry m,float x,float y,float xx,float yy,float w,float r,float g,float b,float z){
        float dx=xx-x,dy=yy-y,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.00001)return;
        float ox=-dy/len*w,oy=dx/len*w;
        vert(v,m,x-ox,y-oy,z,0,.5f,r,g,b,1);vert(v,m,xx-ox,yy-oy,z,0,.5f,r,g,b,1);
        vert(v,m,xx+ox,yy+oy,z,1,.5f,r,g,b,1);vert(v,m,x+ox,y+oy,z,1,.5f,r,g,b,1);
        vert(v,m,x+ox,y+oy,-z,1,.5f,r,g,b,1);vert(v,m,xx+ox,yy+oy,-z,1,.5f,r,g,b,1);
        vert(v,m,xx-ox,yy-oy,-z,0,.5f,r,g,b,1);vert(v,m,x-ox,y-oy,-z,0,.5f,r,g,b,1);
    }
    private static void quad(VertexConsumer v,MatrixStack.Entry m,float x,float y,float sx,float sy,float z,float r,float g,float b){
        vert(v,m,x-sx,y-sy,z,0,1,r,g,b,1);vert(v,m,x+sx,y-sy,z,1,1,r,g,b,1);
        vert(v,m,x+sx,y+sy,z,1,0,r,g,b,1);vert(v,m,x-sx,y+sy,z,0,0,r,g,b,1);
        vert(v,m,x-sx,y+sy,-z,0,0,r,g,b,1);vert(v,m,x+sx,y+sy,-z,1,0,r,g,b,1);
        vert(v,m,x+sx,y-sy,-z,1,1,r,g,b,1);vert(v,m,x-sx,y-sy,-z,0,1,r,g,b,1);
    }
    private static void vert(VertexConsumer v,MatrixStack.Entry m,float x,float y,float z,float u,float w,float r,float g,float b,float a){
        v.vertex(m,x,y,z).color(r,g,b,Math.clamp(a,0,1)).texture(u,w).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,0,z<0?-1:1);
    }
}
