package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;
import yorickbm.skyblockaddon.core.util.UsernameCache;

import java.util.List;
import java.util.UUID;

/**
 * A plain list of players (visitors on an island, banned players) as player heads.
 */
public class PlayerListRegistry extends SkyblockAddonRegistry<DataComponent> {
    private final List<UUID> players;

    public PlayerListRegistry(final List<UUID> players) {
        this.players = List.copyOf(players);
    }

    @Override
    public void getNextData(final DataComponent component) {
        final UUID playerId = this.players.get(this.index);
        final String username = UsernameCache.getBlocking(playerId);

        component.put("SkullOwner", username);
        component.put("owner_name", username);
        component.put(GuiDataKeys.PLAYER_ID, playerId);

        this.index++;
    }

    @Override
    public int getSize() {
        return this.players.size();
    }
}
