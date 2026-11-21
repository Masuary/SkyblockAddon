package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The island file is written straight from the live island (member list plus each group's members), so the state
 * these tests pin down is exactly what a save persists.
 */
class IslandVisitorDemotionTest {
    private static final UUID DEMOTED_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Test
    void memberMovedToVisitorsIsOnlyInTheVisitorsGroup() {
        final TestIsland island = new TestIsland();
        island.addMember(DEMOTED_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertTrue(island.addMember(DEMOTED_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertEquals(SkyblockAddonCore.VISITORS_GROUP_ID, island.getGroupForEntityUUID(DEMOTED_MEMBER).orElseThrow().getId());
        assertTrue(island.getDefaultGroup().hasMember(DEMOTED_MEMBER));
        assertFalse(island.getMembersGroup().hasMember(DEMOTED_MEMBER));
    }

    @Test
    void memberAssignedToVisitorsIsOnlyInTheVisitorsGroup() {
        final TestIsland island = new TestIsland();
        island.addMember(DEMOTED_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertTrue(island.assignGroup(DEMOTED_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID));

        final long groupsContainingDemotedMember = island.getGroups().stream().filter(group -> group.hasMember(DEMOTED_MEMBER)).count();
        assertEquals(1, groupsContainingDemotedMember);
        assertEquals(SkyblockAddonCore.VISITORS_GROUP_ID, island.getGroupForEntityUUID(DEMOTED_MEMBER).orElseThrow().getId());
    }
}
