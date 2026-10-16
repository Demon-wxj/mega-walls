package com.megawalls.minigame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
public final class Isolation {
    /** Both collision pickup and optional direct-pickup packets use this authority. */
    public static boolean pickup(ServerPlayerEntity p,net.minecraft.entity.ItemEntity item){
        if(!p.isAlive()||p.isSpectator()||!item.isAlive()||p.getWorld()!=item.getWorld()||LobbyItems.isBound(item.getStack()))return false;
        String game=GameSessions.game(p);
        var data=item.getStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
        String origin=data.getString("mg_item_game");
        if(!origin.isEmpty()&&!origin.equals(game))return false;
        for(String tag:item.getCommandTags())if(tag.startsWith("mg_item_")&&!tag.substring(8).equals(game))return false;
        if(!BedWars.pickup(p,item))return false;
        // Creative editing can collect ordinary items without joining the active match.
        if(p.isCreative())return true;
        if(LobbyItems.running(game)&&!LobbyItems.roundPlayer(p))return false;
        if(inRound(p)||PotDuel.inRound(p))return false;
        return !ClassicDuel.inRound(p)||ClassicDuel.fighter(p)&&item.getCommandTags().contains("mg_item_"+ClassicDuel.ID);
    }
    /** Stamp before an alternate drop is spawned, or after vanilla creates its entity. */
    public static void dropped(ServerPlayerEntity p,net.minecraft.entity.ItemEntity item){
        if(item==null)return;
        if(LobbyItems.isBound(item.getStack())||noLoot(p)&&!p.isCreative()){item.discard();return;}
        if(BedWars.inRound(p)&&BedWars.member(p))BedWars.stamp(item);
        String game=GameSessions.game(p);item.addCommandTag("mg_item_"+game);
        var stack=item.getStack();var data=stack.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
        data.putString("mg_item_game",game);stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));
    }
    /** Waiting games use vanilla interaction permissions for players who are not spectating. */
    public static boolean editor(ServerPlayerEntity p,String game){return !p.isSpectator()&&GameSessions.game(p).equals(game)&&switch(game){case ClassicDuel.ID->ClassicDuel.stage()==ClassicDuel.Stage.WAITING;case PotDuel.ID->PotDuel.stage()==PotDuel.Stage.WAITING;case BedWars.ID->BedWars.stage()==BedWars.Stage.WAITING;case Spleef.ID->Spleef.stage()==Spleef.Stage.WAITING;default->false;};}
    public static boolean inRound(ServerPlayerEntity p){return HotPotato.inRound(p)||TntRun.inRound(p)||Spleef.inRound(p);}
    public static boolean noLoot(ServerPlayerEntity p){return inRound(p)||ClassicDuel.inRound(p)||PotDuel.inRound(p);}
    public static ServerPlayerEntity owner(Entity entity){
        if(entity instanceof ServerPlayerEntity p)return p;
        if(entity instanceof net.minecraft.entity.projectile.ProjectileEntity projectile&&projectile.getOwner() instanceof ServerPlayerEntity p)return p;
        if(entity instanceof net.minecraft.entity.AreaEffectCloudEntity cloud&&cloud.getOwner() instanceof ServerPlayerEntity p)return p;
        if(entity instanceof net.minecraft.entity.TntEntity tnt&&tnt.getOwner() instanceof ServerPlayerEntity p)return p;
        return com.megawalls.skill.Summons.owner(entity);
    }
    public static boolean permits(Entity source,ServerPlayerEntity target){
        if(source==null)return true;
        // A projectile keeps its original game and round even after its owner switches games.
        for(String tag:source.getCommandTags())if(tag.startsWith("mg_source_")){
            if(tag.equals("mg_source_pot_pvp"))return source.getWorld()==target.getWorld()&&PotDuel.permits(source,target);
            if(tag.equals("mg_source_bedwars"))return BedWars.permits(source,target);
            return source.getWorld()==target.getWorld()&&tag.substring(10).equals(GameSessions.game(target));
        }
        var p=owner(source);
        if(p!=null&&PotDuel.member(p))return PotDuel.fighter(p)&&PotDuel.fighter(target)&&GameSessions.same(p,target);
        if(p!=null&&BedWars.member(p))return BedWars.fighter(p)&&BedWars.fighter(target)&&GameSessions.same(p,target)&&(p==target||!BedWars.sameTeam(p,target));
        return p==null||GameSessions.same(p,target);
    }
    public static void register(){
        // Tagging consumes the attack before vanilla or combat Mods can apply damage/knockback.
        AttackEntityCallback.EVENT.register((p,w,hand,e,hit)->{
            if(!(p instanceof ServerPlayerEntity sp))return ActionResult.PASS;
            if(HotPotato.member(sp)||e instanceof ServerPlayerEntity target&&HotPotato.member(target)){
                if(hand==net.minecraft.util.Hand.MAIN_HAND&&e instanceof ServerPlayerEntity target)HotPotato.attack(sp,target);
                return ActionResult.FAIL;
            }
            return Spleef.member(sp)||e instanceof ServerPlayerEntity target&&Spleef.member(target)?ActionResult.FAIL:ActionResult.PASS;
        });
        // Reading an existing lectern book does not edit the protected arena. Keep
        // vanilla's block handler (including Scholar) instead of duplicating its UI.
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(p.isSpectator()||p.isSneaking()||hand!=net.minecraft.util.Hand.MAIN_HAND)return ActionResult.PASS;
            var state=w.getBlockState(hit.getBlockPos());
            if(!state.isOf(net.minecraft.block.Blocks.LECTERN)||!state.get(net.minecraft.block.LecternBlock.HAS_BOOK))return ActionResult.PASS;
            if(w.isClient())return ActionResult.SUCCESS;
            return state.onUse(w,p,hit);
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,s)->GameSessions.joined(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register((old,p,alive)->GameSessions.mark(p,GameSessions.game(old)));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->!(entity instanceof ServerPlayerEntity p)
            ||permits(source.getSource(),p)&&permits(source.getAttacker(),p));
        ServerEntityEvents.ENTITY_LOAD.register((entity,w)->{if(!(entity instanceof ServerPlayerEntity)&&entity.getCommandTags().stream().noneMatch(t->t.startsWith("mg_source_"))){var p=owner(entity);if(p!=null){entity.addCommandTag("mg_source_"+GameSessions.game(p));if(PotDuel.fighter(p))PotDuel.stamp(entity);}}});
        ServerEntityEvents.ENTITY_LOAD.register((entity,w)->{if(ClassicDuel.running()&&entity instanceof net.minecraft.entity.ItemEntity item&&DuelArena.contains(w,item.getBlockPos())){item.addCommandTag("mg_item_"+ClassicDuel.ID);var stack=item.getStack();var data=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);var n=data==null?new net.minecraft.nbt.NbtCompound():data.copyNbt();n.putString("mg_item_game",ClassicDuel.ID);stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(n));}});
        AttackEntityCallback.EVENT.register((p,w,hand,e,hit)->p instanceof ServerPlayerEntity sp&&PotDuel.member(sp)&&(!(e instanceof ServerPlayerEntity target)||!PotDuel.paired(sp,target))?ActionResult.FAIL:ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,state,be)->{
            if(!(p instanceof ServerPlayerEntity sp))return true;
            if(editor(sp,PotDuel.ID)&&!BedArena.contains(sp.getServerWorld(),pos)&&!DuelArena.contains(sp.getServerWorld(),pos))return true;
            if(PotDuel.member(sp)){PotDuel.breakPlaced(sp,pos);return false;}
            return !PotDuel.protectedAt(sp.getServerWorld(),pos);
        });
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(!(p instanceof ServerPlayerEntity sp)||p.getStackInHand(hand).isOf(net.minecraft.item.Items.NETHER_STAR))return ActionResult.PASS;
            if(editor(sp,PotDuel.ID))return ActionResult.PASS;
            if(!PotDuel.member(sp)&&!PotDuel.protectedAt(sp.getServerWorld(),hit.getBlockPos()))return ActionResult.PASS;
            var item=p.getStackInHand(hand).getItem();
            // PASS on ordinary ground lets vanilla continue to using a potion or pearl in hand.
            if(item instanceof net.minecraft.item.BlockItem block&&PotDuel.fighter(sp)&&hand==net.minecraft.util.Hand.MAIN_HAND){
                var context=new net.minecraft.item.ItemPlacementContext(sp,hand,p.getStackInHand(hand),hit);
                var target=context.getBlockPos();
                var second=block.getBlock() instanceof net.minecraft.block.BedBlock?target.offset(p.getHorizontalFacing()):block.getBlock() instanceof net.minecraft.block.DoorBlock||block.getBlock() instanceof net.minecraft.block.TallPlantBlock?target.up():null;
                return PotDuel.inside(sp.getServerWorld(),target)&&ArenaBlocks.replaceableGround(w.getBlockState(target))&&(second==null||PotDuel.inside(sp.getServerWorld(),second)&&ArenaBlocks.replaceableGround(w.getBlockState(second)))
                    ?PotArena.authorized(()->block.place(context)):ActionResult.FAIL;
            }
            return PotDuel.fighter(sp)&&!w.getBlockState(hit.getBlockPos()).hasBlockEntity()&&!(item instanceof net.minecraft.item.BucketItem)?ActionResult.PASS:ActionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((p,w,hand,e,hit)->p instanceof ServerPlayerEntity sp&&PotDuel.member(sp)&&!editor(sp,PotDuel.ID)?ActionResult.FAIL:ActionResult.PASS);
        UseItemCallback.EVENT.register((p,w,hand)->p instanceof ServerPlayerEntity sp&&PotDuel.member(sp)&&!editor(sp,PotDuel.ID)&&!LobbyItems.isControl(p.getStackInHand(hand))&&!p.getStackInHand(hand).isOf(net.minecraft.item.Items.NETHER_STAR)&&(!PotDuel.fighter(sp)||hand==net.minecraft.util.Hand.OFF_HAND)?net.minecraft.util.TypedActionResult.fail(p.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand)));
        AttackEntityCallback.EVENT.register((p,w,hand,e,hit)->p instanceof ServerPlayerEntity sp&&ClassicDuel.member(sp)&&(!(e instanceof ServerPlayerEntity target)||!ClassicDuel.paired(sp,target))?ActionResult.FAIL:ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,state,be)->{
            if(!(p instanceof ServerPlayerEntity sp))return true;
            if(Spleef.member(sp)&&!editor(sp,Spleef.ID)){Spleef.dig(sp,pos);return false;}
            return !inRound(sp)&&!TntRun.protectedAt(sp.getServerWorld(),pos)&&!Spleef.protectedAt(sp.getServerWorld(),pos)&&!(HotPotato.stage()==HotPotato.Stage.RUNNING&&HotPotato.inside(sp.getServerWorld(),pos));
        });
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,state,be)->!(p instanceof ServerPlayerEntity sp)||editor(sp,ClassicDuel.ID)||!(ClassicDuel.member(sp)||DuelArena.contains(sp.getServerWorld(),pos)||DuelArena.shell(sp.getServerWorld(),pos))||ClassicDuel.fighter(sp)&&DuelArena.editable(sp.getServerWorld(),pos));
        UseBlockCallback.EVENT.register((p,w,hand,hit)->{
            if(p.getStackInHand(hand).isOf(net.minecraft.item.Items.NETHER_STAR))return ActionResult.PASS;
            if(p instanceof ServerPlayerEntity sp&&!editor(sp,ClassicDuel.ID)&&(ClassicDuel.member(sp)||DuelArena.contains(sp.getServerWorld(),hit.getBlockPos())||DuelArena.contains(sp.getServerWorld(),hit.getBlockPos().offset(hit.getSide())))){
                if(!ClassicDuel.fighter(sp))return ActionResult.FAIL;
                var stack=p.getStackInHand(hand);
                if(stack.getItem() instanceof net.minecraft.item.BlockItem block){
                    var context=new net.minecraft.item.ItemPlacementContext(sp,hand,stack,hit);var target=context.getBlockPos();
                    var second=block.getBlock() instanceof net.minecraft.block.BedBlock?target.offset(p.getHorizontalFacing()):block.getBlock() instanceof net.minecraft.block.DoorBlock||block.getBlock() instanceof net.minecraft.block.TallPlantBlock?target.up():null;
                    if(!DuelArena.editable(sp.getServerWorld(),target)||second!=null&&!DuelArena.editable(sp.getServerWorld(),second))return ActionResult.FAIL;
                }else if(!DuelArena.contains(sp.getServerWorld(),hit.getBlockPos()))return ActionResult.FAIL;
            }
            if(p instanceof ServerPlayerEntity sp&&Spleef.canThrow(sp,hand)){
                // Throw when aiming at the platform without opening a chest or toggling a block.
                var result=p.getStackInHand(hand).getItem().use(w,p,hand);
                p.setStackInHand(hand,result.getValue());
                return result.getResult();
            }
            if(p instanceof ServerPlayerEntity sp&&(inRound(sp)||Spleef.protectedAt(sp.getServerWorld(),hit.getBlockPos())||Spleef.protectedAt(sp.getServerWorld(),hit.getBlockPos().offset(hit.getSide()))||TntRun.protectedAt(sp.getServerWorld(),hit.getBlockPos())||HotPotato.stage()==HotPotato.Stage.RUNNING&&HotPotato.inside(sp.getServerWorld(),hit.getBlockPos())))return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((p,w,hand)->p instanceof ServerPlayerEntity sp&&inRound(sp)&&!Spleef.canThrow(sp,hand)&&!LobbyItems.isControl(p.getStackInHand(hand))&&!p.getStackInHand(hand).isOf(net.minecraft.item.Items.NETHER_STAR)?net.minecraft.util.TypedActionResult.fail(p.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand)));
        UseItemCallback.EVENT.register((p,w,hand)->p instanceof ServerPlayerEntity sp&&ClassicDuel.member(sp)&&!editor(sp,ClassicDuel.ID)&&(hand==net.minecraft.util.Hand.OFF_HAND||ClassicDuel.paused(sp))&&!LobbyItems.isControl(p.getStackInHand(hand))&&!p.getStackInHand(hand).isOf(net.minecraft.item.Items.NETHER_STAR)?net.minecraft.util.TypedActionResult.fail(p.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand)));
        UseItemCallback.EVENT.register((p,w,hand)->{
            if(!(p instanceof ServerPlayerEntity sp)||editor(sp,ClassicDuel.ID)||!(p.getStackInHand(hand).getItem() instanceof net.minecraft.item.BucketItem))return net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand));
            var start=p.getEyePos();var hit=w.raycast(new net.minecraft.world.RaycastContext(start,start.add(p.getRotationVec(1).multiply(p.getBlockInteractionRange())),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.SOURCE_ONLY,p));
            boolean target=DuelArena.contains(sp.getServerWorld(),hit.getBlockPos());
            return (ClassicDuel.member(sp)||target)&&(!ClassicDuel.fighter(sp)||!target)?net.minecraft.util.TypedActionResult.fail(p.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(p.getStackInHand(hand));
        });
        UseEntityCallback.EVENT.register((p,w,hand,e,hit)->p instanceof ServerPlayerEntity sp&&inRound(sp)?ActionResult.FAIL:ActionResult.PASS);
        ServerLifecycleEvents.SERVER_STARTED.register(s->{
            var objective=s.getScoreboard().getNullableObjective("mw_sidebar");
            if(objective!=null)for(var slot:net.minecraft.scoreboard.ScoreboardDisplaySlot.values())if(s.getScoreboard().getObjectiveForSlot(slot)==objective)s.getScoreboard().setObjectiveSlot(slot,null);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s->PrivateSidebar.clearAll());
    }
}
