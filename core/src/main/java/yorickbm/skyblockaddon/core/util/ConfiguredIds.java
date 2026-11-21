package yorickbm.skyblockaddon.core.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry ids configured as one comma-separated config value (for example "minecraft:jukebox, create:linked_controller").
 */
public final class ConfiguredIds {

    private ConfiguredIds() {
    }

    public static Set<String> parse(final String commaSeparatedIds) {
        return Arrays.stream(commaSeparatedIds.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
