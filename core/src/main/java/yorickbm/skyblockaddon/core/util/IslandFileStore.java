package yorickbm.skyblockaddon.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Island files on disk: {@code <island id>.nbt}, the GZIP-compressed NBT of the island (the format of
 * {@code NbtIo.writeCompressed}), written from the uncompressed NBT bytes so the writing thread never touches a live
 * NBT object.
 */
public final class IslandFileStore {
    private IslandFileStore() {}

    /**
     * Write through temp file + fsync + atomic rename, so a crash mid-write leaves the previous file intact.
     */
    public static void writeAtomically(final Path folderPath, final UUID islandId, final byte[] uncompressedNbt) throws IOException {
        Files.createDirectories(folderPath);
        final Path path = folderPath.resolve(IslandDataFiles.fileNameFor(islandId));
        final Path tempPath = folderPath.resolve(islandId + IslandDataFiles.TEMPORARY_EXTENSION);
        try {
            try (final FileChannel tempFile = FileChannel.open(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                final ByteBuffer remainingBytes = ByteBuffer.wrap(gzip(uncompressedNbt));
                while (remainingBytes.hasRemaining()) tempFile.write(remainingBytes);
                tempFile.force(true);
            }
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException atomicMoveNotSupported) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    /**
     * @return whether a file was deleted (false when there was none)
     */
    public static boolean delete(final Path folderPath, final UUID islandId) throws IOException {
        return Files.deleteIfExists(folderPath.resolve(IslandDataFiles.fileNameFor(islandId)));
    }

    private static byte[] gzip(final byte[] uncompressed) throws IOException {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (final GZIPOutputStream gzipStream = new GZIPOutputStream(compressed)) {
            gzipStream.write(uncompressed);
        }
        return compressed.toByteArray();
    }
}
