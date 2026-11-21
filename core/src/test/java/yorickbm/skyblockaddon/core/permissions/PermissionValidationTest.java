package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionValidationTest {
    private static final String VALID_PERMISSION = """
            {"id": "open_chests", "category": "Storage", "triggers": ["onRightClickBlock"], "item": {"item": "minecraft:CHEST"},
             "data": {"items": [], "blocks": [".*:chest"], "entities": []}}
            """;

    @TempDir
    Path temporaryFolder;

    private IllegalStateException loadFailure(final String permissionsJson) throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, "{\"permissions\": [" + permissionsJson + "]}");
        final IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new PermissionManager().loadPermissions(registry));
        assertTrue(failure.getMessage().contains(registry.toAbsolutePath().toString()), failure.getMessage());
        return failure;
    }

    @Test
    void permissionWithoutIdFailsNamingItsPosition() throws IOException {
        final IllegalStateException failure = loadFailure(VALID_PERMISSION + """
                , {"category": "General", "triggers": ["onPlaceBlock"], "item": {"item": "minecraft:BRICKS"}, "data": {"blocks": [".*:.*"]}}
                """);

        assertTrue(failure.getMessage().contains("permission #2"), failure.getMessage());
    }

    @Test
    void permissionWithoutTriggersFailsNamingThePermission() throws IOException {
        final IllegalStateException failure = loadFailure("""
                {"id": "place_blocks", "category": "General", "item": {"item": "minecraft:BRICKS"}, "data": {"blocks": [".*:.*"]}}
                """);

        assertTrue(failure.getMessage().contains("'place_blocks'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("triggers"), failure.getMessage());
    }

    @Test
    void permissionWithoutDataFailsNamingThePermission() throws IOException {
        final IllegalStateException failure = loadFailure("""
                {"id": "interact_bed", "category": "General", "triggers": ["onSleepInBed"], "item": {"item": "minecraft:RED_BED"}}
                """);

        assertTrue(failure.getMessage().contains("'interact_bed'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("data"), failure.getMessage());
    }

    @Test
    void permissionWithoutCategoryOrItemFailsNamingThePermission() throws IOException {
        assertTrue(loadFailure("""
                {"id": "interact_bed", "triggers": ["onSleepInBed"], "item": {"item": "minecraft:RED_BED"}, "data": {}}
                """).getMessage().contains("category"));
        assertTrue(loadFailure("""
                {"id": "interact_bed", "category": "General", "triggers": ["onSleepInBed"], "data": {}}
                """).getMessage().contains("item"));
    }

    @Test
    void emptyEntryFailsNamingItsPosition() throws IOException {
        final IllegalStateException failure = loadFailure(VALID_PERMISSION + ", null");

        assertTrue(failure.getMessage().contains("permission #2"), failure.getMessage());
    }

    @Test
    void missingRuleListsReadAsEmpty() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, """
                {"permissions": [{"id": "vh_gear_tools", "category": "Vault", "triggers": ["onRightClickBlock"], "item": {"item": "minecraft:ANVIL"},
                                  "data": {"blocks": ["the_vault:vault_forge"]}}]}
                """);
        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registry);

        final Permission permission = manager.getPermissions().get(0);
        assertTrue(permission.getData().getSkyblockaddonData().isEmpty());
        assertTrue(permission.getData().getItemsData().isEmpty());
        assertTrue(permission.getData().getEntitiesData().isEmpty());
    }
}
