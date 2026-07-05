package yorickbm.skyblockaddon.core.permissions;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PermissionStateMigrator {
    private static final MigrationDefinition DEFINITION = loadDefinition();
    public static final int CURRENT_SCHEMA_VERSION = DEFINITION.schemaVersion;
    private static final Map<String, List<String>> REPLACEMENTS = copyReplacements(DEFINITION.replacements);
    private static final Set<String> RETIRED_PERMISSION_IDS = Set.copyOf(DEFINITION.retired);

    private PermissionStateMigrator() {}

    /**
     * Copies legacy permission values to replacement IDs that were not explicitly stored.
     * Legacy keys remain available for rollback and custom compatibility resources.
     *
     * @param permissions mutable group permission state
     * @param storedPermissionIds IDs that were explicitly present in the source NBT
     * @return number of replacement values written
     */
    public static int migrate(
            final Map<String, Boolean> permissions,
            final Set<String> storedPermissionIds
    ) {
        int migrated = 0;
        for (final Map.Entry<String, List<String>> replacement : REPLACEMENTS.entrySet()) {
            if (!storedPermissionIds.contains(replacement.getKey())) continue;

            final boolean value = permissions.getOrDefault(replacement.getKey(), false);
            for (final String target : replacement.getValue()) {
                if (storedPermissionIds.contains(target)) continue;
                permissions.put(target, value);
                migrated++;
            }
        }
        return migrated;
    }

    public static Map<String, List<String>> replacements() {
        return REPLACEMENTS;
    }

    public static boolean isRetired(final String permissionId) {
        return permissionId != null && RETIRED_PERMISSION_IDS.contains(permissionId.toLowerCase(Locale.ROOT));
    }

    private static MigrationDefinition loadDefinition() {
        final String resource = "/assets/" + SkyblockAddonCore.MOD_ID
                + "/registries/permission_migrations.json";
        try (final InputStream input = PermissionStateMigrator.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing permission migration resource " + resource);
            final MigrationDefinition definition = new Gson().fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8),
                    MigrationDefinition.class
            );
            if (definition == null || definition.schemaVersion < 1
                    || definition.replacements == null || definition.retired == null) {
                throw new IllegalStateException("Invalid permission migration resource " + resource);
            }
            return definition;
        } catch (final Exception exception) {
            throw new IllegalStateException("Failed to load permission migration rules", exception);
        }
    }

    private static Map<String, List<String>> copyReplacements(
            final Map<String, List<String>> configuredReplacements
    ) {
        final Map<String, List<String>> replacements = new LinkedHashMap<>();
        configuredReplacements.forEach((source, targets) ->
                replacements.put(source.toLowerCase(Locale.ROOT), List.copyOf(targets))
        );
        return Map.copyOf(replacements);
    }

    private static final class MigrationDefinition {
        @SerializedName("schema_version")
        private int schemaVersion;
        private Set<String> retired;
        private Map<String, List<String>> replacements;
    }
}
