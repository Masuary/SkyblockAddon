package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sir's W2c rule: a plain click on a vehicle that is boarded with a plain click and opened with a sneak click (ships,
 * aircraft, chest boats) is judged by the mount permission only; a sneak click on it, and any click on storage that
 * cannot be ridden, still needs the Chests permission.
 */
class RiddenStorageVehicleClickTest {
    private static final Predicate<String> VISITOR = permissionId -> false;
    private static final List<String> DEFAULT_RULES = RiddenStorageVehicles.parseRules(RiddenStorageVehicles.DEFAULT_RULES);

    private static PermissionManager bundled(final String pack) {
        try {
            final Path registry = Path.of(Objects.requireNonNull(RiddenStorageVehicleClickTest.class.getResource(
                    "/assets/skyblockaddon/registries/permissions/" + pack + ".json")).toURI());
            final PermissionManager manager = new PermissionManager();
            manager.loadPermissions(registry);
            return manager;
        } catch (final URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Optional<String> clickDenial(final PermissionManager manager, final String entityId, final boolean sneaking, final Optional<String> handItemId) {
        return InteractionPermissionDecision.findDenyingEntityClick(manager.getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_ENTITY), VISITOR,
                entityId, handItemId, sneaking, DEFAULT_RULES).map(Permission::getId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void aPlainClickOnAShipAircraftOrChestBoatIsLeftToTheMountCheck(final String pack) {
        final PermissionManager manager = bundled(pack);
        for (final String vehicle : List.of("smallships:oak_cog", "immersive_aircraft:biplane", "immersive_aircraft:cargo_airship",
                "wildbackport:chest_boat", "wildbackport:mangrove_chest_boat", "somemod:oak_chest_boat", "immersivepetroleum:speedboat")) {
            assertTrue(clickDenial(manager, vehicle, false, Optional.empty()).isEmpty(), vehicle);
            assertTrue(clickDenial(manager, vehicle, false, Optional.of("minecraft:diamond_sword")).isEmpty(), vehicle + " with a sword in hand");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void aSneakClickOnAShipAircraftOrChestBoatNeedsChests(final String pack) {
        final PermissionManager manager = bundled(pack);
        for (final String vehicle : List.of("smallships:oak_cog", "immersive_aircraft:biplane", "wildbackport:chest_boat", "immersivepetroleum:speedboat")) {
            assertEquals(Optional.of("open_chests"), clickDenial(manager, vehicle, true, Optional.empty()), vehicle);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"vaulthunters", "woldsvaults", "default"})
    void storageThatCannotBeRiddenNeedsChestsOnAnyClick(final String pack) {
        final PermissionManager manager = bundled(pack);
        for (final String storage : List.of("minecraft:chest_minecart", "minecraft:hopper_minecart", "quark:chest_passenger",
                "littlelogistics:chest_car", "littlelogistics:chest_barge", "immersiveengineering:cart_woodencrate")) {
            assertEquals(Optional.of("open_chests"), clickDenial(manager, storage, false, Optional.empty()), storage);
        }
    }

    @Test
    void otherEntitiesAreJudgedAsBefore() {
        final PermissionManager manager = bundled("woldsvaults");
        assertEquals(Optional.of("interact_animals"), clickDenial(manager, "minecraft:cow", false, Optional.empty()));
        assertEquals(Optional.of("interact_item_frame"), clickDenial(manager, "minecraft:item_frame", false, Optional.empty()));
        assertTrue(clickDenial(manager, "minecraft:boat", false, Optional.empty()).isEmpty());
    }

    @Test
    void anEmptyRuleListMakesNoVehicleARiddenStorageVehicle() {
        final PermissionManager manager = bundled("woldsvaults");
        assertFalse(RiddenStorageVehicles.isRiddenStorageVehicle(List.of(), "smallships:oak_cog"));
        assertEquals(Optional.of("open_chests"), InteractionPermissionDecision.findDenyingEntityClick(
                manager.getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_ENTITY), VISITOR, "smallships:oak_cog", Optional.empty(), false, List.of())
                .map(Permission::getId));
    }

    @Test
    void anInvalidRuleIsRefusedWhenParsed() {
        assertThrows(IllegalArgumentException.class, () -> RiddenStorageVehicles.parseRules("smallships:(cog"));
    }

    @Test
    void negatedRulesExcludeAVehicle() {
        final List<String> rules = RiddenStorageVehicles.parseRules("smallships:.*, !smallships:drakkar");
        assertTrue(RiddenStorageVehicles.isRiddenStorageVehicle(rules, "smallships:oak_cog"));
        assertFalse(RiddenStorageVehicles.isRiddenStorageVehicle(rules, "smallships:drakkar"));
    }
}
