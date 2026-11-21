package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbandonedIslandQueryTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void islandWithOwnerIsNotAbandoned() {
        final TestIsland island = new TestIsland();

        assertFalse(island.hasNoOwnerAndNoMembers());
    }

    @Test
    void islandWithoutOwnerAndMembersIsAbandoned() {
        final TestIsland island = ownerlessIslandAt(0, 0);

        assertTrue(island.hasNoOwnerAndNoMembers());
    }

    @Test
    void ownerlessIslandWithMemberIsNotAbandonedAndKeepsItsOwnerUnchanged() {
        final TestIsland island = ownerlessIslandWithMemberAt(0, 0);

        assertFalse(island.hasNoOwnerAndNoMembers());
        assertEquals(SkyblockAddonCore.MOD_UUID, island.getOwner());
        assertTrue(island.getMembers().contains(MEMBER));
    }

    @Test
    void findAbandonedIslandsKeepsOnlyAbandonedIslandsSortedByGridPosition() {
        final TestIsland owned = new TestIsland();
        final TestIsland abandonedEast = ownerlessIslandAt(1000, 0);
        final TestIsland abandonedWest = ownerlessIslandAt(-1000, 2000);
        final TestIsland abandonedWestNorth = ownerlessIslandAt(-1000, -1000);
        final TestIsland ownerlessWithMember = ownerlessIslandWithMemberAt(2000, 0);

        final List<Island> abandoned = AbandonedIslandQuery.findAbandonedIslands(
                List.of(owned, abandonedEast, ownerlessWithMember, abandonedWest, abandonedWestNorth));

        assertEquals(List.of(abandonedWestNorth, abandonedWest, abandonedEast), abandoned);
        assertEquals(SkyblockAddonCore.MOD_UUID, ownerlessWithMember.getOwner());
    }

    @Test
    void gridSlotIsTheCenterDividedByTheIslandSpacing() {
        final int spacing = SkyblockAddonCore.ISLAND_SIZE * 2 + SkyblockAddonCore.ISLAND_BUFFER;

        assertEquals(new IslandGridSlot(0, 0), IslandGridSlot.containing(new Vec3i(0, 0, 0)));
        assertEquals(new IslandGridSlot(1, -2), IslandGridSlot.containing(new Vec3i(spacing, 0, -2 * spacing)));
        assertEquals(new IslandGridSlot(-3, 4), IslandGridSlot.containing(new Vec3i(-3 * spacing, 0, 4 * spacing)));
    }

    private static TestIsland ownerlessIslandAt(final int centerX, final int centerZ) {
        final TestIsland island = new TestIsland();
        island.setOwner(SkyblockAddonCore.MOD_UUID);
        island.setCenter(new Vec3i(centerX, 110, centerZ));
        return island;
    }

    private static TestIsland ownerlessIslandWithMemberAt(final int centerX, final int centerZ) {
        final TestIsland island = new TestIsland();
        island.addMember(MEMBER, SkyblockAddonCore.MOD_UUID);
        island.setOwner(SkyblockAddonCore.MOD_UUID);
        island.setCenter(new Vec3i(centerX, 110, centerZ));
        return island;
    }
}
