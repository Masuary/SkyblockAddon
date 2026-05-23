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
        LANGUAGE_MAP.clear(); //Make sure its empty

        try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final HashMap<String, String> map = GSON.fromJson(reader, HashMap.class);
            LANGUAGE_MAP.putAll(map);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getLocalizedString(final String key) {
        return LANGUAGE_MAP.getOrDefault(key, key);
    }
}
