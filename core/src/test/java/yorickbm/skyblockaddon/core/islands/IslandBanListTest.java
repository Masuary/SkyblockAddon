package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandBanListTest {
    private static final UUID ADMIN_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID PLAIN_MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID GRIEFER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SECOND_GRIEFER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final IslandActor OWNER_ACTOR = new IslandActor(TestIsland.OWNER, false);

    private TestIsland islandWithMembers() {
        final TestIsland island = new TestIsland();
        island.getMembersGroup().setPermission(IslandActions.ADMIN_MENU_PERMISSION, true);
        final UUID helpersGroupId = island.addCustomGroup("Helpers");
        island.addMember(ADMIN_MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        island.addMember(PLAIN_MEMBER, helpersGroupId);
        return island;
    }

    @Test
    void ownerAndAdminMemberMayBanAndUnban() {
        final TestIsland island = islandWithMembers();

        assertEquals(IslandActionResult.SUCCESS, IslandActions.banPlayer(island, OWNER_ACTOR, GRIEFER));
        assertEquals(IslandActionResult.SUCCESS, IslandActions.banPlayer(island, new IslandActor(ADMIN_MEMBER, false), SECOND_GRIEFER));
        assertEquals(List.of(GRIEFER, SECOND_GRIEFER), island.getBannedPlayers());

        assertEquals(IslandActionResult.SUCCESS, IslandActions.unbanPlayer(island, new IslandActor(ADMIN_MEMBER, false), GRIEFER));
        assertFalse(island.isBanned(GRIEFER));
        assertTrue(island.isBanned(SECOND_GRIEFER));
    }

    @Test
    void memberWithoutAdminRightsMayNotBan() {
        final TestIsland island = islandWithMembers();

        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.banPlayer(island, new IslandActor(PLAIN_MEMBER, false), GRIEFER));
        assertEquals(IslandActionResult.NOT_ALLOWED, IslandActions.checkExpel(island, new IslandActor(PLAIN_MEMBER, false), GRIEFER));
        assertFalse(island.isBanned(GRIEFER));
    }

    @Test
    void ownerAndMembersCannotBeBanned() {
        final TestIsland island = islandWithMembers();

        assertEquals(IslandActionResult.TARGET_IS_MEMBER, IslandActions.banPlayer(island, OWNER_ACTOR, PLAIN_MEMBER));
        assertEquals(IslandActionResult.TARGET_IS_MEMBER, IslandActions.banPlayer(island, new IslandActor(ADMIN_MEMBER, false), TestIsland.OWNER));
        assertEquals(IslandActionResult.TARGET_IS_SELF, IslandActions.banPlayer(island, OWNER_ACTOR, TestIsland.OWNER));
        assertFalse(island.banPlayer(PLAIN_MEMBER));
        assertTrue(island.getBannedPlayers().isEmpty());
    }

    @Test
    void membersCannotBeExpelled() {
        final TestIsland island = islandWithMembers();

        assertEquals(IslandActionResult.TARGET_IS_MEMBER, IslandActions.checkExpel(island, OWNER_ACTOR, PLAIN_MEMBER));
        assertEquals(IslandActionResult.SUCCESS, IslandActions.checkExpel(island, OWNER_ACTOR, GRIEFER));
    }

    @Test
    void banningTwiceAndUnbanningUnknownAreReported() {
        final TestIsland island = islandWithMembers();
        IslandActions.banPlayer(island, OWNER_ACTOR, GRIEFER);

        assertEquals(IslandActionResult.TARGET_IS_BANNED, IslandActions.banPlayer(island, OWNER_ACTOR, GRIEFER));
        assertEquals(IslandActionResult.TARGET_NOT_BANNED, IslandActions.unbanPlayer(island, OWNER_ACTOR, SECOND_GRIEFER));
    }

    @Test
    void bannedGuestLosesCustomGroupAndCannotBeAssignedAgain() {
        final TestIsland island = islandWithMembers();
        final UUID trustedGroupId = island.addCustomGroup("Trusted");
        island.assignGroup(GRIEFER, trustedGroupId);

        IslandActions.banPlayer(island, OWNER_ACTOR, GRIEFER);

        assertTrue(island.getGroups().stream().noneMatch(group -> group.hasMember(GRIEFER)));
        assertEquals(IslandActionResult.TARGET_IS_BANNED, IslandActions.assignGroup(island, OWNER_ACTOR, GRIEFER, trustedGroupId));
    }

    @Test
    void joiningAsMemberLiftsTheBan() {
        final TestIsland island = islandWithMembers();
        IslandActions.banPlayer(island, OWNER_ACTOR, GRIEFER);

        island.addMember(GRIEFER, SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertFalse(island.isBanned(GRIEFER));
    }
}
