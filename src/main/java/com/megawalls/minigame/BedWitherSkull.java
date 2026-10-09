package com.megawalls.minigame;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Vanilla skull entity type for client rendering, with BedWars-only collision rules. */
public final class BedWitherSkull extends WitherSkullEntity {
    public BedWitherSkull(EntityType<? extends WitherSkullEntity> type,World world){super(type,world);}

    @Override protected void onCollision(HitResult hit){
        if(getWorld() instanceof ServerWorld && BedWars.projectileValid(this))BedSpecial.explodeSkull(this);
        discard();
    }
}
