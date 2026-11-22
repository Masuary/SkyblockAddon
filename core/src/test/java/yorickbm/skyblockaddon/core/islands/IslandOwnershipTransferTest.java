package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandOwnershipTransferTest {
    private static final UUID NEW_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final IslandActor OWNER_ACTOR = new IslandActor(TestIsland.OWNER, false);

    @Test
    void transferSwapsOwnerAndOldOwnerJoinsMembersGroup() {
        final TestIsland island = new TestIsland();
        final UUID helpersGroupId = island.addCustomGroup("Helpers");
        island.addMember(NEW_OWNER, helpersGroupId);
        island.addMember(OTHER_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertEquals(IslandActionResult.SUCCESS, IslandActions.transferOwnership(island, OWNER_ACTOR, NEW_OWNER));

        assertEquals(NEW_OWNER, island.getOwner());
        assertFalse(island.getMembers().contains(NEW_OWNER));
        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(NEW_OWNER)));
        assertTrue(island.getMembers().contains(TestIsland.OWNER));
        assertEquals(SkyblockAddonCore.MEMBERS_GROUP_ID, island.getGroupForEntityUUID(TestIsland.OWNER).orElseThrow().getId());
        assertEquals(1, island.getGroups().stream().filter(group -> group.hasMember(TestIsland.OWNER)).count());
        assertTrue(island.getMembers().contains(OTHER_MEMBER));
        assertEquals(2, island.getMembers().size());
    }

    @Test
    void onlyTheOwnerMayTransfer() {
        final TestIsland island = new TestIsland();
        island.getMembersGroup().setPermission(IslandActions.ADMIN_MENU_PERMISSION, true);
        island.addMember(NEW_OWNER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        island.addMember(OTHER_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertEquals(IslandActionResult.OWNER_ONLY, IslandActions.transferOwnership(island, new IslandActor(OTHER_MEMBER, false), NEW_OWNER));
        assertEquals(IslandActionResult.OWNER_ONLY, IslandActions.transferOwnership(island, new IslandActor(GUEST, true), NEW_OWNER));
        assertEquals(TestIsland.OWNER, island.getOwner());
    }

    @Test
    void targetMustBeAMember() {
        final TestIsland island = new TestIsland();
        final UUID trustedGroupId = island.addCustomGroup("Trusted");
        island.assignGroup(GUEST, trustedGroupId);

        assertEquals(IslandActionResult.TARGET_NOT_MEMBER, IslandActions.transferOwnership(island, OWNER_ACTOR, GUEST));
        assertEquals(IslandActionResult.TARGET_IS_SELF, IslandActions.transferOwnership(island, OWNER_ACTOR, TestIsland.OWNER));
        assertEquals(TestIsland.OWNER, island.getOwner());
    }

    @Test
    void oldOwnerCanLeaveAfterTransfer() {
        final TestIsland island = new TestIsland();
        island.addMember(NEW_OWNER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        IslandActions.transferOwnership(island, OWNER_ACTOR, NEW_OWNER);

        island.removeFromIsland(TestIsland.OWNER);

        assertFalse(island.isPartOf(TestIsland.OWNER));
        assertEquals(NEW_OWNER, island.getOwner());
        assertTrue(island.getMembers().isEmpty());
    }
}
