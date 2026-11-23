package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandFileStoreTest {
    private static final UUID ISLAND = UUID.fromString("a753cd61-735e-486e-b3f3-421dd0e90264");

    private static byte[] readGzipped(final Path file) throws IOException {
        try (final InputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            return input.readAllBytes();
        }
    }

    @Test
    void writesTheGzippedBytesUnderTheIslandFileNameAndLeavesNoTempFile(@TempDir final Path folder) throws IOException {
        final byte[] island = "island nbt".getBytes(StandardCharsets.UTF_8);
        IslandFileStore.writeAtomically(folder, ISLAND, island);

        assertArrayEquals(island, readGzipped(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
        assertFalse(Files.exists(folder.resolve(ISLAND + IslandDataFiles.TEMPORARY_EXTENSION)));
    }

    @Test
    void replacesAnExistingFile(@TempDir final Path folder) throws IOException {
        IslandFileStore.writeAtomically(folder, ISLAND, "old".getBytes(StandardCharsets.UTF_8));
        IslandFileStore.writeAtomically(folder, ISLAND, "new".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), readGzipped(folder.resolve(IslandDataFiles.fileNameFor(ISLAND))));
    }

    @Test
    void createsAMissingFolder(@TempDir final Path folder) throws IOException {
        final Path islandData = folder.resolve("islanddata");
        IslandFileStore.writeAtomically(islandData, ISLAND, "island".getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.isRegularFile(islandData.resolve(IslandDataFiles.fileNameFor(ISLAND))));
    }

    @Test
    void deleteReportsWhetherAFileWasThere(@TempDir final Path folder) throws IOException {
        IslandFileStore.writeAtomically(folder, ISLAND, "island".getBytes(StandardCharsets.UTF_8));

        assertTrue(IslandFileStore.delete(folder, ISLAND));
        assertFalse(IslandFileStore.delete(folder, ISLAND));
    }
}
