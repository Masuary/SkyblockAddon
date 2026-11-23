package yorickbm.skyblockaddon.util;

import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.permissions.RiddenStorageVehicles;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The configured {@link RiddenStorageVehicles} rules, parsed again only when the config text changes (config reload).
 */
public final class RiddenStorageVehicleRules {
    public static final String CONFIG_KEY = "permissions.ridden_storage_vehicles";

    private static final AtomicReference<ParsedRules> LAST_PARSED = new AtomicReference<>(new ParsedRules("", List.of()));

    private RiddenStorageVehicleRules() {
    }

    public static List<String> rules() {
        final String rawValue = SkyblockAddonConfig.getForKey(CONFIG_KEY);
        final ParsedRules cached = LAST_PARSED.get();
        if (cached.rawValue().equals(rawValue)) return cached.rules();

        final ParsedRules parsed;
        try {
            parsed = new ParsedRules(rawValue, RiddenStorageVehicles.parseRules(rawValue));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException("SkyblockAddon config " + CONFIG_KEY + " holds an invalid rule: " + exception.getMessage(), exception);
        }
        LAST_PARSED.set(parsed);
        return parsed.rules();
    }

    private record ParsedRules(String rawValue, List<String> rules) {
    }
}
