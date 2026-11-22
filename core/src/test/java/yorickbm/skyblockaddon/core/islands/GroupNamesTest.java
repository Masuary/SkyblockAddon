package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupNamesTest {
    private static final IslandActor OWNER_ACTOR = new IslandActor(TestIsland.OWNER, false);

    @Test
    void oneSurroundingPairOfBracketsIsStripped() {
        assertEquals("Oak Log", GroupNames.normalise("[Oak Log]"));
        assertEquals("Oak Log", GroupNames.normalise("  [ Oak Log ] "));
        assertEquals("[[Oak Log]]", GroupNames.normalise("[[Oak Log]]"));
    }

    @Test
    void namesWithoutSurroundingBracketsStayAsTheyAre() {
        assertEquals("Oak Log", GroupNames.normalise("Oak Log"));
        assertEquals("[Oak] Log", GroupNames.normalise("[Oak] Log"));
        assertEquals("Oak [Log]", GroupNames.normalise("Oak [Log]"));
        assertEquals("[]", GroupNames.normalise("[]"));
    }

    @Test
    void bracketsThatAreNotOneSurroundingPairStay() {
        assertEquals("[VIP] Helpers [EU]", GroupNames.normalise("[VIP] Helpers [EU]"));
        assertEquals("[VIP] [EU]", GroupNames.normalise("[VIP] [EU]"));
        assertTrue(GroupNames.matches("[VIP] Helpers [EU]", "[VIP] Helpers [EU]"));
        assertFalse(GroupNames.matches("[VIP] Helpers [EU]", "VIP] Helpers [EU"));
    }

    @Test
    void typedNamesMatchWithOrWithoutBracketsIgnoringCase() {
        assertTrue(GroupNames.matches("Oak Log", "[Oak Log]"));
        assertTrue(GroupNames.matches("Oak Log", "oak log"));
        assertTrue(GroupNames.matches("[Oak Log]", "Oak Log"));
        assertFalse(GroupNames.matches("Oak Log", "Oak"));
    }

    @Test
    void islandFindsAGroupByItsNameWithOrWithoutBrackets() {
        final TestIsland island = new TestIsland();
        final UUID helpers = island.addCustomGroup("Helpers");

        assertEquals(Optional.of(helpers), island.getGroupByName("Helpers"));
        assertEquals(Optional.of(helpers), island.getGroupByName("[helpers]"));
        assertEquals(Optional.empty(), island.getGroupByName("Builders"));
    }

    @Test
    void duplicateCheckIgnoresBrackets() {
        final TestIsland island = new TestIsland();
        island.addCustomGroup("Helpers");

        final IslandActionResult result = IslandActions.createGroup(island, OWNER_ACTOR, new TestIsland.TestGroup(UUID.randomUUID(), "[Helpers]", false));

        assertEquals(IslandActionResult.GROUP_NAME_EXISTS, result);
    }
}
