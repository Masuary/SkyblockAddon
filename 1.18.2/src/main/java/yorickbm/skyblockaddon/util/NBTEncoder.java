package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.util.IslandDataFiles;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class NBTEncoder {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Load a single NBT file from a folder by UUID filename.
     *
     * @param folderPath - Folder to load NBT file from
     * @param clazz      - Class instance in which to load the NBT file
     * @param uuid       - UUID matching the filename (<uuid>.nbt)
     */
    public static <T extends NBTSerializable> T loadSingleFromFolder(final Path folderPath, final Class<T> clazz, final UUID uuid) {
        final Path filePath = folderPath.resolve(uuid.toString() + ".nbt");

        if (!Files.exists(filePath)) {
            LOGGER.warn("Island file not found: " + filePath.getFileName());
            return null;
        }

        try (final FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {
            final CompoundTag NBT = NbtIo.readCompressed(fileInputStream);
            final T instance = clazz.getDeclaredConstructor().newInstance();
            instance.deserializeNBT(NBT);
            return instance;
        } catch (final Exception e) {
            LOGGER.error("Failed to load island file '{}'", filePath.toAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Load NBT files into a collection of CompoundTags
     *
     * @param folderPath - Folder to load NBT files from
     */
    public static Collection<CompoundTag> loadNBTFromFolder(final Path folderPath) {
        final Collection<CompoundTag> objects = new ArrayList<>();

        //Create folder if it doesn't exist
        if (!folderPath.toFile().exists()) {
            final boolean rslt = folderPath.toFile().mkdirs();
            if (!rslt) {
                throw new RuntimeException("Failed to create container at '" + folderPath.toFile().getAbsolutePath() + "'.");
            }
        }

        //List files into list (skipping in-progress temp files)
        final List<Path> nbtFiles = new ArrayList<>();
        try {
            Files.list(folderPath)
                    .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .forEach(nbtFiles::add);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        for (final Path path : nbtFiles) {
            try (final FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
                final CompoundTag NBT = NbtIo.readCompressed(fileInputStream);
                objects.add(NBT);
            } catch (final Exception e) {
                LOGGER.error("Failed to load '"+path.toFile().getName()+"'", e);
            }
        }

        return objects;
    }

    /**
     * Load NBT files into a collection of clazz.
     *
     * @param folderPath - Folder to load NBT files from
     * @param clazz      - Class instance in which to load the NBT files
     */
    public static <T extends NBTSerializable> Collection<T> loadFromFolder(final Path folderPath, final Class<T> clazz) throws RuntimeException {
        final Collection<T> objects = new ArrayList<>();

        //Create folder if it doesn't exist
        if (!folderPath.toFile().exists()) {
            final boolean rslt = folderPath.toFile().mkdirs();
            if (!rslt) {
                throw new RuntimeException("Failed to create container at '" + folderPath.toFile().getAbsolutePath() + "'.");
            }
        }

        final List<Path> islandFiles = new ArrayList<>();
        try (final Stream<Path> folderEntries = Files.list(folderPath)) {
            folderEntries.filter(Files::isRegularFile).forEach(path -> {
                if (IslandDataFiles.islandIdFromFileName(path.getFileName().toString()).isPresent()) {
                    islandFiles.add(path);
                } else if (!path.getFileName().toString().endsWith(IslandDataFiles.TEMPORARY_EXTENSION)) {
                    LOGGER.warn("Ignoring '{}' in '{}': island files are named <island id>.nbt", path.getFileName(), folderPath);
                }
            });
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list island files in '" + folderPath.toAbsolutePath() + "'", e);
        }

        for (final Path path : islandFiles) {
            final UUID idFromFileName = IslandDataFiles.islandIdFromFileName(path.getFileName().toString()).orElseThrow();
            try (final FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
                final CompoundTag NBT = NbtIo.readCompressed(fileInputStream);
                final T instance = clazz.getDeclaredConstructor().newInstance();
                instance.deserializeNBT(NBT);
                if (!idFromFileName.equals(instance.getId())) {
                    LOGGER.error("Island file '{}' holds island {}; not loaded. Rename it to {} if it is the only copy of that island.",
                            path.toAbsolutePath(), instance.getId(), IslandDataFiles.fileNameFor(instance.getId()));
                    continue;
                }
                objects.add(instance);
            } catch (final Exception e) {
                LOGGER.error("Failed to load island file '{}'; that island is not loaded", path.toAbsolutePath(), e);
            }
        }
        return objects;
    }

    /**
     * Save a collection of clazz to nbt files.
     *
     * Each file is written via temp-file + atomic rename so a crash mid-write cannot leave a
     * truncated/corrupt file on disk. Stale `.tmp` siblings from a prior aborted save are
     * cleaned up before the new attempt.
     *
     * @param collection - Collection of clazz
     */
    public static <T extends NBTSerializable> void saveToFile(final Collection<T> collection, final Path filePath) throws RuntimeException {
        //Create folder if it doesn't exist
        if (!filePath.toFile().exists()) {
            final boolean rslt = filePath.toFile().mkdirs();
            if (!rslt) {
                throw new RuntimeException("Failed to create container at '" + filePath.toFile().getAbsolutePath() + "'.");
            }
        }

        final List<UUID> failedSaves = new ArrayList<>();
        for (final T data : collection) {
            try {
                writeAtomically(data, filePath);
            } catch (final IOException | RuntimeException e) {
                failedSaves.add(data.getId());
                LOGGER.error("Failed to save island {} to '{}'", data.getId(), filePath.toAbsolutePath(), e);
            }
        }
        if (!failedSaves.isEmpty()) {
            LOGGER.error("{} of {} island file(s) were not saved: {}", failedSaves.size(), collection.size(), failedSaves);
        }
    }

    private static <T extends NBTSerializable> void writeAtomically(final T data, final Path folderPath) throws IOException {
        final Path path = folderPath.resolve(IslandDataFiles.fileNameFor(data.getId()));
        final Path tempPath = folderPath.resolve(data.getId() + IslandDataFiles.TEMPORARY_EXTENSION);
        try {
            try (final OutputStream out = Files.newOutputStream(tempPath)) {
                NbtIo.writeCompressed(data.serializeNBT(), out);
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
     * Remove an NBT file with the given UUID from the specified folder.
     *
     * @param folderPath - Folder where the NBT file is stored
     * @param object     - Object to remove
     *
     * @returns - If the file has been able to be removed
     */
    public static <T extends NBTSerializable> boolean removeFileFromFolder(Path folderPath, T object) {
        // Resolve the path to the specific file
        Path fileToDelete = folderPath.resolve(object.getId().toString() + ".nbt");
        // Check if the file exists before attempting to delete
        if (Files.exists(fileToDelete)) {
            try {
                Files.delete(fileToDelete);
                LOGGER.info("Successfully deleted NBT file: " + fileToDelete.getFileName());
                return true;
            } catch (IOException e) {
                LOGGER.error("Failed to delete file '" + fileToDelete.getFileName() + "': " + e.getMessage());
                return false;
            }
        } else {
            LOGGER.warn("File not found for deletion: " + fileToDelete.getFileName());
            return false;
        }
    }
}
