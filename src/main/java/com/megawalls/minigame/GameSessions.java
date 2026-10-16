package com.megawalls.minigame;

import com.megawalls.MWMod;
import com.megawalls.game.*;
import com.megawalls.player.MWPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.world.GameMode;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.util.*;

/** One membership per player; disk-backed inventory/ender chest/status profiles per game. */
public final class GameSessions {
    public static final String MW="megawalls", HOT="hot_potato", HUB="hub";
    private static final String PREFIX="mg_game_";
    public static String game(ServerPlayerEntity p) {
        for(String tag:p.getCommandTags())if(tag.startsWith(PREFIX))return tag.substring(PREFIX.length());
        return p.getCommandTags().contains("mw_player")||p.getCommandTags().contains("mw_practice")?MW:HUB;
    }
    public static boolean mw(ServerPlayerEntity p){return game(p).equals(MW);}
    public static boolean same(ServerPlayerEntity a,ServerPlayerEntity b){return game(a).equals(game(b))&&a.getServerWorld()==b.getServerWorld();}
    public static void mark(ServerPlayerEntity p,String game) {
        for(String tag:new HashSet<>(p.getCommandTags()))if(tag.startsWith(PREFIX))p.removeCommandTag(tag);
        p.addCommandTag(PREFIX+game);if(game.equals(MW))p.addCommandTag("mg_mw");else p.removeCommandTag("mg_mw");
        com.megawalls.minigame.GameDifficulty.update(p.getServer());
    }
    private static Path profile(ServerPlayerEntity p,String game){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("minigame-profiles").resolve(p.getUuidAsString()).resolve(game+".dat");}
    public static boolean checkpoint(ServerPlayerEntity p){return write(profile(p,game(p)),capture(p));}
    public static boolean write(Path path,NbtCompound n) {
        try {Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");NbtIo.writeCompressed(n,temp);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);return true;}
        catch(Exception e){MWMod.LOGGER.error("Cannot save minigame player profile {}",path,e);return false;}
    }
    public static NbtCompound read(Path path) throws java.io.IOException {return NbtIo.readCompressed(path,NbtSizeTracker.ofUnlimitedBytes());}
    public static NbtCompound capture(ServerPlayerEntity p) {
        var n=new NbtCompound();n.put("inventory",p.getInventory().writeNbt(new NbtList()));
        var pending=new NbtList();for(var stack:((com.megawalls.equipment.PendingKitOwner)p).mw$pendingKit())if(!stack.isEmpty())pending.add(stack.encode(p.getRegistryManager()));n.put("pendingKit",pending);
        n.put("ender",p.getEnderChestInventory().toNbtList(p.getRegistryManager()));n.putInt("slot",p.getInventory().selectedSlot);
        n.putInt("mode",p.interactionManager.getGameMode().getId());n.putDouble("max",p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue());
        n.putFloat("health",p.getHealth());n.putFloat("absorption",p.getAbsorptionAmount());p.getHungerManager().writeNbt(n);
        n.putInt("xpTotal",p.totalExperience);n.putInt("xpLevel",p.experienceLevel);n.putFloat("xpProgress",p.experienceProgress);
        var effects=new NbtList();for(var effect:p.getStatusEffects())effects.add(effect.writeNbt());n.put("effects",effects);
        n.putString("team",p.getScoreboardTeam()==null?"":p.getScoreboardTeam().getName());
        var tags=new NbtList();for(String tag:p.getCommandTags())if(tag.startsWith("mw_")||tag.equals("wither_dead"))tags.add(NbtString.of(tag));n.put("tags",tags);
        n.put("mwData",MWPlayerData.getServer(p).toNbt());
        n.putBoolean("glowing",p.isGlowingLocal());
        n.putBoolean("invulnerable",p.isInvulnerable());
        n.putBoolean("allowFlying",p.getAbilities().allowFlying);n.putBoolean("flying",p.getAbilities().flying);n.putFloat("flySpeed",p.getAbilities().getFlySpeed());
        return n;
    }
    public static void selectSlot(ServerPlayerEntity p,int slot){
        p.getInventory().selectedSlot=Math.clamp(slot,0,8);
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(p.getInventory().selectedSlot));
    }
    public static void restore(ServerPlayerEntity p,NbtCompound n) {
        p.getInventory().clear();p.getInventory().readNbt(n.getList("inventory",NbtElement.COMPOUND_TYPE));
        com.megawalls.equipment.EquipmentHandler.clearPending(p);
        for(var entry:n.getList("pendingKit",NbtElement.COMPOUND_TYPE))restorePendingStack(p,entry);
        p.getEnderChestInventory().clear();p.getEnderChestInventory().readNbtList(n.getList("ender",NbtElement.COMPOUND_TYPE),p.getRegistryManager());
        selectSlot(p,n.getInt("slot"));
        p.clearStatusEffects();var mode=GameMode.byId(n.getInt("mode"));p.changeGameMode(mode);
        // changeGameMode can be a no-op after a restored NBT profile. Reconcile
        // survival editing permissions and the actual client mode in that case too.
        mode.setAbilities(p.getAbilities());p.setInvulnerable(n.getBoolean("invulnerable"));
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket(net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket.GAME_MODE_CHANGED,mode.getId()));
        p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(n.contains("max")?n.getDouble("max"):20);
        for(var value:n.getList("effects",NbtElement.COMPOUND_TYPE)){var effect=StatusEffectInstance.fromNbt((NbtCompound)value);if(effect!=null)p.addStatusEffect(effect);}
        p.setGlowing(n.getBoolean("glowing"));
        p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),n.getFloat("health"))));p.setAbsorptionAmount(n.getFloat("absorption"));p.getHungerManager().readNbt(n);
        p.totalExperience=n.getInt("xpTotal");p.experienceLevel=n.getInt("xpLevel");p.experienceProgress=n.getFloat("xpProgress");
        p.getScoreboard().clearTeam(p.getName().getString());var team=p.getScoreboard().getTeam(n.getString("team"));if(team!=null)p.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);
        for(String tag:new HashSet<>(p.getCommandTags()))if(tag.startsWith("mw_")||tag.equals("wither_dead"))p.removeCommandTag(tag);
        for(var tag:n.getList("tags",NbtElement.STRING_TYPE))p.addCommandTag(tag.asString());
        MWPlayerData.fromNbt(n.getCompound("mwData")).saveServer(p);
        p.getAbilities().allowFlying=n.getBoolean("allowFlying");p.getAbilities().flying=n.getBoolean("flying")&&p.getAbilities().allowFlying;
        p.getAbilities().setFlySpeed(n.contains("flySpeed")?n.getFloat("flySpeed"):.05f);p.sendAbilitiesUpdate();
        p.extinguish();p.fallDistance=0;p.setVelocity(net.minecraft.util.math.Vec3d.ZERO);p.currentScreenHandler.syncState();
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(p.experienceProgress,p.totalExperience,p.experienceLevel));
    }
    private static NbtCompound fresh(String game) {
        var n=new NbtCompound();n.putInt("mode",(game.equals(HOT)||game.equals(Spleef.ID))?GameMode.ADVENTURE.getId():GameMode.SURVIVAL.getId());
        n.putDouble("max",game.equals(MW)?40:20);n.putFloat("health",game.equals(MW)?40:20);n.putInt("foodLevel",20);n.putFloat("foodSaturationLevel",5);return n;
    }
    /** Check the incoming profile before switching, so a full bag never drops a required phone. */
    public static boolean canReceiveItem(ServerPlayerEntity p,String next,net.minecraft.item.Item item) {
        if(!next.matches("[a-z0-9_-]{1,32}")||next.equals("_current"))return false;
        var inventory=p.getInventory();
        if(!game(p).equals(next)){
            if(!Files.exists(profile(p,next)))return true;
            try{inventory=new net.minecraft.entity.player.PlayerInventory(p);inventory.readNbt(read(profile(p,next)).getList("inventory",NbtElement.COMPOUND_TYPE));}
            catch(Exception e){MWMod.LOGGER.error("Cannot inspect incoming minigame profile",e);return false;}
        }
        for(int i=0;i<inventory.size();i++)if(inventory.getStack(i).isOf(item))return true;
        return inventory.getEmptySlot()>=0;
    }
    private static void restorePendingStack(ServerPlayerEntity p,NbtElement n){
        net.minecraft.item.ItemStack.fromNbt(p.getRegistryManager(),n).filter(stack->com.megawalls.equipment.EquipmentHandler.isEquipmentItem(stack)&&com.megawalls.equipment.EquipmentHandler.ownedBy(p,stack)).ifPresent(stack->((com.megawalls.equipment.PendingKitOwner)p).mw$pendingKit().add(stack));
    }
    public static boolean switchTo(ServerPlayerEntity p,String next) {
        if(!next.matches("[a-z0-9_-]{1,32}")||next.equals("_current")||!p.isAlive())return false;
        if(!com.megawalls.menu.TabletopBridge.permitsSwitch(p,next))return false;
        if(com.megawalls.menu.TabletopBridge.destination(next)&&!com.megawalls.menu.TabletopBridge.canEnter(p,next))return false;
        if(GameState.score(p.getScoreboard(),"#pack","mw_internal")>0&&GameState.score(p.getScoreboard(),"#isolation","mw_internal")!=1){p.sendMessage(net.minecraft.text.Text.literal("请安装配套的玩法隔离版超级战墙数据包，再 /reload。"),false);return false;}
        if(game(p).equals(next)){if(!com.megawalls.menu.TabletopBridge.prepareInventory(p,next))return false;mark(p,next);return true;}

        NbtCompound incoming;
        try{incoming=Files.exists(profile(p,next))?read(profile(p,next)):fresh(next);}catch(Exception e){p.sendMessage(net.minecraft.text.Text.literal("目标玩法的玩家档案无法读取，切换已取消。"),false);return false;}
        p.closeHandledScreen();com.megawalls.fun.FunMode.clear(p);
        HotPotato.leave(p); // Restore the pre-round checkpoint before saving the outgoing profile.
        TntRun.leave(p);
        Spleef.leave(p);
        if(Spleef.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("决一死战赛前档案尚未恢复，切换已取消。"),false);return false;}
        PotDuel.leave(p);
        if(PotDuel.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("Pot PVP 赛前档案尚未恢复，切换已取消。"),false);return false;}
        BedWars.leave(p);
        if(BedWars.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("起床战争赛前档案尚未恢复，切换已取消。"),false);return false;}
        ClassicDuel.leave(p);
        if(ClassicDuel.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("UHC PvP 赛前档案尚未恢复，切换已取消。"),false);return false;}
        if(TntRun.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("TNT 跑酷赛前档案尚未恢复，切换已取消。"),false);return false;}
        if(HotPotato.hasRecovery(p)){p.sendMessage(net.minecraft.text.Text.literal("赛前档案尚未恢复，切换已取消；请联系管理员检查备份。"),false);return false;}
        if(mw(p))MatchFlow.depart(p);
        LobbyItems.leaving(p);
        if(!write(profile(p,game(p)),capture(p))){p.sendMessage(net.minecraft.text.Text.literal("玩家档案保存失败，切换已取消。"),false);return false;}
        var selection=new NbtCompound();selection.putString("game",next);
        if(!write(profile(p,"_current"),selection))return false;
        com.megawalls.equipment.EquipmentHandler.clearPending(p);
        com.megawalls.skill.SkillManager.forget(p.getUuid());com.megawalls.passive.LegacyBattle.forget(p.getUuid());com.megawalls.skill.Summons.forget(p.getUuid());
        PrivateSidebar.clear(p);mark(p,next);restore(p,incoming);
        if(!com.megawalls.menu.TabletopBridge.prepareInventory(p,next))return false;
        p.removeCommandTag("mw_ready");p.removeCommandTag("mw_practice");
        p.setCameraEntity(p);
        if(!next.equals(MW)&&p.getScoreboardTeam()!=null&&(MatchTeams.ALL.contains(p.getScoreboardTeam().getName())||p.getScoreboardTeam().getName().startsWith("bw_")))p.getScoreboard().clearTeam(p.getName().getString());
        if(next.equals(MW)){p.addCommandTag("mg_mw");PlayerVitals.health(p);}else{
            for(String tag:new HashSet<>(p.getCommandTags()))if(tag.startsWith("mw_")||tag.equals("wither_dead"))p.removeCommandTag(tag);
            new MWPlayerData().saveServer(p);
        }
        LobbyItems.sync(p);
        com.megawalls.skill.SkillManager.syncToClient(p);com.megawalls.nametag.NameTagHandler.updateNameTag(p);return true;
    }
    public static void joined(ServerPlayerEntity p){
        String current=game(p);
        try {if(Files.exists(profile(p,"_current"))){String saved=read(profile(p,"_current")).getString("game");if(saved.matches("[a-z0-9_-]{1,32}")&&!saved.equals(current)){current=saved;mark(p,current);restore(p,Files.exists(profile(p,current))?read(profile(p,current)):fresh(current));}}}
        catch(Exception e){MWMod.LOGGER.error("Cannot read current game profile",e);}
        mark(p,current);if(HotPotato.hasRecovery(p)){mark(p,HOT);HotPotato.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(Spleef.hasRecovery(p)){mark(p,Spleef.ID);Spleef.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(game(p).equals(Spleef.ID))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(TntRun.hasRecovery(p)){mark(p,TntRun.ID);TntRun.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(PotDuel.hasRecovery(p)){mark(p,PotDuel.ID);PotDuel.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(game(p).equals(PotDuel.ID))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(BedWars.hasRecovery(p)){mark(p,BedWars.ID);BedWars.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(game(p).equals(BedWars.ID))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(ClassicDuel.hasRecovery(p)){mark(p,ClassicDuel.ID);ClassicDuel.recover(p);com.megawalls.menu.HubMenu.travel(p,HUB);}
        if(game(p).equals(ClassicDuel.ID))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(game(p).equals(TntRun.ID))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(game(p).equals(HOT))com.megawalls.menu.HubMenu.travel(p,HUB);
        if(!mw(p))new MWPlayerData().saveServer(p);
    }
}
