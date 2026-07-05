package yorickbm.skyblockaddon.enhanced;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.IslandAdministrationService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EnhancedGuiHelper {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConcurrentHashMap<UUID, GameProfile> RESOLVED_PROFILES = new ConcurrentHashMap<>();

    private EnhancedGuiHelper() {}

    public static boolean canManageIsland(final ServerPlayer player, final Island island) {
        return IslandAdministrationService.canManage(player, island);
    }

    public static void addLore(ItemStack item, Component... lines) {
        CompoundTag display = item.getOrCreateTagElement("display");
        ListTag lore = display.contains("Lore") ? display.getList("Lore", 8) : new ListTag();
        for (Component line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", lore);
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
}
