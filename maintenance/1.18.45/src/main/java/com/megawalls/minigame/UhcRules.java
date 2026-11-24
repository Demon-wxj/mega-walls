package com.megawalls.minigame;
import com.megawalls.game.RegionProtection;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.injection.callback.*;
public class UhcRules {
    public int spawnGuard=2;
    public boolean flowWater=true,flowLava=true;
    public static boolean valid(boolean original,Object config){return original&&((UhcRules)config).spawnGuard>=1&&((UhcRules)config).spawnGuard<=5;}
    public static boolean guarded(ServerWorld w,BlockPos pos){
        var c=ArenaRuleAccess.uhc();if(c==null||!ClassicDuel.inside(w,pos))return false;
        int r=((UhcRules)(Object)c).spawnGuard;return near(c.spawn1,pos,r)||near(c.spawn2,pos,r);
    }
    private static boolean near(ClassicDuel.Point p,BlockPos q,int r){
        if(p==null)return false;int y=MathHelper.floor(p.y());
        return q.getY()>=y-1&&q.getY()<=y+2&&Math.abs(q.getX()-MathHelper.floor(p.x()))<=r&&Math.abs(q.getZ()-MathHelper.floor(p.z()))<=r;
    }
    public static boolean editable(boolean original,ServerWorld w,BlockPos p){return original&&!guarded(w,p);}
    public static void place(ItemPlacementContext ctx,CallbackInfoReturnable<Boolean> cb){
        if(ctx.getWorld() instanceof ServerWorld w&&guarded(w,ctx.getBlockPos())){warn(ctx.getPlayer());cb.setReturnValue(false);}
    }
    private static void warn(PlayerEntity p){if(p instanceof ServerPlayerEntity sp)sp.sendMessage(Text.literal("出生保护区内不能放方块或倒桶。"),true);}
    public static void bucket(Object bucket,PlayerEntity player,World world,BlockPos p,CallbackInfoReturnable<Boolean> cb){
        if(!(world instanceof ServerWorld w))return;
        if(guarded(w,p)){warn(player);cb.setReturnValue(false);return;}
        if(cb.isCancelled()||!DuelArena.contains(w,p)||!ClassicDuel.running())return;
        var fluid=bucket==Items.WATER_BUCKET?Fluids.WATER:bucket==Items.LAVA_BUCKET?Fluids.LAVA:null;
        if(fluid!=null)w.scheduleFluidTick(p,fluid,fluid.getTickRate(w)); // Deduplicated by vanilla; also wakes a previously stationary source.
    }
    public static void flow(WorldAccess world,BlockPos pos,Direction dir,FluidState fluid,CallbackInfo cb){
        if(!(world instanceof ServerWorld w))return;BlockPos from=pos.offset(dir.getOpposite());
        if(DuelArena.contains(w,pos)||DuelArena.contains(w,from)){
            var c=(UhcRules)(Object)ArenaRuleAccess.uhc();
            if(!DuelArena.allowFlow(w,from,pos)||guarded(w,from)||guarded(w,pos)||(fluid.isIn(net.minecraft.registry.tag.FluidTags.WATER)&&!c.flowWater)||(fluid.isIn(net.minecraft.registry.tag.FluidTags.LAVA)&&!c.flowLava))cb.cancel();
        }else if(guarded(w,pos)||RegionProtection.contains(w,pos)||BedWars.mappedAt(w,pos)||PotArena.contains(w,pos))cb.cancel();
    }
}
