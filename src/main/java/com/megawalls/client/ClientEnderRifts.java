package com.megawalls.client;

import com.megawalls.network.S2C_EnderRift;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Short-lived visual state only. Endpoints never reference moving entities. */
public final class ClientEnderRifts {
    private static final int MAX_CASTS = 32;
    private static final LinkedHashMap<UUID, Cast> ACTIVE = new LinkedHashMap<>();
    private static final LinkedHashMap<UUID, Long> SEEN = new LinkedHashMap<>();
    private static ClientWorld world;
    private static long clientTicks;
    private static long acceptedEvents;
    private static long renderedFrames;

    static final class Cast {
        final S2C_EnderRift event;
        final long born;
        Cast(S2C_EnderRift event, long born) { this.event = event; this.born = born; }
        int age() { return world == null ? S2C_EnderRift.LIFETIME_TICKS : (int) Math.max(0, clientTicks - born); }
    }

    public record Endpoint(UUID castId, Vec3d position, float yaw, int ageTicks, boolean arrival) {}
    public record Snapshot(long acceptedEvents, long renderedFrames, List<Endpoint> endpoints) {}

    private ClientEnderRifts() {}

    public static void play(MinecraftClient client, S2C_EnderRift event) {
        if (client.world == null || client.player == null || !event.valid()
                || !client.world.getRegistryKey().getValue().equals(event.dimension())) return;
        if (world != client.world) { reset(); world = client.world; }
        long now = clientTicks;
        if (SEEN.containsKey(event.castId())) return;
        // Ignore stale effects after large stalls, but tolerate normal time-update/network skew.
        if (world.getTime() - event.serverTick() > 60) return;
        double range = S2C_EnderRift.VIEW_DISTANCE + 32;
        if (client.player.squaredDistanceTo(event.origin()) > range * range
                && client.player.squaredDistanceTo(event.destination()) > range * range) return;
        if (ACTIVE.size() >= MAX_CASTS) {
            var oldest = ACTIVE.keySet().iterator(); oldest.next(); oldest.remove();
        }
        ACTIVE.put(event.castId(), new Cast(event, now));
        SEEN.put(event.castId(), now);
        while (SEEN.size() > 128) { var first = SEEN.keySet().iterator(); first.next(); first.remove(); }
        acceptedEvents++;
        EnderRiftParticles.play(client, event);
    }

    public static void tick(MinecraftClient client) {
        // Server TimeUpdate packets can jump world.getTime() by many ticks. Cosmetic lifetime
        // uses this local monotonic clock so a synchronization correction cannot erase a new rift.
        clientTicks++;
        if (world != client.world) { reset(); world = client.world; }
        if (world == null) return;
        ACTIVE.values().removeIf(cast -> cast.age() >= S2C_EnderRift.LIFETIME_TICKS);
        SEEN.values().removeIf(born -> clientTicks - born > 100);
        EnderRiftParticles.tick();
    }

    public static void reset() {
        ACTIVE.clear(); SEEN.clear(); world = null;
        EnderRiftParticles.reset();
    }

    static Iterable<Cast> active() { return ACTIVE.values(); }
    static long clockTicks() { return clientTicks; }
    static void rendered() { renderedFrames++; }

    /** Read-only instrumentation for the isolated real-client regression fixture. */
    public static Snapshot debugSnapshot() {
        var endpoints = new ArrayList<Endpoint>();
        for (var cast : ACTIVE.values()) {
            endpoints.add(new Endpoint(cast.event.castId(), cast.event.origin(), cast.event.originYaw(), cast.age(), false));
            endpoints.add(new Endpoint(cast.event.castId(), cast.event.destination(), cast.event.destinationYaw(), cast.age(), true));
        }
        return new Snapshot(acceptedEvents, renderedFrames, List.copyOf(endpoints));
    }
}
