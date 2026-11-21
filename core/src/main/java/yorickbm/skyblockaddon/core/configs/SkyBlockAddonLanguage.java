package yorickbm.skyblockaddon.core.configs;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SkyBlockAddonLanguage {
    private static final Gson GSON = new Gson();
    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();

    public static void loadLocalization(final Path path) {
        final Map<?, ?> loadedEntries;
        try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loadedEntries = GSON.fromJson(reader, Map.class);
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load island language file '" + path.toAbsolutePath() + "': " + exception, exception);
        }
        if (loadedEntries == null) {
            throw new IllegalStateException("Island language file '" + path.toAbsolutePath() + "' is empty");
        }

        final Map<String, String> translations = new HashMap<>();
        loadedEntries.forEach((key, value) -> translations.put(String.valueOf(key), String.valueOf(value)));
        LANGUAGE_MAP.clear();
        LANGUAGE_MAP.putAll(translations);
    }

    public static String getLocalizedString(final String key) {
        return LANGUAGE_MAP.getOrDefault(key, key);
    }
}
