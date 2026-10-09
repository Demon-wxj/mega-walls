package com.megawalls.minigame;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.*;

/** Bounded, round-scoped implementations of the six historical special-item types. */
public final class BedSpecial {
    private record Temporary(ServerWorld w,BlockPos pos,BlockState state,BlockState original,int until){}
    private record Trap(ServerWorld w,BlockPos pos,BlockState original,String team){}
    private record Warp(ServerWorld w,Vec3d start,int until){}
    private static final List<Temporary> temporary=new ArrayList<>();
    private static final List<Trap> traps=new ArrayList<>();
    private static final Map<UUID,Warp> warps=new HashMap<>();
    private static final Map<UUID,Integer> cooldowns=new HashMap<>();
    public static void clear(){temporary.clear();traps.clear();warps.clear();cooldowns.clear();}
    public static void forget(UUID id){warps.remove(id);cooldowns.remove(id);}
    public static void damaged(ServerPlayerEntity p){if(warps.remove(p.getUuid())!=null)p.sendMessage(Text.literal("回城被伤害打断。"),true);}
    public static boolean magnet(LivingEntity e){return e instanceof ServerPlayerEntity p&&BedWars.fighter(p)&&BedShop.special(p.getEquippedStack(EquipmentSlot.FEET)).equals("magnet")&&p.getRandom().nextFloat()<.75f;}
    private static boolean canPlace(ServerWorld w,BlockPos pos){return BedArena.contains(w,pos)&&ArenaBlocks.replaceableGround(w.getBlockState(pos));}
    public static ActionResult use(ServerPlayerEntity p,Hand hand,BlockHitResult hit){
        if(!BedWars.fighter(p)||hand!=Hand.MAIN_HAND)return ActionResult.PASS;var stack=p.getStackInHand(hand);String special=BedShop.special(stack);if(special.isEmpty()||special.equals("magnet"))return ActionResult.PASS;
        var w=p.getServerWorld();int now=p.getServer().getTicks();boolean used=false;
        switch(special){
            // Primed TNT is an entity: accepting a replaceable site must not erase its original Light block.
            case "tnt"->{if(hit==null)return ActionResult.FAIL;var pos=hit.getBlockPos().offset(hit.getSide());if(!canPlace(w,pos))return ActionResult.FAIL;var e=new TntEntity(w,pos.getX()+.5,pos.getY(),pos.getZ()+.5,p);e.setFuse(80);BedWars.stamp(e);used=w.spawnEntity(e);}
            case "rescue"->{if(now<cooldowns.getOrDefault(p.getUuid(),0)||temporary.size()>1000)return ActionResult.FAIL;var center=p.getBlockPos().down();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var q=center.add(x,0,z);var st=Blocks.GLASS.getDefaultState();var original=w.getBlockState(q);if(canPlace(w,q)&&BedArena.set(w,q,st)){temporary.add(new Temporary(w,q,st,original,now+100));used=true;}}if(used)cooldowns.put(p.getUuid(),now+400);}
            case "wall"->{var facing=p.getHorizontalFacing();var across=facing.rotateYClockwise();var center=p.getBlockPos().offset(facing,2);for(int x=-2;x<=2;x++)for(int y=0;y<3;y++){var q=center.offset(across,x).up(y);if(canPlace(w,q)&&BedArena.set(w,q,Blocks.BRICKS.getDefaultState()))used=true;}}
            case "trap"->{if(hit==null||traps.size()>=64)return ActionResult.FAIL;var q=hit.getBlockPos().offset(hit.getSide());var original=w.getBlockState(q);if(canPlace(w,q)&&BedArena.set(w,q,Blocks.TRIPWIRE.getDefaultState())){traps.add(new Trap(w,q,original,BedWars.team(p)));used=true;}}
            case "warp"->{if(warps.containsKey(p.getUuid()))return ActionResult.FAIL;warps.put(p.getUuid(),new Warp(w,p.getPos(),now+120));p.sendMessage(Text.literal("回城中：保持不动 6 秒，受伤会打断。"),true);used=true;}
        }
        if(used){stack.decrement(1);return ActionResult.SUCCESS;}return ActionResult.FAIL;
    }
    public static void tick(MinecraftServer s){
        int now=s.getTicks();temporary.removeIf(t->{if(now<t.until)return false;if(t.w.getBlockState(t.pos).equals(t.state))BedArena.set(t.w,t.pos,t.original);return true;});
        warps.entrySet().removeIf(entry->{var p=s.getPlayerManager().getPlayer(entry.getKey());var v=entry.getValue();if(p==null||!BedWars.fighter(p))return true;if(p.getWorld()!=v.w||p.getPos().squaredDistanceTo(v.start)>.04){p.sendMessage(Text.literal("移动打断回城。"),true);return true;}if(now>=v.until){BedWars.teleportBase(p);return true;}return false;});
        traps.removeIf(t->{if(!t.w.getBlockState(t.pos).isOf(Blocks.TRIPWIRE))return true;for(var p:t.w.getPlayers())if(BedWars.fighter(p)&&!t.team.equals(BedWars.team(p))&&p.getBoundingBox().intersects(new Box(t.pos).expand(.25))){for(var effect:List.of(StatusEffects.BLINDNESS,StatusEffects.SLOWNESS,StatusEffects.WEAKNESS))p.addStatusEffect(new StatusEffectInstance(effect,100,2));for(var mate:t.w.getPlayers())if(BedWars.inRound(mate)&&t.team.equals(BedWars.team(mate)))mate.sendMessage(Text.literal("基地陷阱已被敌人触发！"),false);BedArena.set(t.w,t.pos,t.original);return true;}return false;});
    }
    public static void explode(TntEntity e){
        if(!(e.getWorld() instanceof ServerWorld w)||!BedWars.projectileValid(e))return;var owner=(ServerPlayerEntity)e.getOwner();var center=e.getPos();
        // Compute exposure before removing cover. No vanilla blast can push other games' players.
        var impacts=new LinkedHashMap<ServerPlayerEntity,Double>();for(var p:w.getPlayers())if(BedWars.fighter(p)&&(p==owner||!BedWars.sameTeam(owner,p))){double distance=p.getPos().distanceTo(center)/8;if(distance<=1)impacts.put(p,(1-distance)*net.minecraft.world.explosion.Explosion.getExposure(center,p));}
        var origin=e.getBlockPos();for(int x=-4;x<=4;x++)for(int y=-4;y<=4;y++)for(int z=-4;z<=4;z++)if(x*x+y*y+z*z<=16){var q=origin.add(x,y,z);if(BedArena.added(w,q)&&!(w.getBlockState(q).getBlock() instanceof BedBlock))BedArena.set(w,q,Blocks.AIR.getDefaultState());}
        for(var entry:impacts.entrySet()){var p=entry.getKey();double impact=entry.getValue();if(impact<=0)continue;if(p.damage(w.getDamageSources().explosion(e,owner),(float)((impact*impact+impact)*28+1))){var direction=p.getPos().subtract(center);p.takeKnockback(impact,-direction.x,-direction.z);p.velocityModified=true;}}
        w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,e.getX(),e.getY(),e.getZ(),1,0,0,0,0);w.playSound(null,e.getBlockPos(),net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.BLOCKS,4,1);
    }
    public static void explodeSkull(net.minecraft.entity.projectile.WitherSkullEntity e){
        if(!(e.getWorld() instanceof ServerWorld w)||!BedWars.projectileValid(e)||!(e.getOwner() instanceof ServerPlayerEntity owner))return;
        var center=e.getPos();var origin=e.getBlockPos();
        // Only this round's player-built blocks are destructible. The arena's original map and beds stay intact.
        for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++)if(x*x+y*y+z*z<=9){
            var q=origin.add(x,y,z);
            if(BedArena.added(w,q)&&!(w.getBlockState(q).getBlock() instanceof BedBlock))BedArena.set(w,q,Blocks.AIR.getDefaultState());
        }
        for(var p:w.getPlayers())if(BedWars.fighter(p)&&(p==owner||!BedWars.sameTeam(owner,p))){
            double distance=p.getPos().distanceTo(center);if(distance>4)continue;
            double impact=(1-distance/4)*net.minecraft.world.explosion.Explosion.getExposure(center,p);
            if(impact>0&&p.damage(w.getDamageSources().explosion(e,owner),(float)(impact*8+2))){
                var direction=p.getPos().subtract(center);p.takeKnockback(impact,-direction.x,-direction.z);p.velocityModified=true;
            }
        }
        w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,e.getX(),e.getY(),e.getZ(),1,0,0,0,0);
        w.playSound(null,e.getBlockPos(),net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.BLOCKS,3,1);
    }
}

