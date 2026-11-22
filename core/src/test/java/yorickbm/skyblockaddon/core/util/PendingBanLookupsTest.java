package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingBanLookupsTest {
    private static final UUID FIRST_PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SECOND_PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void firstLookupOfAPlayerStarts() {
        final PendingBanLookups lookups = new PendingBanLookups();

        assertEquals(Optional.empty(), lookups.tryStart(FIRST_PLAYER, "Griefer"));
    }

    @Test
    void secondLookupWhilePendingIsRefusedNamingThePendingName() {
        final PendingBanLookups lookups = new PendingBanLookups();
        lookups.tryStart(FIRST_PLAYER, "Griefer");

        assertEquals(Optional.of("Griefer"), lookups.tryStart(FIRST_PLAYER, "OtherName"));
        assertEquals(Optional.of("Griefer"), lookups.tryStart(FIRST_PLAYER, "Griefer"));
    }

    @Test
    void playersLookUpIndependently() {
        final PendingBanLookups lookups = new PendingBanLookups();
        lookups.tryStart(FIRST_PLAYER, "Griefer");

        assertEquals(Optional.empty(), lookups.tryStart(SECOND_PLAYER, "Griefer"));
    }

    @Test
    void finishedLookupAllowsTheNextOne() {
        final PendingBanLookups lookups = new PendingBanLookups();
        lookups.tryStart(FIRST_PLAYER, "Griefer");

        lookups.finish(FIRST_PLAYER);

        assertEquals(Optional.empty(), lookups.tryStart(FIRST_PLAYER, "OtherName"));
    }

    @Test
    void clearForgetsEveryPendingLookup() {
        final PendingBanLookups lookups = new PendingBanLookups();
        lookups.tryStart(FIRST_PLAYER, "Griefer");
        lookups.tryStart(SECOND_PLAYER, "OtherName");

        lookups.clear();

        assertEquals(Optional.empty(), lookups.tryStart(FIRST_PLAYER, "Griefer"));
        assertEquals(Optional.empty(), lookups.tryStart(SECOND_PLAYER, "OtherName"));
    }
}
