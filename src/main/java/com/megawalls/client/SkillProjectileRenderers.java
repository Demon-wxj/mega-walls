package com.megawalls.client;

import com.megawalls.skill.SkillModels;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.WitherSkullEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.WitherSkullEntity;

/** Keep native models and bind their additional material layers to the very same entity. */
final class SkillProjectileRenderers {
    static final class Fireball extends FlyingItemEntityRenderer<SkillModels.Fireball> {
        Fireball(EntityRendererFactory.Context context){super(context,1.5f,true);}
        @Override public void render(SkillModels.Fireball entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light){
            super.render(entity,yaw,delta,matrices,consumers,light);
            ClientSkillCinematics.trackProjectile(entity);
        }
    }
    static final class Skull extends WitherSkullEntityRenderer {
        Skull(EntityRendererFactory.Context context){super(context);}
        @Override public void render(WitherSkullEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light){
            super.render(entity,yaw,delta,matrices,consumers,light);
            if(entity instanceof SkillModels.Skull)ClientSkillCinematics.trackProjectile(entity);
        }
    }
}
