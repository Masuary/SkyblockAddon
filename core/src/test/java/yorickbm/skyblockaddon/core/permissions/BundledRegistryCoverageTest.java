package yorickbm.skyblockaddon.core.permissions;

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

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults"})
    void everyKnownTriggerIsReferenced(final String pack) {
        assertEquals(List.of(), bundled(pack).findUnreferencedTriggers());
    }
}
