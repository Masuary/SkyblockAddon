package yorickbm.skyblockaddon.core.islands;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class AbandonedIslandQuery {
    private static final Comparator<Island> BY_GRID_POSITION = Comparator
            .comparingInt((Island island) -> island.getCenter().getX())
            .thenComparingInt(island -> island.getCenter().getZ())
            .thenComparing(Island::getId);

    private AbandonedIslandQuery() {
    }

    public static List<Island> findAbandonedIslands(final Collection<? extends Island> islands) {
        return islands.stream()
                .filter(Island::hasNoOwnerAndNoMembers)
                .map(Island.class::cast)
                .sorted(BY_GRID_POSITION)
                .toList();
    }
}
