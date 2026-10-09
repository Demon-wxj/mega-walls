package com.megawalls.skill;

import net.minecraft.entity.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Networked visuals only. SkillProjectiles owns collision, damage and lifetime. */
public final class SkillModels {
    public static final EntityType<Arrow> ARROW=register("skill_arrow",Arrow::new,.5f);
    public static final EntityType<Skull> SKULL=register("skill_skull",Skull::new,.3125f);
    public static final EntityType<Fireball> FIREBALL=register("skill_fireball",Fireball::new,.5f);
    public static final EntityType<Iron> IRON=register("skill_iron",Iron::new,.98f);
    public static final EntityType<Bolt> BOLT=register("skill_bolt",Bolt::new,.1f);
    private static <T extends Entity> EntityType<T> register(String name,EntityType.EntityFactory<T> factory,float size){
        var key=RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of("megawalls",name));
        return Registry.register(Registries.ENTITY_TYPE,key,EntityType.Builder.create(factory,SpawnGroup.MISC)
            .dimensions(size,size).disableSaving().disableSummon().makeFireImmune()
            .maxTrackingRange(8).trackingTickInterval(1).build(key.getValue().toString()));
    }
    public static void init() {}
    /** Rendered as a block, but never runs falling-block placement, drops or crushing damage. */
    public static class Iron extends FallingBlockEntity {
        public Iron(EntityType<? extends FallingBlockEntity> type,World world){super(type,world);dropItem=false;setNoGravity(true);}
        @Override public net.minecraft.block.BlockState getBlockState(){return net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState();}
        @Override public void tick(){age++;if(!getWorld().isClient&&age>20)discard();}
        @Override public boolean handleFallDamage(float a,float b,DamageSource source){return false;}
        @Override public boolean canHit(){return false;}
    }
    /** Vanilla lightning renderer only: no fire, copper oxidation, mob conversion or extra damage. */
    public static class Bolt extends LightningEntity {
        public Bolt(EntityType<? extends LightningEntity> type,World world){super(type,world);setCosmetic(true);}
        @Override public void tick(){age++;if(age%2==0)seed=getRandom().nextLong();if(!getWorld().isClient&&age>=8)discard();}
    }
    private static void visualTick(Entity e){
        e.age++;
        // Predict only the visible motion. Never run vanilla projectile collisions or explosions.
        if(e.getWorld().isClient) e.setPosition(e.getPos().add(e.getVelocity()));
    }
    public static class Arrow extends ArrowEntity {
        public Arrow(EntityType<? extends ArrowEntity> type,World world){super(type,world);setNoGravity(true);pickupType=PickupPermission.DISALLOWED;}
        @Override public void tick(){visualTick(this);}
        @Override public void onPlayerCollision(PlayerEntity player){}
        @Override public boolean damage(DamageSource source,float amount){return false;}
    }
    public static class Skull extends WitherSkullEntity {
        public Skull(EntityType<? extends WitherSkullEntity> type,World world){super(type,world);setNoGravity(true);}
        @Override public void tick(){visualTick(this);}
        @Override public boolean damage(DamageSource source,float amount){return false;}
    }
    public static class Fireball extends FireballEntity {
        public Fireball(EntityType<? extends FireballEntity> type,World world){super(type,world);setNoGravity(true);}
        @Override public void tick(){visualTick(this);}
        @Override public boolean damage(DamageSource source,float amount){return false;}
    }
}
