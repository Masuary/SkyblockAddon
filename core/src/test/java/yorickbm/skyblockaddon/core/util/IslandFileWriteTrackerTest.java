package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandFileWriteTrackerTest {
    private static final UUID ISLAND = UUID.fromString("a753cd61-735e-486e-b3f3-421dd0e90264");
    private static final UUID OTHER_ISLAND = UUID.fromString("004f267c-0000-4000-8000-000000000000");

    private static byte[] bytes(final String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anIslandNeverWrittenSinceBootIsWritten() {
        assertTrue(new IslandFileWriteTracker().needsWrite(ISLAND, bytes("island")));
    }

    @Test
    void unchangedBytesAfterASuccessfulWriteAreSkipped() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        tracker.recordWritten(ISLAND, bytes("island"));

        assertFalse(tracker.needsWrite(ISLAND, bytes("island")));
    }

    @Test
    void changedBytesAreWritten() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        tracker.recordWritten(ISLAND, bytes("island"));

        assertTrue(tracker.needsWrite(ISLAND, bytes("island with a new member")));
    }

    @Test
    void aFailedWriteIsRetriedBecauseOnlySuccessfulWritesAreRecorded() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        tracker.recordWritten(ISLAND, bytes("island"));

        assertTrue(tracker.needsWrite(ISLAND, bytes("changed but the write failed")));
        assertTrue(tracker.needsWrite(ISLAND, bytes("changed but the write failed")));
    }

    @Test
    void islandsAreTrackedSeparately() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        tracker.recordWritten(ISLAND, bytes("same content"));

        assertTrue(tracker.needsWrite(OTHER_ISLAND, bytes("same content")));
    }

    @Test
    void aForgottenIslandIsWrittenAgain() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        tracker.recordWritten(ISLAND, bytes("island"));
        tracker.forget(ISLAND);

        assertTrue(tracker.needsWrite(ISLAND, bytes("island")));
    }

    @Test
    void recordingKeepsADigestNotTheCallersArray() {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final byte[] written = bytes("island");
        tracker.recordWritten(ISLAND, written);
        written[0] = 'X';

        assertFalse(tracker.needsWrite(ISLAND, bytes("island")));
    }
}
