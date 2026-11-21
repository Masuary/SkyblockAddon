package yorickbm.skyblockaddon.util;

import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;

import java.util.UUID;

public final class IslandGroupAssignments {
    private IslandGroupAssignments() {}

    public static boolean assignWithoutMembership(final Island island, final UUID entity, final IslandGroup targetGroup) {
        if(island == null || targetGroup == null) return false;
        if(island.isOwner(entity)) return false;

        island.getGroups().forEach(group -> group.removeMember(entity));
        targetGroup.addMember(entity);
        return true;
    }
}
