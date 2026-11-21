package yorickbm.skyblockaddon.core.islands;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import yorickbm.skyblockaddon.core.util.geometry.BoundingBox;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class IslandManager {
    private final Map<UUID, Island> islandsByUUID = new ConcurrentHashMap<>();
    private volatile Supplier<? extends Collection<IslandSlot>> configuredReservedSlotsSource = Set::of;
    private final IslandSlotAllocator slotAllocator = new IslandSlotAllocator(() -> configuredReservedSlotsSource.get());

    LoadingCache<UUID, Optional<UUID>> CACHE_islandByPlayerUUID;
    LoadingCache<BoundingBox, Optional<UUID>> CACHE_islandByBoundingBox;

    private static final IslandManager instance = new IslandManager();
    public static IslandManager getInstance() {
        return instance;
    }

    /**
     * Initialize default data into manager
     * @param islands List of islands
     * @param reusableLocations Centers of purged slots that may be handed out again
     * @param nextFreshSlot Next unused slot of the island grid
     * @param adminReservedSlots Slots reserved with /island admin reserve
     */
    public void initializeData(List<? extends Island> islands, List<Vec3i> reusableLocations, Vec3i nextFreshSlot, List<IslandSlot> adminReservedSlots) {
        this.islandsByUUID.clear();
        islands.forEach(island -> islandsByUUID.put(island.getId(), island)); //Store islands in map
        slotAllocator.initialize(nextFreshSlot, reusableLocations, adminReservedSlots);
    }

    public void setConfiguredReservedSlotsSource(final Supplier<? extends Collection<IslandSlot>> configuredReservedSlotsSource) {
        this.configuredReservedSlotsSource = configuredReservedSlotsSource;
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
                        return findIslandId(isl -> isl.isPartOf(key));
                    }
                });

        CACHE_islandByBoundingBox = CacheBuilder.newBuilder()
                .expireAfterAccess(24, TimeUnit.HOURS)
                .maximumSize(Math.max(256L, (long) Math.floor(maxServerPlayers * 4.0)))
                .build(new CacheLoader<>() {
                    @Override
                    public Optional<UUID> load(@Nonnull final BoundingBox key) {
                        return findIslandId(isl -> isl.getIslandBoundingBox().isInside(key.getCenter()));
                    }
                });
    }

    private Optional<UUID> findIslandId(final Predicate<Island> match) {
        return islandsByUUID.values().stream().filter(match).findFirst().map(Island::getId);
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
     * Take the next free island slot: never a reserved slot or one inside an existing island.
     */
    public IslandSlotAllocator.Allocation allocateIslandSlot() {
        return slotAllocator.allocate(this::isSlotOccupied);
    }

    private boolean isSlotOccupied(final Vec3i slotCenter) {
        return islandsByUUID.values().stream().anyMatch(island -> island.getIslandBoundingBox().isInside(slotCenter));
    }

    /**
     * Add island center location as reusable location
     * @param center Vec3i object of island center
     */
    public void islandSpaceReusable(Vec3i center) {
        slotAllocator.release(center);
    }

    public boolean reserveSlot(final IslandSlot slot) {
        return slotAllocator.reserve(slot);
    }

    public boolean unreserveSlot(final IslandSlot slot) {
        return slotAllocator.unreserve(slot);
    }

    public List<IslandSlot> getAdminReservedSlots() {
        return slotAllocator.getAdminReservedSlots();
    }

    public Collection<IslandSlot> getConfiguredReservedSlots() {
        return slotAllocator.getConfiguredReservedSlots();
    }

    public Vec3i getNextFreshSlot() {
        return slotAllocator.getNextFreshSlot();
    }

    public List<Vec3i> getReusableLocations() {
        return slotAllocator.getReusableSlots();
    }
    public Map<UUID, Island> getEntrySet() { return this.islandsByUUID; }
}
