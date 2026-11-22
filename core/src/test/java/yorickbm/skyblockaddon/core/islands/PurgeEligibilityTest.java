package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurgeEligibilityTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void islandWithAnOwnerIsNotAbandoned() {
        assertEquals(PurgeEligibility.NOT_ABANDONED, PurgeEligibility.of(new TestIsland(), 4));
    }

    @Test
    void islandWithOnlyMembersIsNotAbandoned() {
        final TestIsland island = new TestIsland();
        island.addMember(MEMBER, SkyblockAddonCore.MEMBERS_GROUP_ID);
        island.setOwner(SkyblockAddonCore.NO_OWNER_ID);

        assertEquals(PurgeEligibility.NOT_ABANDONED, PurgeEligibility.of(island, 4));
    }

    @Test
    void abandonedIslandWithoutTrackedChunksHasNothingToPurge() {
        assertEquals(PurgeEligibility.NO_TRACKED_CHUNKS, PurgeEligibility.of(abandonedIsland(), 0));
    }

    @Test
    void abandonedIslandWithTrackedChunksIsPurgeable() {
        assertEquals(PurgeEligibility.PURGEABLE, PurgeEligibility.of(abandonedIsland(), 3));
    }

    @Test
    void refusalsHaveTheirOwnMessages() {
        assertEquals("commands.admin.purge.notallowed", PurgeEligibility.NOT_ABANDONED.languageKey());
        assertEquals("commands.admin.purge.no.chunks", PurgeEligibility.NO_TRACKED_CHUNKS.languageKey());
    }

    private static TestIsland abandonedIsland() {
        final TestIsland island = new TestIsland();
        island.setOwner(SkyblockAddonCore.NO_OWNER_ID);
        return island;
    }
}
