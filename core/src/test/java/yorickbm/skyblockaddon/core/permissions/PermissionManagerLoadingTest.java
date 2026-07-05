package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerLoadingTest {
    private PermissionManager manager;

    @BeforeEach
    void setUp() {
        PermissionGroupRegistry.getInstance().clear();
        manager = new PermissionManager();
    }

    @Test
    void rejectsInvalidRegexWithPermissionAndSource(@TempDir final Path directory) throws IOException {
        final Path file = directory.resolve("invalid.json");
        Files.writeString(file, permissionFile("invalid_regex", "["));

        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.loadPermissions(directory, ignored -> true)
        );

        assertTrue(fullMessage(exception).contains("invalid_regex"));
        assertTrue(fullMessage(exception).contains("invalid.json"));
        assertTrue(fullMessage(exception).contains("["));
    }

    @Test
    void rejectsDuplicatePermissionIdsAcrossFiles(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve("first.json"), permissionFile("duplicate", "minecraft:chest"));
        Files.writeString(directory.resolve("second.json"), permissionFile("duplicate", "minecraft:barrel"));

        final IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.loadPermissions(directory, ignored -> true)
        );

        assertTrue(fullMessage(exception).contains("Duplicate permission ID 'duplicate'"));
        assertTrue(fullMessage(exception).contains("second.json"));
    }

    @Test
    void failedReloadPreservesPreviouslyLoadedPermissions(@TempDir final Path directory) throws IOException {
        final Path validDirectory = Files.createDirectory(directory.resolve("valid"));
        Files.writeString(validDirectory.resolve("valid.json"), permissionFile("valid", "minecraft:chest"));
        assertEquals(1, manager.loadPermissions(validDirectory, ignored -> true));

        final Path invalidDirectory = Files.createDirectory(directory.resolve("invalid"));
        Files.writeString(invalidDirectory.resolve("invalid.json"), permissionFile("invalid", "["));
        assertThrows(IllegalStateException.class, () -> manager.loadPermissions(invalidDirectory, ignored -> true));

        assertEquals(1, manager.getPermissions().size());
        assertEquals("valid", manager.getPermissions().get(0).getId());
    }

    @Test
    void legacyPermissionsOverrideMatchingBundledIdsAndKeepNewIds(@TempDir final Path directory) throws IOException {
        final Path legacyFile = directory.resolve("PermissionRegistry.json");
        Files.writeString(legacyFile, permissionFile("shared", "legacy:block"));

        final Path permissionsDirectory = Files.createDirectory(directory.resolve("permissions"));
        Files.writeString(permissionsDirectory.resolve("base.json"), """
                {
                  "permissions": [
                    %s,
                    %s
                  ]
                }
                """.formatted(permission("shared", "bundled:block"), permission("new", "new:block")));

        assertEquals(2, manager.loadPermissions(legacyFile, permissionsDirectory, ignored -> true));
        assertEquals("legacy:block", manager.getPermissions().stream()
                .filter(permission -> permission.getId().equals("shared"))
                .findFirst().orElseThrow().getData().getFiltersForContext("block").get(0));
        assertTrue(manager.getPermissions().stream().anyMatch(permission -> permission.getId().equals("new")));
    }

    @Test
    void correctsLegacyWoldsVaultSalvagerTypo(@TempDir final Path directory) throws IOException {
        final Path legacyFile = directory.resolve("PermissionRegistry.json");
        Files.writeString(legacyFile, permissionFile("vh_recycle", "woldsvaults:vault_salager"));

        assertEquals(1, manager.loadPermissions(legacyFile));
        assertEquals(
                "woldsvaults:vault_salvager",
                manager.getPermissions().get(0).getData().getFiltersForContext("block").get(0)
        );
    }

    @Test
    void failedMergedReloadPreservesPreviouslyLoadedPermissions(@TempDir final Path directory) throws IOException {
        final Path validDirectory = Files.createDirectory(directory.resolve("valid"));
        Files.writeString(validDirectory.resolve("valid.json"), permissionFile("valid", "minecraft:chest"));
        assertEquals(1, manager.loadPermissions(validDirectory, ignored -> true));

        final Path legacyFile = directory.resolve("PermissionRegistry.json");
        Files.writeString(legacyFile, permissionFile("legacy", "legacy:block"));
        final Path invalidDirectory = Files.createDirectory(directory.resolve("invalid"));
        Files.writeString(invalidDirectory.resolve("invalid.json"), permissionFile("invalid", "["));

        assertThrows(
                IllegalStateException.class,
                () -> manager.loadPermissions(legacyFile, invalidDirectory, ignored -> true)
        );
        assertEquals(1, manager.getPermissions().size());
        assertEquals("valid", manager.getPermissions().get(0).getId());
    }

    @Test
    void mergedLoadRetiresBroadLegacyPermissionWhenReplacementExists(@TempDir final Path directory) throws IOException {
        final Path legacyFile = directory.resolve("PermissionRegistry.json");
        Files.writeString(legacyFile, permissionFile("mod_create", "create:.*"));
        final Path permissionsDirectory = Files.createDirectory(directory.resolve("permissions"));
        Files.writeString(
                permissionsDirectory.resolve("create.json"),
                permissionFile("create_machines", "create:mechanical_press")
        );

        assertEquals(1, manager.loadPermissions(legacyFile, permissionsDirectory, ignored -> true));
        assertEquals("create_machines", manager.getPermissions().get(0).getId());
    }

    private String permissionFile(final String id, final String blockPattern) {
        return "{\"permissions\":[" + permission(id, blockPattern) + "]}";
    }

    private String permission(final String id, final String blockPattern) {
        return """
                {
                  "id": "%s",
                  "priority": 1,
                  "order": 1,
                  "item": {
                    "display_name": ["{\\\"text\\\":\\\"Test\\\"}"],
                    "item": "minecraft:paper",
                    "lore": []
                  },
                  "category": "General",
                  "triggers": ["onRightClickBlock"],
                  "data": {"block": ["%s"]}
                }
                """.formatted(id, blockPattern);
    }

    private String fullMessage(final Throwable throwable) {
        final StringBuilder message = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) message.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return message.toString();
    }
}
