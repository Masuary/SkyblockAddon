package yorickbm.skyblockaddon.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers a SHA-256 digest of the serialized bytes of each island file queued for writing since boot, so a save can
 * skip the islands that did not change. The server thread records a digest when it queues the write; the island writer
 * thread hands it back through {@link #writeFailed} when the write fails, so that island is written again at the next
 * save unless a newer version of it was queued meanwhile. Thread-safe.
 */
public final class IslandFileWriteTracker {
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final Map<UUID, QueuedDigest> lastQueuedDigests = new HashMap<>();

    /**
     * The digest of one queued island write, handed back to {@link #writeFailed} by the thread that performed it.
     */
    public static final class QueuedDigest {
        private final byte[] digest;

        private QueuedDigest(final byte[] digest) {
            this.digest = digest;
        }
    }

    public synchronized boolean needsWrite(final UUID islandId, final byte[] serializedIsland) {
        final QueuedDigest lastQueued = this.lastQueuedDigests.get(islandId);
        return lastQueued == null || !MessageDigest.isEqual(lastQueued.digest, digestOf(serializedIsland));
    }

    public synchronized QueuedDigest recordQueued(final UUID islandId, final byte[] serializedIsland) {
        final QueuedDigest queued = new QueuedDigest(digestOf(serializedIsland));
        this.lastQueuedDigests.put(islandId, queued);
        return queued;
    }

    /**
     * A queued write failed: the island is written again at the next save, unless a newer version was queued after it
     * (that write is still pending and carries the current bytes).
     */
    public synchronized void writeFailed(final UUID islandId, final QueuedDigest failedWrite) {
        this.lastQueuedDigests.remove(islandId, failedWrite);
    }

    public synchronized void forget(final UUID islandId) {
        this.lastQueuedDigests.remove(islandId);
    }

    private static byte[] digestOf(final byte[] serializedIsland) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(serializedIsland);
        } catch (final NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException("Every Java runtime must provide " + DIGEST_ALGORITHM, missingAlgorithm);
        }
    }
}
