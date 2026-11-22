package yorickbm.skyblockaddon.core.permissions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The permission categories of the bundled registries, in screen order, with the label players see.
 */
public final class PermissionCategories {
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("general", "General");
        LABELS.put("building", "Building");
        LABELS.put("storage", "Storage");
        LABELS.put("stations", "Stations");
        LABELS.put("animals", "Animals and Mobs");
        LABELS.put("redstone", "Doors and Redstone");
        LABELS.put("transport", "Travel");
        LABELS.put("vaulthunters", "Vault Hunters");
        LABELS.put("mods", "Mods");
        LABELS.put("admin_controls", "Admin");
    }

    private PermissionCategories() {
    }

    /**
     * The label of a category id, or the id itself with its first letter capitalised for categories a server added.
     */
    public static String labelFor(final String categoryId) {
        final String knownLabel = LABELS.get(categoryId.toLowerCase());
        if (knownLabel != null) return knownLabel;
        final String spaced = categoryId.replace("_", " ");
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public static Map<String, String> labelsInScreenOrder() {
        return Collections.unmodifiableMap(LABELS);
    }
}
