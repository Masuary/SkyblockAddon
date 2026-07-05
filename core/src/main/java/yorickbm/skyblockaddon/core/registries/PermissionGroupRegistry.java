package yorickbm.skyblockaddon.core.registries;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.JSON.PermissionGroupJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class PermissionGroupRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PermissionGroupRegistry INSTANCE = new PermissionGroupRegistry();

    public static PermissionGroupRegistry getInstance() { return INSTANCE; }

    // group name → (context → patterns)
    private final Map<String, Map<String, List<String>>> groups = new HashMap<>();
    private final Set<String> unavailableOptionalGroups = new LinkedHashSet<>();

    public void clear() {
        groups.clear();
        unavailableOptionalGroups.clear();
    }

    public State snapshotState() {
        return new State(copyGroups(groups), Set.copyOf(unavailableOptionalGroups));
    }

    public void restoreState(final State state) {
        groups.clear();
        groups.putAll(copyGroups(state.groups()));
        unavailableOptionalGroups.clear();
        unavailableOptionalGroups.addAll(state.unavailableOptionalGroups());
    }

    private Map<String, Map<String, List<String>>> copyGroups(
            final Map<String, Map<String, List<String>>> source
    ) {
        final Map<String, Map<String, List<String>>> copy = new HashMap<>();
        source.forEach((groupName, contexts) -> {
            final Map<String, List<String>> contextCopy = new HashMap<>();
            contexts.forEach((context, patterns) -> contextCopy.put(context, new ArrayList<>(patterns)));
            copy.put(groupName, contextCopy);
        });
        return copy;
    }

    public record State(
            Map<String, Map<String, List<String>>> groups,
            Set<String> unavailableOptionalGroups
    ) { }

    public int loadFromDirectory(final Path dir, final Predicate<String> isModLoaded) {
        if (!dir.toFile().isDirectory()) return 0;

        int loaded = 0;
        final Map<String, Map<String, List<String>>> candidateGroups = new HashMap<>();
        final Set<String> candidateUnavailableOptionalGroups = new LinkedHashSet<>();
        final List<Path> files = new ArrayList<>();
        try (var paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to list permission group files in " + dir, e);
        }

        final Gson gson = new Gson();
        for (final Path file : files) {
            try {
                final String json = Files.readString(file);
                final PermissionGroupJson data = gson.fromJson(json, PermissionGroupJson.class);
                if (data == null || data.groups == null) continue;

                // Skip if required mod is not loaded
                if (data.mod != null && !data.mod.isEmpty() && !isModLoaded.test(data.mod)) {
                    data.groups.keySet().stream()
                            .map(name -> name.toLowerCase(Locale.ROOT))
                            .forEach(candidateUnavailableOptionalGroups::add);
                    LOGGER.debug("Skipping group file {} (mod '{}' not loaded)", file.getFileName(), data.mod);
                    continue;
                }

                // Merge groups into registry
                for (final Map.Entry<String, Map<String, List<String>>> entry : data.groups.entrySet()) {
                    mergeGroup(candidateGroups, entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                    loaded++;
                }
                final String modLabel = (data.mod != null && !data.mod.isEmpty()) ? data.mod : "always";
                LOGGER.debug("Loaded {} group(s) from {} [mod: {}]", data.groups.size(), file.getFileName(), modLabel);
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to load permission group file " + file, e);
            }
        }

        validateReferences(candidateGroups, candidateUnavailableOptionalGroups);
        groups.clear();
        groups.putAll(candidateGroups);
        unavailableOptionalGroups.clear();
        unavailableOptionalGroups.addAll(candidateUnavailableOptionalGroups);
        return loaded;
    }

    private void mergeGroup(
            final Map<String, Map<String, List<String>>> targetGroups,
            final String name,
            final Map<String, List<String>> contextPatterns
    ) {
        final Map<String, List<String>> existing = targetGroups.computeIfAbsent(name, k -> new HashMap<>());
        for (final Map.Entry<String, List<String>> entry : contextPatterns.entrySet()) {
            existing.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }

    /**
     * Expand a list of patterns, resolving any #group_name references.
     * Handles negated group refs like !#group_name.
     */
    public List<String> expandPatterns(final String context, final List<String> patterns) {
        return expandPatterns(context, patterns, new LinkedHashSet<>(), groups, unavailableOptionalGroups);
    }

    private List<String> expandPatterns(
            final String context,
            final List<String> patterns,
            final Set<String> resolutionPath,
            final Map<String, Map<String, List<String>>> availableGroups,
            final Set<String> unavailableGroups
    ) {
        if (patterns == null) return List.of();
        final List<String> result = new ArrayList<>();
        for (final String pattern : patterns) {
            if (pattern.startsWith("!#")) {
                final String groupName = pattern.substring(2).toLowerCase(Locale.ROOT);
                getGroupPatterns(context, groupName, resolutionPath, availableGroups, unavailableGroups).stream()
                        .map(p -> "!" + p)
                        .forEach(result::add);
            } else if (pattern.startsWith("#")) {
                final String groupName = pattern.substring(1).toLowerCase(Locale.ROOT);
                result.addAll(getGroupPatterns(context, groupName, resolutionPath, availableGroups, unavailableGroups));
            } else {
                result.add(pattern);
            }
        }
        return result;
    }

    private List<String> getGroupPatterns(
            final String context,
            final String groupName,
            final Set<String> resolutionPath,
            final Map<String, Map<String, List<String>>> availableGroups,
            final Set<String> unavailableGroups
    ) {
        final Map<String, List<String>> group = availableGroups.get(groupName);
        if (group == null) {
            if (unavailableGroups.contains(groupName)) return List.of();
            throw new IllegalArgumentException(
                    "Unresolved permission group '#" + groupName + "' for context '" + context + "'"
            );
        }

        if (!resolutionPath.add(groupName)) {
            final String cycle = String.join(" -> ", resolutionPath) + " -> " + groupName;
            throw new IllegalArgumentException("Cyclic permission group reference for context '" + context + "': " + cycle);
        }

        try {
            final List<String> contextPatterns = group.getOrDefault(context, List.of());
            return expandPatterns(context, contextPatterns, resolutionPath, availableGroups, unavailableGroups);
        } finally {
            resolutionPath.remove(groupName);
        }
    }

    private void validateReferences(
            final Map<String, Map<String, List<String>>> candidateGroups,
            final Set<String> candidateUnavailableOptionalGroups
    ) {
        for (final String groupName : candidateGroups.keySet().stream().sorted().toList()) {
            final Map<String, List<String>> contexts = candidateGroups.get(groupName);
            for (final Map.Entry<String, List<String>> context : contexts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                expandPatterns(
                        context.getKey(),
                        context.getValue(),
                        new LinkedHashSet<>(),
                        candidateGroups,
                        candidateUnavailableOptionalGroups
                );
            }
        }
    }

    public boolean hasGroup(final String name) {
        return groups.containsKey(name.toLowerCase(Locale.ROOT));
    }
}
