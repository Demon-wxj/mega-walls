package com.megawalls.maintenance.client;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.*;
import net.minecraft.entity.*;
public final class VisualRenderers {
    public static class Iron extends FallingBlockEntityRenderer {
        public Iron(EntityRendererFactory.Context c){super(c);}
        @Override public boolean shouldRender(FallingBlockEntity e,Frustum f,double x,double y,double z){return ClientEffectTuning.enabled()&&super.shouldRender(e,f,x,y,z);}
    }
    public static class Bolt extends LightningEntityRenderer {
        public Bolt(EntityRendererFactory.Context c){super(c);}
        @Override public boolean shouldRender(LightningEntity e,Frustum f,double x,double y,double z){return ClientEffectTuning.enabled()&&super.shouldRender(e,f,x,y,z);}
    }
}
