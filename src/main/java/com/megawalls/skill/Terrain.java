package com.megawalls.skill;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.BlockTags;
public final class Terrain {
    public static void blast(net.minecraft.server.network.ServerPlayerEntity player,net.minecraft.util.math.Vec3d center,double radius){
        blast(player,center,radius,Integer.MIN_VALUE);
    }
    public static void blast(net.minecraft.server.network.ServerPlayerEntity player,net.minecraft.util.math.Vec3d center,double radius,int minY){
        if(!com.megawalls.game.GameState.active(player)||com.megawalls.game.GameState.phase(player)==1)return;
        var world=player.getServerWorld();
        for(var pos:BlockPos.iterate(BlockPos.ofFloored(center.add(-radius,-radius,-radius)),BlockPos.ofFloored(center.add(radius,radius,radius))))
            if(pos.getY()>=minY&&pos.toCenterPos().squaredDistanceTo(center)<=radius*radius&&breakable(world,pos))world.breakBlock(pos,true,player);
    }
    public static boolean breakable(ServerWorld world,BlockPos pos) {
        var s=world.getBlockState(pos);
        return !com.megawalls.game.RegionProtection.contains(world,pos)&&world.isChunkLoaded(pos)&&world.getWorldBorder().contains(pos)&&world.getBlockEntity(pos)==null
            &&s.getHardness(world,pos)>=0&&s.getHardness(world,pos)<50
            &&(s.isIn(BlockTags.PICKAXE_MINEABLE)||s.isIn(BlockTags.SHOVEL_MINEABLE)||s.isIn(BlockTags.AXE_MINEABLE));
    }
}
