package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileMoverTest {
    @Test
    void replacesDestinationUsingAtomicMoveWhenSupported(@TempDir final Path directory) throws Exception {
        final Path source = directory.resolve("source.tmp");
        final Path destination = directory.resolve("destination.json");
        Files.writeString(source, "replacement");
        Files.writeString(destination, "original");

        AtomicFileMover.moveReplacing(source, destination);

        assertEquals("replacement", Files.readString(destination));
        assertFalse(Files.exists(source));
    }

    @Test
    void fallsBackOnlyWhenAtomicMoveIsUnsupported() throws Exception {
        final List<List<CopyOption>> attempts = new ArrayList<>();

        AtomicFileMover.moveReplacing(
                Path.of("source.tmp"),
                Path.of("destination.json"),
                (source, destination, options) -> {
                    attempts.add(Arrays.asList(options));
                    if (attempts.size() == 1) {
                        throw new AtomicMoveNotSupportedException(
                                source.toString(),
                                destination.toString(),
                                "test filesystem"
                        );
                    }
                }
        );

        assertEquals(2, attempts.size());
        assertTrue(attempts.get(0).contains(StandardCopyOption.ATOMIC_MOVE));
        assertTrue(attempts.get(0).contains(StandardCopyOption.REPLACE_EXISTING));
        assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING), attempts.get(1));
    }

    @Test
    void propagatesUnexpectedAtomicMoveFailureWithoutFallback() {
        final List<List<CopyOption>> attempts = new ArrayList<>();
        final IOException failure = new IOException("permission denied");

        final IOException thrown = assertThrows(
                IOException.class,
                () -> AtomicFileMover.moveReplacing(
                        Path.of("source.tmp"),
                        Path.of("destination.json"),
                        (source, destination, options) -> {
                            attempts.add(Arrays.asList(options));
                            throw failure;
                        }
                )
        );

        assertEquals(failure, thrown);
        assertEquals(1, attempts.size());
        assertTrue(attempts.get(0).contains(StandardCopyOption.ATOMIC_MOVE));
    }

    @Test
    void propagatesFallbackFailure() {
        final List<List<CopyOption>> attempts = new ArrayList<>();

        final IOException thrown = assertThrows(
                IOException.class,
                () -> AtomicFileMover.moveWithoutReplacement(
                        Path.of("source.tmp"),
                        Path.of("destination"),
                        (source, destination, options) -> {
                            attempts.add(Arrays.asList(options));
                            if (attempts.size() == 1) {
                                throw new AtomicMoveNotSupportedException(
                                        source.toString(),
                                        destination.toString(),
                                        "test filesystem"
                                );
                            }
                            throw new IOException("fallback failed");
                        }
                )
        );

        assertEquals("fallback failed", thrown.getMessage());
        assertEquals(2, attempts.size());
        assertTrue(attempts.get(0).contains(StandardCopyOption.ATOMIC_MOVE));
        assertTrue(attempts.get(1).isEmpty());
    }
}
