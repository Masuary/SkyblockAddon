package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerTest {
    private static final String ONE_PERMISSION = """
            {"permissions": [{"id": "destroy_blocks", "category": "Blocks", "triggers": [], "item": {"item": "minecraft:STONE"}, "data": {}}]}
            """;

    @TempDir
    Path temporaryFolder;

    @Test
    void malformedFileFailsNamingTheFile() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, "{\"permissions\": [ {\"id\": \"destroy_blocks\", }");
        final PermissionManager manager = new PermissionManager();

        final IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.loadPermissions(registry));

        assertTrue(failure.getMessage().contains(registry.toAbsolutePath().toString()), failure.getMessage());
    }

    @Test
    void missingFileFails() {
        final PermissionManager manager = new PermissionManager();

        assertThrows(IllegalStateException.class, () -> manager.loadPermissions(temporaryFolder.resolve("absent.json")));
    }

    @Test
    void fileWithoutPermissionsFails() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, "{\"permissions\": []}");

        assertThrows(IllegalStateException.class, () -> new PermissionManager().loadPermissions(registry));
    }

    @Test
    void failedReloadKeepsPreviousPermissions() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, ONE_PERMISSION);
        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registry);

        Files.writeString(registry, "not json");
        assertThrows(IllegalStateException.class, () -> manager.loadPermissions(registry));

        assertEquals(1, manager.getPermissions().size());
    }

    @Test
    void invalidPatternFailsNamingThePermissionAndKeepsPreviousPermissions() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, ONE_PERMISSION);
        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registry);

        Files.writeString(registry, """
                {"permissions": [
                  {"id": "open_chests", "category": "Storage", "triggers": ["onRightClickBlock"], "item": {"item": "minecraft:CHEST"},
                   "data": {"items": [], "blocks": ["minecraft:[chest"], "entities": []}},
                  {"id": "place_blocks", "category": "General", "triggers": ["onPlaceBlock"]}
                ]}
                """);
        final IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.loadPermissions(registry));

        assertTrue(failure.getMessage().contains("open_chests"), failure.getMessage());
        assertTrue(failure.getMessage().contains("minecraft:[chest"), failure.getMessage());
        assertEquals("destroy_blocks", manager.getPermissions().get(0).getId());
    }

    @Test
    void bundledPermissionFilesLoad() throws URISyntaxException {
        for (final String bundledFile : List.of("vaulthunters.json", "default.json")) {
            final Path registry = Path.of(Objects.requireNonNull(getClass().getResource("/assets/skyblockaddon/registries/permissions/" + bundledFile), bundledFile).toURI());

            assertTrue(new PermissionManager().loadPermissions(registry) > 0, bundledFile);
        }
    }
}
