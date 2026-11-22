package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
    void clientOnlyEmptyClicksAreNotKnownTriggers() {
        assertFalse(PermissionTriggers.knownTriggers().contains("onRightClickEmpty"));
        assertFalse(PermissionTriggers.knownTriggers().contains("onLeftClickEmpty"));
    }

    @Test
    void bundledVaultHuntersRegistriesReferenceEveryKnownTrigger() throws URISyntaxException {
        for (final String registryName : List.of("vaulthunters.json", "woldsvaults.json")) {
            final Path registry = Path.of(Objects.requireNonNull(getClass().getResource("/assets/skyblockaddon/registries/permissions/" + registryName)).toURI());
            final PermissionManager manager = new PermissionManager();
            manager.loadPermissions(registry);

            assertEquals(Set.of(), new HashSet<>(manager.findUnreferencedTriggers()), registryName);
        }
    }

    @Test
    void bundledRegistriesLetDestroyBlocksGuardGadgetBreaking() throws URISyntaxException {
        for (final String registryName : List.of("default.json", "vaulthunters.json", "woldsvaults.json")) {
            final Path registry = Path.of(Objects.requireNonNull(getClass().getResource("/assets/skyblockaddon/registries/permissions/" + registryName)).toURI());
            final PermissionManager manager = new PermissionManager();
            manager.loadPermissions(registry);

            assertEquals(List.of("destroy_blocks"), manager.getPermissionsForTrigger(PermissionTriggers.ON_BREAK_BLOCK).stream().map(Permission::getId).toList(),
                    registryName);
        }
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
