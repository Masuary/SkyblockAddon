package yorickbm.skyblockaddon.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers a SHA-256 digest of the serialized bytes of each island file written since boot, so a save can skip the
 * islands that did not change. Only successful writes are recorded, so a failed write is retried at the next save.
 * Not thread-safe: islands are saved on the server thread only.
 */
public final class IslandFileWriteTracker {
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final Map<UUID, byte[]> lastWrittenDigests = new HashMap<>();

    public boolean needsWrite(final UUID islandId, final byte[] serializedIsland) {
        final byte[] lastWrittenDigest = this.lastWrittenDigests.get(islandId);
        return lastWrittenDigest == null || !MessageDigest.isEqual(lastWrittenDigest, digestOf(serializedIsland));
    }

    public void recordWritten(final UUID islandId, final byte[] serializedIsland) {
        this.lastWrittenDigests.put(islandId, digestOf(serializedIsland));
    }

    public void forget(final UUID islandId) {
        this.lastWrittenDigests.remove(islandId);
    }

    private static byte[] digestOf(final byte[] serializedIsland) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(serializedIsland);
        } catch (final NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException("Every Java runtime must provide " + DIGEST_ALGORITHM, missingAlgorithm);
        }
    }
}
