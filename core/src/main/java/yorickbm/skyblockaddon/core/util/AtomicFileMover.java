package yorickbm.skyblockaddon.core.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFileMover {
    private AtomicFileMover() {}

    public static void moveReplacing(final Path source, final Path destination) throws IOException {
        moveReplacing(source, destination, Files::move);
    }

    public static void moveWithoutReplacement(final Path source, final Path destination) throws IOException {
        moveWithoutReplacement(source, destination, Files::move);
    }

    static void moveReplacing(
            final Path source,
            final Path destination,
            final MoveOperation moveOperation
    ) throws IOException {
        moveWithAtomicFallback(
                source,
                destination,
                moveOperation,
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
        );
    }

    static void moveWithoutReplacement(
            final Path source,
            final Path destination,
            final MoveOperation moveOperation
    ) throws IOException {
        moveWithAtomicFallback(source, destination, moveOperation, new CopyOption[0]);
    }

    private static void moveWithAtomicFallback(
            final Path source,
            final Path destination,
            final MoveOperation moveOperation,
            final CopyOption[] fallbackOptions
    ) throws IOException {
        final CopyOption[] atomicOptions = new CopyOption[fallbackOptions.length + 1];
        System.arraycopy(fallbackOptions, 0, atomicOptions, 0, fallbackOptions.length);
        atomicOptions[fallbackOptions.length] = StandardCopyOption.ATOMIC_MOVE;

        try {
            moveOperation.move(source, destination, atomicOptions);
        } catch (final AtomicMoveNotSupportedException exception) {
            moveOperation.move(source, destination, fallbackOptions);
        }
    }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path destination, CopyOption... options) throws IOException;
    }
}
