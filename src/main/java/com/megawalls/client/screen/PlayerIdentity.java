package com.megawalls.client.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** The player opening the menu, independent of the selected Mega Walls class. */
public final class PlayerIdentity {
    private static final Map<String, SkinTextures> MEMBER_SKINS = Map.of(
            "thexty_", localSkin("xty"), "thebcc_", localSkin("bcc"),
            "thewsd_", localSkin("wsd"), "thezzl_", localSkin("zzl"),
            "theswk_", localSkin("swk"), "thelgg_", localSkin("lgg"),
            "thegou_", localSkin("gou"), "theyuu_", localSkin("yuu"),
            "thesxl_", localSkin("sxl"));
    private static GameProfile cachedProfile;
    private static int cachedPropertiesHash;
    private static Supplier<SkinTextures> accountSkin;

    private PlayerIdentity() {}

    private static SkinTextures localSkin(String file) {
        // The supplied sheets use three-pixel arms, except yuu's four-pixel-arm skin.
        // Keep the model with its texture; a wide model stretches the slim arm UVs.
        SkinTextures.Model model = file.equals("yuu") ? SkinTextures.Model.WIDE : SkinTextures.Model.SLIM;
        return new SkinTextures(Identifier.of("megawalls", "textures/gui/cinematic/" + file + ".png"),
                null, null, null, model, true);
    }

    public static String name() { return profile().getName(); }

    public static boolean isMember(String name) {
        return name != null && MEMBER_SKINS.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static boolean member() { return isMember(name()); }

    public static String role(String name) {
        return "Freezea".equalsIgnoreCase(name) ? "作者" : "玩家";
    }

    /** The network profile is authoritative when present, including restored skin properties. */
    static GameProfile profile() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            PlayerListEntry entry = entry(client);
            return entry == null ? client.player.getGameProfile() : entry.getProfile();
        }
        String name = client.getSession().getUsername();
        if (name == null || name.isEmpty()) name = "玩家";
        UUID id = client.getSession().getUuidOrNull();
        if (id == null) id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(id, name);
    }

    private static PlayerListEntry entry(MinecraftClient client) {
        return client.player == null || client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
    }

    public static SkinTextures skin() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameProfile profile = profile();
        SkinTextures memberSkin = MEMBER_SKINS.get(profile.getName().toLowerCase(Locale.ROOT));
        if (memberSkin != null) {
            return client.getResourceManager().getResource(memberSkin.texture()).isPresent()
                    ? memberSkin : DefaultSkinHelper.getSkinTextures(profile);
        }

        PlayerListEntry entry = entry(client);
        if (entry != null) {
            SkinTextures networkSkin = entry.getSkinTextures();
            if (networkSkin != null && !isClassSkin(networkSkin.texture())) return networkSkin;
        }
        // MWClassSkinMixin replaces PlayerListEntry's returned texture for class outfits.
        // Resolve that entry's profile directly to retain the account skin in the portrait.
        int propertiesHash = profile.getProperties().hashCode();
        if (cachedProfile != profile || cachedPropertiesHash != propertiesHash || accountSkin == null) {
            cachedProfile = profile;
            cachedPropertiesHash = propertiesHash;
            accountSkin = client.getSkinProvider().getSkinTexturesSupplier(profile);
        }
        SkinTextures result = accountSkin.get();
        return result == null ? DefaultSkinHelper.getSkinTextures(profile) : result;
    }

    private static boolean isClassSkin(Identifier texture) {
        if (!texture.getNamespace().equals("megawalls")) return false;
        return texture.getPath().equals("textures/skin/hoops.png")
                || texture.getPath().equals("textures/skin/enderman.png");
    }

    static void clear() {
        cachedProfile = null;
        accountSkin = null;
        cachedPropertiesHash = 0;
    }
}
