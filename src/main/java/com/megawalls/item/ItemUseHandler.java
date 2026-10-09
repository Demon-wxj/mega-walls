package com.megawalls.item;

import com.megawalls.player.MWPlayerData;
import com.megawalls.skill.SkillManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 物品右键使用处理器
 * 功能：
 * 1. 敌人指南针：右键指南针指向最近敌人
 * 2. 随身末影箱：右键末影箱物品直接打开末影箱GUI（不放置方块）
 */
public class ItemUseHandler {

    private static final java.util.Map<UUID, Long> compassCooldown = new java.util.HashMap<>();
    private static final int COMPASS_COOLDOWN_TICKS = 60;

    public static void register() {
        // 右键空气：末影箱打开GUI，指南针追踪，剑/弓触发技能
        UseItemCallback.EVENT.register((player, world, hand) -> {
            // Let the server decide the mode; client prediction must not consume a normal block use.
            if(world.isClient||player instanceof ServerPlayerEntity sp&&!com.megawalls.minigame.GameSessions.mw(sp))return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.ENDER_CHEST)) {
                if (!world.isClient) openPortableEnderChest((ServerPlayerEntity) player);
                return net.minecraft.util.TypedActionResult.success(player.getStackInHand(hand));
            }
            if (stack.isOf(Items.COMPASS)) {
                if (!world.isClient) PlayerCompass.use((ServerPlayerEntity) player, false);
                return net.minecraft.util.TypedActionResult.success(player.getStackInHand(hand));
            }
            // 职业技能改由客户端右键瞬间发送 C2S_UseSkill 网络包触发（降低延迟、避免重复）
            return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
        });

        // 右键方块：末影箱不可放置，改为打开GUI
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if(world.isClient||player instanceof ServerPlayerEntity sp&&!com.megawalls.minigame.GameSessions.mw(sp))return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if(stack.isOf(Items.COMPASS)){if(!world.isClient)PlayerCompass.use((ServerPlayerEntity)player,false);return ActionResult.SUCCESS;}
            if (stack.isOf(Items.ENDER_CHEST)) {
                if (!world.isClient) openPortableEnderChest((ServerPlayerEntity) player);
                return ActionResult.FAIL; // 取消放置
            }
            return ActionResult.PASS;
        });
    }

    /**
     * 打开随身末影箱GUI（不放置方块，9x3=27格）
     */
    private static void openPortableEnderChest(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> GenericContainerScreenHandler.createGeneric9x3(
                        syncId, playerInventory, p.getEnderChestInventory()),
                Text.literal("§5随身末影箱")
        ));
        player.playSound(net.minecraft.sound.SoundEvents.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * 指南针追踪最近敌人
     */
    /** All classes accept any sword/bow; mole additionally accepts shovels. Kit marking only controls equipment retention. */
    public static boolean isClassWeapon(ServerPlayerEntity player, ItemStack stack) {
        int classId = MWPlayerData.getServer(player).getClassId();
        if (classId < 1 || classId > 13) return false;

        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof BowItem
                || classId == 12 && stack.getItem() instanceof ShovelItem;
    }
}
