package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import yorickbm.skyblockaddon.core.util.MatchResult;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The W2b entries of the bundled Vault Hunters and Wold's Vaults registries, judged by the same decisions the events use.
 */
class BundledRegistryCoverageTest {
    private static final Predicate<String> VISITOR = permissionId -> false;

    private static PermissionManager bundled(final String pack) {
        try {
            final Path registry = Path.of(Objects.requireNonNull(BundledRegistryCoverageTest.class.getResource(
                    "/assets/skyblockaddon/registries/permissions/" + pack + ".json")).toURI());
            final PermissionManager manager = new PermissionManager();
            manager.loadPermissions(registry);
            return manager;
        } catch (final URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Optional<String> entityDenial(final PermissionManager manager, final Predicate<String> group, final String entityId) {
        return InteractionPermissionDecision.findDenyingEntityInteraction(manager.getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_ENTITY), group, entityId, Optional.empty())
                .map(Permission::getId);
    }

    private static Optional<String> blockClickDenial(final PermissionManager manager, final Predicate<String> group, final String blockId) {
        return InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_RIGHT_CLICK_BLOCK, manager.getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_BLOCK), group,
                new InteractionSubject(Optional.empty(), Optional.empty(), Optional.of(blockId))).map(Permission::getId);
    }

    private static boolean itemUseInAirDenied(final PermissionManager manager, final String itemId) {
        return InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_RIGHT_CLICK_ITEM, manager.getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_ITEM), VISITOR,
                new InteractionSubject(Optional.of(itemId), Optional.empty(), Optional.empty())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void entityClicksAreSplitIntoFramesVillagersAndAnimals(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("interact_item_frame"), entityDenial(manager, VISITOR, "minecraft:item_frame"));
        assertEquals(Optional.of("interact_item_frame"), entityDenial(manager, VISITOR, "minecraft:armor_stand"));
        assertEquals(Optional.of("interact_item_frame"), entityDenial(manager, VISITOR, "quark:glass_frame"));
        assertEquals(Optional.of("interact_villagers"), entityDenial(manager, VISITOR, "minecraft:villager"));
        assertEquals(Optional.of("interact_animals"), entityDenial(manager, VISITOR, "minecraft:cow"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "minecraft:chest_minecart"));
        assertTrue(entityDenial(manager, VISITOR, "taterzens:npc").isEmpty());
        assertTrue(entityDenial(manager, VISITOR, "corpse:corpse").isEmpty());
        assertTrue(entityDenial(manager, "interact_villagers"::equals, "minecraft:villager").isEmpty());
        assertTrue(entityDenial(manager, "interact_animals"::equals, "minecraft:item_frame").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void playersVaultCompanionsAndVehiclesAreNotAnimals(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertTrue(entityDenial(manager, VISITOR, "minecraft:player").isEmpty());
        assertTrue(entityDenial(manager, VISITOR, "the_vault:spirit").isEmpty());
        assertTrue(entityDenial(manager, VISITOR, "minecraft:boat").isEmpty());
        assertTrue(entityDenial(manager, VISITOR, "minecraft:minecart").isEmpty());
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "wildbackport:chest_boat"));
        assertTrue(entityDenial(manager, "open_chests"::equals, "minecraft:hopper_minecart").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void vehiclesWithAnInventoryNeedTheChestToggle(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "immersive_aircraft:biplane"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "immersive_aircraft:cargo_airship"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "littlelogistics:chest_car"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "immersiveengineering:cart_woodencrate"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "quark:chest_passenger"));
        assertTrue(entityDenial(manager, VISITOR, "littlelogistics:seater_barge").isEmpty());
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "smallships:oak_cog"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "immersivepetroleum:speedboat"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "somemod:oak_chest_boat"));
        assertTrue(entityDenial(manager, "open_chests"::equals, "smallships:oak_cog").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void bedsAreLeftToSleepInBedsAndNotRefusedByTheCatchAll(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertTrue(blockClickDenial(manager, VISITOR, "minecraft:red_bed").isEmpty());
        assertTrue(blockClickDenial(manager, VISITOR, "comforts:hammock_white").isEmpty());
        assertEquals(Optional.of("interact_generic"), blockClickDenial(manager, VISITOR, "minecraft:lectern"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void storageCartsOfOtherModsNeedTheChestToggle(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "supplementaries:dispenser_minecart"));
        assertEquals(Optional.of("open_chests"), entityDenial(manager, VISITOR, "lootr:lootr_minecart"));
    }

    @Test
    void plainChestsOfModsWithTheirOwnToggleStayUnderChests() {
        final PermissionManager manager = bundled("woldsvaults");
        assertEquals(Optional.of("open_chests"), blockClickDenial(manager, "use_shops"::equals, "lightmanscurrency:coin_chest"));
        assertTrue(blockClickDenial(manager, "open_chests"::equals, "lightmanscurrency:coin_chest").isEmpty());
        assertEquals(Optional.of("use_shops"), blockClickDenial(manager, "open_chests"::equals, "lightmanscurrency:display_case"));
        assertTrue(blockClickDenial(manager, "open_chests"::equals, "pneumaticcraft:reinforced_chest").isEmpty());
        assertEquals(Optional.of("mod_pneumaticcraft"), blockClickDenial(manager, "open_chests"::equals, "pneumaticcraft:air_compressor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void aModdedDoorNeedsOnlyTheDoorToggle(final String pack) {
        assertTrue(blockClickDenial(bundled(pack), "interact_doors"::equals, "create:andesite_door").isEmpty());
        assertEquals(Optional.of("interact_doors"), blockClickDenial(bundled(pack), "mod_create"::equals, "create:andesite_door"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void npcMountsStayOpenAndOtherMountsNeedVehicles(final String pack) {
        final PermissionManager manager = bundled(pack);
        final List<String> rules = manager.getPermissionsForTrigger(PermissionTriggers.ON_MOUNT).get(0).getData().getEntitiesData();
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(rules, "taterzens:npc"));
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(rules, "minecraft:horse"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void namedStationsOpenWithTheirOwnPermissionOnly(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("use_workstations"), blockClickDenial(manager, VISITOR, "minecraft:crafting_table"));
        assertTrue(blockClickDenial(manager, "use_workstations"::equals, "minecraft:crafting_table").isEmpty());
        assertEquals(Optional.of("interact_doors"), blockClickDenial(manager, VISITOR, "minecraft:oak_fence_gate"));
        assertTrue(blockClickDenial(manager, "interact_doors"::equals, "minecraft:oak_fence_gate").isEmpty());
        assertEquals(Optional.of("activate_waystones"), blockClickDenial(manager, VISITOR, "waystones:white_sharestone"));
        assertEquals(Optional.of("open_chests"), blockClickDenial(manager, VISITOR, "the_vault:gilded_strongbox"));
        assertEquals(Optional.of("vh_gear_tools"), blockClickDenial(manager, VISITOR, "the_vault:tool_vise"));
        assertEquals(Optional.of("interact_generic"), blockClickDenial(manager, VISITOR, "copycats:copycat_stone_pressure_plate"));
        assertEquals(Optional.of("interact_generic"), blockClickDenial(manager, "use_workstations"::equals, "minecraft:lectern"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void aModToggleOpensOnlyThatModsBlocksAndTheCatchAllNeverOpensAMod(final String pack) {
        final PermissionManager manager = bundled(pack);
        final Predicate<String> createOnly = "mod_create"::equals;
        assertTrue(blockClickDenial(manager, createOnly, "create:mechanical_crafter").isEmpty());
        assertEquals(Optional.of("interact_generic"), blockClickDenial(manager, createOnly, "minecraft:lectern"));
        assertEquals(Optional.of("mod_mekanism"), blockClickDenial(manager, createOnly, "mekanism:metallurgic_infuser"));
        assertEquals(Optional.of("mod_create"), blockClickDenial(manager, "interact_generic"::equals, "create:mechanical_crafter"));
        assertEquals(Optional.of("mod_powah"), blockClickDenial(manager, "interact_generic"::equals, "powah:energizing_orb"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void draconicGatesAreNotDoors(final String pack) {
        assertEquals(Optional.of("interact_generic"), blockClickDenial(bundled(pack), "interact_doors"::equals, "draconicevolution:flux_gate"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void anyBreakWithoutTheBreakPermissionIsDenied(final String pack) {
        final PermissionManager manager = bundled(pack);
        final List<Permission> breakPermissions = manager.getPermissionsForTrigger(PermissionTriggers.ON_BREAK_BLOCK);
        final InteractionSubject laserBreak = new InteractionSubject(Optional.of("mininggadgets:mininggadget"), Optional.empty(), Optional.empty());
        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_BREAK_BLOCK, breakPermissions, VISITOR, laserBreak).isPresent());
        assertFalse(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_BREAK_BLOCK, breakPermissions, "destroy_blocks"::equals, laserBreak).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void terraformingAndTeleportItemsAreJudgedInTheAirButFoodIsNot(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertTrue(itemUseInAirDenied(manager, "botania:rainbow_rod"));
        assertTrue(itemUseInAirDenied(manager, "waystones:warp_stone"));
        assertTrue(itemUseInAirDenied(manager, "minecraft:egg"));
        assertFalse(itemUseInAirDenied(manager, "minecraft:golden_apple"));
        assertFalse(itemUseInAirDenied(manager, "minecraft:bow"));
    }

    private static Optional<String> placementDenial(final PermissionManager manager, final Predicate<String> group, final String handItemId, final String clickedBlockId) {
        return InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_PLACE_BLOCK, manager.getPermissionsForTrigger(PermissionTriggers.ON_PLACE_BLOCK), group,
                new InteractionSubject(Optional.of(handItemId), Optional.empty(), Optional.of(clickedBlockId))).map(Permission::getId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void spawnEggsNeedOnlyTheSpawnEggToggle(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("interact_spawn"), placementDenial(manager, VISITOR, "minecraft:zombie_spawn_egg", "minecraft:grass_block"));
        assertEquals(Optional.empty(), placementDenial(manager, "interact_spawn"::equals, "minecraft:zombie_spawn_egg", "minecraft:grass_block"));
        assertEquals(Optional.of("interact_spawn"), InteractionPermissionDecision.findDenyingEntityPlacement(
                manager.getPermissionsForTrigger(PermissionTriggers.ON_PLACE_BLOCK), "place_blocks"::equals, "minecraft:zombie_spawn_egg").map(Permission::getId));
        assertEquals(Optional.empty(), InteractionPermissionDecision.findDenyingEntityPlacement(
                manager.getPermissionsForTrigger(PermissionTriggers.ON_PLACE_BLOCK), "interact_spawn"::equals, "minecraft:zombie_spawn_egg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void easyVillagersAndPortalFramesStayUnderPlaceBlocks(final String pack) {
        final PermissionManager manager = bundled(pack);
        assertEquals(Optional.of("place_blocks"), placementDenial(manager, "interact_easy_villagers"::equals, "easy_villagers:villager", "minecraft:grass_block"));
        assertEquals(Optional.of("place_blocks"), placementDenial(manager, "use_vault_portal"::equals, "the_vault:vault_crystal", "the_vault:vault_stone_pillar"));
        assertEquals(Optional.of("place_blocks"), placementDenial(manager, "use_vault_portal"::equals, "minecraft:stick", "the_vault:vault_portal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void openingAVaultPortalNeedsOnlyTheVaultPortalToggle(final String pack) {
        final List<Permission> openPermissions = bundled(pack).getPermissionsForTrigger(PermissionTriggers.ON_OPEN_VAULT_PORTAL);
        final InteractionSubject crystalOnFrame = new InteractionSubject(Optional.of("the_vault:vault_crystal"), Optional.empty(), Optional.of("the_vault:vault_stone_pillar"));
        assertEquals(Optional.of("use_vault_portal"), InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_OPEN_VAULT_PORTAL, openPermissions, "place_blocks"::equals, crystalOnFrame)
                .map(Permission::getId));
        assertEquals(Optional.empty(), InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_OPEN_VAULT_PORTAL, openPermissions, "use_vault_portal"::equals, crystalOnFrame));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void everyKnownTriggerIsReferenced(final String pack) {
        assertEquals(List.of(), bundled(pack).findUnreferencedTriggers());
    }
}
