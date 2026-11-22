package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandActionsTest {
    private static final UUID ADMIN_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID PLAIN_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static final IslandActor OWNER_ACTOR = new IslandActor(TestIsland.OWNER, false);
    private static final IslandActor ADMIN_MEMBER_ACTOR = new IslandActor(ADMIN_MEMBER, false);
    private static final IslandActor PLAIN_MEMBER_ACTOR = new IslandActor(PLAIN_MEMBER, false);
    private static final IslandActor GUEST_ACTOR = new IslandActor(GUEST, false);
    private static final IslandActor OPERATOR_ACTOR = new IslandActor(OPERATOR, true);

    private UUID helpersGroupId;

    private TestIsland islandWithAdminAndPlainMember() {
        final TestIsland island = new TestIsland();
        island.getMembersGroup().setPermission(IslandActions.ADMIN_MENU_PERMISSION, true);
        helpersGroupId = island.addCustomGroup("Helpers");
        island.getGroup(helpersGroupId).setPermission(IslandActions.ADMIN_MENU_PERMISSION, false);
        island.addMember(ADMIN_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        island.addMember(PLAIN_MEMBER, helpersGroupId);
        return island;
    }

    @Test
    void ownerAdminGroupMemberAndOperatorMayManage() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertTrue(IslandActions.canManage(island, OWNER_ACTOR));
        assertTrue(IslandActions.canManage(island, ADMIN_MEMBER_ACTOR));
        assertTrue(IslandActions.canManage(island, OPERATOR_ACTOR));
        assertFalse(IslandActions.canManage(island, PLAIN_MEMBER_ACTOR));
        assertFalse(IslandActions.canManage(island, GUEST_ACTOR));
    }

    @Test
    void guestInAdminGroupMayNotManage() {
        final TestIsland island = islandWithAdminAndPlainMember();
        final UUID trustedGroupId = island.addCustomGroup("Trusted");
        island.getGroup(trustedGroupId).setPermission(IslandActions.ADMIN_MENU_PERMISSION, true);
        island.assignGroup(GUEST, trustedGroupId);

        assertFalse(IslandActions.canManage(island, GUEST_ACTOR));
    }

    @Test
    void restrictedMemberCannotMoveThemselvesBackIntoMembers() {
        final TestIsland island = islandWithAdminAndPlainMember();

        final IslandActionResult result = IslandActions.assignGroup(island, PLAIN_MEMBER_ACTOR, PLAIN_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertEquals(IslandActionResult.NOT_ALLOWED, result);
        assertEquals(helpersGroupId, island.getGroupForEntityUUID(PLAIN_MEMBER).orElseThrow().getId());
    }

    @Test
    void restrictedMemberCannotChangeVisibilityOrGroups() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.toggleVisibility(island, PLAIN_MEMBER_ACTOR));
        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.removeGroup(island, PLAIN_MEMBER_ACTOR, helpersGroupId));
        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.createGroup(island, PLAIN_MEMBER_ACTOR, new TestIsland.TestGroup(UUID.randomUUID(), "Builders", false)));
        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.kickMember(island, PLAIN_MEMBER_ACTOR, ADMIN_MEMBER));
        assertFalse(island.isVisible());
        assertTrue(island.hasGroup(helpersGroupId));
        assertTrue(island.getMembers().contains(ADMIN_MEMBER));
    }

    @Test
    void adminMemberAssignsGroupAndPlayerStaysInOneGroup() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.assignGroup(island, ADMIN_MEMBER_ACTOR, PLAIN_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID));

        assertEquals(1, island.getGroups().stream().filter(group -> group.hasMember(PLAIN_MEMBER)).count());
        assertTrue(island.getMembers().contains(PLAIN_MEMBER));
    }

    @Test
    void demotingAMemberToVisitorsRemovesThemFromTheIsland() {
        final TestIsland island = islandWithAdminAndPlainMember();

        final IslandActionResult result = IslandActions.assignGroup(island, OWNER_ACTOR, PLAIN_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID);

        assertEquals(IslandActionResult.MEMBER_REMOVED, result);
        assertTrue(result.succeeded());
        assertFalse(island.isPartOf(PLAIN_MEMBER));
        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(PLAIN_MEMBER)));
    }

    @Test
    void demotingYourselfToVisitorsIsRefused() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.TARGET_IS_SELF, IslandActions.assignGroup(island, ADMIN_MEMBER_ACTOR, ADMIN_MEMBER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertTrue(island.getMembers().contains(ADMIN_MEMBER));
    }

    @Test
    void ownerCannotBeDemotedToVisitors() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.TARGET_IS_OWNER, IslandActions.assignGroup(island, OPERATOR_ACTOR, TestIsland.OWNER, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertTrue(island.isOwner(TestIsland.OWNER));
    }

    @Test
    void movingAGuestToVisitorsIsAPlainAssignment() {
        final TestIsland island = islandWithAdminAndPlainMember();
        island.assignGroup(GUEST, helpersGroupId);

        assertEquals(IslandActionResult.SUCCESS, IslandActions.assignGroup(island, OWNER_ACTOR, GUEST, SkyblockAddonCore.VISITORS_GROUP_ID));

        assertEquals(SkyblockAddonCore.VISITORS_GROUP_ID, island.getGroupForEntityUUID(GUEST).orElseThrow().getId());
    }

    @Test
    void guestAssignedThroughActionDoesNotBecomeMember() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.assignGroup(island, OWNER_ACTOR, GUEST, helpersGroupId));

        assertFalse(island.isPartOf(GUEST));
        assertEquals(helpersGroupId, island.getGroupForEntityUUID(GUEST).orElseThrow().getId());
    }

    @Test
    void ownerCannotBeAssignedOrKicked() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.TARGET_IS_OWNER, IslandActions.assignGroup(island, ADMIN_MEMBER_ACTOR, TestIsland.OWNER, helpersGroupId));
        assertEquals(IslandActionResult.TARGET_IS_OWNER, IslandActions.kickMember(island, ADMIN_MEMBER_ACTOR, TestIsland.OWNER));
    }

    @Test
    void defaultGroupsCannotBeRemoved() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.DEFAULT_GROUP_PROTECTED, IslandActions.removeGroup(island, OWNER_ACTOR, SkyblockAddonCore.MEMBERS_GROUP_ID));
        assertEquals(IslandActionResult.DEFAULT_GROUP_PROTECTED, IslandActions.removeGroup(island, OWNER_ACTOR, SkyblockAddonCore.VISITORS_GROUP_ID));
    }

    @Test
    void removedGroupMovesMembersBackToMembers() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.removeGroup(island, OWNER_ACTOR, helpersGroupId));

        assertEquals(SkyblockAddonCore.MEMBERS_GROUP_ID, island.getGroupForEntityUUID(PLAIN_MEMBER).orElseThrow().getId());
    }

    @Test
    void groupNamesStayUnique() {
        final TestIsland island = islandWithAdminAndPlainMember();

        final IslandActionResult result = IslandActions.createGroup(island, OWNER_ACTOR, new TestIsland.TestGroup(UUID.randomUUID(), "helpers", false));

        assertEquals(IslandActionResult.GROUP_NAME_EXISTS, result);
    }

    @Test
    void kickRemovesMemberFromListAndGroups() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.kickMember(island, ADMIN_MEMBER_ACTOR, PLAIN_MEMBER));

        assertFalse(island.isPartOf(PLAIN_MEMBER));
        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(PLAIN_MEMBER)));
    }

    @Test
    void kickingGuestRemovesTheirGroupAssignment() {
        final TestIsland island = islandWithAdminAndPlainMember();
        island.assignGroup(GUEST, helpersGroupId);

        assertEquals(IslandActionResult.SUCCESS, IslandActions.kickMember(island, OWNER_ACTOR, GUEST));

        assertFalse(island.isInAnyGroup(GUEST));
        assertEquals(IslandActionResult.TARGET_NOT_MEMBER, IslandActions.kickMember(island, OWNER_ACTOR, GUEST));
    }

    @Test
    void operatorMayToggleVisibility() {
        final TestIsland island = islandWithAdminAndPlainMember();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.toggleVisibility(island, OPERATOR_ACTOR));

        assertTrue(island.isVisible());
    }
}
