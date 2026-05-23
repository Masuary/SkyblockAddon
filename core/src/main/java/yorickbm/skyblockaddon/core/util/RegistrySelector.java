package yorickbm.skyblockaddon.core.util;

import java.util.Map;
import java.util.function.Supplier;

public final class RegistrySelector {

    /**
     * Map of registry name → function that returns the path to the JSON to load
     */
    private final Map<String, Supplier<String>> registryMap;

    public RegistrySelector(Map<String, Supplier<String>> registryMap) {
        this.registryMap = registryMap;
    }

    /**
     * Returns the registry file path for the given key.
     */
    public String getRegistry(String key) {
        Supplier<String> supplier = registryMap.get(key);
        if (supplier == null) {
            throw new IllegalArgumentException("No registry mapping for key: " + key);
        }
        return supplier.get();
    }
}
