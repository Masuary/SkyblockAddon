package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;
import yorickbm.skyblockaddon.core.util.UsernameCache;

import java.util.List;
import java.util.UUID;

public class GroupMemberRegistry extends SkyblockAddonRegistry<DataComponent> {
    private final List<UUID> members;
    private final IslandGroup group;

    public GroupMemberRegistry(Island island, UUID groupId) {
        this.group = island.getGroup(groupId);
        this.members = this.group.getMembers();
    }

    @Override
    public void getNextData(DataComponent component) {
        final String username = UsernameCache.getBlocking(this.members.get(this.index));

        component.put("SkullOwner", username);
        component.put("owner_name", username);
        component.put("group_name", group.getName());
        component.put("player_id", this.members.get(this.index));

        this.index++;
    }

    @Override
    public int getSize() {
        return this.members.size();
    }
}
