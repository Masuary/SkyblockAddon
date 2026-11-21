package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * The islands a player can travel to: public, owned, not banning the viewer, optionally matching an owner name,
 * most recently active first.
 */
public final class IslandTravelListing {
    public static final long NEVER_ACTIVE = 0L;

    private IslandTravelListing() {
    }

    public static List<Island> travelDestinations(final Collection<? extends Island> islands, final UUID viewer,
                                                  final String ownerNameQuery, final Function<Island, String> ownerNameOf) {
        final String normalizedQuery = ownerNameQuery == null ? "" : ownerNameQuery.trim().toLowerCase(Locale.ROOT);
        final Comparator<Island> mostRecentlyActiveFirst = Comparator
                .comparingLong(Island::getLastActiveEpochMillis).reversed()
                .thenComparing(island -> ownerNameOf.apply(island).toLowerCase(Locale.ROOT))
                .thenComparing(Island::getId);

        return islands.stream()
                .filter(Island::isVisible)
                .filter(island -> !island.getOwner().equals(SkyblockAddonCore.NO_OWNER_ID))
                .filter(island -> !island.isBanned(viewer))
                .filter(island -> normalizedQuery.isEmpty() || ownerNameOf.apply(island).toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(Island.class::cast)
                .sorted(mostRecentlyActiveFirst)
                .toList();
    }

    /**
     * Owner plus members.
     */
    public static int playerCount(final Island island) {
        return island.getMembers().size() + 1;
    }

    public static String describeLastActive(final long lastActiveEpochMillis, final long nowEpochMillis) {
        if (lastActiveEpochMillis <= NEVER_ACTIVE) return "Unknown";
        final LocalDate lastActiveDay = Instant.ofEpochMilli(lastActiveEpochMillis).atZone(ZoneOffset.UTC).toLocalDate();
        final LocalDate today = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneOffset.UTC).toLocalDate();
        final long daysAgo = Duration.between(lastActiveDay.atStartOfDay(), today.atStartOfDay()).toDays();
        if (daysAgo <= 0) return "Today";
        if (daysAgo == 1) return "Yesterday";
        if (daysAgo < 30) return daysAgo + " days ago";
        return lastActiveDay.toString();
    }
}
