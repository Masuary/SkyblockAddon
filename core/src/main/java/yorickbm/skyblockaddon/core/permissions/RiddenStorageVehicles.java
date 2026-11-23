package yorickbm.skyblockaddon.core.permissions;

import yorickbm.skyblockaddon.core.util.ConfiguredIds;
import yorickbm.skyblockaddon.core.util.MatchResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Vehicles that carry storage but are boarded with a plain click and opened with a sneak click (Small Ships, Immersive
 * Aircraft, chest boats, the Immersive Petroleum speedboat). A plain click on them is left to the mount permission; a
 * sneak click, and any storage screen of theirs, needs the permissions that judge the entity.
 */
public final class RiddenStorageVehicles {
    public static final String DEFAULT_RULES = "smallships:.*, immersive_aircraft:.*, .*:(.*_)?chest_boat, immersivepetroleum:speedboat";

    private RiddenStorageVehicles() {
    }

    /**
     * Comma-separated entity id rules in the registry rule syntax (regular expressions, {@code !} negates). An invalid
     * expression is refused here, so a typo in the config stops the server instead of opening or closing vehicles.
     */
    public static List<String> parseRules(final String commaSeparatedRules) {
        final List<String> rules = List.copyOf(ConfiguredIds.parse(commaSeparatedRules));
        rules.forEach(rule -> Pattern.compile(rule.startsWith("!") ? rule.substring(1) : rule));
        return rules;
    }

    public static boolean isRiddenStorageVehicle(final List<String> rules, final String entityTypeId) {
        return !rules.isEmpty() && PermissionManager.checkMatch(rules, entityTypeId) == MatchResult.BLOCK;
    }
}
