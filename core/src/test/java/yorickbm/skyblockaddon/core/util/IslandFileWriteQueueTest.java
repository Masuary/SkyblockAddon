package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandFileWriteQueueTest {
    private static final UUID ISLAND = UUID.fromString("a753cd61-735e-486e-b3f3-421dd0e90264");
    private static final UUID OTHER_ISLAND = UUID.fromString("004f267c-0000-4000-8000-000000000000");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static byte[] bytes(final String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readGzipped(final Path file) throws IOException {
        try (final InputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            return input.readAllBytes();
        }
    }

    private static IslandFileWriteQueue.IslandWrite queued(final IslandFileWriteTracker tracker, final UUID islandId, final String content) {
        return new IslandFileWriteQueue.IslandWrite(islandId, bytes(content), tracker.recordQueued(islandId, bytes(content)));
    }

    @Test
    void laterWritesOfAnIslandWinAndEveryBatchIsReported(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final List<IslandFileWriteQueue.BatchReport> reports = new CopyOnWriteArrayList<>();
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(folder, tracker, reports::add);

        queue.queueWrites(List.of(queued(tracker, ISLAND, "first"), queued(tracker, OTHER_ISLAND, "other")));
        queue.queueWrites(List.of(queued(tracker, ISLAND, "second")));
        queue.awaitQueued(TIMEOUT);

        assertArrayEquals(bytes("second"), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertArrayEquals(bytes("other"), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(OTHER_ISLAND))));
        assertEquals(List.of(2, 1), reports.stream().map(IslandFileWriteQueue.BatchReport::written).toList());
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void aDeleteRunsAfterTheWritesQueuedBeforeIt(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(folder, tracker, report -> {
        });

        queue.queueWrites(List.of(queued(tracker, ISLAND, "island")));
        queue.queueDelete(ISLAND, failure -> {
            throw new AssertionError(failure);
        });
        queue.awaitQueued(TIMEOUT);

        assertFalse(Files.exists(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void aFailedWriteIsReportedAndQueuedAgainAtTheNextSave(@TempDir final Path folder) throws Exception {
        final Path notAFolder = folder.resolve("islanddata");
        Files.writeString(notAFolder, "a file where the island folder should be");
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final List<IslandFileWriteQueue.BatchReport> reports = new CopyOnWriteArrayList<>();
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(notAFolder, tracker, reports::add);

        queue.queueWrites(List.of(queued(tracker, ISLAND, "island")));
        queue.awaitQueued(TIMEOUT);

        assertEquals(0, reports.get(0).written());
        assertTrue(reports.get(0).failures().containsKey(ISLAND));
        assertTrue(tracker.needsWrite(ISLAND, bytes("island")));
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void shutdownWaitsForQueuedWritesAndRefusesNewOnes(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(folder, tracker, report -> {
        });

        queue.queueWrites(List.of(queued(tracker, ISLAND, "island")));
        assertTrue(queue.shutdownAndDrain(TIMEOUT));

        assertTrue(Files.isRegularFile(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertThrows(IllegalStateException.class, () -> queue.queueWrites(List.of(queued(tracker, OTHER_ISLAND, "other"))));
    }

    @Test
    void awaitQueuedTimesOutWhileTheWriterIsBusy(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final CountDownLatch writerRelease = new CountDownLatch(1);
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(folder, tracker, report -> {
            try {
                writerRelease.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        queue.queueWrites(List.of(queued(tracker, ISLAND, "island")));
        assertThrows(TimeoutException.class, () -> queue.awaitQueued(Duration.ofMillis(100)));
        writerRelease.countDown();
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }
}
