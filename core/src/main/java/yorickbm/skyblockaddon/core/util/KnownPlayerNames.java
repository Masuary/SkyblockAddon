package yorickbm.skyblockaddon.core.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a typed player name against the players this server has seen (UUID to last known name), without a network
 * lookup. A name two known players share is only resolved when exactly one of them matches the typed casing; any other
 * ambiguity is left to a Mojang lookup.
 */
public final class KnownPlayerNames {

    private KnownPlayerNames() {
    }

    public static Optional<UUID> find(final Map<UUID, String> knownNames, final String typedName) {
        final List<UUID> sameNameIgnoringCase = knownNames.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(typedName))
                .map(Map.Entry::getKey)
                .toList();
        if (sameNameIgnoringCase.size() == 1) return Optional.of(sameNameIgnoringCase.get(0));

        final List<UUID> sameName = sameNameIgnoringCase.stream()
                .filter(id -> knownNames.get(id).equals(typedName))
                .toList();
        return sameName.size() == 1 ? Optional.of(sameName.get(0)) : Optional.empty();
    }
}
