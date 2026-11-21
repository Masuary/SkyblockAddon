package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandTravelListing;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class IslandRegistry extends SkyblockAddonRegistry<DataComponent> {
    protected final List<Island> islands;
    private final Function<Island, String> ownerNameOf;
    private final long nowEpochMillis;

    public IslandRegistry(final Collection<? extends Island> islands, final UUID viewer, final Function<Island, String> ownerNameOf, final long nowEpochMillis) {
        this.islands = IslandTravelListing.travelDestinations(islands, viewer, "", ownerNameOf);
        this.ownerNameOf = ownerNameOf;
        this.nowEpochMillis = nowEpochMillis;
    }

    @Override
    public void getNextData(DataComponent component) {
        final Island island = islands.get(this.index);
        final String username = ownerNameOf.apply(island);

        component.put("SkullOwner", username);
        component.put("owner_name", username);
        component.put("skull_texture", island.getSkullTexture());

        component.put("biome", island.getBiome());
        component.put("member_count", String.valueOf(IslandTravelListing.playerCount(island)));
        component.put("last_active", IslandTravelListing.describeLastActive(island.getLastActiveEpochMillis(), nowEpochMillis));

        component.put(GuiDataKeys.ISLAND_ID, island.getId());
        component.put(GuiDataKeys.PLAYER_ID, island.getOwner());

        index++;
    }

    @Override
    public int getSize() {
        return islands.size();
    }
}
