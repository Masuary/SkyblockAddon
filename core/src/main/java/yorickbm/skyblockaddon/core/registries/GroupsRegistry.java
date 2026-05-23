package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.registries.interfaces.ComponentObjectCoupling;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GroupsRegistry extends SkyblockAddonRegistry<DataComponent> implements ComponentObjectCoupling<IslandGroup> {
    private final List<IslandGroup> groups;
    private final Island island;

    public GroupsRegistry(final Island island) {
        groups = new ArrayList<>(island.getGroups());
        this.island = island;
    }

    @Override
    public void getNextData(DataComponent tag) {
        tag.put("group_id", this.groups.get(this.index).getId());
        this.index++;
    }

    @Override
    public int getSize() {
        return this.groups.size();
    }

    /**
     * Get Island Group for component
     *
     * @param component DataComponent object
     * @return Optional of IslandGroup object
     */
    public Optional<IslandGroup> getDataForComponent(DataComponent component) {
        if(!component.contains("group_id")) return Optional.empty();

        return Optional.ofNullable(
                this.island.getGroup(component.getObject("group_id", UUID.class))
        );
    }
}
