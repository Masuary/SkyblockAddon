package yorickbm.guilibrary.util.JSON;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JSONEncoder {
    public static <T extends JSONSerializable> T loadFromFile(final Path filePath, final Class<T> clazz) throws Exception {
        final String content = new String(Files.readAllBytes(filePath));
        final T instance = clazz.getDeclaredConstructor().newInstance();
        instance.fromJSON(content);
        return instance;
    }

    public static <T extends JSONSerializable> Collection<T> loadFromFolder(final Path folderPath, final Class<T> clazz) {
        final Collection<T> objects = new ArrayList<>();

        //Create folder if it doesn't exist
        if (!folderPath.toFile().exists()) {
            final boolean rslt = folderPath.toFile().mkdirs();
            if (!rslt) {
                throw new RuntimeException("Failed to create container at '" + folderPath.toFile().getAbsolutePath() + "'.");
            }
        }

        //List files into list
        final List<Path> files = new ArrayList<>();
        try {
            Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(files::add);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        for (final Path file : files) {
            try {
                objects.add(loadFromFile(file, clazz));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        return objects;
    }

}
