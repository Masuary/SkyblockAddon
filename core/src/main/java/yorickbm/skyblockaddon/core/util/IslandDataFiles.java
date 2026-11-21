package yorickbm.skyblockaddon.core.util;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class IslandDataFiles {
    public static final String EXTENSION = ".nbt";
    public static final String TEMPORARY_EXTENSION = ".nbt.tmp";

    private static final Pattern ISLAND_FILE_NAME = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.nbt", Pattern.CASE_INSENSITIVE);

    private IslandDataFiles() {}

    public static String fileNameFor(final UUID islandId) {
        return islandId + EXTENSION;
    }

    /**
     * Island files are named after the island id. Any other file in the island folder (copies, backups,
     * temp files of an interrupted save) is not island data.
     */
    public static Optional<UUID> islandIdFromFileName(final String fileName) {
        final var matcher = ISLAND_FILE_NAME.matcher(fileName);
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(UUID.fromString(matcher.group(1)));
    }
}
