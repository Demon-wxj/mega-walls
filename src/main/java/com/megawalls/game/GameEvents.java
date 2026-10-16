package com.megawalls.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GameEvents {
    private static final java.util.Map<java.util.UUID,Integer> spawnShield=new java.util.HashMap<>();
    private record RespawnTeleport(ServerPlayerEntity player, net.minecraft.server.network.ServerPlayNetworkHandler connection,
                                   int matchId, int roster, String point, int queuedTick) {}
    private static final java.util.Map<java.util.UUID,RespawnTeleport> respawnTeleports=new java.util.HashMap<>();
    private record CoreHit(int tick, float amount) {}
    private static final java.util.Map<java.util.UUID,CoreHit> coreHits=new java.util.HashMap<>();
    public static void clearRuntime() { spawnShield.clear(); coreHits.clear(); respawnTeleports.clear(); }
    private static void completeRespawnTeleports(net.minecraft.server.MinecraftServer server) {
        var iterator=respawnTeleports.values().iterator();
        while(iterator.hasNext()) {
            var pending=iterator.next();var player=pending.player();
            if(player.getServer()!=server||server.getTicks()<=pending.queuedTick())continue;
            iterator.remove();
            // AFTER_RESPAWN runs before onClientStatus assigns connection.player to the new entity.
            // A later tick also lets reset, disconnect, a second death or leaving this match cancel the warp.
            if(server.getPlayerManager().getPlayer(player.getUuid())!=player||player.isDisconnected()
                    ||player.networkHandler!=pending.connection()||pending.connection().player!=player
                    ||!player.isAlive()||player.isSpectator()||!com.megawalls.minigame.GameSessions.mw(player)
                    ||!MatchFlow.running(server)||MatchFlow.matchId(server)!=pending.matchId()
                    ||MatchFlow.roster(player)!=pending.roster()||pending.roster()<=0
                    ||player.getCommandTags().contains("mw_eliminated")||player.getScoreboardTeam()==null
                    ||!(player.getScoreboardTeam().getName()+"spawn").equals(pending.point())
                    ||MapConfig.get(server,pending.point()+"_set")!=1)continue;
            MapConfig.teleport(player,pending.point());
            var destination=MapConfig.point(server,pending.point());
            if(player.getServerWorld()==server.getOverworld()
                    &&player.getPos().squaredDistanceTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5)<.01)
                spawnShield.put(player.getUuid(),server.getTicks()+60);
        }
    }
    /** Negative status effects honor the same participation and respawn immunity as damage. */
    public static boolean canControl(ServerPlayerEntity caster,ServerPlayerEntity target) {
        return GameState.enemy(caster,target)&&GameState.phase(caster)!=1
                &&!target.getAbilities().invulnerable
                &&!target.isInvulnerableTo(target.getDamageSources().magic())
                &&((com.megawalls.mixin.MWPlayerProtectionAccessor)target).mw$getJoinInvulnerabilityTicks()<=0
                &&spawnShield.getOrDefault(target.getUuid(),0)<=target.getServer().getTicks();
    }
    private static int coreDamage(WitherEntity core, float amount) {
        int now=core.getServer().getTicks();var last=coreHits.get(core.getUuid());
        if(last!=null&&now-last.tick()<10){if(amount<=last.amount())return 0;coreHits.put(core.getUuid(),new CoreHit(last.tick(),amount));return Math.max(0,Math.round(amount)-Math.round(last.amount()));}
        coreHits.put(core.getUuid(),new CoreHit(now,amount));return Math.max(1,Math.round(amount));
    }
    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,damage,blocked)->{
            if(entity instanceof ServerPlayerEntity p&&com.megawalls.minigame.GameSessions.mw(p)) {
                float lost=DamageResult.loss(p,source);
                if(!blocked)CombatCredit.record(p,source,lost);
            }
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) -> {
            for (String tag : oldPlayer.getCommandTags()) {
                if (tag.startsWith("mw_") || tag.equals("wither_dead")) player.addCommandTag(tag);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if(entity instanceof ServerPlayerEntity&&source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL))return true;
            if(entity instanceof ServerPlayerEntity victim&&GameState.participant(victim)&&!GameState.isPractice(victim)){
                int phase=GameState.phase(victim);
                if(phase==4||victim.getCommandTags().contains("mw_eliminated"))return false;
                if(phase==1&&(source.getAttacker() instanceof ServerPlayerEntity||com.megawalls.skill.Summons.owner(source.getAttacker())!=null))return false;
                if(source.getAttacker() instanceof ServerPlayerEntity attacker&&phase>=2&&GameState.enemy(attacker,victim))spawnShield.remove(attacker.getUuid());
                if(spawnShield.getOrDefault(victim.getUuid(),0)>victim.getServer().getTicks())return false;
            }
            if(source.getAttacker() instanceof WitherEntity core && MatchTeams.core(core)!=null && entity instanceof ServerPlayerEntity victim)
                return com.megawalls.wither.WitherDefense.enemy(core,victim);
            var summonOwner=com.megawalls.skill.Summons.owner(source.getAttacker());
            if(summonOwner!=null&&entity instanceof ServerPlayerEntity other)return com.megawalls.skill.Summons.enemy(summonOwner,other);
            if (entity instanceof WitherEntity wither) {
                String team = MatchTeams.core(wither);
                if (team == null) return true;
                if (source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) return true;
                if (source.getAttacker() instanceof ServerPlayerEntity attacker
                        && GameState.active(attacker) && GameState.phase(attacker) == 2
                        && attacker.getServerWorld()==wither.getWorld()
                        && GameState.participant(attacker) && !attacker.getScoreboardTeam().getName().equals(team)
                        && source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)
                        && amount > 0 && Float.isFinite(amount)) {
                    var board = attacker.getScoreboard();
                    var objective = board.getNullableObjective("mw_wither_" + team);
                    if (objective != null) {
                        var hp = board.getOrCreateScore(ScoreHolder.fromName("#global"), objective);
                        if(hp.getScore()>0){
                            int damage=Math.min(hp.getScore(),coreDamage(wither,amount));
                            if(damage>0){hp.setScore(hp.getScore()-damage);com.megawalls.wither.WitherDefense.onHit(wither,attacker,damage,hp.getScore());}
                        }
                    }
                }
                // Core health lives only in the scoreboard; vanilla health cannot desynchronize it.
                return false;
            }
            if (entity instanceof ServerPlayerEntity victim && GameState.participant(victim)
                    && source.getAttacker() instanceof ServerPlayerEntity attacker) {
                return (GameState.enemy(attacker, victim)||com.megawalls.skill.TrueDamage.isDeathImpact(source,victim)) && (GameState.phase(victim) >= 2
                        || GameState.isPractice(attacker) && GameState.isPractice(victim));
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            DamageResult.loss(player,source);
            com.megawalls.skill.SkillManager.forget(player.getUuid());
            if (!GameState.participant(player)) return;
            int phase = GameState.phase(player);
            if (phase < 1 || phase > 3) return;
            boolean eliminated = phase == 3 || GameState.score(player.getScoreboard(), "#global",
                    "mw_wither_" + player.getScoreboardTeam().getName()) <= 0;
            if (eliminated) { player.addCommandTag("wither_dead"); player.addCommandTag("mw_eliminated"); }
            if(eliminated)MatchFlow.eliminate(player);
            run(player, "megawalls:system/on_death");
            var killer=CombatCredit.resolve(player,source,eliminated);
            if (killer != null && killer != player
                    && GameState.participant(killer) && !killer.isTeammate(player)) {
                String objectiveName = eliminated ? "mw_final_kills" : "mw_kills";
                var objective = killer.getScoreboard().getNullableObjective(objectiveName);
                if (objective != null) {
                    var score = killer.getScoreboard().getOrCreateScore(killer, objective);
                    score.setScore(score.getScore() + 1);
                }
                com.megawalls.passive.PassiveHandler.onPlayerKill(killer, player);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (alive || !com.megawalls.minigame.GameSessions.mw(player)) return;
            if (!GameState.participant(player) && com.megawalls.player.MWPlayerData.getServer(player).getClassId() <= 0) return;
            int phase = GameState.phase(player);
            var resetData = com.megawalls.player.MWPlayerData.getServer(player);
            com.megawalls.equipment.EquipmentHandler.clearPending(player);
            resetData.setEnergy(0);resetData.setSkillCooldown(0);resetData.resetEpsGate();
            if (phase == 0) {
                // Practice/lobby deaths also restore the selected kit. Preserve vanilla-retained ordinary items.
                var ordinary=new java.util.ArrayList<net.minecraft.item.ItemStack>();
                for(int i=0;i<player.getInventory().size();i++){
                    var stack=player.getInventory().getStack(i);
                    if(!stack.isEmpty()&&!com.megawalls.equipment.EquipmentHandler.isEquipmentItem(stack))ordinary.add(stack.copy());
                }
                if(player.getServer().getCommandFunctionManager().getFunction(net.minecraft.util.Identifier.of("megawalls","system/finalize_kit")).isPresent()){
                    run(player,"megawalls:system/finalize_kit");
                    for(var stack:ordinary)player.giveItemStack(stack);
                    com.megawalls.equipment.EquipmentHandler.bind(player);
                }
                com.megawalls.skill.SkillManager.syncToClient(player);return;
            }
            if (phase == 4) {player.getInventory().clear();player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);com.megawalls.skill.SkillManager.syncToClient(player);return;}
            if (phase < 1 || phase > 3) return;
            if (oldPlayer.getCommandTags().contains("mw_eliminated")) {
                player.addCommandTag("mw_eliminated"); player.addCommandTag("wither_dead");
            }
            if(GameState.score(player.getScoreboard(),"#pack","mw_internal")>=8){
                if(player.getCommandTags().contains("mw_eliminated")){player.getInventory().clear();player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);com.megawalls.skill.SkillManager.syncToClient(player);return;}
                run(player,"megawalls:system/finalize_kit");
                if(player.getScoreboardTeam()!=null)respawnTeleports.put(player.getUuid(),new RespawnTeleport(
                        player,player.networkHandler,MatchFlow.matchId(player.getServer()),MatchFlow.roster(player),
                        player.getScoreboardTeam().getName()+"spawn",player.getServer().getTicks()));
            }else run(player, "megawalls:system/respawn");
            com.megawalls.passive.EnderRetention.restore(player);
            com.megawalls.equipment.EquipmentHandler.bind(player);
            player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);player.getHungerManager().setSaturationLevel(5);
            MatchFlow.giveNightVision(player);
            player.currentScreenHandler.syncState();
            var data = com.megawalls.player.MWPlayerData.getServer(player);
            data.setEnergy(0); data.setSkillCooldown(0);
            com.megawalls.skill.SkillManager.syncToClient(player);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(GameEvents::completeRespawnTeleports);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{clearRuntime();CombatCredit.clear();});
    }
    private static void run(ServerPlayerEntity player, String function) {
        player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2), "function " + function);
    }
}
