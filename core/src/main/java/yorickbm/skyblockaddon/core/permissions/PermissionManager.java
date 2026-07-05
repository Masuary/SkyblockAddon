package yorickbm.skyblockaddon.core.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.JSON.LoreLineDeserializer;
import yorickbm.skyblockaddon.core.JSON.LoreLineJson;
import yorickbm.skyblockaddon.core.JSON.PermissionJson;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;
import yorickbm.skyblockaddon.core.util.MatchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class PermissionManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final String LEGACY_WOLDS_SALVAGER_TYPO = "woldsvaults:vault_salager";
    private static final String WOLDS_SALVAGER_ID = "woldsvaults:vault_salvager";
    private List<Permission> permissions = new ArrayList<>();

    private static final PermissionManager instance = new PermissionManager();
    public static PermissionManager getInstance() { return instance; }

    public PermissionManager() {
        this.permissions = new ArrayList<>();
    }

    public List<Permission> snapshotState() {
        return List.copyOf(permissions);
    }

    public void restoreState(final List<Permission> state) {
        permissions = new ArrayList<>(state);
        PATTERN_CACHE.clear();
    }

    // ── Loading ────────────────────────────────────────────────────────────

    /**
     * Load from a single file (old format, backward compat).
     * Replaces all previously loaded permissions.
     */
    public int loadPermissions(final Path path) {
        if (!path.toFile().isFile()) return -1;
        try {
            final PermissionJson json = loadLegacyPermissionFile(path);
            final List<Permission> candidates = new ArrayList<>();
            final Set<String> permissionIds = new HashSet<>();
            if (json.permissions != null) resolveAndAdd(json.permissions, candidates, permissionIds, path);
            sortByPriority(candidates);
            permissions = candidates;
            PATTERN_CACHE.clear();
            return candidates.size();
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to load permissions from " + path, ex);
        }
    }

    /**
     * Load from a directory of JSON files, each optionally gated by a mod.
     * Replaces all previously loaded permissions.
     */
    public int loadPermissions(final Path dir, final Predicate<String> isModLoaded) {
        if (!dir.toFile().isDirectory()) return -1;

        final List<Path> files = new ArrayList<>();
        try (var paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to scan permissions directory " + dir, e);
        }

        final Gson gson = new GsonBuilder()
                .registerTypeAdapter(LoreLineJson.class, new LoreLineDeserializer())
                .create();
        final List<Permission> candidates = new ArrayList<>();
        final Set<String> permissionIds = new HashSet<>();
        for (final Path file : files) {
            try {
                final String content = Files.readString(file);
                final PermissionJson json = gson.fromJson(content, PermissionJson.class);
                if (json == null || json.permissions == null) continue;

                if (json.mod != null && !json.mod.isEmpty() && !isModLoaded.test(json.mod)) {
                    LOGGER.debug("Skipping permission file {} (mod '{}' not loaded)", file.getFileName(), json.mod);
                    continue;
                }

                resolveAndAdd(json.permissions, candidates, permissionIds, file);
                LOGGER.debug("Loaded {} permissions from {}", json.permissions.size(), file.getFileName());
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to load permission file " + file, e);
            }
        }

        sortByPriority(candidates);
        permissions = candidates;
        PATTERN_CACHE.clear();
        return candidates.size();
    }

    /**
     * Loads a deployed legacy registry together with the per-mod registry directory.
     * Legacy entries take precedence by ID so server-specific patterns and metadata
     * remain authoritative, while permissions introduced by newer releases are added.
     */
    public int loadPermissions(
            final Path legacyFile,
            final Path directory,
            final Predicate<String> isModLoaded
    ) {
        if (!legacyFile.toFile().isFile()) return loadPermissions(directory, isModLoaded);
        if (!directory.toFile().isDirectory()) return loadPermissions(legacyFile);

        final List<Permission> candidates = new ArrayList<>();
        final Set<String> permissionIds = new HashSet<>();
        final Set<String> legacyPermissionIds = new HashSet<>();

        try {
            final PermissionJson legacyJson = loadLegacyPermissionFile(legacyFile);
            if (legacyJson != null && legacyJson.permissions != null) {
                final List<Permission> activeLegacyPermissions = legacyJson.permissions.stream()
                        .filter(permission -> permission != null
                                && !PermissionStateMigrator.isRetired(permission.getId()))
                        .toList();
                resolveAndAdd(activeLegacyPermissions, candidates, permissionIds, legacyFile);
                legacyPermissionIds.addAll(permissionIds);
                final long retiredCount = legacyJson.permissions.size() - activeLegacyPermissions.size();
                if (retiredCount > 0) {
                    LOGGER.info("Skipped {} retired legacy permissions; their stored states migrate to granular IDs",
                            retiredCount);
                }
            }

            final Gson gson = createGson();
            final Set<String> directoryPermissionIds = new HashSet<>();
            for (final Path file : listJsonFiles(directory)) {
                final PermissionJson json = gson.fromJson(Files.readString(file), PermissionJson.class);
                if (json == null || json.permissions == null) continue;
                if (json.mod != null && !json.mod.isEmpty() && !isModLoaded.test(json.mod)) continue;

                for (final Permission permission : json.permissions) {
                    validatePermissionId(permission, file);
                    final String normalizedId = normalizeId(permission.getId());
                    if (!directoryPermissionIds.add(normalizedId)) {
                        throw new IllegalArgumentException(
                                "Duplicate permission ID '" + permission.getId() + "' in " + file
                        );
                    }
                    if (legacyPermissionIds.contains(normalizedId)) {
                        LOGGER.debug("Using legacy override for permission '{}' instead of {}",
                                permission.getId(), file.getFileName());
                        continue;
                    }
                    resolveAndAdd(List.of(permission), candidates, permissionIds, file);
                }
            }

            sortByPriority(candidates);
            permissions = candidates;
            PATTERN_CACHE.clear();
            return candidates.size();
        } catch (final Exception exception) {
            throw new IllegalStateException(
                    "Failed to merge legacy permissions from " + legacyFile + " with " + directory,
                    exception
            );
        }
    }

    private List<Path> listJsonFiles(final Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LoreLineJson.class, new LoreLineDeserializer())
                .create();
    }

    private PermissionJson loadLegacyPermissionFile(final Path path) throws IOException {
        final String content = Files.readString(path);
        final String correctedContent = content.replace(LEGACY_WOLDS_SALVAGER_TYPO, WOLDS_SALVAGER_ID);
        if (!content.equals(correctedContent)) {
            LOGGER.warn("Corrected legacy Wold's Vaults block ID '{}' to '{}' while loading {}",
                    LEGACY_WOLDS_SALVAGER_TYPO, WOLDS_SALVAGER_ID, path);
        }
        return createGson().fromJson(correctedContent, PermissionJson.class);
    }

    private void resolveAndAdd(
            final List<Permission> loaded,
            final List<Permission> candidates,
            final Set<String> permissionIds,
            final Path source
    ) {
        for (final Permission p : loaded) {
            validatePermissionId(p, source);

            final String normalizedId = normalizeId(p.getId());
            if (!permissionIds.add(normalizedId)) {
                throw new IllegalArgumentException("Duplicate permission ID '" + p.getId() + "' in " + source);
            }

            if (p.getData() != null) {
                p.getData().resolve(PermissionGroupRegistry.getInstance());
                validatePatterns(p, source);
            }
            candidates.add(p);
        }
    }

    private void validatePermissionId(final Permission permission, final Path source) {
        if (permission == null || permission.getId() == null || permission.getId().isBlank()) {
            throw new IllegalArgumentException("Permission in " + source + " has no ID");
        }
    }

    private String normalizeId(final String permissionId) {
        return permissionId.toLowerCase(Locale.ROOT);
    }

    private void validatePatterns(final Permission permission, final Path source) {
        for (final String context : permission.getData().getContextKeys()) {
            final List<String> patterns = permission.getData().getFiltersForContext(context);
            if (patterns == null) continue;

            for (final String rule : patterns) {
                final String regex = rule.startsWith("!") ? rule.substring(1) : rule;
                if (regex.isEmpty()) {
                    throw new IllegalArgumentException("Permission '" + permission.getId() + "' in " + source
                            + " has an empty " + context + " pattern");
                }
                try {
                    Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                } catch (final PatternSyntaxException exception) {
                    throw new IllegalArgumentException("Permission '" + permission.getId() + "' in " + source
                            + " has invalid " + context + " pattern '" + rule + "'", exception);
                }
            }
        }
    }

    private void sortByPriority(final List<Permission> values) {
        values.sort(Comparator.comparingInt(Permission::getPriority).reversed());
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public List<Permission> getPermissions() { return Collections.unmodifiableList(permissions); }

    public List<Permission> getPermissionsFor(final String category) {
        return permissions.stream()
                .filter(pm -> pm.getCategory().equalsIgnoreCase(category))
                .sorted(Comparator.comparingInt(Permission::getOrder))
                .collect(Collectors.toList());
    }

    public List<Permission> getPermissionsForTrigger(final String trigger) {
        return permissions.stream()
                .filter(pm -> pm.hasTrigger(trigger))
                .collect(Collectors.toList());
    }

    // ── Match logic ────────────────────────────────────────────────────────

    /**
     * Run permission filter logic.
     *
     * @return SKIP  – a negated rule matched (item/block explicitly excluded)
     *         BLOCK – a positive rule matched (or all-negation list with no match)
     *         SKIP  – no rule matched at all (onlyNegations = false)
     */
    public static MatchResult checkMatch(final List<String> rules, final String item) {
        boolean foundNonNegatedMatch = false;
        boolean onlyNegations = true;

        if (rules.isEmpty()) return MatchResult.BLOCK;

        for (final String rule : rules) {
            final boolean isNegation = rule.startsWith("!");
            final String patternString = isNegation ? rule.substring(1) : rule;
            final Pattern pattern = getPattern(patternString);

            if (pattern.matcher(item).matches()) {
                if (isNegation) return MatchResult.SKIP;
                else foundNonNegatedMatch = true;
            }

            if (!isNegation) onlyNegations = false;
        }

        return foundNonNegatedMatch ? MatchResult.BLOCK
                : (onlyNegations ? MatchResult.BLOCK : MatchResult.SKIP);
    }

    /**
     * Check whether any negated pattern in the list explicitly matches the value.
     * Used by InteractionValidator to detect explicit exclusions.
     */
    public static boolean hasExplicitNegation(final List<String> patterns, final String value) {
        for (final String p : patterns) {
            if (!p.startsWith("!")) continue;
            final String pat = p.substring(1);
            if (getPattern(pat).matcher(value).matches()) return true;
        }
        return false;
    }

    private static Pattern getPattern(final String regex) {
        return PATTERN_CACHE.computeIfAbsent(regex, value -> Pattern.compile(value, Pattern.CASE_INSENSITIVE));
    }
}
