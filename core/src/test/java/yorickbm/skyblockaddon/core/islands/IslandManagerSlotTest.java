package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandManagerSlotTest {
    private static final int SPACING = IslandSlot.GRID_SPACING;

    @Test
    void slotUnderAnExistingIslandIsNeverHandedOut() {
        final IslandManager manager = new IslandManager();
        manager.initializeData(List.of(new TestIsland()), List.of(), new Vec3i(0, 0, 0), List.of());

        assertEquals(new Vec3i(0, 0, SPACING), manager.allocateIslandSlot().center());
    }

    @Test
    void purgedSlotUnderACarriedIslandIsSkipped() {
        final IslandManager manager = new IslandManager();
        manager.initializeData(List.of(new TestIsland()), List.of(new Vec3i(0, 0, 0)), new Vec3i(3 * SPACING, 0, 0), List.of());

        assertEquals(new Vec3i(3 * SPACING, 0, 0), manager.allocateIslandSlot().center());
    }

    @Test
    void configuredAndPersistedReservationsAreBothRespected() {
        final IslandManager manager = new IslandManager();
        manager.setConfiguredReservedSlotsSource(() -> Set.of(new IslandSlot(0, 0)));
        manager.initializeData(List.of(), List.of(), new Vec3i(0, 0, 0), List.of(new IslandSlot(0, SPACING)));

        assertEquals(new Vec3i(SPACING, 0, SPACING), manager.allocateIslandSlot().center());
    }
}
