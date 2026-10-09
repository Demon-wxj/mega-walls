package com.megawalls.game;

import net.minecraft.block.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.item.*;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import java.util.*;

/** Independent wither, castle and wall selections; later player blocks remain removable. */
public final class RegionProtection {
    private record Placement(ServerWorld world,BlockPos pos) {}
    private static final List<Placement> pending=new ArrayList<>();
    private static boolean box(BlockPos p,BlockPos a,BlockPos b){return p.getX()>=Math.min(a.getX(),b.getX())&&p.getX()<=Math.max(a.getX(),b.getX())&&p.getY()>=Math.min(a.getY(),b.getY())&&p.getY()<=Math.max(a.getY(),b.getY())&&p.getZ()>=Math.min(a.getZ(),b.getZ())&&p.getZ()<=Math.max(a.getZ(),b.getZ());}
    /** 0 = outside wall selections, 1 = closed, 2 = opened. Closed overlaps win. */
    private static int wallState(ServerWorld w,BlockPos p){
        var s=w.getServer();if(w!=s.getOverworld())return 0;
        int total=MapConfig.get(s,"wall_count")==4?4:1;
        int phase=MatchFlow.phase(s);
        boolean opened=phase==2||phase==3||phase==4&&MapConfig.get(s,"wall_saved")==1&&MapConfig.get(s,"wall_segment")>=MapConfig.walls(s);
        boolean insideOpened=false;
        for(int i=0;i<total;i++){
            String first="wall"+(i*2+1),second="wall"+(i*2+2);
            if(MapConfig.get(s,first+"_set")!=1||MapConfig.get(s,second+"_set")!=1||!box(p,MapConfig.point(s,first),MapConfig.point(s,second)))continue;
            if(!opened||i>=MapConfig.walls(s))return 1;
            insideOpened=true;
        }
        return insideOpened?2:0;
    }
    private static boolean region(ServerWorld w,BlockPos p,String suffix){
        var s=w.getServer();if(w!=s.getOverworld())return false;
        for(String t:MatchTeams.ALL)if(MapConfig.get(s,t+suffix+"1_set")==1&&MapConfig.get(s,t+suffix+"2_set")==1
            &&box(p,MapConfig.point(s,t+suffix+"1"),MapConfig.point(s,t+suffix+"2")))return true;
        return false;
    }
    private static boolean configuredCastle(ServerWorld w,BlockPos p){return region(w,p,"castle");}
    public static boolean wither(ServerWorld w,BlockPos p){return MatchFlow.phase(w.getServer())!=3&&region(w,p,"witherzone");}
    public static boolean castle(ServerWorld w,BlockPos p){return MatchFlow.phase(w.getServer())!=3&&wallState(w,p)!=2&&configuredCastle(w,p);}
    private static boolean otherProtected(ServerWorld w,BlockPos p){
        if(com.megawalls.minigame.TntRun.protectedAt(w,p)||com.megawalls.minigame.Spleef.protectedAt(w,p))return true;
        if(com.megawalls.minigame.HotPotato.stage()==com.megawalls.minigame.HotPotato.Stage.RUNNING&&com.megawalls.minigame.HotPotato.inside(w,p))return true;
        return false;
    }
    public static boolean contains(ServerWorld w,BlockPos p){return otherProtected(w,p)||wither(w,p)||wallState(w,p)==1||castle(w,p);}
    private static boolean builder(PlayerEntity p,ServerWorld w,BlockPos pos){return p.isCreative()&&(!(wither(w,pos)||wallState(w,pos)==1||castle(w,pos))||!MatchFlow.running(w.getServer()));}
    private static final class Placed extends PersistentState {
        final Set<Long> positions=new HashSet<>();
        static final Type<Placed> TYPE=new Type<>(Placed::new,(nbt,lookup)->{var value=new Placed();for(long p:nbt.getLongArray("positions"))value.positions.add(p);return value;},null);
        @Override public net.minecraft.nbt.NbtCompound writeNbt(net.minecraft.nbt.NbtCompound nbt,RegistryWrapper.WrapperLookup lookup){nbt.putLongArray("positions",positions.stream().mapToLong(Long::longValue).toArray());return nbt;}
    }
    private static Placed placed(ServerWorld w){return w.getPersistentStateManager().getOrCreate(Placed.TYPE,"megawalls_placed_castle");}
    public static void clearRestored(ServerWorld w,Collection<BlockPos> restored){
        if(restored.isEmpty())return;var data=placed(w);boolean changed=false;
        for(var p:restored)changed|=data.positions.remove(p.asLong());
        if(changed)data.markDirty();
    }
    private static void removeMarker(ServerWorld w,BlockPos p){if(configuredCastle(w,p)){var data=placed(w);if(data.positions.remove(p.asLong()))data.markDirty();}}
    private static boolean blocked(PlayerEntity p,ServerWorld w,BlockPos pos){
        if(wallState(w,pos)==1)return true;
        if(builder(p,w,pos))return false;
        return otherProtected(w,pos)||wither(w,pos)||castle(w,pos)&&!placed(w).positions.contains(pos.asLong());
    }
    private static ActionResult deny(PlayerEntity p){p.sendMessage(net.minecraft.text.Text.literal("§e原建筑受到保护；凋零区和未开放的分隔墙禁止改动。"),true);return ActionResult.FAIL;}
    private static void flushPlacements(net.minecraft.server.MinecraftServer s){
        for(var attempt:pending)if(attempt.world.getServer()==s&&castle(attempt.world,attempt.pos)
            &&!wither(attempt.world,attempt.pos)&&!attempt.world.getBlockState(attempt.pos).isAir()){
            var data=placed(attempt.world);if(data.positions.add(attempt.pos.asLong()))data.markDirty();
        }
        pending.clear();
    }
    public static void register(){
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,state,entity)->!(w instanceof ServerWorld sw)||!blocked(p,sw,pos));
        PlayerBlockBreakEvents.AFTER.register((w,p,pos,state,entity)->{if(w instanceof ServerWorld sw&&configuredCastle(sw,pos)){
            removeMarker(sw,pos);
            for(var side:net.minecraft.util.math.Direction.values()){var neighbor=pos.offset(side);if(sw.getBlockState(neighbor).isAir())removeMarker(sw,neighbor);}
        }});
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(!(w instanceof ServerWorld sw))return ActionResult.PASS;
            Item item=p.getStackInHand(hand).getItem();var target=hit.getBlockPos();var adjacent=target.offset(hit.getSide());
            if(item instanceof BlockItem blockItem){
                var dest=w.getBlockState(target).isReplaceable()?target:adjacent;
                BlockPos second=blockItem.getBlock() instanceof BedBlock?dest.offset(p.getHorizontalFacing())
                    :blockItem.getBlock() instanceof DoorBlock||blockItem.getBlock() instanceof TallPlantBlock?dest.up():null;
                if(wallState(sw,dest)==1||second!=null&&wallState(sw,second)==1)return deny(p);
                if(builder(p,sw,target)){removeMarker(sw,dest);if(second!=null)removeMarker(sw,second);return ActionResult.PASS;}
                if(otherProtected(sw,dest)||wither(sw,dest)||castle(sw,dest)&&!w.getBlockState(dest).isAir()
                    ||second!=null&&(otherProtected(sw,second)||wither(sw,second)||castle(sw,second)&&!w.getBlockState(second).isAir()))return deny(p);
                if(castle(sw,dest)&&w.getBlockState(dest).isAir())pending.add(new Placement(sw,dest.toImmutable()));
                if(second!=null&&castle(sw,second)&&w.getBlockState(second).isAir())pending.add(new Placement(sw,second.toImmutable()));
                return ActionResult.PASS;
            }
            boolean changes=item instanceof BucketItem||item instanceof FlintAndSteelItem||item instanceof FireChargeItem||item instanceof ShovelItem||item instanceof HoeItem||item instanceof AxeItem;
            return changes&&(wallState(sw,target)==1||wallState(sw,adjacent)==1||!builder(p,sw,target)&&(contains(sw,target)||contains(sw,adjacent)))?deny(p):ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((p,w,hand)->{
            if(!(w instanceof ServerWorld sw)||!(p.getStackInHand(hand).getItem() instanceof BucketItem))return net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand));
            var start=p.getEyePos();var hit=w.raycast(new RaycastContext(start,start.add(p.getRotationVec(1).multiply(p.getBlockInteractionRange())),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.SOURCE_ONLY,p));
            var target=hit.getBlockPos();var adjacent=target.offset(hit.getSide());
            return wallState(sw,target)==1||wallState(sw,adjacent)==1||!builder(p,sw,target)&&(contains(sw,target)||contains(sw,adjacent))
                ?new net.minecraft.util.TypedActionResult<>(deny(p),p.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand));
        });
        ServerTickEvents.END_SERVER_TICK.register(RegionProtection::flushPlacements);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->pending.clear());
    }
}
