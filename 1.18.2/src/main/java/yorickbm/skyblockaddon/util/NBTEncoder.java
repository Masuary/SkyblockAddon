package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.util.IslandDataFiles;
import yorickbm.skyblockaddon.core.util.IslandFileStore;
import yorickbm.skyblockaddon.core.util.IslandFileWriteQueue;
import yorickbm.skyblockaddon.core.util.IslandFileWriteTracker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Each file is written via temp-file + fsync + atomic rename so a crash mid-write cannot leave a
     * truncated/corrupt file on disk. Stale `.tmp` siblings from a prior aborted save are
     * cleaned up before the new attempt.
     *
     * @param collection - Collection of clazz
     */
    public static <T extends NBTSerializable> void saveToFile(final Collection<T> collection, final Path filePath) throws RuntimeException {
        createFolderIfMissing(filePath);

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

    public record IslandSerializationReport(List<IslandFileWriteQueue.IslandWrite> changed, int unchanged, List<UUID> failed) {
    }

    /**
     * Serialize every island and keep only those whose bytes differ from their last queued write (every island, the
     * first time after boot), recording them as queued in the tracker. Runs on the server thread without touching the
     * disk; the caller hands the result to the island writer. An island that fails to serialize is reported and tried
     * again at the next save.
     */
    public static <T extends NBTSerializable> IslandSerializationReport serializeChanged(final Collection<T> collection,
                                                                                         final IslandFileWriteTracker writeTracker) {
        final List<IslandFileWriteQueue.IslandWrite> changed = new ArrayList<>();
        final List<UUID> failedSerializations = new ArrayList<>();
        try {
            serializeEach(collection, writeTracker, changed, failedSerializations);
        } catch (final Error fatal) {
            changed.forEach(write -> writeTracker.writeFailed(write.islandId(), write.queuedDigest()));
            throw fatal;
        }
        if (!failedSerializations.isEmpty()) {
            LOGGER.error("{} of {} island(s) were not saved: {}", failedSerializations.size(), collection.size(), failedSerializations);
        }
        return new IslandSerializationReport(List.copyOf(changed), collection.size() - changed.size() - failedSerializations.size(), List.copyOf(failedSerializations));
    }

    private static <T extends NBTSerializable> void serializeEach(final Collection<T> collection, final IslandFileWriteTracker writeTracker,
                                                                  final List<IslandFileWriteQueue.IslandWrite> changed, final List<UUID> failedSerializations) {
        for (final T data : collection) {
            try {
                final byte[] serializedIsland = uncompressedBytes(data.serializeNBT());
                if (!writeTracker.needsWrite(data.getId(), serializedIsland)) continue;
                changed.add(new IslandFileWriteQueue.IslandWrite(data.getId(), serializedIsland, writeTracker.recordQueued(data.getId(), serializedIsland)));
            } catch (final IOException | RuntimeException e) {
                failedSerializations.add(data.getId());
                LOGGER.error("Failed to serialize island {} for saving", data.getId(), e);
            }
        }
    }

    private static void createFolderIfMissing(final Path folderPath) {
        if (folderPath.toFile().exists()) return;
        if (!folderPath.toFile().mkdirs()) {
            throw new RuntimeException("Failed to create container at '" + folderPath.toFile().getAbsolutePath() + "'.");
        }
    }

    private static byte[] uncompressedBytes(final CompoundTag tag) throws IOException {
        final ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (final DataOutputStream output = new DataOutputStream(serialized)) {
            NbtIo.write(tag, output);
        }
        return serialized.toByteArray();
    }

    private static <T extends NBTSerializable> void writeAtomically(final T data, final Path folderPath) throws IOException {
        IslandFileStore.writeAtomically(folderPath, data.getId(), uncompressedBytes(data.serializeNBT()));
    }
}
