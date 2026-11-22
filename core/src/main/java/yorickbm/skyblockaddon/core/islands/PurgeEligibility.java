package yorickbm.skyblockaddon.core.islands;

/**
 * Whether /island admin purge may free an island. An abandoned island with no tracked chunks is refused: there is
 * nothing to clear, and the carried spawn island is kept that way.
 */
public enum PurgeEligibility {
    PURGEABLE("commands.admin.purge.ask"),
    NOT_ABANDONED("commands.admin.purge.notallowed"),
    NO_TRACKED_CHUNKS("commands.admin.purge.no.chunks");

    private final String languageKey;

    PurgeEligibility(final String languageKey) {
        this.languageKey = languageKey;
    }

    public static PurgeEligibility of(final Island island, final int trackedChunkCount) {
        if (!island.isAbandoned()) return NOT_ABANDONED;
        if (trackedChunkCount == 0) return NO_TRACKED_CHUNKS;
        return PURGEABLE;
    }

    public String languageKey() {
        return languageKey;
    }

    public boolean purgeable() {
        return this == PURGEABLE;
    }
}
