package com.megawalls.minigame;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.storage.StorageIoWorker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Keep each arena's recovery journal until its restored chunks have reached disk. */
public final class ArenaChunkSave {
    private static final Map<Map<BlockPos, ?>, Pending> saves = new IdentityHashMap<>();
    private static final int CHUNKS_PER_TICK = 4;

    private static final class Pending {
        final Iterator<ChunkPos> chunks;
        CompletableFuture<Void> pendingIO = CompletableFuture.completedFuture(null);
        boolean flushing;

        Pending(Map<BlockPos, ?> originals) {
            var touched = new LinkedHashSet<ChunkPos>();
            for (BlockPos pos : originals.keySet()) touched.add(new ChunkPos(pos));
            chunks = touched.iterator();
        }
    }

    /** Called on the server thread after each restore batch. The caller owns failure recovery. */
    public static boolean more(boolean restoringBlocks, ServerWorld world, Map<BlockPos, ?> originals) {
        if (restoringBlocks) return true;
        if (originals.isEmpty()) return false;
        Pending save = saves.computeIfAbsent(originals, Pending::new);
        if (!save.pendingIO.isDone()) return true;
        save.pendingIO.join(); // Propagate any write failure before the journal can be removed.
        if (save.flushing) return false;

        var storage = world.getChunkManager().chunkLoadingManager;
        if (save.chunks.hasNext()) {
            var writes = new ArrayList<CompletableFuture<Void>>(CHUNKS_PER_TICK);
            for (int i = 0; i < CHUNKS_PER_TICK && save.chunks.hasNext(); i++) {
                var pos = save.chunks.next();
                var chunk = world.getChunk(pos.x, pos.z);
                world.getPointOfInterestStorage().saveChunk(pos);
                writes.add(storage.setNbt(pos, ChunkSerializer.serialize(world, chunk)));
            }
            save.pendingIO = CompletableFuture.allOf(writes.toArray(CompletableFuture<?>[]::new));
        } else {
            save.flushing = true;
            save.pendingIO = storage.getWorker() instanceof StorageIoWorker io
                    ? io.completeAll(true)
                    : CompletableFuture.runAsync(storage::completeAll, Util.getIoWorkerExecutor());
        }
        return true;
    }

    public static void clear(Map<BlockPos, ?> originals) {
        saves.remove(originals);
    }
}
