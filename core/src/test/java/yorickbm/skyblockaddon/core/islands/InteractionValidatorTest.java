package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionValidatorTest {
    private TestIslandGroup group;

    @BeforeEach
    void setUp() {
        PermissionGroupRegistry.getInstance().clear();
        group = new TestIslandGroup();
    }

    @Test
    void firstMatchingPermissionControlsTheResult() {
        final Permission specific = permission("specific", List.of("minecraft:chest"));
        final Permission fallback = permission("fallback", List.of(".*:.*"));
        group.setPermission("specific", false);
        group.setPermission("fallback", true);

        assertTrue(InteractionValidator.checkPermission(
                group, List.of(specific, fallback), Map.of("block", "minecraft:chest")
        ));
        assertFalse(InteractionValidator.checkPermission(
                group, List.of(specific, fallback), Map.of("block", "minecraft:barrel")
        ));
    }

    @Test
    void explicitNegationSkipsSpecificPermissionAndFallsBack() {
        final Permission exceptChest = permission("except_chest", List.of("!minecraft:chest", ".*:.*"));
        final Permission fallback = permission("fallback", List.of(".*:.*"));
        group.setPermission("except_chest", false);
        group.setPermission("fallback", true);

        assertFalse(InteractionValidator.checkPermission(
                group, List.of(exceptChest, fallback), Map.of("block", "minecraft:chest")
        ));
        assertTrue(InteractionValidator.checkPermission(
                group, List.of(exceptChest, fallback), Map.of("block", "minecraft:barrel")
        ));
    }

    @Test
    void allNegationFilterClaimsEveryNonExcludedValue() {
        final Permission destroyBlocks = permission("destroy_blocks", List.of("!storagedrawers:.*"));
        group.setPermission("destroy_blocks", false);

        assertTrue(InteractionValidator.checkPermission(
                group, List.of(destroyBlocks), Map.of("block", "minecraft:stone")
        ));
        assertFalse(InteractionValidator.checkPermission(
                group, List.of(destroyBlocks), Map.of("block", "storagedrawers:oak_full_drawers_1")
        ));
    }

    private Permission permission(final String id, final List<String> blockPatterns) {
        final Permission permission = new Permission();
        permission.fromJSON("""
                {
                  "id": "%s",
                  "triggers": ["onRightClickBlock"],
                  "data": {"block": %s}
                }
                """.formatted(id, new com.google.gson.Gson().toJson(blockPatterns)));
        permission.getData().resolve(PermissionGroupRegistry.getInstance());
        return permission;
    }

    private static final class TestIslandGroup extends IslandGroup {
        private TestIslandGroup() {
            super(UUID.randomUUID(), false);
        }

        @Override
        public String getName() {
            return "Test";
        }
    }
}
