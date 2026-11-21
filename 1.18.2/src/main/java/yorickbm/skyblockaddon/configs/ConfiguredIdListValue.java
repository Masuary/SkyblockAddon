package yorickbm.skyblockaddon.configs;

import yorickbm.skyblockaddon.core.util.ConfiguredIds;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A config value holding a comma-separated list of registry ids, parsed again only when its text changes (config reload).
 */
public final class ConfiguredIdListValue {
    private final String configKey;
    private final AtomicReference<ParsedIds> lastParsed = new AtomicReference<>(ParsedIds.EMPTY);

    public ConfiguredIdListValue(final String configKey) {
        this.configKey = configKey;
    }

    public Set<String> ids() {
        final String rawValue = SkyblockAddonConfig.getForKey(this.configKey);
        final ParsedIds cached = this.lastParsed.get();
        if (cached.rawValue().equals(rawValue)) return cached.ids();

        final ParsedIds parsed = new ParsedIds(rawValue, ConfiguredIds.parse(rawValue));
        this.lastParsed.set(parsed);
        return parsed.ids();
    }

    private record ParsedIds(String rawValue, Set<String> ids) {
        private static final ParsedIds EMPTY = new ParsedIds("", Set.of());
    }
}
