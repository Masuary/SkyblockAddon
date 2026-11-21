package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandMembershipTest {
    private static final UUID FIRST_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID SECOND_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Test
    void memberMovedIntoCustomGroupStaysMember() {
        final TestIsland island = new TestIsland();
        final UUID helpers = island.addCustomGroup("Helpers");
        island.addMember(FIRST_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertTrue(island.addMember(FIRST_MEMBER, helpers));

        assertTrue(island.isPartOf(FIRST_MEMBER));
        assertEquals(helpers, island.getGroupForEntityUUID(FIRST_MEMBER).orElseThrow().getId());
    }

    @Test
    void playerIsNeverInTwoGroups() {
        final TestIsland island = new TestIsland();
        final UUID helpers = island.addCustomGroup("Helpers");
        final UUID builders = island.addCustomGroup("Builders");
        island.addMember(FIRST_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        island.addMember(FIRST_MEMBER, helpers);
        island.assignGroup(FIRST_MEMBER, builders);

        final long groupsContainingMember = island.getGroups().stream().filter(group -> group.hasMember(FIRST_MEMBER)).count();
        assertEquals(1, groupsContainingMember);
        assertEquals(builders, island.getGroupForEntityUUID(FIRST_MEMBER).orElseThrow().getId());
    }

    @Test
    void guestAssignedToCustomGroupDoesNotBecomeMember() {
        final TestIsland island = new TestIsland();
        final UUID trusted = island.addCustomGroup("Trusted");

        assertTrue(island.assignGroup(GUEST, trusted));

        assertFalse(island.isPartOf(GUEST));
        assertEquals(trusted, island.getGroupForEntityUUID(GUEST).orElseThrow().getId());
    }

    @Test
    void ownershipHandOverRemovesNewOwnerFromMembersAndGroups() {
        final TestIsland island = new TestIsland();
        final UUID helpers = island.addCustomGroup("Helpers");
        island.addMember(FIRST_MEMBER, helpers);
        island.addMember(SECOND_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        island.handOverOwnershipToNextMember();

        assertEquals(FIRST_MEMBER, island.getOwner());
        assertFalse(island.getMembers().contains(FIRST_MEMBER));
        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(FIRST_MEMBER)));
        assertEquals(1, island.getMembers().size());
    }

    @Test
    void newOwnerCanLeaveAfterHandOver() {
        final TestIsland island = new TestIsland();
        island.addMember(FIRST_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        island.addMember(SECOND_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        island.handOverOwnershipToNextMember();
        island.handOverOwnershipToNextMember();

        assertEquals(SECOND_MEMBER, island.getOwner());
        assertTrue(island.getMembers().isEmpty());
    }

    @Test
    void lastOwnerLeavingAbandonsAndHidesIsland() {
        final TestIsland island = new TestIsland();
        island.setVisibility(true);

        island.handOverOwnershipToNextMember();

        assertTrue(island.isAbandoned());
        assertFalse(island.isVisible());
    }

    @Test
    void shortSkullTextureIsCleared() {
        final TestIsland island = new TestIsland();
        island.setSkullTexture("eyJ0ZXh0dXJlcyI6e319aaaa");

        island.setSkullTexture("short");

        assertEquals("", island.getSkullTexture());
    }
}
