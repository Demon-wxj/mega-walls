package com.megawalls.client;

import com.megawalls.MWMod;
import com.megawalls.network.S2C_EnderRift;
import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

/** AAA/Effekseer is referenced exclusively from the client entrypoint tree. */
public final class EnderRiftParticles {
    private record Playing(ParticleEmitter emitter, ClientWorld world, long expires) {}
    private static final ArrayList<Playing> PLAYING = new ArrayList<>();
    private static long generation;
    private static long startedEmitters;
    private static boolean warned;

    private EnderRiftParticles() {}
    public static long debugStartedEmitters() { return startedEmitters; }
    public static int debugActiveEmitters() { return PLAYING.size(); }

    static void play(MinecraftClient client, S2C_EnderRift event) {
        spawn(client, event.origin(), event.originYaw(), "ender_departure");
        spawn(client, event.destination(), event.destinationYaw(), "ender_arrival");
    }

    private static void spawn(MinecraftClient client, Vec3d foot, float yaw, String name) {
        ClientWorld expectedWorld = client.world;
        if (expectedWorld == null) return;
        long epoch = generation, born = ClientEnderRifts.clockTicks();
        EffectRegistry.tryLoad(MWMod.id(name)).thenAccept(effect -> client.execute(() -> {
            if (generation != epoch || client.world != expectedWorld || ClientEnderRifts.clockTicks() - born >= S2C_EnderRift.LIFETIME_TICKS) return;
            if (effect.isEmpty()) {
                if (!warned) { MWMod.LOGGER.warn("Ender rift Effekseer assets unavailable; check AAA native/resource initialization."); warned = true; }
                return;
            }
            var emitter = effect.get().play();
            if (!emitter.exists()) {
                if (!warned) { MWMod.LOGGER.warn("Ender rift emitter did not start; AAA Particles native renderer may be unavailable."); warned = true; }
                return;
            }
            double radians = Math.toRadians(yaw);
            emitter.setPosition((float) (foot.x + Math.sin(radians) * .38), (float) (foot.y + 1.30),
                    (float) (foot.z - Math.cos(radians) * .38));
            emitter.setRotation(0, (float) -radians, 0);
            emitter.setScale(1, 1, 1);
            if (ClientEnderRifts.clockTicks() > born) emitter.setProgress((ClientEnderRifts.clockTicks() - born) * 3f);
            PLAYING.add(new Playing(emitter, expectedWorld, born + S2C_EnderRift.LIFETIME_TICKS));
            startedEmitters++;
        }));
    }

    static void tick() {
        var world = MinecraftClient.getInstance().world;
        PLAYING.removeIf(p -> {
            if (world != p.world || world == null || ClientEnderRifts.clockTicks() >= p.expires) {
                p.emitter.stop(); return true;
            }
            return !p.emitter.exists();
        });
    }

    static void reset() {
        generation++;
        for (var p : PLAYING) p.emitter.stop();
        PLAYING.clear();
    }
}
