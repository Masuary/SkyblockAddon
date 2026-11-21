package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandTravelListingTest {
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final long NOW = Instant.parse("2026-11-21T12:00:00Z").toEpochMilli();
    private static final long ONE_DAY = 24L * 60 * 60 * 1000;

    private static TestIsland publicIsland(final UUID owner, final long lastActive) {
        final TestIsland island = new TestIsland();
        island.setOwner(owner);
        island.setVisibility(true);
        island.setLastActiveEpochMillis(lastActive);
        return island;
    }

    @Test
    void hidesPrivateOwnerlessAndBanningIslandsAndSortsByActivity() {
        final UUID aliceId = UUID.randomUUID();
        final UUID bobId = UUID.randomUUID();
        final UUID carolId = UUID.randomUUID();
        final UUID daveId = UUID.randomUUID();
        final TestIsland alice = publicIsland(aliceId, NOW - 3 * ONE_DAY);
        final TestIsland bob = publicIsland(bobId, NOW);
        final TestIsland carol = publicIsland(carolId, IslandTravelListing.NEVER_ACTIVE);
        final TestIsland privateIsland = publicIsland(daveId, NOW);
        privateIsland.setVisibility(false);
        final TestIsland ownerless = publicIsland(SkyblockAddonCore.NO_OWNER_ID, NOW);
        final TestIsland banning = publicIsland(UUID.randomUUID(), NOW);
        banning.banPlayer(VIEWER);
        final Map<UUID, String> names = Map.of(aliceId, "Alice", bobId, "Bob", carolId, "Carol");
        final Function<Island, String> ownerNameOf = island -> names.getOrDefault(island.getOwner(), "Other");

        final List<Island> destinations = IslandTravelListing.travelDestinations(
                List.of(alice, bob, carol, privateIsland, ownerless, banning), VIEWER, "", ownerNameOf);

        assertEquals(List.of(bob, alice, carol), destinations);
    }

    @Test
    void searchMatchesOwnerNameIgnoringCase() {
        final UUID aliceId = UUID.randomUUID();
        final UUID bobId = UUID.randomUUID();
        final TestIsland alice = publicIsland(aliceId, NOW);
        final TestIsland bob = publicIsland(bobId, NOW);
        final Map<UUID, String> names = Map.of(aliceId, "Alice", bobId, "Bob");

        final List<Island> destinations = IslandTravelListing.travelDestinations(
                List.of(alice, bob), VIEWER, "  aLi ", island -> names.get(island.getOwner()));

        assertEquals(List.of(alice), destinations);
    }

    @Test
    void equalActivitySortsByOwnerName() {
        final UUID aliceId = UUID.randomUUID();
        final UUID bobId = UUID.randomUUID();
        final TestIsland bob = publicIsland(bobId, NOW);
        final TestIsland alice = publicIsland(aliceId, NOW);
        final Map<UUID, String> names = Map.of(aliceId, "alice", bobId, "Bob");

        final List<Island> destinations = IslandTravelListing.travelDestinations(
                List.of(bob, alice), VIEWER, null, island -> names.get(island.getOwner()));

        assertEquals(List.of(alice, bob), destinations);
    }

    @Test
    void playerCountIncludesOwner() {
        final TestIsland island = publicIsland(UUID.randomUUID(), NOW);
        island.addMember(UUID.randomUUID(), SkyblockAddonCore.MEMBERS_GROUP_ID);

        assertEquals(2, IslandTravelListing.playerCount(island));
    }

    @Test
    void lastActiveIsDescribedRelativeToToday() {
        assertEquals("Unknown", IslandTravelListing.describeLastActive(IslandTravelListing.NEVER_ACTIVE, NOW));
        assertEquals("Today", IslandTravelListing.describeLastActive(NOW - 60_000, NOW));
        assertEquals("Yesterday", IslandTravelListing.describeLastActive(NOW - ONE_DAY, NOW));
        assertEquals("5 days ago", IslandTravelListing.describeLastActive(NOW - 5 * ONE_DAY, NOW));
        assertEquals("2026-09-22", IslandTravelListing.describeLastActive(NOW - 60 * ONE_DAY, NOW));
    }

    @Test
    void ownerChangeClearsCachedName() {
        final TestIsland island = publicIsland(UUID.randomUUID(), NOW);
        island.name = "Previous owner";

        island.setOwner(UUID.randomUUID());

        assertEquals("", island.name);
    }
}
