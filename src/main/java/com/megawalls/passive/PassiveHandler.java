package com.megawalls.passive;

import com.megawalls.player.MWPlayerData;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 被动技能处理器（逐步对齐；未核实参数保留当前实现）
 */
public class PassiveHandler {
    private static final Map<UUID, Integer> attackCount = new HashMap<>();
    private static final Map<UUID, Integer> comboHits = new HashMap<>();
    private static final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private static final Map<UUID, Long> natureForceTimer = new HashMap<>();

    private static final Map<String, Integer> cooldowns = new HashMap<>();
    public static int remaining(ServerPlayerEntity p,String key){return Math.max(0,cooldowns.getOrDefault(p.getUuid()+key,0)-p.getServer().getTicks());}
    public static int combo(ServerPlayerEntity p){return comboHits.getOrDefault(p.getUuid(),0);}
    public static int natureRemaining(ServerPlayerEntity p){return (int)Math.max(0,600-(p.getServer().getTicks()-natureForceTimer.getOrDefault(p.getUuid(),(long)p.getServer().getTicks())));}
    public static void forget(UUID id){cooldowns.keySet().removeIf(k->k.startsWith(id.toString()));comboHits.remove(id);natureForceTimer.remove(id);attackCount.remove(id);lastDamageTime.remove(id);processedHits.remove(id);}
    public static boolean ready(ServerPlayerEntity player, String key, int ticks) {
        String id = player.getUuid() + key; int now = player.getServer().getTicks();
        if (cooldowns.getOrDefault(id, 0) > now) return false;
        cooldowns.put(id, now + ticks); return true;
    }
    public static void clear() { cooldowns.clear(); attackCount.clear(); comboHits.clear(); lastDamageTime.clear(); natureForceTimer.clear(); processedHits.clear(); }

    private record ProcessedHit(net.minecraft.entity.damage.DamageSource source,int tick) {}
    private static final Map<UUID,ProcessedHit> processedHits=new HashMap<>();
    private static boolean legalHit(ServerPlayerEntity attacker,ServerPlayerEntity target){
        if(com.megawalls.game.GameState.enemy(attacker,target))return true;
        return !target.isAlive()&&com.megawalls.game.GameState.active(attacker)&&!attacker.isTeammate(target)
            &&attacker.getServerWorld()==target.getServerWorld()&&!attacker.getUuid().equals(target.getUuid())
            &&(com.megawalls.game.GameState.isPractice(attacker)&&com.megawalls.game.GameState.isPractice(target)
                ||com.megawalls.game.GameState.participant(attacker)&&com.megawalls.game.GameState.participant(target)
                &&Math.abs(com.megawalls.game.MatchFlow.roster(target))>0&&com.megawalls.game.GameState.phase(target)>=2&&com.megawalls.game.GameState.phase(target)<=3);
    }
    private static void successfulDamage(ServerPlayerEntity target,net.minecraft.entity.damage.DamageSource source){
        if(!com.megawalls.game.GameState.participant(target)&&!com.megawalls.game.GameState.isPractice(target))return;
        if(!com.megawalls.game.GameState.active(target)&&target.isAlive())return;
        if(com.megawalls.game.DamageResult.loss(target,source)<=0)return;
        lowHealth(target);
            comboHits.put(target.getUuid(),0);
            if(source.isOf(net.minecraft.entity.damage.DamageTypes.FALL)) LegacyBattle.finishFall(target,source);
            // Only successful enemy melee/arrow hits drive combat passives; skills do not recursively trigger them.
            if (!(source.getAttacker() instanceof ServerPlayerEntity attacker) || !legalHit(attacker,target)) return;
            boolean arrow = source.getSource() instanceof net.minecraft.entity.projectile.ArrowEntity;
            boolean melee = source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK);
            if (!melee && !arrow) return;
            int now=target.getServer().getTicks();
            var previous=processedHits.get(target.getUuid());
            if(previous!=null&&previous.source()==source&&previous.tick()==now)return;
            processedHits.put(target.getUuid(),new ProcessedHit(source,now));
            comboHits.put(target.getUuid(), 0);
            int cls = MWPlayerData.getServer(attacker).getClassId();
            com.megawalls.skill.EnergyRules.hit(attacker, arrow);
            if (MWPlayerData.getServer(target).getClassId() == 13) com.megawalls.skill.EnergyRules.gain(target, 2);
            if (cls == 2 && arrow) {
                if (ready(attacker, "agile", com.megawalls.skill.RestorationSkillRules.AGILE_DURATION+com.megawalls.skill.RestorationSkillRules.AGILE_RECOVERY)) {
                    attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, com.megawalls.skill.RestorationSkillRules.AGILE_DURATION, 0));
                    attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, com.megawalls.skill.RestorationSkillRules.AGILE_DURATION, 1));
                }
                if (attacker.getRandom().nextFloat() < .9f) {
                    attacker.giveItemStack(new ItemStack(Items.ARROW, 2)); attacker.getHungerManager().add(1, 1);
                }
            }
            if (melee && cls == 6 && attacker.getRandom().nextFloat() < .75f) attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 1));
            if (melee && cls == 8 && attacker.getRandom().nextFloat() < .2f && ready(attacker,"souleater",60)) { attacker.heal(2); attacker.getHungerManager().add(3, 1); }
            if (cls == 13 && comboHits.merge(attacker.getUuid(), 1, Integer::sum) >= 3) {
                attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
                attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
                comboHits.put(attacker.getUuid(), 0);
            }
            if(cls==10 && attacker.getRandom().nextFloat()<(arrow?.2f:.1f) && ready(attacker,"melting",20)){
                target.setFireTicks(60);attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,60,arrow?0:1));
            }
            if(!target.isAlive())return;
            int victimClass = MWPlayerData.getServer(target).getClassId();
            if (victimClass == 1 && melee && target.getRandom().nextFloat() < .375f) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 0));
            if (victimClass == 1 && arrow && target.getRandom().nextFloat() < .24f && ready(target, "berserk", 360)) {
                LegacyBattle.empower(target,.75f,100);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1));
            }
            if(victimClass==11 && target.getRandom().nextFloat()<.08f && ready(target,"animal",40)) com.megawalls.skill.Summons.spawn(target,11,400);
            if (victimClass == 9 && arrow) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
    }

    /** User rule: any actual damage, including environment; never ordinary-hit energy or offensive procs. */
    private static void lowHealth(ServerPlayerEntity target) {
        if(!com.megawalls.game.GameState.active(target))return;
        int cls=MWPlayerData.getServer(target).getClassId();
        if(cls==4&&target.getHealth()<21&&ready(target,"rejuvenate",com.megawalls.skill.RestorationSkillRules.REJUVENATE_COOLDOWN))
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,30,4));
        if(cls==7&&target.getHealth()<com.megawalls.skill.RestorationSkillRules.CREEPER_WILLPOWER_HP&&ready(target,"willpower",400)) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,200,1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,200,0));
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, damage, blocked) -> {
            if (!(entity instanceof ServerPlayerEntity target) || blocked || damage <= 0) return;
            successfulDamage(target,source);
        });

        // 挖掘方块事件
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity)) return;
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            if (!com.megawalls.game.GameState.active(sp)) return;
            int cls = MWPlayerData.getServer(sp).getClassId();
            boolean natural=MiningTreasure.claimGathering(sp,pos,state);

            // 僵尸：训练有素 - 90%挖矿/木获得急迫I 5秒
            if (cls == 1 && (state.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE) || state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) && Math.random() < 0.9) {
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 100, 0));
            }

            // 骷髅：效率 - 100%额外矿石/木头
            if (natural && cls == 2 && (state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES) || state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES) || state.isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES))) {
                var drops = net.minecraft.block.Block.getDroppedStacks(state, sp.getServerWorld(), pos, blockEntity, sp, sp.getMainHandStack());
                for (ItemStack drop : drops) sp.giveItemStack(drop.copy());
            }

            // Existing full-level rule: one ingot for each eligible natural block.
            if (natural && cls == 3) {
                if (state.isOf(net.minecraft.block.Blocks.DIRT) || state.isOf(net.minecraft.block.Blocks.SAND)
                        || state.isOf(net.minecraft.block.Blocks.GRAVEL) || state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)) {
                    sp.giveItemStack(new ItemStack(Items.IRON_INGOT));
                }
            }

            // 末影人：天然矿石/原木 36% 连挖；不额外抽取采集奖励或宝箱。
            if (natural && cls == 5 && com.megawalls.game.GameState.phase(sp)!=3 && (state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES) || state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES) || state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) && sp.getRandom().nextFloat() < 0.36) {
                for (Direction dir : Direction.values()) {
                    BlockPos near = pos.offset(dir);
                    if (world.getBlockState(near).isOf(state.getBlock()) && world.getBlockState(near).getHardness(world, near) >= 0 && world.getBlockEntity(near) == null) {
                        if (!com.megawalls.game.RegionProtection.contains(sp.getServerWorld(), near)) world.breakBlock(near, true, sp);
                    }
                }
            }

            // 海诺布的宝箱概率由 MiningTreasure 配置统一提供。

            // 苦力怕：TNT挖掘 - 100%挖煤矿获得TNT
            if (natural && cls == 7 && state.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES)) {
                sp.giveItemStack(new ItemStack(Items.TNT));
            }

            // 恐惧魔王：暗物质 - 100%挖铁矿变铁锭
            // MWSmeltingMixin replaces iron drops; do not create a second set here.

            // 铁傀儡：化木为铁 - 10%伐木获得铁块
            if (natural && cls == 9 && sp.getRandom().nextFloat() < 0.1 && state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
                sp.giveItemStack(new ItemStack(Items.IRON_BLOCK));
            }

            // 烈焰人：化石燃料 - 100%挖矿获得回复I 10秒
            if (natural && cls == 10 && state.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES) && ready(sp, "fossil", 200)) {
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
            }

            // 鼹鼠：捷径 - 20%挖铲子方块获得速度II+急迫II 4秒
            if (cls == 12 && state.isIn(net.minecraft.registry.tag.BlockTags.SHOVEL_MINEABLE) && Math.random() < 0.2) {
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 1));
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 80, 1));
            }

            if(natural && cls==12 && com.megawalls.game.GameState.phase(sp)==1 && state.isIn(net.minecraft.registry.tag.BlockTags.SHOVEL_MINEABLE) && sp.getRandom().nextFloat()<.021f){
                ItemStack food=new ItemStack(Items.COOKIE,1);
                var tag=new net.minecraft.nbt.NbtCompound();tag.putInt("mw_food_energy",4);
                food.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(tag));
                food.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("能量曲奇 (+4)"));sp.giveItemStack(food);
            }
            MiningTreasure.onBreak(sp, pos, state);

        });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity instanceof ServerPlayerEntity p))return;
            successfulDamage(p,source);
            LegacyBattle.finishFall(p,source);
            int cls=MWPlayerData.getServer(p).getClassId();
            if((cls==7||cls==3) && (com.megawalls.game.GameState.participant(p)||com.megawalls.game.GameState.isPractice(p))
                    && (com.megawalls.game.GameState.isPractice(p)||com.megawalls.game.GameState.phase(p)>=2&&com.megawalls.game.GameState.phase(p)<=3)) {
                int count=cls==3?3:1;
                for(int i=0;i<count;i++)com.megawalls.skill.Summons.spawnDeath(p,cls,400);
            }
            comboHits.remove(p.getUuid());LegacyBattle.forget(p.getUuid());
        });
    }

    /**
     * 玩家击杀时调用
     */
    public static void onPlayerKill(ServerPlayerEntity killer, LivingEntity victim) {
        int cls = MWPlayerData.getServer(killer).getClassId();

        // 海诺布：力量 - 击杀后力量加成6秒
        if (cls == 6) {
            LegacyBattle.empower(killer,.85f,120);
        }

        // 恐惧魔王：灵魂汲取 - 击杀后回复I 6秒+力量I 5秒
        if (cls == 8) {
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0));
            LegacyBattle.empower(killer,.75f,100);
        }

        // 铁傀儡：钢铁之心 - 击杀后伤害吸收II 12秒
        if (cls == 9 && ready(killer, "ironheart", com.megawalls.skill.RestorationSkillRules.IRON_HEART_COOLDOWN)) {
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 240, 1));
        }

        // 烈焰人：烈焰召唤 - 击杀100%召唤2只烈焰人
        if (cls == 10) {
            com.megawalls.skill.Summons.spawn(killer,10,400);
            com.megawalls.skill.Summons.spawn(killer,10,400);
        }

        // 当前满级规则：普通击杀 1 牛排、最终击杀 4 牛排。
        if (cls == 13) {
            killer.giveItemStack(new ItemStack(Items.COOKED_BEEF, victim.getCommandTags().contains("mw_eliminated")?4:1));
        }
    }

    /**
     * 每秒tick（自然之力等定时被动）
     */
    public static void tickSecond(ServerPlayerEntity player) {
        if (!com.megawalls.game.GameState.active(player)) return;
        int cls = MWPlayerData.getServer(player).getClassId();
        UUID uuid = player.getUuid();
        long now = player.getServer().getTicks();

        if (cls == 1 && com.megawalls.game.GameState.phase(player) == 1) player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 40, 2));
        // 猎人：自然之力 - 每30秒随机buff
        if (cls == 11) {
            long last = natureForceTimer.computeIfAbsent(uuid,id->now);
            if (now - last >= 600) {
                natureForceTimer.put(uuid, now);
                switch (player.getRandom().nextInt(com.megawalls.game.GameState.phase(player) == 1 ? 6 : 5)) {
                    case 0:
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 220, 0));
                        break;
                    case 1:
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 0));
                        break;
                    case 2:
                        LegacyBattle.empower(player,1.3f,120);
                        break;
                    case 3:
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
                        break;
                    case 4:
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 1));
                        break;
                    case 5:
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 300, 2));
                        break;
                }
            }
        }

        // 猎人：黄金眼镜 - 永久夜视
        if (cls == 11) {
            if (player.getStatusEffect(StatusEffects.NIGHT_VISION) == null) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0));
            }
        }
    }
}
