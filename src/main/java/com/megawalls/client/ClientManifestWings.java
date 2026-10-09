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
import net.minecraft.util.math.RotationAxis;

/** Compact swept-back falcon wings and distinct torn bone wings, anchored to body orientation. */
final class ClientManifestWings {
    private static final Identifier HUNTER=MWMod.id("textures/effect/wing_feather_hunter.png");
    private static final Identifier DREAD=MWMod.id("textures/effect/wing_feather_dread.png");
    private static final Identifier ENERGY=MWMod.id("textures/effect/wing_energy.png");
    private static final Identifier LINE=MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW=MWMod.id("textures/effect/rift_glow.png");
    private static final float ROOT_HEIGHT=1.35f;
    private static final int SEGMENTS=7;
    private static final Feather[] FALCON=feathers(false), SOUL=feathers(true);

    private record P(float x,float y,float z) {
        P add(float dx,float dy,float dz){return new P(x+dx,y+dy,z+dz);}
    }
    private record Feather(P root,P tip,float width,float bow,float depth,int layer) {}
    private ClientManifestWings() {}

    static void preload(MinecraftClient client){
        for(var id:new Identifier[]{HUNTER,DREAD,ENERGY,LINE,GLOW})client.getTextureManager().getTexture(id);
    }

    // The caller has translated the matrix to the player's feet. Minecraft yaw zero faces +Z.
    static void render(WorldRenderContext c,S2C_SkillPattern p,float age,float fade){
        boolean dread=p.shape()==S2C_SkillPattern.SOUL_WINGS;
        if((!dread&&p.shape()!=S2C_SkillPattern.EAGLE_EYE)||fade<=.001f||c.matrixStack()==null||c.consumers()==null)return;
        var m=c.matrixStack();
        float deploy=smooth(Math.min(1,age/7f));
        float spread=.55f+.45f*deploy;
        float flap=(1-deploy)*23+(float)Math.sin(age*.125f)*2.5f;
        Feather[] feathers=dread?SOUL:FALCON;
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.yaw()));
        m.translate(0,ROOT_HEIGHT,-.24);
        for(int side=-1;side<=1;side+=2){
            m.push();
            m.scale(side,1,1);
            m.translate(.22,0,0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flap));
            m.scale(spread,1,1);
            m.translate(-.22,0,0);
            if(dread)membrane(c,m.peek(),age,fade);
            var body=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(dread?DREAD:HUNTER));
            for(int i=0;i<feathers.length;i++)feather(body,m.peek(),feathers[i],i,age,fade,dread);
            var line=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
            spine(line,m.peek(),age,fade,dread);
            connectedVeins(line,m.peek(),feathers,age,fade,dread);
            for(int i=0;i<feathers.length;i++)featherLight(line,m.peek(),feathers[i],i,age,fade,dread);
            edgeArcs(line,m.peek(),feathers,age,fade,dread);
            var glow=c.consumers().getBuffer(RenderLayer.getEyes(GLOW));
            wingStars(glow,m.peek(),feathers,age,fade,dread);
            contourStars(glow,m.peek(),age,fade,dread);
            m.pop();
        }
        m.pop();
    }

    private static Feather[] feathers(boolean dread){
        if(dread)return new Feather[]{
                // Three long skeletal fingers, with only three short quills at the shoulder.
                new Feather(point(1.02f,2.20f,-.38f),point(1.68f,2.62f,-.70f),.065f,.018f,.035f,0),
                new Feather(point(1.12f,1.98f,-.41f),point(1.90f,1.90f,-.78f),.075f,.015f,.030f,0),
                new Feather(point(.86f,1.55f,-.30f),point(1.41f,.94f,-.60f),.070f,.025f,.040f,0),
                new Feather(point(.38f,1.48f,-.02f),point(.72f,1.14f,-.20f),.065f,.020f,.030f,1),
                new Feather(point(.59f,1.75f,-.12f),point(.98f,1.48f,-.32f),.075f,.025f,.035f,1),
                new Feather(point(.46f,1.69f,-.08f),point(.86f,2.02f,-.22f),.060f,.015f,.025f,2)
        };
        Feather[] result=new Feather[14];
        float[] tipX={1.86f,1.89f,1.85f,1.77f,1.65f,1.51f,1.35f};
        float[] tipY={2.61f,2.43f,2.23f,2.03f,1.83f,1.65f,1.51f};
        for(int i=0;i<7;i++){
            P root=point(.75f+.075f*i,1.88f+.032f*i,-.16f-.021f*i);
            P tip=point(tipX[i],tipY[i],-.65f-.025f*i);
            result[i]=new Feather(root,tip,.103f-i*.004f,.025f,.045f,0);
        }
        for(int i=0;i<3;i++){
            P root=point(.36f+.22f*i,1.52f+.15f*i,-.035f-.045f*i);
            P tip=point(.78f+.23f*i,1.00f+.20f*i,-.24f-.065f*i);
            result[7+i]=new Feather(root,tip,.084f,.018f,.040f,1);
        }
        for(int i=0;i<4;i++){
            P root=point(.25f+.22f*i,1.43f+.16f*i,.014f-.035f*i);
            P tip=point(.60f+.23f*i,1.37f+.19f*i,-.13f-.050f*i);
            result[10+i]=new Feather(root,tip,.078f,.014f,.030f,2);
        }
        return result;
    }

    private static void feather(VertexConsumer v,MatrixStack.Entry m,Feather f,int index,float age,float fade,boolean dread){
        // Full alpha-blended feather bodies supply readable midtones even without shader bloom.
        // RGB remains unchanged during expiry: fading alpha avoids a black closing silhouette.
        float shade=f.layer==0?.89f:f.layer==1?.96f:1;
        float r=shade,g=shade,b=dread?shade:shade*.94f;
        float[] normal=perpendicular(f);
        for(int j=0;j<SEGMENTS;j++){
            float t0=j/(float)SEGMENTS,t1=(j+1)/(float)SEGMENTS;
            P a=center(f,index,t0,age),b1=center(f,index,t1,age);
            float w0=f.width*(.48f+.52f*sin(t0*(float)Math.PI));
            float w1=f.width*(.48f+.52f*sin(t1*(float)Math.PI));
            P left0=a.add(-normal[0]*w0,-normal[1]*w0,0),right0=a.add(normal[0]*w0,normal[1]*w0,0);
            P left1=b1.add(-normal[0]*w1,-normal[1]*w1,0),right1=b1.add(normal[0]*w1,normal[1]*w1,0);
            texturedQuad(v,m,left0,right0,right1,left1,0,1-t0,1,1-t1,r,g,b,fade*.96f);
        }
    }

    private static P center(Feather f,int index,float t,float age){
        float arc=sin(t*(float)Math.PI);
        float flutter=sin(age*.20f+index*.52f)*t*t;
        return new P(mix(f.root.x,f.tip.x,t)+f.bow*arc,
                mix(f.root.y,f.tip.y,t)+flutter*(f.layer==2?.009f:.018f),
                mix(f.root.z,f.tip.z,t)+f.depth*arc+flutter*.025f);
    }

    private static float[] perpendicular(Feather f){
        float dx=f.tip.x-f.root.x,dy=f.tip.y-f.root.y;
        float len=(float)Math.sqrt(dx*dx+dy*dy);
        return new float[]{-dy/len,dx/len};
    }

    private static void featherLight(VertexConsumer v,MatrixStack.Entry m,Feather f,int index,float age,float fade,boolean dread){
        float[] n=perpendicular(f);
        for(int j=0;j<SEGMENTS;j++){
            float t0=j/(float)SEGMENTS,t1=(j+1)/(float)SEGMENTS;
            P a=center(f,index,t0,age).add(0,0,.018f),b=center(f,index,t1,age).add(0,0,.018f);
            float flowing=.24f+.76f*(float)Math.pow(Math.max(0,sin(t0*7.5f-age*.46f+index*.61f)),5);
            float power=fade*flowing*(f.layer==2?.20f:.34f);
            line(v,m,a,b,dread?.009f:.006f,power*(dread?.65f:1),power*(dread?.20f:.82f),power*(dread?1:.45f));
            if(f.layer==0){
                float w0=f.width*(.48f+.52f*sin(t0*(float)Math.PI))*.78f;
                float w1=f.width*(.48f+.52f*sin(t1*(float)Math.PI))*.78f;
                float edge=fade*(.055f+flowing*.19f)*sin((.06f+t0*.88f)*(float)Math.PI);
                line(v,m,a.add(n[0]*w0,n[1]*w0,.004f),b.add(n[0]*w1,n[1]*w1,.004f),.005f,
                        dread?edge*.63f:edge,dread?edge*.14f:edge*.68f,dread?edge:edge*.25f);
            }
            // Short asymmetric barbs show the construction of each feather without filling it white.
            if((j==2||j==4)&&f.layer==0){
                float width=f.width*.57f*sin(t0*(float)Math.PI);
                float barb=power*.38f;
                line(v,m,a,center(f,index,Math.min(.97f,t0+.10f),age).add(n[0]*width,n[1]*width,.026f),.004f,
                        barb,barb*(dread?.35f:.85f),barb*(dread?1:.52f));
            }
        }
    }

    private static void spine(VertexConsumer v,MatrixStack.Entry m,float age,float fade,boolean dread){
        if(dread){
            P shoulder=point(.20f,1.38f,.018f),elbow=point(.69f,1.94f,-.24f);
            bone(v,m,shoulder,elbow,.042f,age,fade);
            bone(v,m,elbow,point(1.67f,2.60f,-.68f),.038f,age+2,fade);
            bone(v,m,elbow,point(1.89f,1.90f,-.75f),.033f,age+5,fade);
            bone(v,m,point(.55f,1.69f,-.17f),point(1.40f,.96f,-.57f),.029f,age+8,fade);
            // Two short elbow spurs replace the former repeated crown of oversized spikes.
            bone(v,m,elbow,point(.72f,2.21f,-.30f),.026f,age,fade*.80f);
            bone(v,m,point(.90f,2.09f,-.33f),point(1.01f,2.33f,-.40f),.018f,age,fade*.70f);
            return;
        }
        P last=point(.20f,1.38f,.035f);
        for(int j=1;j<=16;j++){
            float t=j/16f;
            P next=point(.20f+1.15f*t,1.38f+.83f*sin(t*1.30f),.035f-.34f*t);
            float power=fade*(.55f+.22f*sin(age*.32f-t*7));
            line(v,m,last,next,.023f,power*.36f,power*.23f,power*.05f);
            line(v,m,last.add(0,0,.007f),next.add(0,0,.007f),.007f,power*.75f,power*.61f,power*.31f);
            last=next;
        }
    }

    private static void bone(VertexConsumer v,MatrixStack.Entry m,P a,P b,float width,float age,float fade){
        P last=a;
        for(int j=1;j<=7;j++){
            float t=j/7f;
            P next=new P(mix(a.x,b.x,t),mix(a.y,b.y,t),mix(a.z,b.z,t)-.025f*sin(t*(float)Math.PI));
            float power=fade*(.69f+.21f*sin(age*.30f-t*6));
            float taper=1-t*.64f;
            line(v,m,last,next,width*taper,power*.29f,power*.07f,power*.47f);
            line(v,m,last.add(0,0,.008f),next.add(0,0,.008f),.007f*taper,power*.74f,power*.35f,power*.88f);
            last=next;
        }
    }

    /** One LINE consumer owns this whole pass; helpers must not request other materials. */
    private static void connectedVeins(VertexConsumer v,MatrixStack.Entry m,Feather[] feathers,float age,float fade,boolean dread){
        if(dread){
            P shoulder=point(.22f,1.39f,.030f),elbow=point(.69f,1.94f,-.23f);
            P upper=point(1.67f,2.60f,-.67f),middle=point(1.89f,1.90f,-.74f),lower=point(1.40f,.96f,-.56f);
            flowCurve(v,m,shoulder,point(.49f,1.92f,-.05f),upper,20,.009f,age,fade,true,.60f);
            flowCurve(v,m,shoulder,point(.81f,1.24f,-.24f),lower,16,.008f,age+5,fade,true,.52f);
            // Concave membrane rims run between fingers. Interior veins branch from those rims.
            flowCurve(v,m,upper,point(1.03f,1.93f,-.43f),middle,17,.009f,age+2,fade,true,.57f);
            flowCurve(v,m,middle,point(.92f,1.51f,-.35f),lower,17,.009f,age+7,fade,true,.54f);
            for(int group=0;group<2;group++)for(int vein=0;vein<5;vein++){
                float t=.17f+vein*.145f;
                P a=group==0?upper:middle,b=group==0?middle:lower;
                P control=group==0?point(1.03f,1.93f,-.43f):point(.92f,1.51f,-.35f);
                P end=curve(a,control,b,t);
                P start=group==0?elbow:point(.59f,1.69f,-.20f);
                P mid=new P(mix(start.x,end.x,.54f)-.04f,mix(start.y,end.y,.54f)+.06f,mix(start.z,end.z,.54f)+.035f);
                flowCurve(v,m,start,mid,end,8,.005f,age+vein*.7f+group*3,fade,true,.30f);
                if(vein==1||vein==3){
                    P branch=curve(a,control,b,Math.min(.93f,t+.085f));
                    P fork=curve(start,mid,end,.61f);
                    flowCurve(v,m,fork,end.add(-.06f,.025f,.01f),branch,5,.004f,age+vein,fade,true,.23f);
                }
            }
            return;
        }

        P shoulder=point(.20f,1.38f,.035f),upperControl=point(.72f,2.24f,-.18f),tip=feathers[0].tip;
        // Three close curved upper streams connect the shoulder to the extended primary group.
        for(int band=0;band<3;band++){
            float offset=band*.026f;
            flowCurve(v,m,shoulder.add(0,-offset,.014f),upperControl.add(0,-offset,.018f),tip.add(-offset,-offset,.016f),
                    20,band==0?.011f:.006f,age+band*2,fade,false,band==0?.60f:.34f);
        }
        // Each lower segment curls inward between neighbouring feather groups, retaining gaps.
        P[] lowerRim={shoulder,feathers[7].tip,feathers[8].tip,feathers[9].tip,
                feathers[6].tip,feathers[5].tip,feathers[4].tip,feathers[3].tip,
                feathers[2].tip,feathers[1].tip,feathers[0].tip};
        for(int i=0;i<lowerRim.length-1;i++){
            P a=lowerRim[i].add(0,0,.020f),b=lowerRim[i+1].add(0,0,.020f);
            P control=new P(mix(a.x,b.x,.5f)-.038f,mix(a.y,b.y,.5f)+.058f,mix(a.z,b.z,.5f)+.02f);
            flowCurve(v,m,a,control,b,6,.007f,age+i*.9f,fade,false,.46f);
            flowCurve(v,m,a.add(-.012f,.026f,-.005f),control.add(-.01f,.028f,-.005f),b.add(-.012f,.026f,-.005f),
                    6,.004f,age+i*.9f+3,fade,false,.24f);
        }
        // Seven branching feather veins meet the upper arch instead of floating as separate leaves.
        for(int i=0;i<7;i++){
            Feather f=feathers[i];
            P start=curve(shoulder,upperControl,tip,.26f+i*.061f);
            P end=center(f,i,.73f,age).add(0,0,.029f);
            P mid=f.root.add(.055f,-.025f,.055f);
            flowCurve(v,m,start,mid,end,10,.005f,age+i*.55f,fade,false,.39f);
            P left=center(f,i,.46f,age).add(-.028f,.024f,.036f);
            P right=center(f,i,.88f,age).add(.010f,-.017f,.034f);
            flowCurve(v,m,left,center(f,i,.70f,age).add(.018f,.018f,.045f),right,7,.004f,age+i*.55f+2,fade,false,.26f);
        }
        for(int i=7;i<10;i++){
            Feather f=feathers[i];
            flowCurve(v,m,shoulder,point(f.root.x,1.52f+(i-7)*.14f,f.root.z+.03f),
                    center(f,i,.82f,age).add(0,0,.035f),10,.006f,age+i,fade,false,.38f);
        }
    }

    private static void flowCurve(VertexConsumer v,MatrixStack.Entry m,P a,P control,P b,int segments,
                                  float width,float age,float fade,boolean dread,float strength){
        P last=a;
        for(int j=1;j<=segments;j++){
            float t=j/(float)segments;
            P next=curve(a,control,b,t);
            float moving=.28f+.72f*(float)Math.pow(.5f+.5f*sin(t*9-age*.25f),3);
            float power=fade*strength*moving;
            line(v,m,last,next,width,power*(dread?.66f:1),power*(dread?.21f:.74f),power*(dread?1:.30f));
            last=next;
        }
    }

    private static P curve(P a,P control,P b,float t){
        float inv=1-t;
        return new P(inv*inv*a.x+2*inv*t*control.x+t*t*b.x,
                inv*inv*a.y+2*inv*t*control.y+t*t*b.y,
                inv*inv*a.z+2*inv*t*control.z+t*t*b.z);
    }

    private static void membrane(WorldRenderContext c,MatrixStack.Entry m,float age,float fade){
        var v=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(ENERGY));
        float breath=.012f*sin(age*.13f);
        P upperRoot=point(.72f,1.95f,-.29f),upperTip=point(1.58f,2.48f,-.68f);
        P middleTip=point(1.78f,1.91f,-.73f),upperTear=point(1.18f,1.98f,-.47f+breath);
        P lowerRoot=point(.61f,1.68f,-.24f),lowerTip=point(1.31f,1.09f,-.57f);
        P lowerTear=point(1.08f,1.53f,-.43f-breath);
        // Two small concave lobes, separated at the elbow, leave the main fingers exposed.
        texturedQuad(v,m,upperRoot,upperTip,upperTear,upperRoot,0,1,1,0,.16f,.045f,.25f,fade*.62f);
        texturedQuad(v,m,upperRoot,upperTear,middleTip,upperRoot,0,1,1,0,.13f,.035f,.23f,fade*.57f);
        texturedQuad(v,m,lowerRoot,middleTip,lowerTear,lowerRoot,0,1,1,0,.14f,.035f,.24f,fade*.55f);
        texturedQuad(v,m,lowerRoot,lowerTear,lowerTip,lowerRoot,0,1,1,0,.16f,.045f,.25f,fade*.61f);
    }

    private static void edgeArcs(VertexConsumer v,MatrixStack.Entry m,Feather[] feathers,float age,float fade,boolean dread){
        // Two short travelling wisps use the new feather tips, never the previous large oval.
        int outer=dread?3:7;
        for(int arc=0;arc<2;arc++){
            int selected=(arc*2+(int)(age/8))%outer;
            Feather f=feathers[selected];
            P last=null;
            for(int j=0;j<=8;j++){
                float t=.66f+j*.035f;
                float noise=sin(j*3.8f+(int)(age*1.6f)*2.3f+arc)*.012f;
                P now=center(f,selected,t,age).add(noise,noise,.025f);
                if(last!=null){
                    float strength=fade*sin(j/9f*(float)Math.PI)*.40f;
                    line(v,m,last,now,.006f,strength,strength*(dread?.37f:.86f),strength*(dread?1:.60f));
                }
                last=now;
            }
        }
    }

    private static void wingStars(VertexConsumer v,MatrixStack.Entry m,Feather[] feathers,float age,float fade,boolean dread){
        for(int i=0;i<(dread?9:14);i++){
            Feather f=feathers[(i*5)%feathers.length];
            float t=.14f+fract(i*.618034f+age*.016f)*.80f;
            P p=center(f,(i*5)%feathers.length,t,age).add(.015f*sin(i*5.1f),.012f*sin(i*2.9f),.026f);
            float blink=(float)Math.pow(.5f+.5f*sin(age*.63f+i*2.3f),5);
            float size=.010f+.016f*blink,power=fade*(.08f+.42f*blink);
            star(v,m,p,size,power,power*(dread?.41f:.87f),power*(dread?1:.49f));
        }
        // A few detached, rising wisps add volume behind the feather surface.
        for(int i=0;i<3;i++){
            float t=fract(i*.38197f+age*.020f);
            P p=point(.65f+i*.34f,1.20f+t*.95f,-.40f-i*.09f-.035f*sin(i+age*.09f));
            float power=fade*sin(t*(float)Math.PI)*.20f;
            star(v,m,p,.020f,power,power*(dread?.14f:.60f),power*(dread?1:.18f));
        }
    }

    /** Sparse travelling lights share the GLOW pass with the feather stars. */
    private static void contourStars(VertexConsumer v,MatrixStack.Entry m,float age,float fade,boolean dread){
        P shoulder=point(.20f,1.38f,.055f);
        P upperControl=dread?point(.55f,2.05f,-.10f):point(.72f,2.24f,-.16f);
        P tip=dread?point(1.67f,2.60f,-.65f):FALCON[0].tip.add(0,0,.024f);
        for(int i=0;i<5;i++){
            float t=fract(i*.20f+age*.014f);
            P p=curve(shoulder,upperControl,tip,t);
            float pulse=.32f+.68f*(float)Math.pow(.5f+.5f*sin(age*.40f+i*2),3);
            float power=fade*.38f*sin(t*(float)Math.PI)*pulse;
            star(v,m,p,.013f+.009f*pulse,power,power*(dread?.31f:.81f),power*(dread?1:.39f));
        }
        for(int i=0;i<4;i++){
            float t=fract(i*.25f+age*.019f);
            P a=dread?point(1.89f,1.90f,-.72f):FALCON[7+i%3].root.add(0,0,.026f);
            P b=dread?point(1.40f,.96f,-.54f):FALCON[7+i%3].tip.add(0,0,.026f);
            P control=dread?point(.92f,1.51f,-.33f):a.add(.22f,-.05f,-.02f);
            float power=fade*.26f*sin(t*(float)Math.PI);
            star(v,m,curve(a,control,b,t),.016f,power,power*(dread?.25f:.75f),power*(dread?1:.32f));
        }
    }

    private static void star(VertexConsumer v,MatrixStack.Entry m,P p,float size,float r,float g,float b){
        texturedQuad(v,m,p.add(-size,-size,0),p.add(size,-size,0),p.add(size,size,0),p.add(-size,size,0),0,1,1,0,r,g,b,1);
        // Perpendicular card keeps the world-oriented mote readable from oblique views.
        texturedQuad(v,m,p.add(0,-size,-size),p.add(0,-size,size),p.add(0,size,size),p.add(0,size,-size),0,1,1,0,r,g,b,1);
    }

    private static void line(VertexConsumer v,MatrixStack.Entry m,P a,P b,float width,float r,float g,float blue){
        float dx=b.x-a.x,dy=b.y-a.y,length=(float)Math.sqrt(dx*dx+dy*dy);
        if(length<.00001f)return;
        float ox=-dy/length*width,oy=dx/length*width;
        texturedQuad(v,m,a.add(-ox,-oy,0),a.add(ox,oy,0),b.add(ox,oy,0),b.add(-ox,-oy,0),0,.5f,1,.5f,r,g,blue,1);
    }

    private static void texturedQuad(VertexConsumer v,MatrixStack.Entry m,P a,P b,P c,P d,
                                     float u0,float v0,float u1,float v1,float r,float g,float blue,float alpha){
        float nx=(b.y-a.y)*(d.z-a.z)-(b.z-a.z)*(d.y-a.y);
        float ny=(b.z-a.z)*(d.x-a.x)-(b.x-a.x)*(d.z-a.z);
        float nz=(b.x-a.x)*(d.y-a.y)-(b.y-a.y)*(d.x-a.x);
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len<.00001f){nx=0;ny=0;nz=1;}else{nx/=len;ny/=len;nz/=len;}
        vertex(v,m,a,u0,v0,r,g,blue,alpha,nx,ny,nz);
        vertex(v,m,b,u1,v0,r,g,blue,alpha,nx,ny,nz);
        vertex(v,m,c,u1,v1,r,g,blue,alpha,nx,ny,nz);
        vertex(v,m,d,u0,v1,r,g,blue,alpha,nx,ny,nz);
        // Explicitly reversed winding is necessary on both mirrored halves, including the back.
        vertex(v,m,d,u0,v1,r,g,blue,alpha,-nx,-ny,-nz);
        vertex(v,m,c,u1,v1,r,g,blue,alpha,-nx,-ny,-nz);
        vertex(v,m,b,u1,v0,r,g,blue,alpha,-nx,-ny,-nz);
        vertex(v,m,a,u0,v0,r,g,blue,alpha,-nx,-ny,-nz);
    }

    private static void vertex(VertexConsumer v,MatrixStack.Entry m,P p,float u,float texV,float r,float g,float b,float alpha,float nx,float ny,float nz){
        v.vertex(m,p.x,p.y,p.z).color(clamp(r),clamp(g),clamp(b),clamp(alpha)).texture(u,texV)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,nx,ny,nz);
    }
    private static P point(float x,float y,float z){return new P(x,y-ROOT_HEIGHT,z);}
    private static float sin(float x){return (float)Math.sin(x);}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static float smooth(float t){return t*t*(3-2*t);}
    private static float fract(float t){return t-(float)Math.floor(t);}
    private static float clamp(float t){return Math.max(0,Math.min(1,t));}
}
