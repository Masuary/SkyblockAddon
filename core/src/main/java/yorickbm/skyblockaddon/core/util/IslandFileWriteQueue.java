package yorickbm.skyblockaddon.core.util;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Writes and deletes island files on one background thread, in the order they were queued, so the server thread never
 * waits for the disk (an fsync per island file took 12-22 s for 288 islands on a loaded HDD). The server thread
 * serializes the islands and queues their bytes; a later write or delete of the same island always runs after an
 * earlier one. Pending work is lost only if the JVM dies before {@link #shutdownAndDrain} ran, like the world's own
 * chunk writes.
 */
public final class IslandFileWriteQueue {
    private static final String THREAD_NAME = "SkyblockAddon island writer";

    private final Path folderPath;
    private final IslandFileWriteTracker writeTracker;
    private final Consumer<BatchReport> batchListener;
    private final Consumer<Throwable> taskFailureListener;
    private final ExecutorService writer;

    /**
     * One island's bytes queued by the server thread, with the digest the tracker recorded for them.
     */
    public record IslandWrite(UUID islandId, byte[] uncompressedNbt, IslandFileWriteTracker.QueuedDigest queuedDigest) {
    }

    /**
     * What happened to one queued batch: how many files were written, how many were skipped because a newer version of
     * the island was queued after them, which failed and why, and how long it took.
     */
    public record BatchReport(int written, int superseded, Map<UUID, Throwable> failures, long millis) {
    }

    /**
     * @param batchListener       called on the writer thread after each batch of writes, to log it
     * @param taskFailureListener called on the writer thread when a queued task fails outside a single island's write
     *                            (an {@link Error}, a failing listener), so no failure goes unreported
     */
    public IslandFileWriteQueue(final Path folderPath, final IslandFileWriteTracker writeTracker, final Consumer<BatchReport> batchListener,
                                final Consumer<Throwable> taskFailureListener) {
        this.folderPath = folderPath;
        this.writeTracker = writeTracker;
        this.batchListener = batchListener;
        this.taskFailureListener = taskFailureListener;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void queueWrites(final List<IslandWrite> writes) {
        if (writes.isEmpty()) return;
        final List<IslandWrite> batch = List.copyOf(writes);
        submit(() -> writeBatch(batch));
    }

    /**
     * Delete an island's file after every write queued before it.
     *
     * @param failureListener called on the writer thread when the delete fails
     */
    public void queueDelete(final UUID islandId, final Consumer<Exception> failureListener) {
        submit(() -> {
            try {
                IslandFileStore.delete(this.folderPath, islandId);
            } catch (final Exception deleteFailure) {
                failureListener.accept(deleteFailure);
            }
        });
    }

    /**
     * Wait until everything queued so far is on disk.
     *
     * @throws TimeoutException when the writes take longer than the timeout
     */
    public void awaitQueued(final Duration timeout) throws TimeoutException, InterruptedException {
        final Future<?> marker = submit(() -> {
        });
        try {
            marker.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final ExecutionException impossible) {
            throw new IllegalStateException("The island writer's empty marker task failed", impossible);
        }
    }

    /**
     * Stop taking work and wait for everything queued to finish.
     *
     * @return true when the queue drained in time, false when writes were still running at the timeout
     */
    public boolean shutdownAndDrain(final Duration timeout) throws InterruptedException {
        this.writer.shutdown();
        return this.writer.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Future<?> submit(final Runnable task) {
        try {
            return this.writer.submit(() -> runReportingFailures(task));
        } catch (final RejectedExecutionException closed) {
            throw new IllegalStateException("The island writer was shut down; island files can no longer be written for this server", closed);
        }
    }

    private void runReportingFailures(final Runnable task) {
        try {
            task.run();
        } catch (final Throwable taskFailure) {
            this.taskFailureListener.accept(taskFailure);
            if (taskFailure instanceof final Error error) throw error;
        }
    }

    /**
     * An {@link Error} while writing one island (out of memory) stops the batch: that island and the rest are handed
     * back to the tracker so the next save writes them, the batch is reported, and the error is rethrown.
     */
    private void writeBatch(final List<IslandWrite> batch) {
        final long startNanos = System.nanoTime();
        int written = 0;
        int superseded = 0;
        final Map<UUID, Throwable> failures = new LinkedHashMap<>();
        for (int index = 0; index < batch.size(); index++) {
            final IslandWrite write = batch.get(index);
            if (!this.writeTracker.isLatestQueued(write.islandId(), write.queuedDigest())) {
                superseded++;
                continue;
            }
            try {
                IslandFileStore.writeAtomically(this.folderPath, write.islandId(), write.uncompressedNbt());
                written++;
            } catch (final Exception writeFailure) {
                this.writeTracker.writeFailed(write.islandId(), write.queuedDigest());
                failures.put(write.islandId(), writeFailure);
            } catch (final Error fatal) {
                for (final IslandWrite unwritten : batch.subList(index, batch.size())) {
                    this.writeTracker.writeFailed(unwritten.islandId(), unwritten.queuedDigest());
                    failures.put(unwritten.islandId(), fatal);
                }
                this.batchListener.accept(new BatchReport(written, superseded, failures, (System.nanoTime() - startNanos) / 1_000_000L));
                throw fatal;
            }
        }
        this.batchListener.accept(new BatchReport(written, superseded, failures, (System.nanoTime() - startNanos) / 1_000_000L));
    }

}
