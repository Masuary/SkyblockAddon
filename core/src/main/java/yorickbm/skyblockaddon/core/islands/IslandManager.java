package yorickbm.skyblockaddon.core.islands;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.BoundingBox;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IslandManager {
    private Vec3i lastLocation;
    private Queue<Vec3i> reusableLocations;
    private HashMap<UUID, Island> islandsByUUID;

    LoadingCache<UUID, Optional<UUID>> CACHE_islandByPlayerUUID;
    LoadingCache<BoundingBox, Optional<UUID>> CACHE_islandByBoundingBox;

    private static final IslandManager instance = new IslandManager();
    public static IslandManager getInstance() {
        return instance;
    }

    public IslandManager() {
        islandsByUUID = new HashMap<>();
        lastLocation = new Vec3i(0,0,0);
        reusableLocations = new LinkedList();
    }

    /**
     * Initialize default data into manager
     * @param islands List of islands
     * @param reusableLocations List of Vec3i objects
     * @param lastLocation Vec3i object
     */
    public void initializeData(List<? extends Island> islands, List<Vec3i> reusableLocations, Vec3i lastLocation) {
        this.islandsByUUID.clear();
        this.reusableLocations.clear();
        islands.forEach(island -> islandsByUUID.put(island.getId(), island)); //Store islands in map
        this.reusableLocations.addAll(reusableLocations);
        this.lastLocation = lastLocation;
    }

    /**
     * Creates reverse lookup caches for optimized performance.
     */
    public void initializeCaches(int maxServerPlayers) {
        CACHE_islandByPlayerUUID = CacheBuilder.newBuilder()
                .expireAfterAccess(12, TimeUnit.HOURS)
                .maximumSize((long) Math.floor(maxServerPlayers * 2.5))
                .build(new CacheLoader<>() {
                    @Override
                    public Optional<UUID> load(@Nonnull final UUID key) {
                        return safeFindIslandId(isl -> isl.isPartOf(key));
                    }
                });

        CACHE_islandByBoundingBox = CacheBuilder.newBuilder()
                .expireAfterAccess(24, TimeUnit.HOURS)
                .maximumSize(Math.max(256L, (long) Math.floor(maxServerPlayers * 4.0)))
                .build(new CacheLoader<>() {
                    @Override
                    public Optional<UUID> load(@Nonnull final BoundingBox key) {
                        return safeFindIslandId(isl -> isl.getIslandBoundingBox().isInside(key.getCenter()));
                    }
                });
    }

    /**
     * Stream over {@code islandsByUUID} defensively. The map is mutated from the server thread
     * but {@code CacheLoader.load} may run from any caller's thread, so a concurrent modification
     * can surface as {@link java.util.ConcurrentModificationException} mid-iteration. We catch it,
     * log it, and return empty so the caller falls back to a fresh slow-path lookup rather than
     * crashing the player join / command handler.
     */
    private Optional<UUID> safeFindIslandId(final java.util.function.Predicate<Island> match) {
        try {
            return islandsByUUID.values().stream().filter(match).findFirst().map(Island::getId);
        } catch (final java.util.ConcurrentModificationException cme) {
            org.apache.logging.log4j.LogManager.getLogger().warn("CME during island lookup; returning empty for this attempt", cme);
            return Optional.empty();
        }
    }

    /**
     * Fill reverse lookup caches with data for entity.
     * I.E. Island UUID for entity.
     *
     * @param uuid - Player whose data is loaded into reverse lookup cache
     */
    public void loadIslandIntoReverseCache(UUID uuid) {
        try {
            final Optional<UUID> islandId = CACHE_islandByPlayerUUID.get(uuid);
            if (islandId.isEmpty()) return; //Got no island
            final Island island = getIslandByUUID(islandId.get());
            if (island == null) return; //Cache pointed at a now-removed island; tolerate.
            island.getName(); //Load owners name into cache
            CACHE_islandByBoundingBox.put(island.getIslandBoundingBox(), islandId); //Store into bounding box cache
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get island by its UUID
     *
     * @param id - UUID to lookup
     * @return - Island associated with islandId
     */
    public Island getIslandByUUID(UUID id) {
        return islandsByUUID.get(id);
    }

    /**
     * Get island where entity is a part of.
     *
     * @param uuid - UUID to check
     * @return - Island of entity
     */
    public Island getIslandByEntityUUID(UUID uuid) {
        final Optional<UUID> islandId = CACHE_islandByPlayerUUID.getIfPresent(uuid);

        if (islandId == null || islandId.isEmpty()) {
            final Optional<Island> island = islandsByUUID.values().stream()
                    .filter(isl -> isl.isPartOf(uuid))
                    .findFirst();
            island.ifPresent(value -> CACHE_islandByPlayerUUID.put(uuid, Optional.of(value.getId())));
            return island.orElse(null);
        }
        
        return getIslandByUUID(islandId.get());
    }

    /**
     * Get the island for a specific block position
     * @param pos - Block position
     * @return - Island block position falls in
     */
    public Island getIslandByPos(Vec3i pos) {
        if (CACHE_islandByBoundingBox != null) {
            final Optional<UUID> islandId = CACHE_islandByBoundingBox.asMap().entrySet().stream()
                    .filter(entry -> entry.getKey().isInside(pos))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(Optional.empty());
            if (islandId.isPresent()) return getIslandByUUID(islandId.get());
        }

        final Optional<Island> island = islandsByUUID.values().stream().filter(isl -> isl.getIslandBoundingBox().isInside(pos)).findFirst();
        if (island.isPresent() && CACHE_islandByBoundingBox != null) {
            CACHE_islandByBoundingBox.put(island.get().getIslandBoundingBox(), Optional.of(island.get().getId()));
        }
        return island.orElse(null);
    }

    /**
     * Register a new island through object
     * @param island - Island to register
     */
    public void registerIsland(Island island, UUID entity) {
        islandsByUUID.put(island.getId(), island);

        //Register island into cache
        CACHE_islandByPlayerUUID.put(entity, Optional.of(island.getId()));
        CACHE_islandByBoundingBox.put(island.getIslandBoundingBox(), Optional.of(island.getId()));
    }

    /**
     * Clear island from cache
     * @param island Island object
     */
    public void clearIslandCache(Island island) {
        CACHE_islandByBoundingBox.invalidate(island.getIslandBoundingBox());
        Stream.concat(island.getMembers().stream(), Stream.of(island.getOwner()))
                .forEach(CACHE_islandByPlayerUUID::invalidate);
        islandsByUUID.remove(island.getId());
    }

    /**
     * Clear player from cache. Just invalidates the entry - leaving an explicit Optional.empty()
     * cached would force every subsequent lookup through the 12h-TTL slow path even after the
     * player rejoins or gets re-invited.
     */
    public void clearCacheForPlayer(UUID uuid) {
        CACHE_islandByPlayerUUID.invalidate(uuid);
    }

    /**
     * Get collection of all created islands for the world.
     *
     * @return Collection of islands
     */
    public Collection<Island> getIslands() {
        return Collections.unmodifiableCollection(islandsByUUID.values());
    }

    /**
     * Get collection of all current islands in cache
     */
    public Collection<Island> getIslandsFromCache() {
        Set<UUID> activeIslandUUIDs = CACHE_islandByPlayerUUID
                .asMap()
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        return activeIslandUUIDs.stream()
                .map(islandsByUUID::get)
                .filter(Objects::nonNull).toList();
    }

    /**
     * Get next island generation location
     */
    public Vec3i getNextIslandGen() {
        final Vec3i islandLocation = reusableLocations.isEmpty() ? lastLocation : reusableLocations.remove();
        if(islandLocation == lastLocation) lastLocation = nextGridLocation(lastLocation);
        return islandLocation;
    }

    /**
     * Add island center location as reusable location
     * @param center Vec3i object of island center
     */
    public void islandSpaceReusable(Vec3i center) {
        reusableLocations.add(new Vec3i(center.getX(), 0, center.getZ()));
    }

    /**
     * Get next Vec3i for an island based on snake pattern island generation algorithm.
     *
     * @param location Last island location
     * @return Location for next island
     */
    private Vec3i nextGridLocation(Vec3i location) {
        int x = location.getX();
        int z = location.getZ();
        final int d = SkyblockAddonCore.ISLAND_SIZE * 2 + SkyblockAddonCore.ISLAND_BUFFER;

        if (x < z) {
            if (-1 * x < z) return new Vec3i(x + d, 0, z);
            return new Vec3i(x, 0, z + d);
        }

        if (x > z) {
            if (-1 * x >= z) return new Vec3i(x - d, 0, z);
            return new Vec3i(x, 0, z - d);
        }

        if (x <= 0) return new Vec3i(x, 0, z + d);
        return new Vec3i(x, 0, z - d);
    }

    /**
     * Simple getters & setters
     */
    public Vec3i getLastLocation() {
        return this.lastLocation;
    }
    public Queue<Vec3i> getReusableLocations() {
        return reusableLocations;
    }
    public HashMap<UUID, Island> getEntrySet() { return this.islandsByUUID; }
}
