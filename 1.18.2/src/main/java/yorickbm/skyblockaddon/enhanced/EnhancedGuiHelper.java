package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EnhancedGuiHelper {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConcurrentHashMap<UUID, GameProfile> RESOLVED_PROFILES = new ConcurrentHashMap<>();

    private EnhancedGuiHelper() {}

    /**
     * A click in an open screen re-checks the right the screen was opened with, since it may be gone by now; without it
     * the screen closes with the denial message. Returns whether the click was denied.
     */
    public static boolean closeWhenDenied(ServerPlayer player, boolean allowed, IslandActionResult denial) {
        if (allowed) return false;
        MasuGui.closeFor(player);
        IslandActionService.sendFailure(player, denial, "");
        return true;
    }

    /** "minecraft:dark_forest" becomes "Dark Forest"; a missing biome is "Unknown". */
    static String biomeName(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) return "Unknown";
        String path = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        return Arrays.stream(path.replace('/', ' ').replace('_', ' ').split(" "))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    static boolean isOnline(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayer(playerId) != null;
    }

    static String onlineText(boolean online) {
        return online ? "Online" : "Offline";
    }

    static int onlineColor(boolean online) {
        return online ? EnhancedDialog.VALUE_GREEN : EnhancedDialog.VALUE_GRAY;
    }

    public static ItemStack getPlayerHead(UUID playerUuid) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag tag = head.getOrCreateTag();

        GameProfile resolvedProfile = resolveGameProfile(playerUuid);
        if (resolvedProfile != null) {
            CompoundTag skullOwner = NbtUtils.writeGameProfile(new CompoundTag(), resolvedProfile);
            tag.put("SkullOwner", skullOwner);
        } else {
            CompoundTag skullOwner = new CompoundTag();
            skullOwner.putUUID("Id", playerUuid);
            tag.put("SkullOwner", skullOwner);
        }

        return head;
    }

    private static GameProfile resolveGameProfile(UUID playerUuid) {
        GameProfile cached = RESOLVED_PROFILES.get(playerUuid);
        if (cached != null && !cached.getProperties().get("textures").isEmpty()) {
            return cached;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;

        GameProfileCache profileCache = server.getProfileCache();
        if (profileCache == null) return null;

        Optional<GameProfile> profileOpt = profileCache.get(playerUuid);
        if (profileOpt.isEmpty()) return null;

        GameProfile profile = profileOpt.get();

        if (profile.getProperties().get("textures").isEmpty()) {
            try {
                MinecraftSessionService sessionService = server.getSessionService();
                profile = sessionService.fillProfileProperties(profile, false);
            } catch (Exception e) {
                LOGGER.debug("Could not resolve skin for player {}: {}", playerUuid, e.getMessage());
            }
        }

        if (!profile.getProperties().get("textures").isEmpty()) {
            RESOLVED_PROFILES.put(playerUuid, profile);
        }

        return profile;
    }

    public static String countOf(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
