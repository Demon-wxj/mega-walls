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

/** Short, distinct material volumes layered over the original, authoritative skill effects. */
final class ClientClassSurfaces {
    private static final Identifier SMOKE=MWMod.id("textures/effect/class_smoke.png");
    private static final Identifier WATER=MWMod.id("textures/effect/class_water.png");
    private static final Identifier LEAF=MWMod.id("textures/effect/class_leaf.png");
    private static final Identifier FOAM=MWMod.id("textures/effect/class_foam.png");
    private static final Identifier SHARD=MWMod.id("textures/effect/class_shard.png");
    private static final Identifier ENERGY=MWMod.id("textures/effect/wing_energy.png");
    private static final Identifier LINE=MWMod.id("textures/effect/skill_filament.png");
    private static final Identifier GLOW=MWMod.id("textures/effect/rift_glow.png");
    private static final float TAU=(float)(Math.PI*2);
    private record P(float x,float y,float z){P add(float x1,float y1,float z1){return new P(x+x1,y+y1,z+z1);}}
    private ClientClassSurfaces(){}

    static void preload(MinecraftClient client){
        for(var id:new Identifier[]{SMOKE,WATER,LEAF,FOAM,SHARD,ENERGY,LINE,GLOW})client.getTextureManager().getTexture(id);
    }

    // Matrix is already translated to p.origin(). Never creates entities or changes hit geometry.
    static void render(WorldRenderContext c,S2C_SkillPattern p,float age,float fade){
        if(c.matrixStack()==null||c.consumers()==null||fade<=.001f)return;
        float radius=(float)Math.max(.3,Math.min(4.5,p.radius()));
        float face=(float)Math.toDegrees(Math.atan2(c.camera().getPos().x-p.origin().x,c.camera().getPos().z-p.origin().z));
        switch(p.shape()){
            case S2C_SkillPattern.HEAL -> heal(c,radius,age,fade,face);
            case S2C_SkillPattern.ARROW_IMPACT -> arrow(c,p,radius,age,fade,face);
            case S2C_SkillPattern.WEB_IMPACT -> web(c,radius,age,fade);
            case S2C_SkillPattern.TIDAL -> tidal(c,radius,age,fade);
            case S2C_SkillPattern.CREEPER_BLAST -> blast(c,radius,age,fade,face);
            case S2C_SkillPattern.IRON_PUNCH -> iron(c,radius,age,fade,face);
            case S2C_SkillPattern.MOLE_BORE -> mole(c,p,radius,age,fade,face);
            case S2C_SkillPattern.LIGHTNING -> lightning(c,radius,age,fade,face);
            default -> {}
        }
    }

    private static void heal(WorldRenderContext c,float radius,float age,float fade,float face){
        var m=c.matrixStack();
        float reach=Math.min(1.65f,radius*.50f+.25f);
        var mist=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(WATER));
        for(int ribbon=0;ribbon<5;ribbon++){
            float base=ribbon*TAU/5+age*.035f;
            for(int j=0;j<10;j++){
                float t0=j/10f,t1=(j+1)/10f;
                float a=base+t0*1.95f,b=base+t1*1.95f;
                float rad0=reach*(.92f-t0*.30f),rad1=reach*(.92f-t1*.30f);
                float y0=.08f+t0*2.5f,y1=.08f+t1*2.5f;
                float width=.13f+.12f*sin(t0*(float)Math.PI);
                P p0=polar(rad0,a,y0),p1=polar(rad1,b,y1);
                quad(mist,m.peek(),p0.add(-cos(a)*width,0,-sin(a)*width),p0.add(cos(a)*width,0,sin(a)*width),
                        p1.add(cos(b)*width,0,sin(b)*width),p1.add(-cos(b)*width,0,-sin(b)*width),
                        0,1-t0,1,1-t1,.35f,1,.68f,fade*.36f);
            }
        }
        var leaves=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(LEAF));
        for(int i=0;i<16;i++){
            float t=fract(i*.618034f+age*.032f),a=i*2.39996f+age*.055f;
            P p=polar(reach*(.55f+.40f*sin(i*1.7f)),a,.15f+t*2.65f);
            m.push();m.translate(p.x,p.y,p.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face+sin(i+age*.09f)*30));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin(i*2.3f)*48));
            quad(leaves,m.peek(),new P(-.13f,-.26f,0),new P(.13f,-.26f,0),new P(.13f,.26f,0),new P(-.13f,.26f,0),
                    0,1,1,0,.71f,1,.57f,fade*sin(t*(float)Math.PI)*.87f);m.pop();
        }
        // Curved vertical life streams embrace the body, leaving the centre visible.
        var veins=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int stream=0;stream<6;stream++){
            P last=null;
            for(int j=0;j<=16;j++){
                float t=j/16f,a=stream*TAU/6+age*.035f+t*1.7f;
                float r=reach*(.55f+.18f*sin(t*(float)Math.PI));
                P next=polar(r,a,.10f+t*2.28f);
                if(last!=null){
                    float pulse=fade*(.16f+.32f*(float)Math.pow(.5f+.5f*sin(t*10-age*.35f),3));
                    line(veins,m.peek(),last,next,.010f,pulse*.50f,pulse,pulse*.59f);
                }
                last=next;
            }
        }
    }

    private static void arrow(WorldRenderContext c,S2C_SkillPattern p,float radius,float age,float fade,float face){
        var m=c.matrixStack();
        float flash=fade*Math.max(0,1-age/6f),open=Math.min(1,age/8f);
        var core=c.consumers().getBuffer(RenderLayer.getEyes(ENERGY));
        billboard(core,m,new P(0,.28f,0),.65f*(.55f+open),.62f,face,flash,flash*.81f,flash*.38f,1);
        var light=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        float yaw=(float)Math.toRadians(p.yaw());
        float forwardX=-sin(yaw),forwardY=0,forwardZ=cos(yaw);
        var direction=p.end().subtract(p.origin());
        if(direction.lengthSquared()>.0001){
            direction=direction.normalize();
            forwardX=(float)direction.x;forwardY=(float)direction.y;forwardZ=(float)direction.z;
        }
        P forward=new P(forwardX,forwardY,forwardZ);
        P side=perpendicularTo(forward),up=cross(forward,side);
        for(int i=0;i<24;i++){
            float a=i*2.39996f;
            float reach=Math.min(2.7f,radius*.82f)*(.18f+open*.82f)*(.55f+.45f*hash(i));
            float spread=.22f+.45f*hash(i+11),travel=reach*(.70f+.25f*hash(i+9));
            P end=directional(forward,side,up,travel,cos(a)*reach*spread,sin(a)*reach*spread);
            float head=.42f+open*.4f;
            P start=new P(end.x*head,end.y*head,end.z*head);
            float power=fade*(.36f+.64f*hash(i+5));
            line(light,m.peek(),start,end,.025f*(1-open*.55f),power,power*.80f,power*.37f);
            line(light,m.peek(),end,end.add(end.x*.06f,.08f,end.z*.06f),.009f,power,power*.97f,power*.81f);
        }
        var chips=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SHARD));
        for(int i=0;i<11;i++){
            float a=i*2.39996f,reach=Math.min(2.5f,radius*.74f)*open*(.55f+.40f*hash(i));
            P at=directional(forward,side,up,reach,cos(a)*reach*.40f,sin(a)*reach*.40f);
            fragment(chips,m,at,.055f+.035f*hash(i+4),age*11+i*31,.94f,.78f,.34f,fade*Math.max(0,1-age/19f));
        }
    }

    private static void web(WorldRenderContext c,float radius,float age,float fade){
        var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        var m=c.matrixStack();
        float open=Math.min(1,age/8f),after=Math.max(0,age-7);
        float tremble=(float)Math.exp(-after*.16f);
        for(int strand=0;strand<12;strand++){
            float a=strand*TAU/12;
            P last=polar(radius*.36f*open,a,.055f);
            for(int j=1;j<=6;j++){
                float t=j/6f;
                float r=radius*(.36f+t*.56f)*open;
                float vibration=sin(t*17-after*.95f+strand*.8f)*.065f*tremble;
                P next=polar(r,a+sin(t*(float)Math.PI)*.10f,Math.max(.027f,.05f+sin(t*(float)Math.PI)*(.16f+.25f*(1-open))+vibration));
                float power=fade*(.26f+.58f*sin(t*(float)Math.PI));
                line(v,m.peek(),last,next,.015f,power*.7f,power*.86f,power);last=next;
            }
            // A small diamond knot lies on the web; it is not an added block or marker entity.
            float knotR=radius*(.43f+.37f*open);
            P p=polar(knotR,a,.068f);float size=.05f+.015f*sin(age*.3f+strand);
            line(v,m.peek(),p.add(-size,0,0),p.add(0,0,size),.012f,fade*.8f,fade*.9f,fade);
            line(v,m.peek(),p.add(0,0,size),p.add(size,0,0),.012f,fade*.8f,fade*.9f,fade);
        }
        // Staggered inter-spoke strands settle with different phases after the landing pulse.
        for(int layer=0;layer<3;layer++)for(int strand=0;strand<12;strand++){
            float progress=clamp((age-layer*1.4f)/8f);
            float r=radius*(.38f+layer*.22f)*progress;
            P last=null;
            for(int j=0;j<=4;j++){
                float u=j/4f,a=(strand+u)*TAU/12;
                float sag=sin(u*(float)Math.PI)*(.075f+.035f*layer);
                float y=.08f+layer*.035f+sin(a*5-after*.9f+layer)*.043f*tremble-sag;
                P next=polar(Math.max(0,r-sag),a,Math.max(.026f,y));
                if(last!=null){float power=fade*(.34f+layer*.10f);line(v,m.peek(),last,next,.010f,power*.69f,power*.85f,power);}
                last=next;
            }
        }
    }

    private static void tidal(WorldRenderContext c,float radius,float age,float fade){
        var m=c.matrixStack();
        float pull=Math.max(.24f,1-age*.028f);
        var water=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(WATER));
        for(int layer=0;layer<2;layer++)for(int arc=0;arc<4;arc++)for(int j=0;j<8;j++){
            float a=arc*TAU/4+j*TAU/44+age*.013f*(layer==0?1:-1);
            float b=a+TAU/44;
            float ra=radius*pull*(layer==0?1:.72f);
            float crestA=.42f+.36f*sin(a*3-age*.22f+layer),crestB=.42f+.36f*sin(b*3-age*.22f+layer);
            float heightA=.50f+crestA,heightB=.50f+crestB;
            P lowA=polar(ra,a,.08f),lowB=polar(ra,b,.08f);
            P highA=polar(Math.max(.08f,ra-.18f),a,heightA),highB=polar(Math.max(.08f,ra-.18f),b,heightB);
            P curlA=polar(Math.max(.05f,ra-.48f),a,heightA-.18f),curlB=polar(Math.max(.05f,ra-.48f),b,heightB-.18f);
            float alpha=fade*(layer==0?.56f:.43f);
            quad(water,m.peek(),lowA,lowB,highB,highA,j/8f,1,(j+1)/8f,0,.085f,.46f,.87f,alpha);
            quad(water,m.peek(),highA,highB,curlB,curlA,j/8f,.75f,(j+1)/8f,.18f,.18f,.69f,.96f,alpha*.85f);
        }
        var crest=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(FOAM));
        for(int layer=0;layer<2;layer++)for(int arc=0;arc<4;arc++)for(int j=0;j<8;j++){
            float a=arc*TAU/4+j*TAU/44+age*.013f*(layer==0?1:-1),b=a+TAU/44;
            float r=Math.max(.08f,radius*pull*(layer==0?1:.72f)-.20f);
            P pa=polar(r,a,.92f+.36f*sin(a*3-age*.22f+layer));
            P pb=polar(r,b,.92f+.36f*sin(b*3-age*.22f+layer));
            P qa=polar(Math.max(.05f,r-.25f),a,pa.y-.14f),qb=polar(Math.max(.05f,r-.25f),b,pb.y-.14f);
            quad(crest,m.peek(),pa.add(0,.04f,0),pb.add(0,.04f,0),qb,qa,0,0,1,1,.70f,.92f,1,fade*.88f);
        }
        var foam=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int layer=0;layer<2;layer++)for(int arc=0;arc<4;arc++)for(int j=0;j<6;j++){
            float a=arc*TAU/4+j*TAU/34+age*.013f*(layer==0?1:-1),b=a+TAU/54;
            float r=Math.max(.05f,radius*pull*(layer==0?1:.72f)-.37f);
            P pa=polar(r,a,.80f+.36f*sin(a*3-age*.22f+layer));
            P pb=polar(r,b,.80f+.36f*sin(b*3-age*.22f+layer));
            float power=fade*(.37f+.28f*hash(j+arc*5));
            line(foam,m.peek(),pa,pb,.019f,power*.69f,power*.91f,power);
        }
    }

    private static void blast(WorldRenderContext c,float radius,float age,float fade,float face){
        var m=c.matrixStack();float open=Math.min(1,age/10f);
        float flash=fade*Math.max(0,1-age/5f);
        var glow=c.consumers().getBuffer(RenderLayer.getEyes(GLOW));
        billboard(glow,m,new P(0,.55f,0),1.1f,.85f,face,flash,flash,flash*.88f,1);
        billboard(glow,m,new P(0,1.05f,0),.27f,1.02f,face,flash*.95f,flash,flash*.75f,1);
        billboard(glow,m,new P(0,.12f,0),.90f,.18f,face,flash*.81f,flash*.91f,flash*.61f,1);
        var shell=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(ENERGY));
        float r=radius*(.18f+.82f*open),power=fade*(1-open*.65f)*Math.max(.12f,1-age/19f);
        for(int band=0;band<2;band++)for(int j=0;j<24;j++){
            float a=j*TAU/24,b=(j+1)*TAU/24,lo=band*.40f,hi=lo+.40f;
            P pa=polar(r*cos(lo),a,.06f+r*sin(lo)*.54f),pb=polar(r*cos(lo),b,.06f+r*sin(lo)*.54f);
            P pc=polar(r*cos(hi),b,.06f+r*sin(hi)*.54f),pd=polar(r*cos(hi),a,.06f+r*sin(hi)*.54f);
            quad(shell,m.peek(),pa,pb,pc,pd,j/6f,1,(j+1)/6f,0,.37f,.89f,.21f,power*.30f);
        }
        // Broken shock-front sheets accelerate outwards, followed by delayed smoke.
        for(int i=0;i<12;i++){
            float a=i*TAU/12,b=a+TAU/22;
            float inner=r*.83f;
            quad(shell,m.peek(),polar(inner,a,.06f),polar(r,a,.15f),polar(r,b,.15f),polar(inner,b,.06f),
                    0,1,1,0,.69f,.95f,.38f,power*.40f);
        }
        var dust=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SMOKE));
        for(int i=0;i<13;i++){
            float a=i*2.39996f,dist=r*(.44f+.43f*hash(i));
            P p=polar(dist,a,.17f+age*.038f+hash(i+7)*.45f);
            float smokeFade=fade*clamp((age-2)/5f)*Math.max(.05f,1-age/29f);
            billboard(dust,m,p,.46f+age*.018f,.36f+age*.015f,face,.34f,.39f,.31f,smokeFade*.66f);
        }
    }

    private static void iron(WorldRenderContext c,float radius,float age,float fade,float face){
        var m=c.matrixStack();float spread=Math.min(1,age/7f);
        var metal=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SHARD));
        for(int arm=0;arm<6;arm++){
            float a=arm*TAU/6+.18f,reach=Math.min(3.4f,radius)*spread;
            P root=polar(.35f,a,.028f),left=polar(reach*.69f,a-.10f,.035f);
            P tip=polar(reach,a,.029f),right=polar(reach*.69f,a+.10f,.035f);
            quad(metal,m.peek(),root,left,tip,right,0,1,1,0,.64f,.68f,.69f,fade*.62f*Math.max(.25f,1-age/28f));
            fragment(metal,m,polar(reach*.68f,a,.11f+sin(Math.min(1,age/15f)*(float)Math.PI)*.18f),
                    .075f,age*7+arm*45,.52f,.56f,.59f,fade*.67f);
        }
        var light=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int arm=0;arm<6;arm++){
            float a=arm*TAU/6+.18f;
            float reach=Math.min(3.4f,radius)*spread;
            P left=polar(reach*.70f,a-.10f,.047f),tip=polar(reach,a,.047f),right=polar(reach*.70f,a+.10f,.047f);
            float shine=fade*(.24f+.31f*(float)Math.pow(.5f+.5f*sin(age*.52f-arm),3));
            line(light,m.peek(),left,tip,.015f,shine*.79f,shine*.87f,shine);
            line(light,m.peek(),tip,right,.015f,shine*.79f,shine*.87f,shine);
            P last=polar(.38f,a,.045f);
            for(int j=1;j<=6;j++){
                float dist=Math.min(3.4f,radius)*j/6f*spread;
                P next=polar(dist,a+sin(j*3.1f+arm)*.07f,.047f);
                float power=fade*(1-j/9f)*.63f;
                line(light,m.peek(),last,next,.024f,power,power*.76f,power*.38f);
                if(j==3||j==5){
                    P fork=polar(dist+.28f,a+.15f,.048f);
                    line(light,m.peek(),next,fork,.013f,power*.7f,power*.51f,power*.2f);
                }
                last=next;
            }
        }
        var dust=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SMOKE));
        for(int i=0;i<12;i++){
            P p=polar(Math.min(radius,2.8f)*(.38f+spread*.42f),i*TAU/12,.18f+age*.018f);
            billboard(dust,m,p,.46f+age*.008f,.32f+age*.009f,face,.57f,.55f,.48f,fade*.59f);
        }
    }

    private static void mole(WorldRenderContext c,S2C_SkillPattern p,float radius,float age,float fade,float face){
        var m=c.matrixStack();float yaw=(float)Math.toRadians(p.yaw());
        P forward=new P(-sin(yaw),0,cos(yaw));
        var movement=p.end().subtract(p.origin());
        if(movement.lengthSquared()>.0001){
            movement=movement.normalize();
            forward=new P((float)movement.x,(float)movement.y,(float)movement.z);
        }
        float planar=(float)Math.sqrt(forward.x*forward.x+forward.z*forward.z);
        P side=planar>.001f?new P(forward.z/planar,0,-forward.x/planar):new P(cos(yaw),0,sin(yaw));
        // Trail follows pitch as well as yaw; the small ballistic lift still uses world gravity.
        P up=new P(0,1,0);
        var dust=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SMOKE));
        for(int i=0;i<12;i++){
            float angle=-.85f+i*1.7f/11;
            float dist=Math.min(radius,1.65f)*Math.min(.90f,.20f+age*.075f)*(.55f+.4f*hash(i));
            P at=directional(forward,side,up,-cos(angle)*dist,sin(angle)*dist,.16f+hash(i+13)*.3f+age*.027f);
            billboard(dust,m,at,.32f+age*.012f,.22f+age*.015f,face,.58f,.39f,.19f,fade*.61f);
        }
        var chips=c.consumers().getBuffer(RenderLayer.getEntityTranslucent(SHARD));
        for(int i=0;i<10;i++){
            float a=-.77f+i*1.54f/9,dist=Math.min(radius,1.65f)*Math.min(.91f,.20f+age*.09f)*(.63f+.3f*hash(i+4));
            float y=.12f+hash(i+9)*.15f+age*.09f-age*age*.006f;
            P at=directional(forward,side,up,-cos(a)*dist,sin(a)*dist,Math.max(.045f,y));
            fragment(chips,m,at,.046f+.035f*hash(i+2),age*13+i*41,.63f,.43f,.22f,fade*.83f);
        }
        var sparks=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        for(int i=0;i<10;i++){
            float a=-.85f+i*1.7f/9,dist=Math.min(radius,1.65f)*Math.min(.93f,.33f+age*.065f);
            P at=directional(forward,side,up,-cos(a)*dist,sin(a)*dist,.16f+hash(i+3)*.35f);
            P tail=at.add(-at.x*.12f,-.06f,-at.z*.12f);
            line(sparks,m.peek(),at,tail,.015f,fade*.65f,fade*.37f,fade*.10f);
        }
    }

    private static void lightning(WorldRenderContext c,float radius,float age,float fade,float face){
        var m=c.matrixStack();
        var v=c.consumers().getBuffer(RenderLayer.getEyes(LINE));
        float pulse=fade*(.46f+.54f*(float)Math.pow(.5f+.5f*sin(age*2.3f),3));
        for(int branch=0;branch<9;branch++){
            float a=branch*TAU/9;
            P last=new P(0,.05f,0);
            for(int j=1;j<=6;j++){
                float r=Math.min(2.0f,radius+.6f)*j/6f;
                P next=polar(r,a+sin(j*5.1f+branch+(int)(age*1.5f))*.16f,.05f+hash(j+branch)*.055f);
                float energy=pulse*(1-j*.10f);
                line(v,m.peek(),last,next,.033f,energy*.38f,energy*.24f,energy*.82f);
                line(v,m.peek(),last.add(0,.004f,0),next.add(0,.004f,0),.008f,energy*.96f,energy*.92f,energy);
                if(j==3||j==5){
                    P fork=polar(Math.min(radius+.35f,r+.24f),a+(branch%2==0?.18f:-.18f),.065f);
                    P middle=new P((next.x+fork.x)*.5f+.035f*sin(branch),.086f,(next.z+fork.z)*.5f);
                    line(v,m.peek(),next,middle,.008f,energy*.8f,energy*.70f,energy);
                    line(v,m.peek(),middle,fork,.006f,energy*.7f,energy*.63f,energy*.9f);
                }
                last=next;
            }
        }
        var core=c.consumers().getBuffer(RenderLayer.getEyes(ENERGY));
        float early=Math.max(0,1-age/10f)*pulse;
        billboard(core,m,new P(0,1.07f,0),.16f,1.04f,face,early*.82f,early*.8f,early,1);
        billboard(core,m,new P(0,.94f,0),.057f,.90f,face,early,early*.97f,early,1);
        billboard(core,m,new P(0,.12f,0),.48f,.19f,face,pulse*.64f,pulse*.61f,pulse,1);
    }

    private static void billboard(VertexConsumer v,MatrixStack m,P p,float sx,float sy,float face,float r,float g,float b,float alpha){
        m.push();m.translate(p.x,p.y,p.z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
        quad(v,m.peek(),new P(-sx,-sy,0),new P(sx,-sy,0),new P(sx,sy,0),new P(-sx,sy,0),0,1,1,0,r,g,b,alpha);
        m.pop();
    }

    /** Small decorative mesh, sharing the caller's material; no world block or collision exists. */
    private static void fragment(VertexConsumer v,MatrixStack m,P p,float size,float spin,float r,float g,float b,float alpha){
        m.push();m.translate(p.x,p.y,p.z);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin*.73f));
        float y=size*.70f,z=size*.53f;
        P a=new P(-size,-y,-z),bb=new P(size,-y,-z),cc=new P(size,y,-z),d=new P(-size,y,-z);
        P e=new P(-size,-y,z),f=new P(size,-y,z),h=new P(size,y,z),i=new P(-size,y,z);
        quad(v,m.peek(),a,bb,cc,d,0,1,1,0,r*.77f,g*.77f,b*.77f,alpha);
        quad(v,m.peek(),e,f,h,i,0,1,1,0,r,g,b,alpha);
        quad(v,m.peek(),a,e,i,d,0,1,1,0,r*.83f,g*.83f,b*.83f,alpha);
        quad(v,m.peek(),bb,f,h,cc,0,1,1,0,r*.91f,g*.91f,b*.91f,alpha);
        quad(v,m.peek(),d,cc,h,i,0,1,1,0,r,g,b,alpha);
        quad(v,m.peek(),a,bb,f,e,0,1,1,0,r*.61f,g*.61f,b*.61f,alpha);
        m.pop();
    }

    private static P directional(P forward,P side,P up,float distance,float lateral,float vertical){
        return new P(forward.x*distance+side.x*lateral+up.x*vertical,
                forward.y*distance+side.y*lateral+up.y*vertical,
                forward.z*distance+side.z*lateral+up.z*vertical);
    }
    private static P perpendicularTo(P p){
        P side=cross(p,Math.abs(p.y)>.91f?new P(1,0,0):new P(0,1,0));
        float length=(float)Math.sqrt(side.x*side.x+side.y*side.y+side.z*side.z);
        return new P(side.x/length,side.y/length,side.z/length);
    }
    private static P cross(P a,P b){return new P(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);}

    private static void line(VertexConsumer v,MatrixStack.Entry m,P a,P b,float width,float r,float g,float blue){
        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<.00001f)return;
        // Floor branches need a horizontal cross-section; upright streaks use a vertical one.
        float ox=-dz,oy=0,oz=dx,n=(float)Math.sqrt(ox*ox+oz*oz);
        if(n<.0001f){ox=-dy;oy=dx;oz=0;n=(float)Math.sqrt(ox*ox+oy*oy);}
        ox=ox/n*width;oy=oy/n*width;oz=oz/n*width;
        quad(v,m,a.add(-ox,-oy,-oz),a.add(ox,oy,oz),b.add(ox,oy,oz),b.add(-ox,-oy,-oz),0,.5f,1,.5f,r,g,blue,1);
    }

    private static void quad(VertexConsumer v,MatrixStack.Entry m,P a,P b,P c,P d,float u0,float v0,float u1,float v1,float r,float g,float blue,float alpha){
        P normal=cross(new P(b.x-a.x,b.y-a.y,b.z-a.z),new P(d.x-a.x,d.y-a.y,d.z-a.z));
        float length=(float)Math.sqrt(normal.x*normal.x+normal.y*normal.y+normal.z*normal.z);
        normal=length>.00001f?new P(normal.x/length,normal.y/length,normal.z/length):new P(0,1,0);
        P back=new P(-normal.x,-normal.y,-normal.z);
        vertex(v,m,a,u0,v0,r,g,blue,alpha,normal);vertex(v,m,b,u1,v0,r,g,blue,alpha,normal);
        vertex(v,m,c,u1,v1,r,g,blue,alpha,normal);vertex(v,m,d,u0,v1,r,g,blue,alpha,normal);
        vertex(v,m,d,u0,v1,r,g,blue,alpha,back);vertex(v,m,c,u1,v1,r,g,blue,alpha,back);
        vertex(v,m,b,u1,v0,r,g,blue,alpha,back);vertex(v,m,a,u0,v0,r,g,blue,alpha,back);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry m,P p,float u,float texV,float r,float g,float b,float alpha,P normal){
        v.vertex(m,p.x,p.y,p.z).color(clamp(r),clamp(g),clamp(b),clamp(alpha)).texture(u,texV)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,normal.x,normal.y,normal.z);
    }
    private static P polar(float r,float a,float y){return new P(cos(a)*r,y,sin(a)*r);}
    private static float sin(float x){return (float)Math.sin(x);}
    private static float cos(float x){return (float)Math.cos(x);}
    private static float fract(float x){return x-(float)Math.floor(x);}
    private static float hash(int i){return fract(sin(i*127.13f+12.33f)*43758.5453f);}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
}
