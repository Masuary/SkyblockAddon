package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionTriggerCoverageTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void knownTriggersIncludeEveryHandledTrigger() {
        assertTrue(PermissionTriggers.knownTriggers().containsAll(Set.of(
                PermissionTriggers.ON_PLACE_BLOCK, PermissionTriggers.ON_BREAK_BLOCK, PermissionTriggers.ON_SLEEP_IN_BED,
                PermissionTriggers.ON_HOSTILE_MOB_SPAWN, PermissionTriggers.ON_ELEVATOR_USE)));
    }

    @Test
    void bundledVaultHuntersRegistryLeavesTheEmptyClicksAndBlockBreakingUnreferenced() throws URISyntaxException {
        final Path registry = Path.of(Objects.requireNonNull(getClass().getResource("/assets/skyblockaddon/registries/permissions/vaulthunters.json")).toURI());
        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registry);

        assertEquals(Set.of(PermissionTriggers.ON_BREAK_BLOCK, PermissionTriggers.ON_RIGHT_CLICK_EMPTY, PermissionTriggers.ON_LEFT_CLICK_EMPTY),
                new HashSet<>(manager.findUnreferencedTriggers()));
    }

    @Test
    void registryWithOneTriggerLeavesEveryOtherKnownTriggerUnreferenced() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, """
                {"permissions": [{"id": "place_blocks", "category": "General", "triggers": ["onPlaceBlock", "onGui"],
                                  "item": {"item": "minecraft:BRICKS"}, "data": {"items": [], "blocks": [".*:.*"], "entities": []}}]}
                """);
        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registry);

        final Set<String> expectedUnreferenced = new HashSet<>(PermissionTriggers.knownTriggers());
        expectedUnreferenced.remove(PermissionTriggers.ON_PLACE_BLOCK);
        assertEquals(expectedUnreferenced, new HashSet<>(manager.findUnreferencedTriggers()));
        assertFalse(manager.findUnreferencedTriggers().contains("onGui"));
    }
}
