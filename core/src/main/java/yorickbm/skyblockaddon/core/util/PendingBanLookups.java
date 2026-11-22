package yorickbm.skyblockaddon.core.util;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Mojang name lookups /island ban has running: at most one per player, whatever name it is for.
 */
public final class PendingBanLookups {
    private final Map<UUID, String> pendingNameByPlayer = new ConcurrentHashMap<>();

    /**
     * Start a lookup for the player unless one is already running.
     * @return - Empty when the lookup may start, else the name the player's running lookup is for
     */
    public Optional<String> tryStart(final UUID player, final String playerName) {
        return Optional.ofNullable(pendingNameByPlayer.putIfAbsent(player, playerName));
    }

    public void finish(final UUID player) {
        pendingNameByPlayer.remove(player);
    }

    public void clear() {
        pendingNameByPlayer.clear();
    }
}
