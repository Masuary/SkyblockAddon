package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandSlotAllocatorTest {
    private static final int SPACING = IslandSlot.GRID_SPACING;
    private static final Predicate<Vec3i> NOTHING_OCCUPIED = center -> false;

    private static IslandSlotAllocator allocatorWithConfiguredReservations(final Collection<IslandSlot> configuredReservedSlots) {
        return new IslandSlotAllocator(() -> configuredReservedSlots);
    }

    private static List<Vec3i> allocateCenters(final IslandSlotAllocator allocator, final int count, final Predicate<Vec3i> isOccupied) {
        final List<Vec3i> centers = new ArrayList<>();
        for (int index = 0; index < count; index++) centers.add(allocator.allocate(isOccupied).center());
        return centers;
    }

    @Test
    void freshGridFollowsTheSnakeFromTheOrigin() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());

        final List<Vec3i> centers = allocateCenters(allocator, 4, NOTHING_OCCUPIED);

        assertEquals(List.of(
                new Vec3i(0, 0, 0),
                new Vec3i(0, 0, SPACING),
                new Vec3i(SPACING, 0, SPACING),
                new Vec3i(SPACING, 0, 0)), centers);
    }

    @Test
    void configuredReservedOriginIsSkippedAndTheSequenceContinues() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of(new IslandSlot(0, 0)));

        final IslandSlotAllocator.Allocation first = allocator.allocate(NOTHING_OCCUPIED);
        final Vec3i second = allocator.allocate(NOTHING_OCCUPIED).center();

        assertEquals(new Vec3i(0, 0, SPACING), first.center());
        assertEquals(List.of(new IslandSlot(0, 0)), first.skippedSlots());
        assertEquals(new Vec3i(SPACING, 0, SPACING), second);
    }

    @Test
    void adminReservedSlotIsSkipped() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());
        assertTrue(allocator.reserve(new IslandSlot(0, SPACING)));

        final List<Vec3i> centers = allocateCenters(allocator, 2, NOTHING_OCCUPIED);

        assertEquals(List.of(new Vec3i(0, 0, 0), new Vec3i(SPACING, 0, SPACING)), centers);
    }

    @Test
    void occupiedSlotIsSkipped() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());
        final Predicate<Vec3i> originOccupied = center -> center.getX() == 0 && center.getZ() == 0;

        assertEquals(new Vec3i(0, 0, SPACING), allocator.allocate(originOccupied).center());
    }

    @Test
    void reusableSlotIsHandedOutBeforeTheFreshGrid() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());
        allocator.initialize(new Vec3i(5 * SPACING, 0, 0), List.of(new Vec3i(SPACING, 0, 0)), List.of());

        final List<Vec3i> centers = allocateCenters(allocator, 2, NOTHING_OCCUPIED);

        assertEquals(new Vec3i(SPACING, 0, 0), centers.get(0));
        assertEquals(new Vec3i(5 * SPACING, 0, 0), centers.get(1));
    }

    @Test
    void reservedOrOccupiedReusableSlotsAreDropped() {
        final Vec3i reservedReusable = new Vec3i(0, 0, 0);
        final Vec3i occupiedReusable = new Vec3i(SPACING, 0, 0);
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of(IslandSlot.ofCenter(reservedReusable)));
        allocator.initialize(new Vec3i(2 * SPACING, 0, 0), List.of(reservedReusable, occupiedReusable), List.of());

        final IslandSlotAllocator.Allocation allocation = allocator.allocate(center -> center.equals(occupiedReusable));

        assertEquals(new Vec3i(2 * SPACING, 0, 0), allocation.center());
        assertEquals(List.of(IslandSlot.ofCenter(reservedReusable), IslandSlot.ofCenter(occupiedReusable)), allocation.skippedSlots());
        assertTrue(allocator.getReusableSlots().isEmpty());
    }

    @Test
    void releasedSlotIsReusedOnceEvenWhenReleasedTwice() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());
        final Vec3i first = allocator.allocate(NOTHING_OCCUPIED).center();

        allocator.release(new Vec3i(first.getX(), 117, first.getZ()));
        allocator.release(first);

        assertEquals(List.of(first), allocator.getReusableSlots());
        assertEquals(first, allocator.allocate(NOTHING_OCCUPIED).center());
        assertEquals(new Vec3i(0, 0, SPACING), allocator.allocate(NOTHING_OCCUPIED).center());
    }

    @Test
    void unreserveOnlyRemovesAdminReservations() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of(new IslandSlot(0, 0)));

        assertTrue(allocator.reserve(new IslandSlot(SPACING, 0)));
        assertFalse(allocator.reserve(new IslandSlot(SPACING, 0)));
        assertTrue(allocator.unreserve(new IslandSlot(SPACING, 0)));
        assertFalse(allocator.unreserve(new IslandSlot(0, 0)));
        assertTrue(allocator.getAdminReservedSlots().isEmpty());
        assertEquals(List.of(new IslandSlot(0, 0)), List.copyOf(allocator.getConfiguredReservedSlots()));
    }

    @Test
    void allocationGivesUpInsteadOfLoopingForever() {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of());

        assertThrows(IllegalStateException.class, () -> allocator.allocate(center -> true));
    }

    @Test
    void concurrentAllocationsNeverShareASlot() throws Exception {
        final IslandSlotAllocator allocator = allocatorWithConfiguredReservations(Set.of(new IslandSlot(0, 0)));
        final List<Vec3i> heldCenters = new ArrayList<>(allocateCenters(allocator, 10, NOTHING_OCCUPIED));
        allocator.release(heldCenters.remove(7));
        allocator.release(heldCenters.remove(3));
        final int threadCount = 8;
        final int allocationsPerThread = 250;
        final CountDownLatch startSignal = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            final List<Future<List<Vec3i>>> results = new ArrayList<>();
            for (int thread = 0; thread < threadCount; thread++) {
                final Callable<List<Vec3i>> worker = () -> {
                    startSignal.await();
                    return allocateCenters(allocator, allocationsPerThread, NOTHING_OCCUPIED);
                };
                results.add(executor.submit(worker));
            }
            startSignal.countDown();

            final Set<Vec3i> uniqueCenters = new HashSet<>(heldCenters);
            int totalAllocations = heldCenters.size();
            for (final Future<List<Vec3i>> result : results) {
                final List<Vec3i> centers = result.get(30, TimeUnit.SECONDS);
                totalAllocations += centers.size();
                uniqueCenters.addAll(centers);
            }

            assertEquals(8 + threadCount * allocationsPerThread, totalAllocations);
            assertEquals(totalAllocations, uniqueCenters.size());
            assertFalse(uniqueCenters.contains(new Vec3i(0, 0, 0)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void slotTextIsSnappedToTheNearestSlotCenter() {
        assertEquals(new IslandSlot(0, 0), IslandSlot.parse("0,0"));
        assertEquals(new IslandSlot(-10 * SPACING, -8 * SPACING), IslandSlot.parse(" -10000 , -8000 "));
        assertEquals(new IslandSlot(SPACING, -SPACING), IslandSlot.parse("620,-1390"));
        assertEquals("1000,-1000", new IslandSlot(SPACING, -SPACING).format());
    }

    @Test
    void malformedSlotTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IslandSlot.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> IslandSlot.parse("a,b"));
        assertThrows(IllegalArgumentException.class, () -> IslandSlot.parse("1,2,3"));
    }
}
