package yorickbm.skyblockaddon.core.configs;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SkyBlockAddonLanguage {
    private static final Gson GSON = new Gson();
    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();

    private static final String BUNDLED_LANGUAGE = "/assets/" + SkyblockAddonCore.MOD_ID + "/lang/en_us.json";

    /**
     * Load the server's language file over the bundled English defaults, so keys added in a newer version
     * show English text until the server file defines them.
     */
    public static void loadLocalization(final Path path) {
        final Map<String, String> translations = new HashMap<>(loadBundledDefaults());
        try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            translations.putAll(parseTranslations(reader, path.toAbsolutePath().toString()));
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to read island language file '" + path.toAbsolutePath() + "': " + exception, exception);
        }
        LANGUAGE_MAP.clear();
        LANGUAGE_MAP.putAll(translations);
    }

    private static Map<String, String> loadBundledDefaults() {
        try (final InputStream bundled = SkyBlockAddonLanguage.class.getResourceAsStream(BUNDLED_LANGUAGE)) {
            if (bundled == null) throw new IllegalStateException("Bundled language file " + BUNDLED_LANGUAGE + " is missing from the jar");
            return parseTranslations(new InputStreamReader(bundled, StandardCharsets.UTF_8), BUNDLED_LANGUAGE);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to read bundled language file " + BUNDLED_LANGUAGE, exception);
        }
    }

    private static Map<String, String> parseTranslations(final Reader reader, final String source) {
        final Map<?, ?> loadedEntries;
        try {
            loadedEntries = GSON.fromJson(reader, Map.class);
        } catch (final RuntimeException exception) {
            throw new IllegalStateException("Failed to parse island language file '" + source + "': " + exception, exception);
        }
        if (loadedEntries == null) throw new IllegalStateException("Island language file '" + source + "' is empty");

        final Map<String, String> translations = new HashMap<>();
        loadedEntries.forEach((key, value) -> translations.put(String.valueOf(key), String.valueOf(value)));
        return translations;
    }

    public static String getLocalizedString(final String key) {
        return LANGUAGE_MAP.getOrDefault(key, key);
    }
}
