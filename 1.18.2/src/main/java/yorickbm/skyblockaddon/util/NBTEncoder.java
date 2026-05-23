package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
            LOGGER.error("Failed to load '" + filePath.getFileName() + "'");
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

        //List files into list
        final List<Path> nbtFiles = new ArrayList<>();
        try {
            Files.list(folderPath).forEach(nbtFiles::add);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        for (final Path path : nbtFiles) {
            try (final FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
                final CompoundTag NBT = NbtIo.readCompressed(fileInputStream);
                objects.add(NBT);
            } catch (final Exception e) {
                LOGGER.error("Failed to load '"+path.toFile().getName()+"'");
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

        //List files into list
        final List<Path> nbtFiles = new ArrayList<>();
        try {
            Files.list(folderPath).forEach(nbtFiles::add);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        for (final Path path : nbtFiles) {
            try (final FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
                final CompoundTag NBT = NbtIo.readCompressed(fileInputStream);
                final T instance = clazz.getDeclaredConstructor().newInstance();
                instance.deserializeNBT(NBT);
                objects.add(instance);
            } catch (final Exception e) {
                LOGGER.error("Failed to load '"+path.toFile().getName()+"'");
            }
        }
        return objects;
    }

    /**
     * Save a collection of clazz to nbt files.
     *
     * @param collection - Collection of clazz
     */
    public static <T extends NBTSerializable> void saveToFile(final Collection<T> collection, final Path filePath) throws RuntimeException {
        for (final T data : collection) {
            try {
                final Path path = filePath.resolve(data.getId().toString() + ".nbt");

                //Create folder if it doesn't exist
                if (!filePath.toFile().exists()) {
                    final boolean rslt = filePath.toFile().mkdirs();
                    if (!rslt) {
                        throw new RuntimeException("Failed to create container at '" + filePath.toFile().getAbsolutePath() + "'.");
                    }
                }

                //Create file if it does not exist
                if (!Files.exists(path)) {
                    Files.createFile(path);
                }

                //Write NBT to file
                final FileOutputStream fileOutputStream = new FileOutputStream(path.toFile());
                NbtIo.writeCompressed(data.serializeNBT(), fileOutputStream);
                fileOutputStream.close();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
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
