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
import java.util.function.Consumer;
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

    private static IslandFileWriteQueue newQueue(final Path folder, final IslandFileWriteTracker tracker, final Consumer<IslandFileWriteQueue.BatchReport> batchListener) {
        return new IslandFileWriteQueue(folder, tracker, batchListener, failure -> {
            throw new AssertionError("unexpected writer task failure", failure);
        });
    }

    @Test
    void laterWritesOfAnIslandWinAndEveryBatchIsReported(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final List<IslandFileWriteQueue.BatchReport> reports = new CopyOnWriteArrayList<>();
        final IslandFileWriteQueue queue = newQueue(folder, tracker, reports::add);

        queue.queueWrites(List.of(queued(tracker, ISLAND, "first"), queued(tracker, OTHER_ISLAND, "other")));
        queue.queueWrites(List.of(queued(tracker, ISLAND, "second")));
        queue.awaitQueued(TIMEOUT);

        assertArrayEquals(bytes("second"), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertArrayEquals(bytes("other"), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(OTHER_ISLAND))));
        assertEquals(2, reports.get(0).written() + reports.get(0).superseded());
        assertEquals(1, reports.get(1).written());
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void aDeleteRunsAfterTheWritesQueuedBeforeIt(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final IslandFileWriteQueue queue = newQueue(folder, tracker, report -> {
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
        final IslandFileWriteQueue queue = newQueue(notAFolder, tracker, reports::add);

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
        final IslandFileWriteQueue queue = newQueue(folder, tracker, report -> {
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
        final IslandFileWriteQueue queue = newQueue(folder, tracker, report -> {
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

    @Test
    void aFailingDeleteIsReportedToItsListener(@TempDir final Path folder) throws Exception {
        Files.createDirectories(folder.resolve(IslandDataFiles.fileNameFor(ISLAND)).resolve("not-empty"));
        final IslandFileWriteQueue queue = newQueue(folder, new IslandFileWriteTracker(), report -> {
        });
        final List<Exception> deleteFailures = new CopyOnWriteArrayList<>();

        queue.queueDelete(ISLAND, deleteFailures::add);
        queue.awaitQueued(TIMEOUT);

        assertEquals(1, deleteFailures.size());
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void anErrorInAWriterTaskIsReportedAndLaterTasksStillRun(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final List<Throwable> taskFailures = new CopyOnWriteArrayList<>();
        final List<IslandFileWriteQueue.BatchReport> reports = new CopyOnWriteArrayList<>();
        final IslandFileWriteQueue queue = new IslandFileWriteQueue(folder, tracker, report -> {
            reports.add(report);
            if (!report.failures().isEmpty()) throw new OutOfMemoryError("listener simulates an error");
        }, taskFailures::add);
        final Path notAFolder = folder.resolve("islanddata");
        Files.writeString(notAFolder, "a file where the island folder should be");
        final IslandFileWriteQueue failingQueue = new IslandFileWriteQueue(notAFolder, tracker, report -> {
            throw new OutOfMemoryError("listener simulates an error");
        }, taskFailures::add);

        failingQueue.queueWrites(List.of(queued(tracker, ISLAND, "island")));
        failingQueue.awaitQueued(TIMEOUT);
        queue.queueWrites(List.of(queued(tracker, OTHER_ISLAND, "other")));
        queue.awaitQueued(TIMEOUT);

        assertEquals(1, taskFailures.size());
        assertTrue(taskFailures.get(0) instanceof OutOfMemoryError);
        assertTrue(tracker.needsWrite(ISLAND, bytes("island")));
        assertEquals(1, reports.get(0).written());
        assertTrue(failingQueue.shutdownAndDrain(TIMEOUT));
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void aWriteSupersededBeforeItRanIsSkipped(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final List<IslandFileWriteQueue.BatchReport> reports = new CopyOnWriteArrayList<>();
        final IslandFileWriteQueue queue = newQueue(folder, tracker, reports::add);
        final IslandFileWriteQueue.IslandWrite older = queued(tracker, ISLAND, "older");
        final IslandFileWriteQueue.IslandWrite newer = queued(tracker, ISLAND, "newer");

        queue.queueWrites(List.of(older));
        queue.queueWrites(List.of(newer));
        queue.awaitQueued(TIMEOUT);

        assertArrayEquals(bytes("newer"), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertEquals(1, reports.get(0).superseded());
        assertEquals(1, reports.get(1).written());
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }

    @Test
    void aForgottenIslandsPendingWriteDoesNotRecreateItsFile(@TempDir final Path folder) throws Exception {
        final IslandFileWriteTracker tracker = new IslandFileWriteTracker();
        final IslandFileWriteQueue queue = newQueue(folder, tracker, report -> {
        });
        final IslandFileWriteQueue.IslandWrite pending = queued(tracker, ISLAND, "island");
        tracker.forget(ISLAND);

        queue.queueWrites(List.of(pending));
        queue.awaitQueued(TIMEOUT);

        assertFalse(Files.exists(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertTrue(queue.shutdownAndDrain(TIMEOUT));
    }
}
