package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.MatchResult;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionManagerCheckMatchTest {

    @Test
    void emptyRulesBlock() {
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of(), "minecraft:lever"));
    }

    @Test
    void positiveRuleThatMatchesBlocks() {
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of("minecraft:lever"), "minecraft:lever"));
    }

    @Test
    void positiveRuleThatDoesNotMatchSkips() {
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("minecraft:lever"), "minecraft:stone"));
    }

    @Test
    void onlyNegationsBlockAnythingTheyDoNotName() {
        final List<String> rules = List.of("!minecraft:lever", "!.*:.*_door");

        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(rules, "minecraft:jukebox"));
    }

    @Test
    void onlyNegationsSkipWhatTheyName() {
        final List<String> rules = List.of("!minecraft:lever", "!.*:.*_door");

        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(rules, "minecraft:lever"));
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(rules, "minecraft:oak_door"));
    }

    @Test
    void mixedRulesBlockAPositiveMatch() {
        final List<String> rules = List.of(".*:.*", "!minecraft:player_head");

        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(rules, "minecraft:stone"));
    }

    @Test
    void mixedRulesSkipANegatedMatchEvenWhenAPositiveRuleAlsoMatches() {
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of(".*:.*", "!minecraft:player_head"), "minecraft:player_head"));
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("!minecraft:player_head", ".*:.*"), "minecraft:player_head"));
    }

    @Test
    void mixedRulesSkipWhenNothingMatches() {
        final List<String> rules = List.of("supplementaries:slingshot", "!.*:.*_spawn_egg");

        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(rules, "minecraft:diamond_hoe"));
    }

    @Test
    void matchingIgnoresCase() {
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of("MINECRAFT:Lever"), "minecraft:lever"));
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of("minecraft:lever"), "Minecraft:LEVER"));
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("!EASY_VILLAGERS:.*"), "easy_villagers:trader"));
    }

    @Test
    void rulesMustMatchTheWholeId() {
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("minecraft:lever"), "minecraft:lever_extra"));
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("lever"), "minecraft:lever"));
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of("!.*:.*_door"), "minecraft:oak_trapdoor"));
    }

    @Test
    void neverReturnsAllow() {
        final List<List<String>> ruleSets = List.of(
                List.of(), List.of("a:b"), List.of("!a:b"), List.of("a:b", "!c:d"), List.of(".*:.*"));
        for (final List<String> rules : ruleSets) {
            for (final String value : List.of("a:b", "c:d", "e:f")) {
                assertNotEquals(MatchResult.ALLOW, PermissionManager.checkMatch(rules, value), rules + " / " + value);
            }
        }
    }

    @Test
    void bareExclamationMarkIsANegationOfTheEmptyPattern() {
        assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(List.of("!"), "minecraft:lever"));
        assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(List.of("!"), ""));
    }

    @Test
    void repeatedChecksGiveTheSameResult() {
        final List<String> rules = List.of(".*:.*", "!minecraft:player_head");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(MatchResult.BLOCK, PermissionManager.checkMatch(rules, "minecraft:stone"));
            assertEquals(MatchResult.SKIP, PermissionManager.checkMatch(rules, "minecraft:player_head"));
        }
    }

    @Test
    void invalidPatternThrows() {
        assertThrows(PatternSyntaxException.class, () -> PermissionManager.checkMatch(List.of("minecraft:[lever"), "minecraft:lever"));
    }
}
