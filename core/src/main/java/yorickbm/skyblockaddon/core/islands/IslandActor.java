package yorickbm.skyblockaddon.core.islands;

import java.util.UUID;

/**
 * The player running an island action.
 * @param playerId - Player UUID
 * @param operator - Whether the player has server operator rights (permission level 2)
 */
public record IslandActor(UUID playerId, boolean operator) {
}
