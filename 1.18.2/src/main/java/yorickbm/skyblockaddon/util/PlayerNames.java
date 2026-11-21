package yorickbm.skyblockaddon.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import yorickbm.skyblockaddon.core.util.UsernameCache;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class PlayerNames {

    private PlayerNames() {
    }

    /**
     * Name from the server's profile cache, falling back to the Mojang-backed username cache.
     */
    public static String nameOf(final MinecraftServer server, final UUID playerId) {
        final GameProfileCache profileCache = server.getProfileCache();
        if (profileCache != null) {
            final Optional<String> cachedName = profileCache.get(playerId).map(GameProfile::getName);
            if (cachedName.isPresent()) return cachedName.get();
        }
        return UsernameCache.getBlocking(playerId);
    }

    public static Optional<UUID> findByName(final MinecraftServer server, final Collection<UUID> candidates, final String name) {
        return candidates.stream()
                .filter(candidate -> nameOf(server, candidate).equalsIgnoreCase(name))
                .findFirst();
    }
}
