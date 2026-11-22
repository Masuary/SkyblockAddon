package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityInteractionDecisionTest {
    private static final String ITEM_FRAMES = """
            {"id": "interact_item_frame", "category": "animals", "triggers": ["onRightClickEntity"],
             "data": {"entities": [".*:.*item_frame", "quark:(glowing_)?glass_frame", "minecraft:armor_stand", "minecraft:leash_knot"]}}
            """;
    private static final String VILLAGERS = """
            {"id": "interact_villagers", "category": "animals", "triggers": ["onRightClickEntity"],
             "data": {"entities": ["minecraft:villager", "minecraft:wandering_trader"]}}
            """;
    private static final String ANIMALS = """
            {"id": "interact_animals", "category": "animals", "triggers": ["onRightClickEntity"],
             "data": {"entities": [".*:.*", "!.*:.*item_frame", "!quark:(glowing_)?glass_frame", "!minecraft:armor_stand", "!minecraft:leash_knot",
                                   "!minecraft:villager", "!minecraft:wandering_trader", "!taterzens:npc", "!corpse:corpse"]}}
            """;
    private static final String LEGACY_ITEM_FRAME_WITH_BLOCK_RULES = """
            {"id": "interact_item_frame", "category": "Interactables", "triggers": ["onRightClickEntity"],
             "data": {"items": [], "blocks": [".*:item_frame", ".*:.*_item_frame"], "entities": []}}
            """;
    private static final String NO_RULES = """
            {"id": "interact_entities_all", "category": "animals", "triggers": ["onRightClickEntity"], "data": {}}
            """;

    private static final Predicate<String> VISITOR = permissionId -> false;

    private static Permission permission(final String json) {
        final Permission permission = new Permission();
        permission.fromJSON(json);
        return permission;
    }

    private static List<Permission> entityPermissions() {
        return List.of(permission(ITEM_FRAMES), permission(VILLAGERS), permission(ANIMALS));
    }

    private static Optional<Permission> denying(final Predicate<String> group, final String entityId) {
        return InteractionPermissionDecision.findDenyingEntityInteraction(entityPermissions(), group, entityId, Optional.empty());
    }

    @Test
    void visitorIsDeniedOnFramesVillagersAndAnimals() {
        assertEquals("interact_item_frame", denying(VISITOR, "minecraft:item_frame").orElseThrow().getId());
        assertEquals("interact_item_frame", denying(VISITOR, "minecraft:glow_item_frame").orElseThrow().getId());
        assertEquals("interact_item_frame", denying(VISITOR, "quark:glass_frame").orElseThrow().getId());
        assertEquals("interact_item_frame", denying(VISITOR, "minecraft:armor_stand").orElseThrow().getId());
        assertEquals("interact_villagers", denying(VISITOR, "minecraft:villager").orElseThrow().getId());
        assertEquals("interact_animals", denying(VISITOR, "minecraft:sheep").orElseThrow().getId());
        assertEquals("interact_animals", denying(VISITOR, "the_vault:moo_bloom").orElseThrow().getId());
    }

    @Test
    void npcsAndCorpsesStayOpen() {
        assertTrue(denying(VISITOR, "taterzens:npc").isEmpty());
        assertTrue(denying(VISITOR, "corpse:corpse").isEmpty());
    }

    @Test
    void grantingTradingOpensOnlyVillagers() {
        final Predicate<String> traderGuest = "interact_villagers"::equals;
        assertTrue(denying(traderGuest, "minecraft:villager").isEmpty());
        assertTrue(denying(traderGuest, "minecraft:item_frame").isPresent());
        assertTrue(denying(traderGuest, "minecraft:cow").isPresent());
    }

    @Test
    void grantingAnimalsDoesNotOpenFramesOrVillagers() {
        final Predicate<String> farmHand = "interact_animals"::equals;
        assertTrue(denying(farmHand, "minecraft:cow").isEmpty());
        assertTrue(denying(farmHand, "minecraft:item_frame").isPresent());
        assertTrue(denying(farmHand, "minecraft:villager").isPresent());
    }

    @Test
    void legacyEntityIdsUnderBlocksStillJudgeTheEntity() {
        final List<Permission> legacy = List.of(permission(LEGACY_ITEM_FRAME_WITH_BLOCK_RULES));
        assertTrue(InteractionPermissionDecision.findDenyingEntityInteraction(legacy, VISITOR, "minecraft:item_frame", Optional.empty()).isPresent());
        assertFalse(InteractionPermissionDecision.findDenyingEntityInteraction(legacy, VISITOR, "minecraft:cow", Optional.empty()).isPresent());
    }

    @Test
    void aLackedPermissionWithoutRulesDeniesEveryEntity() {
        final List<Permission> noRules = List.of(permission(NO_RULES));
        assertTrue(InteractionPermissionDecision.findDenyingEntityInteraction(noRules, VISITOR, "taterzens:npc", Optional.empty()).isPresent());
    }

    @Test
    void itemRulesJudgeTheHandItem() {
        final List<Permission> shearsOnly = List.of(permission("""
                {"id": "use_shears", "category": "animals", "triggers": ["onRightClickEntity"], "data": {"items": ["minecraft:shears"]}}
                """));
        assertTrue(InteractionPermissionDecision.findDenyingEntityInteraction(shearsOnly, VISITOR, "minecraft:sheep", Optional.of("minecraft:shears")).isPresent());
        assertFalse(InteractionPermissionDecision.findDenyingEntityInteraction(shearsOnly, VISITOR, "minecraft:sheep", Optional.of("minecraft:wheat")).isPresent());
        assertFalse(InteractionPermissionDecision.findDenyingEntityInteraction(shearsOnly, VISITOR, "minecraft:sheep", Optional.empty()).isPresent());
    }
}
