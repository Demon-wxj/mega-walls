package com.megawalls.game;

import com.megawalls.MWMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Persistent tombstones for round entities that may be in unloaded chunks or other dimensions. */
public final class TerrainEntityCleanup extends PersistentState {
    private static final String ID = "megawalls-terrain-cleanup";
    private static final Type<TerrainEntityCleanup> TYPE = new Type<>(TerrainEntityCleanup::new,
        TerrainEntityCleanup::read, null);
    private static final Set<MinecraftServer> REPORTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;
    private final Set<UUID> pending = new HashSet<>();
    // Keep the tombstone until the discarded entity's absence has reached its dimension's disk save.
    private final Map<UUID, ServerWorld> discarded = new HashMap<>();
    private boolean loadedFromDisk;
    private boolean checkedDisk;
    private String readError;

    private TerrainEntityCleanup() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            try { get(world.getServer()).discard(entity, world); }
            catch (IOException e) { report(world.getServer(), e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Only a real tombstone hit queues work. Merge hits into one save per affected dimension.
            if (server.getTicks() % 20 != 0) return;
            try {
                var state = get(server);
                if (!state.discarded.isEmpty()) state.flush(server, Set.of());
            } catch (IOException e) { report(server, e); }
        });
    }

    public static void ensureAvailable(MinecraftServer server) throws IOException { get(server); }

    public static int pendingCount(MinecraftServer server) throws IOException { return get(server).pending.size(); }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(ID + ".dat");
    }

    private static TerrainEntityCleanup get(MinecraftServer server) throws IOException {
        var state = server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
        if (state.readError != null) throw new IOException(state.readError);
        // PersistentStateManager logs unreadable files and returns a new state. Never overwrite that evidence.
        if (!state.checkedDisk) {
            if (!state.loadedFromDisk && Files.exists(file(server))) {
                state.readError = "已有本局实体清理记录无法读取，原文件保留：" + file(server);
                throw new IOException(state.readError);
            }
            state.checkedDisk = true;
        }
        return state;
    }

    private static TerrainEntityCleanup read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        var state = new TerrainEntityCleanup();
        state.loadedFromDisk = true;
        try {
            if (nbt.getInt("version") != 1 || !(nbt.get("pending") instanceof NbtList list)
                    || !list.isEmpty() && list.getHeldType() != NbtElement.COMPOUND_TYPE)
                throw new IllegalArgumentException("不支持的实体清理记录格式");
            for (var entry : list) {
                var row = (NbtCompound) entry;
                if (!row.containsUuid("uuid") || !state.pending.add(row.getUuid("uuid")))
                    throw new IllegalArgumentException("无效或重复的待清理实体 UUID");
            }
        } catch (RuntimeException e) {
            state.readError = "本局实体清理记录损坏，原文件保留：" + e.getMessage();
        }
        return state;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putInt("version", 1);
        var list = new NbtList();
        for (var uuid : pending) { var row = new NbtCompound(); row.putUuid("uuid", uuid); list.add(row); }
        nbt.put("pending", list);
        return nbt;
    }

    /** Archive is permitted only after all UUIDs are durable, including those not currently loaded. */
    public static void finishRestore(MinecraftServer server, ServerWorld terrainWorld, Collection<UUID> roundEntities)
            throws IOException {
        finishRestore(server, terrainWorld, roundEntities, Map.of());
    }

    public static void finishRestore(MinecraftServer server, ServerWorld terrainWorld, Collection<UUID> roundEntities,
            Map<UUID, ServerWorld> confirmedDestroyed) throws IOException {
        var state = get(server);
        if (state.pending.addAll(roundEntities)) state.markDirty();
        state.saveDurably(file(server), server.getRegistryManager());
        for (var world : server.getWorlds()) {
            var matches = new ArrayList<Entity>();
            for (var entity : world.iterateEntities()) if (state.pending.contains(entity.getUuid())) matches.add(entity);
            for (var entity : matches) state.discard(entity, world);
        }
        var saveWorlds = new HashSet<ServerWorld>();
        saveWorlds.add(terrainWorld);
        for (var entry : confirmedDestroyed.entrySet()) {
            if (!state.pending.contains(entry.getKey())) continue;
            var world = entry.getValue();
            if (world == null || world.getServer() != server)
                throw new IOException("已销毁实体的保存维度不属于当前服务器");
            // Runtime disappearance is safe evidence only after that dimension's entity storage is flushed.
            state.discarded.putIfAbsent(entry.getKey(), world);
            saveWorlds.add(world);
        }
        state.flush(server, saveWorlds);
    }

    private void discard(Entity entity, ServerWorld world) {
        if (!pending.contains(entity.getUuid())) return;
        entity.discard();
        discarded.put(entity.getUuid(), world);
    }

    private void flush(MinecraftServer server, Set<ServerWorld> extraWorlds) throws IOException {
        var batch = new HashMap<>(discarded);
        var worlds = new HashSet<>(extraWorlds);
        worlds.addAll(batch.values());
        for (var world : worlds) world.save(null, true, false);
        if (batch.isEmpty()) return;
        pending.removeAll(batch.keySet());
        markDirty();
        try { saveDurably(file(server), server.getRegistryManager()); }
        catch (IOException e) {
            // The old on-disk ledger still contains these UUIDs. Keep the in-memory retry equally conservative.
            pending.addAll(batch.keySet());
            markDirty();
            throw e;
        }
        for (var entry : batch.entrySet()) discarded.remove(entry.getKey(), entry.getValue());
    }

    /** Vanilla PersistentState.save swallows IO errors. This paired entry point can prove durability to restore. */
    private void saveDurably(Path target, RegistryWrapper.WrapperLookup registries) throws IOException {
        if (readError != null) throw new IOException(readError);
        if (!isDirty()) return;
        Files.createDirectories(target.getParent());
        var root = new NbtCompound(); root.put("data", writeNbt(new NbtCompound(), registries));
        NbtHelper.putDataVersion(root);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        NbtIo.writeCompressed(root, temp);
        try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) {
            throw new IOException("实体清理记录需要支持原子替换的本地文件系统，恢复日志已保留", e);
        }
        loadedFromDisk = true;
        checkedDisk = true;
        setDirty(false);
    }

    @Override public void save(File file, RegistryWrapper.WrapperLookup registries) {
        try { saveDurably(file.toPath(), registries); }
        catch (IOException e) { MWMod.LOGGER.error("Mega Walls entity cleanup save failed; keep pending UUIDs", e); }
    }

    private static void report(MinecraftServer server, IOException e) {
        if (REPORTED.add(server)) MWMod.LOGGER.error("Mega Walls deferred entity cleanup failed; keep recovery data", e);
    }
}
