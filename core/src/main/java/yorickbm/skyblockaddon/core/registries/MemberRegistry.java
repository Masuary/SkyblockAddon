package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;
import yorickbm.skyblockaddon.core.util.UsernameCache;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MemberRegistry extends SkyblockAddonRegistry<DataComponent> {
    private final List<UUID> members;
    private final Island island;

    public MemberRegistry(Island island) {
        this.members = island.getMembers();
        this.island = island;
    }

    @Override
    public void getNextData(final DataComponent component) {
        if(this.index >= this.getSize()) return;

        final String username = UsernameCache.getBlocking(this.members.get(this.index));

        component.put("SkullOwner", username);
        component.put("owner_name", username);

        final Optional<IslandGroup> group = island.getGroupForEntityUUID(this.members.get(this.index));
        group.ifPresentOrElse(
                islandGroup -> component.put("group_name", islandGroup.getName()),
                () -> component.put("group_name", "N/A"));

        component.put("player_id", this.members.get(this.index));

        this.index++;
    }

    @Override
    public int getSize() {
        return members.size();
    }
}
