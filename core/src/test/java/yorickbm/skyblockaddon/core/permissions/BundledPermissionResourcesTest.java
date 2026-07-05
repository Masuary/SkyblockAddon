package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledPermissionResourcesTest {
    @Test
    void loadsEveryBundledGroupAndPermissionResource() {
        final Path registryDirectory = Path.of("src/main/resources/assets/skyblockaddon/registries");
        final PermissionGroupRegistry groupRegistry = PermissionGroupRegistry.getInstance();
        groupRegistry.clear();

        assertEquals(
                90,
                groupRegistry.loadFromDirectory(registryDirectory.resolve("groups"), ignored -> true)
        );

        final PermissionManager manager = new PermissionManager();
        assertEquals(
                100,
                manager.loadPermissions(registryDirectory.resolve("permissions"), ignored -> true)
        );
        assertTrue(manager.getPermissions().stream().anyMatch(permission -> permission.getId().equals("mob_processor")));
        final Set<String> vaultRecycleBlocks = Set.copyOf(
                groupRegistry.expandPatterns("block", java.util.List.of("#vault_recycle"))
        );
        assertTrue(vaultRecycleBlocks.contains("woldsvaults:vault_salvager"));
        assertTrue(vaultRecycleBlocks.contains("woldsvaults:doll_dismantler"));

        final Set<String> chestBlocks = Set.copyOf(
                groupRegistry.expandPatterns("block", java.util.List.of("#chests"))
        );
        assertTrue(chestBlocks.contains("!sophisticatedstorage:.*"));
        assertTrue(groupRegistry.expandPatterns(
                "block",
                java.util.List.of("#sophisticatedstorage_link")
        ).contains("sophisticatedstorage:storage_link"));

        final Set<String> bundledPermissionIds = manager.getPermissions().stream()
                .map(Permission::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(bundledPermissionIds.contains("industrialforegoing_machines"));
        assertTrue(bundledPermissionIds.contains("sophisticatedstorage_storage"));
        assertTrue(bundledPermissionIds.contains("sophisticatedstorage_link"));
        assertTrue(bundledPermissionIds.contains("ars_nouveau_interact"));
        assertTrue(bundledPermissionIds.contains("ars_nouveau_portal"));
        assertFalse(bundledPermissionIds.contains("mod_industrialforegoing"));
        assertFalse(bundledPermissionIds.contains("open_sophstorage"));
        assertFalse(bundledPermissionIds.contains("mod_ars_nouveau"));

        final Permission arsPortalPermission = manager.getPermissions().stream()
                .filter(permission -> permission.getId().equals("ars_nouveau_portal"))
                .findFirst()
                .orElseThrow();
        assertTrue(arsPortalPermission.hasTrigger("onEnterPortal"));
        assertEquals(
                java.util.List.of("ars_nouveau:portal"),
                arsPortalPermission.getData().getFiltersForContext("block")
        );

        assertTrue(java.nio.file.Files.exists(registryDirectory.resolve("groups/creeperpower.json")));
        assertTrue(java.nio.file.Files.notExists(registryDirectory.resolve("groups/creeper_power.json")));
        assertTrue(java.nio.file.Files.exists(registryDirectory.resolve("permissions/creeperpower.json")));
        assertTrue(java.nio.file.Files.notExists(registryDirectory.resolve("permissions/creeper_power.json")));
        PermissionStateMigrator.replacements().forEach((legacyId, replacementIds) -> {
            if (!PermissionStateMigrator.isRetired(legacyId)) return;
            assertTrue(
                    bundledPermissionIds.containsAll(replacementIds),
                    () -> "Retired permission '" + legacyId + "' has missing replacements "
                            + replacementIds.stream().filter(id -> !bundledPermissionIds.contains(id)).toList()
            );
        });
    }

    @Test
    void everyBundledTriggerHasAnImplementedEventPath() {
        final Path registryDirectory = Path.of("src/main/resources/assets/skyblockaddon/registries");
        final PermissionGroupRegistry groupRegistry = PermissionGroupRegistry.getInstance();
        groupRegistry.clear();
        groupRegistry.loadFromDirectory(registryDirectory.resolve("groups"), ignored -> true);

        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registryDirectory.resolve("permissions"), ignored -> true);

        final Set<String> implementedTriggers = Set.of(
                "OnHostileMobSpawn", "OnPassiveMobSpawn", "onAttack", "onBonemeal", "onBucket",
                "onChorusFruit", "onDrop", "onEnderPearl", "onEnterPortal", "onEPBlockInteract",
                "onEVBlockInteract", "onElevatorUse", "onGui", "onLeftClickBlock", "onMount", "onPickup",
                "onPickupPiglin", "onPickupVillager", "onPlaceBlock", "onPlayerChangedDimension",
                "onPortalIgnition", "onQuarkPickarangMixin", "onRightClickBlock", "onRightClickEntity",
                "onRightClickItem", "onSleepInBed", "onTrample", "onUse", "onXp"
        );

        manager.getPermissions().forEach(permission -> assertTrue(
                implementedTriggers.containsAll(permission.getTriggers()),
                () -> "Permission '" + permission.getId() + "' uses unknown triggers "
                        + permission.getTriggers().stream().filter(trigger -> !implementedTriggers.contains(trigger)).toList()
        ));
    }

    @Test
    void loadsBaseResourcesWhenEveryOptionalModIsAbsent() {
        final Path registryDirectory = Path.of("src/main/resources/assets/skyblockaddon/registries");
        final PermissionGroupRegistry groupRegistry = PermissionGroupRegistry.getInstance();
        groupRegistry.clear();
        assertTrue(groupRegistry.loadFromDirectory(registryDirectory.resolve("groups"), ignored -> false) > 0);

        final PermissionManager manager = new PermissionManager();
        assertTrue(manager.loadPermissions(registryDirectory.resolve("permissions"), ignored -> false) > 0);
    }
}
