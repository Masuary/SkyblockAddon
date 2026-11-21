package yorickbm.skyblockaddon.core.permissions;

import yorickbm.skyblockaddon.core.JSON.PermissionJson;
import yorickbm.skyblockaddon.core.util.JSON.JSONEncoder;
import yorickbm.skyblockaddon.core.util.MatchResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class PermissionManager {
    private static final Map<String, MatchRule> COMPILED_MATCH_RULES = new ConcurrentHashMap<>();

    private volatile List<Permission> permissions;

    private static final PermissionManager instance = new PermissionManager();
    public static PermissionManager getInstance() {
        return instance;
    }
    public PermissionManager() {
        this.permissions = new ArrayList<>();
    }

    /**
     * Load permissions from a JSON file. The current permissions stay active when loading fails, because an empty
     * permission list switches island protection off.
     * @param path Path for JSON file
     * @return Amount of permissions loaded
     * @throws IllegalStateException when the file cannot be read or parsed, defines no permissions or holds an invalid
     * item, block or entity pattern
     */
    public int loadPermissions(final Path path) {
        final List<Permission> loadedPermissions;
        try {
            loadedPermissions = JSONEncoder.loadFromFile(path, PermissionJson.class).permissions;
        } catch (final Exception exception) {
            throw new IllegalStateException("Failed to load island permissions from '" + path.toAbsolutePath() + "': " + exception, exception);
        }
        if (loadedPermissions == null || loadedPermissions.isEmpty()) {
            throw new IllegalStateException("'" + path.toAbsolutePath() + "' defines no permissions; refusing to run with island protection switched off");
        }
        compileMatchRules(path, loadedPermissions);
        this.permissions = List.copyOf(loadedPermissions);
        return loadedPermissions.size();
    }

    /**
     * Get all permissions registered with the manager
     * @return List of permission objects
     */
    public List<Permission> getPermissions() {
        return this.permissions;
    }

    /**
     * Retrieve permissions for specified category
     * @param category Category to filter for
     * @return List of permission objects
     */
    public List<Permission> getPermissionsFor(final String category) {
        return this.permissions.stream().filter(pm -> pm.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
    }

    /**
     * Retrieve permissions for specified trigger
     * @param trigger Trigger to filter for
     * @return Liust of permission objects
     */
    public List<Permission> getPermissionsForTrigger(final String trigger) {
        return this.permissions.stream().filter(pm -> pm.hasTrigger(trigger)).collect(Collectors.toList());
    }

    /**
     * Run permission filter logic check, runs several checks to determine if the action matches the permission.
     * @return MatchResult SKIP, ALLOW, or DENIED
     */
    public static MatchResult checkMatch(final List<String> rules, final String item) {
        boolean foundNonNegatedMatch = false;
        boolean onlyNegations = true; // Track if all rules are negated

        if(rules.isEmpty()) return MatchResult.BLOCK; //If its EMPTY we always block
        for (final String rule : rules) {
            final MatchRule matchRule = COMPILED_MATCH_RULES.computeIfAbsent(rule, MatchRule::compile);
            final boolean isNegation = matchRule.negation();

            if (matchRule.pattern().matcher(item).matches()) {
                if (isNegation) {
                    return MatchResult.SKIP; // If a negated rule matches, return SKIP immediately
                } else {
                    foundNonNegatedMatch = true; // Found a non-negated match
                }
            }

            if (!isNegation) {
                onlyNegations = false; // Found a non-negated rule, so it's not "only negations"
            }
        }

        // If no non-negated matches were found, and all rules were negations, return BLOCK
        return foundNonNegatedMatch ? MatchResult.BLOCK : (onlyNegations ? MatchResult.BLOCK : MatchResult.SKIP);
    }

    private static void compileMatchRules(final Path path, final List<Permission> loadedPermissions) {
        for (final Permission permission : loadedPermissions) {
            if (permission.getData() == null) continue;
            permission.getData().getItemBlockAndEntityRules().forEach(rule -> {
                try {
                    COMPILED_MATCH_RULES.computeIfAbsent(rule, MatchRule::compile);
                } catch (final PatternSyntaxException invalidPattern) {
                    throw new IllegalStateException("'" + path.toAbsolutePath() + "' permission '" + permission.getId() + "' has an invalid pattern '" + rule + "': " + invalidPattern.getDescription(), invalidPattern);
                }
            });
        }
    }

    private record MatchRule(boolean negation, Pattern pattern) {
        private static MatchRule compile(final String rule) {
            final boolean negation = rule.startsWith("!");
            return new MatchRule(negation, Pattern.compile(negation ? rule.substring(1) : rule, Pattern.CASE_INSENSITIVE));
        }
    }

}
