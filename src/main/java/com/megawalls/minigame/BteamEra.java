package com.megawalls.minigame;

import com.google.gson.JsonParser;
import com.megawalls.MWMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.WeakHashMap;

/** World-local story completion state; this changes display branding only. */
public final class BteamEra {
    private static final Map<MinecraftServer, Boolean> ACTIVE = new WeakHashMap<>();

    private BteamEra() {}

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("bteam-era.json");
    }

    public static synchronized boolean isActive(MinecraftServer server) {
        if (server == null) return false;
        Boolean cached = ACTIVE.get(server);
        if (cached != null) return cached;
        boolean active = false;
        Path path = file(server);
        if (Files.exists(path)) {
            try {
                var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                active = json.has("schema") && json.get("schema").getAsInt() == 1
                        && json.has("active") && json.get("active").getAsBoolean();
            } catch (Exception e) {
                MWMod.LOGGER.error("Cannot read Bteam era state; keeping the original sidebar brand", e);
            }
        }
        ACTIVE.put(server, active);
        return active;
    }

    public static String displayName(MinecraftServer server) {
        return isActive(server) ? "Bteamy" : "Xteamy";
    }

    /** Called on the server after Breathe finishes. Returns false if persistence failed. */
    public static synchronized boolean activate(MinecraftServer server) {
        if (server == null) return false;
        if (isActive(server)) return true;
        Path target = file(server);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), "bteam-era-", ".tmp");
            Files.writeString(temporary, "{\n  \"schema\": 1,\n  \"active\": true,\n  \"brand\": \"Bteamy\"\n}\n", StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            ACTIVE.put(server, true);
            MWMod.LOGGER.info("Breathe completed: this world's sidebar brand is now Bteamy");
            return true;
        } catch (Exception e) {
            MWMod.LOGGER.error("Cannot persist Bteam era; sidebar brand was not changed", e);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (Exception e) { MWMod.LOGGER.warn("Cannot remove temporary Bteam era file {}", temporary, e); }
            }
        }
    }
}
