package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.islands.InteractionValidator;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
                95,
                groupRegistry.loadFromDirectory(registryDirectory.resolve("groups"), ignored -> true)
        );

        final PermissionManager manager = new PermissionManager();
        assertEquals(
                112,
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
        assertTrue(groupRegistry.expandPatterns(
                "entity",
                java.util.List.of("#vehicles")
        ).contains("immersive_aircraft:biplane"));
        assertTrue(groupRegistry.expandPatterns(
                "block",
                java.util.List.of("#occultism_storage")
        ).contains("occultism:storage_controller"));
        assertTrue(groupRegistry.expandPatterns(
                "block",
                java.util.List.of("#rftoolsutility_spawner")
        ).contains("rftoolsutility:spawner"));

        final Set<String> bundledPermissionIds = manager.getPermissions().stream()
                .map(Permission::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(bundledPermissionIds.contains("industrialforegoing_machines"));
        assertTrue(bundledPermissionIds.contains("sophisticatedstorage_storage"));
        assertTrue(bundledPermissionIds.contains("sophisticatedstorage_link"));
        assertTrue(bundledPermissionIds.contains("ars_nouveau_portal"));
        assertTrue(bundledPermissionIds.containsAll(Set.of(
                "ars_nouveau_machines",
                "botanypots_interact",
                "immersive_paintings_interact",
                "integrated_addons",
                "integratednbt_extractor",
                "occultism_storage",
                "powah_power_network",
                "rftoolsutility_teleporter",
                "toms_storage_terminals",
                "xnet_controller"
        )));
        assertFalse(bundledPermissionIds.contains("ars_nouveau_interact"));
        assertFalse(bundledPermissionIds.contains("interact_functional_drawers"));
        assertFalse(bundledPermissionIds.contains("mod_ars_nouveau"));
        assertFalse(bundledPermissionIds.contains("mod_davespotioneering"));
        assertFalse(bundledPermissionIds.contains("mod_immersiveengineering"));
        assertFalse(bundledPermissionIds.contains("mod_industrialforegoing"));
        assertFalse(bundledPermissionIds.contains("mod_occultism"));
        assertFalse(bundledPermissionIds.contains("open_ID"));
        assertFalse(bundledPermissionIds.contains("open_rftools"));
        assertFalse(bundledPermissionIds.contains("open_sophstorage"));
        assertFalse(bundledPermissionIds.contains("open_Tom"));

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

    @Test
    void splitPermissionsAreNotShadowedByLegacyBroadIds() {
        final Path registryDirectory = Path.of("src/main/resources/assets/skyblockaddon/registries");
        final PermissionGroupRegistry groupRegistry = PermissionGroupRegistry.getInstance();
        groupRegistry.clear();
        groupRegistry.loadFromDirectory(registryDirectory.resolve("groups"), ignored -> true);

        final PermissionManager manager = new PermissionManager();
        manager.loadPermissions(registryDirectory.resolve("permissions"), ignored -> true);
        final List<Permission> blockPermissions = manager.getPermissionsForTrigger("onRightClickBlock");

        final IslandGroup group = new IslandGroup(UUID.randomUUID(), false) {
            @Override
            public String getName() {
                return "Test";
            }
        };

        group.setPermission("occultism_rituals", true);
        group.setPermission("occultism_storage", false);
        assertFalse(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "occultism:golden_sacrificial_bowl")
        ));
        assertTrue(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "occultism:storage_controller")
        ));

        group.setPermission("rftools_redstone", false);
        group.setPermission("rftoolsutility_machines", true);
        assertTrue(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "rftoolsutility:logic")
        ));
        assertFalse(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "rftoolsutility:crafter3")
        ));

        group.setPermission("idyn_logic", false);
        group.setPermission("integrated_addons", true);
        group.setPermission("integratednbt_extractor", true);
        assertTrue(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "integrateddynamics:logic_programmer")
        ));
        assertFalse(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "integratedterminals:part_terminal_storage")
        ));
        assertFalse(InteractionValidator.checkPermission(
                group,
                blockPermissions,
                Map.of("block", "integratednbt:nbt_extractor")
        ));
    }
}
