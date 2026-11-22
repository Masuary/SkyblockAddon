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
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Test
    void memberMovedToVisitorsLeavesTheIsland() {
        final TestIsland island = new TestIsland();
        island.addMember(DEMOTED_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertTrue(island.addMember(DEMOTED_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertLeftTheIsland(island);
    }

    @Test
    void memberAssignedToVisitorsLeavesTheIsland() {
        final TestIsland island = new TestIsland();
        final UUID helpers = island.addCustomGroup("Helpers");
        island.addMember(DEMOTED_MEMBER, helpers);

        assertTrue(island.assignGroup(DEMOTED_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertLeftTheIsland(island);
    }

    @Test
    void ownerCannotBeMovedToVisitors() {
        final TestIsland island = new TestIsland();

        assertFalse(island.assignGroup(TestIsland.OWNER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertTrue(island.isOwner(TestIsland.OWNER));
    }

    @Test
    void guestAssignedToVisitorsStaysAGuest() {
        final TestIsland island = new TestIsland();

        assertTrue(island.assignGroup(GUEST, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertFalse(island.isPartOf(GUEST));
        assertEquals(SkyblockAddonCore.VISITORS_GROUP_ID, island.getGroupForEntityUUID(GUEST).orElseThrow().getId());
    }

    private static void assertLeftTheIsland(final TestIsland island) {
        assertFalse(island.isPartOf(DEMOTED_MEMBER));
        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(DEMOTED_MEMBER)));
        assertEquals(SkyblockAddonCore.VISITORS_GROUP_ID, island.getGroupForEntityUUID(DEMOTED_MEMBER).orElseThrow().getId());
    }
}
