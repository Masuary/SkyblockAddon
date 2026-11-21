package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;
import yorickbm.skyblockaddon.core.util.UsernameCache;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class IslandRegistry extends SkyblockAddonRegistry<DataComponent> {
    protected final List<Island> islands;

    public IslandRegistry(List<Island> islands) {
        this.islands = islands.stream()
                .filter(Island::isVisible) //Only public islands
                .filter(i -> !Objects.equals(i.getOwner(), SkyblockAddonCore.NO_OWNER_ID)) //No islands with no owner
                .sorted(Comparator.comparing(Island::getName)) //Sort alphabetically
                .toList();
    }

    @Override
    public void getNextData(DataComponent component) {
        final Island island = islands.get(this.index);
        final String username = UsernameCache.getBlocking(island.getOwner());

        component.put("SkullOwner", username);
        component.put("owner_name", username);
        component.put("skull_texture", island.getSkullTexture());

        component.put("biome", island.getBiome());

        component.put(GuiDataKeys.ISLAND_ID, island.getId());
        component.put(GuiDataKeys.PLAYER_ID, island.getOwner());

        index++;
    }

    @Override
    public int getSize() {
        return islands.size();
    }
}
