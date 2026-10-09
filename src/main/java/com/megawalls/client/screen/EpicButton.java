package com.megawalls.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Keyboard-focusable native button with a bespoke stone and metal treatment. */
public class EpicButton extends ButtonWidget {
    private final int accent;private String subtitle="";private int icon;
    public EpicButton(int x,int y,int w,int h,Text message,PressAction press,int accent){super(x,y,w,h,message,press,DEFAULT_NARRATION_SUPPLIER);this.accent=accent;}
    public EpicButton subtitle(String value){subtitle=value;return this;}
    public EpicButton icon(int value){icon=value;return this;}
    @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
        var tr=MinecraftClient.getInstance().textRenderer;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean focus=isHovered()||isFocused();
        int edge=active?(focus?accent:0xFF544658):0xFF34303B;
        c.fillGradient(x,y,x+w,y+h,active?(focus?0xFF352740:0xFF211E2C):0xFF181720,0xFF11121A);
        c.drawBorder(x,y,w,h,edge);
        c.fill(x+1,y+1,x+3,y+h-1,active?accent:0xFF45404B);
        c.fill(x+4,y+1,x+w-4,y+2,active&&focus?0x557E6699:0x223F3548);
        if(focus&&active){c.fill(x+w-5,y+5,x+w-3,y+h-5,accent);}
        int tx=x+(w<60?5:10);
        if(icon>0){EpicTheme.icon(c,icon,x+8,y+(h-22)/2,22);tx=x+36;}
        int room=Math.max(1,x+w-(w<60?4:12)-tx);
        int ty=y+(h-8)/2;
        if(!subtitle.isEmpty()&&h>=34)ty=y+8;
        c.drawTextWithShadow(tr,tr.trimToWidth(getMessage().getString(),room),tx,ty,active?EpicTheme.INK:0xFF85818E);
        if(!subtitle.isEmpty()&&h>=34)c.drawTextWithShadow(tr,tr.trimToWidth(subtitle,room),tx,y+h-14,active?EpicTheme.MUTED:0xFF686574);
    }
}
