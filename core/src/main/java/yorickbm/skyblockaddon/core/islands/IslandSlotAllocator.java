package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hands out island grid slots: purged slots first, then the next slot of the snake-shaped grid.
 * Every method is synchronized so concurrent creates, purges and saves never share or lose a slot.
 */
public final class IslandSlotAllocator {
    private static final int MAXIMUM_SKIPPED_FRESH_SLOTS = 100_000;

    private final Supplier<? extends Collection<IslandSlot>> configuredReservedSlots;
    private final Deque<Vec3i> reusableSlots = new ArrayDeque<>();
    private final Set<IslandSlot> adminReservedSlots = new LinkedHashSet<>();
    private Vec3i nextFreshSlot = new Vec3i(0, 0, 0);

    public record Allocation(Vec3i center, List<IslandSlot> skippedSlots) {
    }

    public IslandSlotAllocator(final Supplier<? extends Collection<IslandSlot>> configuredReservedSlots) {
        this.configuredReservedSlots = configuredReservedSlots;
    }

    public synchronized void initialize(final Vec3i nextFreshSlot, final Collection<Vec3i> reusableSlots, final Collection<IslandSlot> adminReservedSlots) {
        this.nextFreshSlot = new Vec3i(nextFreshSlot.getX(), 0, nextFreshSlot.getZ());
        this.reusableSlots.clear();
        reusableSlots.forEach(this::release);
        this.adminReservedSlots.clear();
        this.adminReservedSlots.addAll(adminReservedSlots);
    }

    public synchronized Allocation allocate(final Predicate<Vec3i> isOccupied) {
        final Set<IslandSlot> reservedSlots = getAllReservedSlots();
        final List<IslandSlot> skippedSlots = new ArrayList<>();

        while (!reusableSlots.isEmpty()) {
            final Vec3i candidate = reusableSlots.poll();
            if (isFree(candidate, reservedSlots, isOccupied)) return new Allocation(candidate, skippedSlots);
            skippedSlots.add(IslandSlot.ofCenter(candidate));
        }

        for (int attempt = 0; attempt < MAXIMUM_SKIPPED_FRESH_SLOTS; attempt++) {
            final Vec3i candidate = nextFreshSlot;
            nextFreshSlot = nextGridLocation(candidate);
            if (isFree(candidate, reservedSlots, isOccupied)) return new Allocation(candidate, skippedSlots);
            skippedSlots.add(IslandSlot.ofCenter(candidate));
        }
        throw new IllegalStateException("No free island slot after skipping " + MAXIMUM_SKIPPED_FRESH_SLOTS + " reserved or occupied grid slots; next grid slot is " + nextFreshSlot);
    }

    public synchronized void release(final Vec3i center) {
        final Vec3i slotCenter = new Vec3i(center.getX(), 0, center.getZ());
        if (!reusableSlots.contains(slotCenter)) reusableSlots.add(slotCenter);
    }

    public synchronized boolean reserve(final IslandSlot slot) {
        return adminReservedSlots.add(slot);
    }

    public synchronized boolean unreserve(final IslandSlot slot) {
        return adminReservedSlots.remove(slot);
    }

    public synchronized List<IslandSlot> getAdminReservedSlots() {
        return List.copyOf(adminReservedSlots);
    }

    public Collection<IslandSlot> getConfiguredReservedSlots() {
        return configuredReservedSlots.get();
    }

    public synchronized Vec3i getNextFreshSlot() {
        return nextFreshSlot;
    }

    public synchronized List<Vec3i> getReusableSlots() {
        return List.copyOf(reusableSlots);
    }

    private Set<IslandSlot> getAllReservedSlots() {
        final Set<IslandSlot> reservedSlots = new HashSet<>(configuredReservedSlots.get());
        reservedSlots.addAll(adminReservedSlots);
        return reservedSlots;
    }

    private static boolean isFree(final Vec3i candidate, final Set<IslandSlot> reservedSlots, final Predicate<Vec3i> isOccupied) {
        return !reservedSlots.contains(IslandSlot.ofCenter(candidate)) && !isOccupied.test(candidate);
    }

    private static Vec3i nextGridLocation(final Vec3i location) {
        final int x = location.getX();
        final int z = location.getZ();
        final int spacing = IslandSlot.GRID_SPACING;

        if (x < z) {
            if (-1 * x < z) return new Vec3i(x + spacing, 0, z);
            return new Vec3i(x, 0, z + spacing);
        }

        if (x > z) {
            if (-1 * x >= z) return new Vec3i(x - spacing, 0, z);
            return new Vec3i(x, 0, z - spacing);
        }

        if (x <= 0) return new Vec3i(x, 0, z + spacing);
        return new Vec3i(x, 0, z - spacing);
    }
}
