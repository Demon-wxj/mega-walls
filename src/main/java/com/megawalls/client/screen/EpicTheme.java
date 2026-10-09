package com.megawalls.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

/** Shared visual language. Every texture is a bundled resource, never fetched in the render loop. */
public final class EpicTheme {
    public static final int INK=0xFFF0EBE3,MUTED=0xFF9A9AAE,GOLD=0xFFCEAD71,PURPLE=0xFFB482F2;
    private static final Identifier BACK=Identifier.of("megawalls","textures/gui/cinematic/battlefield.png");
    private static final String[] ICONS={"zombie","skeleton","spider","squid","enderman","herobrine","creeper","dreadlord","golem","blaze","hunter","mole","werewolf"};
    public static void background(DrawContext c,int w,int h){
        RenderSystem.enableBlend();
        float scale=Math.max(w/1672f,h/941f);
        int dw=(int)Math.ceil(1672*scale),dh=(int)Math.ceil(941*scale);
        c.drawTexture(BACK,(w-dw)/2,(h-dh)/2,dw,dh,0,0,1672,941,1672,941);
        c.fillGradient(0,0,w,h,0x50090813,0xBC060810);
        c.fill(0,0,w,2,0xFF5F416E);
        c.fill(0,h-2,w,h,0xFF5F416E);
    }
    public static void panel(DrawContext c,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        c.fill(x,y,x+w,y+h,0xDC11121C);
        c.fill(x+1,y+1,x+w-1,y+2,0xFF373044);
        c.fill(x,y,x+1,y+h,0xFF39313D);c.fill(x+w-1,y,x+w,y+h,0xFF39313D);
        c.fill(x,y+h-1,x+w,y+h,0xFF302B35);
        c.fill(x,y,x+18,y+1,GOLD);c.fill(x+w-18,y+h-1,x+w,y+h,GOLD);
    }
    public static void heading(DrawContext c,TextRenderer tr,String title,String subtitle,int x,int y){
        c.fill(x,y,x+2,y+24,GOLD);
        c.drawTextWithShadow(tr,title,x+9,y,INK);
        c.drawTextWithShadow(tr,subtitle,x+9,y+14,MUTED);
    }
    public static void large(DrawContext c,TextRenderer tr,String text,int x,int y,float scale,int color){
        c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        c.drawTextWithShadow(tr,text,0,0,color);c.getMatrices().pop();
    }
    public static void emblem(DrawContext c,int x,int y,int size,int color){
        // Two stepped pillars and a lintel: an original voxel gate, not a borrowed team logo.
        int u=Math.max(1,size/12);
        c.fill(x,y+2*u,x+2*u,y+10*u,color);c.fill(x+8*u,y+2*u,x+10*u,y+10*u,color);
        c.fill(x+2*u,y,x+8*u,y+2*u,color);c.fill(x+3*u,y+4*u,x+7*u,y+5*u,PURPLE);
        c.fill(x+4*u,y+6*u,x+6*u,y+12*u,color);
    }
    public static void icon(DrawContext c,int id,int x,int y,int size){
        if(id<1||id>ICONS.length)return;
        Identifier texture=Identifier.of("megawalls","textures/gui/cinematic/classes/"+ICONS[id-1]+".png");
        face(c,texture,x,y,size);
    }
    private static void part(DrawContext c,Identifier t,int x,int y,int u,int v,int w,int h,int s){c.drawTexture(t,x,y,w*s,h*s,u,v,w,h,64,64);}
    public static void skin(DrawContext c,Identifier t,int center,int y,int s){
        RenderSystem.enableBlend();int x=center-4*s;
        part(c,t,x,y,8,8,8,8,s);part(c,t,x,y,40,8,8,8,s);
        part(c,t,x,y+8*s,20,20,8,12,s);part(c,t,x,y+8*s,20,36,8,12,s);
        part(c,t,x-4*s,y+8*s,44,20,4,12,s);part(c,t,x+8*s,y+8*s,36,52,4,12,s);
        part(c,t,x,y+20*s,4,20,4,12,s);part(c,t,x+4*s,y+20*s,20,52,4,12,s);
        part(c,t,x-4*s,y+8*s,44,36,4,12,s);part(c,t,x+8*s,y+8*s,52,52,4,12,s);
        part(c,t,x,y+20*s,4,36,4,12,s);part(c,t,x+4*s,y+20*s,4,52,4,12,s);
    }
    public static void face(DrawContext c,String name,int x,int y,int size){
        Identifier t=Identifier.of("megawalls","textures/gui/cinematic/"+name+".png");
        face(c,t,x,y,size);
    }
    public static void face(DrawContext c,Identifier t,int x,int y,int size){
        RenderSystem.enableBlend();c.drawTexture(t,x,y,size,size,8,8,8,8,64,64);c.drawTexture(t,x,y,size,size,40,8,8,8,64,64);
    }
}
