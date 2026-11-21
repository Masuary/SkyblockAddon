package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionPermissionDecisionTest {
    private static final String LIVE_PLACE_BLOCKS = """
            {"id": "place_blocks", "category": "General", "triggers": ["onPlaceBlock", "onUse"],
             "data": {"skyblockaddon": [],
                      "items": ["!easy_villagers:villager", "!.*:.*_spawn_egg", "supplementaries:slingshot"],
                      "blocks": [".*:.*", "!minecraft:player_head", "!the_vault:vault_stone_pillar", "!the_vault:vault_portal"],
                      "entities": []}}
            """;
    private static final String LIVE_INTERACT_GENERIC = """
            {"id": "interact_generic", "category": "General", "triggers": ["onRightClickBlock"],
             "data": {"skyblockaddon": [], "items": [], "blocks": ["!.*:chest", "!.*:.*_chest"], "entities": []}}
            """;
    private static final String TRAVEL_NETHER = """
            {"id": "travel_nether", "category": "General", "triggers": ["onPortalIgnition"],
             "data": {"skyblockaddon": ["dimension:minecraft:the_nether"], "items": [], "blocks": [], "entities": []}}
            """;

    private static final Predicate<String> VISITOR = permissionId -> false;
    private static final Predicate<String> MEMBER = permissionId -> true;

    private static Permission permission(final String json) {
        final Permission permission = new Permission();
        permission.fromJSON(json);
        return permission;
    }

    private static boolean visitorPlacementDenied(final InteractionSubject subject) {
        return InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_PLACE_BLOCK, List.of(permission(LIVE_PLACE_BLOCKS)), VISITOR, subject).isPresent();
    }

    private static InteractionSubject clickWith(final String handItemId, final String heldBlockId, final String clickedBlockId) {
        return new InteractionSubject(Optional.ofNullable(handItemId), Optional.ofNullable(heldBlockId), Optional.ofNullable(clickedBlockId));
    }

    @Test
    void flintAndSteelOnTheGroundIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:flint_and_steel", null, "minecraft:grass_block")));
    }

    @Test
    void fireLitByFlintAndSteelIsDeniedWhenThePlacedBlockIsJudged() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:flint_and_steel", null, "minecraft:fire")));
    }

    @Test
    void hoeTillingIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:diamond_hoe", null, "minecraft:dirt")));
        assertTrue(visitorPlacementDenied(clickWith("minecraft:diamond_hoe", null, "minecraft:farmland")));
    }

    @Test
    void emptyHandOnTheGroundIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith(null, null, "minecraft:grass_block")));
    }

    @Test
    void spawnEggOnTheGroundOrASpawnerIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:zombie_spawn_egg", null, "minecraft:grass_block")));
        assertTrue(visitorPlacementDenied(clickWith("minecraft:zombie_spawn_egg", null, "minecraft:spawner")));
    }

    @Test
    void offhandBlockWithAnEmptyMainHandIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith(null, "minecraft:stone", null)));
        assertTrue(visitorPlacementDenied(clickWith("minecraft:stone", "minecraft:stone", "minecraft:grass_block")));
    }

    @Test
    void gadgetWithoutAnyBlockToJudgeIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("buildinggadgets:gadget_building", null, null)));
        assertTrue(visitorPlacementDenied(clickWith("buildinggadgets:gadget_destruction", null, null)));
    }

    @Test
    void reacharoundWithANonBlockItemIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:stick", null, null)));
    }

    @Test
    void normalBlockIsDenied() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:cobblestone", "minecraft:cobblestone", "minecraft:grass_block")));
    }

    @Test
    void heldPlayerHeadCarveOutAllowsPlacingOnAnyBlock() {
        assertFalse(visitorPlacementDenied(clickWith("minecraft:player_head", "minecraft:player_head", "minecraft:stone")));
        assertFalse(visitorPlacementDenied(clickWith("minecraft:player_head", "minecraft:player_head", null)));
    }

    @Test
    void clickedPlayerHeadDoesNotLetAHeldBlockThrough() {
        assertTrue(visitorPlacementDenied(clickWith("minecraft:stone", "minecraft:stone", "minecraft:player_head")));
    }

    @Test
    void placedCarvedOutBlockIsAllowed() {
        assertFalse(visitorPlacementDenied(clickWith(null, null, "the_vault:vault_portal")));
    }

    @Test
    void deniedItemIsDeniedEvenOnACarvedOutBlock() {
        assertTrue(visitorPlacementDenied(clickWith("supplementaries:slingshot", null, "minecraft:player_head")));
    }

    @Test
    void membersArePermittedEverything() {
        final Optional<Permission> denial = InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_PLACE_BLOCK,
                List.of(permission(LIVE_PLACE_BLOCKS)), MEMBER, clickWith("minecraft:flint_and_steel", null, null));

        assertTrue(denial.isEmpty());
    }

    @Test
    void otherTriggersJudgeTheClickedBlockNotTheHeldOne() {
        final List<Permission> permissions = List.of(permission(LIVE_INTERACT_GENERIC));

        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_RIGHT_CLICK_BLOCK, permissions, VISITOR,
                clickWith("minecraft:chest", "minecraft:chest", "minecraft:crafting_table")).isPresent());
        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_RIGHT_CLICK_BLOCK, permissions, VISITOR,
                clickWith("minecraft:stone", "minecraft:stone", "minecraft:chest")).isEmpty());
    }

    @Test
    void otherTriggersWithoutAClickedBlockSkipTheBlockRules() {
        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_RIGHT_CLICK_BLOCK, List.of(permission(LIVE_INTERACT_GENERIC)), VISITOR,
                clickWith("minecraft:stone", "minecraft:stone", null)).isEmpty());
    }

    @Test
    void permissionWithoutItemOrBlockRulesDeniesOutright() {
        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_PORTAL_IGNITION, List.of(permission(TRAVEL_NETHER)), VISITOR,
                clickWith("minecraft:flint_and_steel", null, "minecraft:obsidian")).isPresent());
    }

    private static boolean entityPlacementDenied(final List<Permission> placePermissions, final Predicate<String> group, final String placerItemId) {
        return InteractionPermissionDecision.findDenyingEntityPlacement(placePermissions, group, placerItemId).isPresent();
    }

    @Test
    void entityPlacersAreDeniedWhereverTheyAreUsed() {
        final List<Permission> placePermissions = List.of(permission(LIVE_PLACE_BLOCKS));

        assertTrue(entityPlacementDenied(placePermissions, VISITOR, "minecraft:armor_stand"));
        assertTrue(entityPlacementDenied(placePermissions, VISITOR, "minecraft:item_frame"));
        assertTrue(entityPlacementDenied(placePermissions, VISITOR, "minecraft:oak_boat"));
        assertTrue(entityPlacementDenied(placePermissions, VISITOR, "minecraft:end_crystal"));
        assertTrue(entityPlacementDenied(placePermissions, VISITOR, "minecraft:zombie_spawn_egg"));
    }

    @Test
    void blockCarveOutsNeverLetAnEntityPlacerThrough() {
        assertFalse(visitorPlacementDenied(clickWith("minecraft:armor_stand", null, "minecraft:player_head")));
        assertTrue(entityPlacementDenied(List.of(permission(LIVE_PLACE_BLOCKS)), VISITOR, "minecraft:armor_stand"));
    }

    @Test
    void groupsWithThePlacePermissionMayPlaceEntities() {
        assertFalse(entityPlacementDenied(List.of(permission(LIVE_PLACE_BLOCKS)), MEMBER, "minecraft:armor_stand"));
    }

    @Test
    void placePermissionWithOnlyItemRulesJudgesThePlacerItem() {
        final List<Permission> itemOnlyPermissions = List.of(permission("""
                {"id": "place_minecarts", "category": "General", "triggers": ["onPlaceBlock"],
                 "data": {"skyblockaddon": [], "items": [".*:.*minecart"], "blocks": [], "entities": []}}
                """));

        assertTrue(entityPlacementDenied(itemOnlyPermissions, VISITOR, "minecraft:chest_minecart"));
        assertFalse(entityPlacementDenied(itemOnlyPermissions, VISITOR, "minecraft:armor_stand"));
    }

    @Test
    void noPermissionsOnTheTriggerAllow() {
        assertTrue(InteractionPermissionDecision.findDenyingPermission(PermissionTriggers.ON_PLACE_BLOCK, List.of(), VISITOR,
                clickWith(null, null, null)).isEmpty());
    }
}
