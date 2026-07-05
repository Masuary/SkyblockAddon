package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.registries.interfaces.ComponentObjectCoupling;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PermissionRegistry extends SkyblockAddonRegistry<DataComponent> implements ComponentObjectCoupling<Permission> {
    List<Permission> permissions;
    Island island;
    UUID groupId;

    public PermissionRegistry(final Island island, final String category, final UUID groupId) {
        permissions = PermissionManager.getInstance().getPermissionsFor(category);
        this.island = island;
        this.groupId = groupId;
    }

    @Override
    public void getNextData(DataComponent component) {
        component.put("permission", index);
        index++;
    }

    @Override
    public int getSize() {
        return this.permissions.size();
    }

    public IslandGroup getGroup() {
        return this.island.getGroup(this.groupId);
    }

    public Island getIsland() {
        return this.island;
    }

    /**
     * Get Permission for component
     * @param component DataComponent object
     * @return Optional of Permission object
     */
    public Optional<Permission> getDataForComponent(DataComponent component) {
        if(!component.contains("permission")) return Optional.empty();

        return Optional.ofNullable(
                this.permissions.get(component.getObject("permission", Integer.class))
        );
    }
}
