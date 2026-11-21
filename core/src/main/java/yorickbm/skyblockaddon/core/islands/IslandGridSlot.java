package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

public record IslandGridSlot(int column, int row) {
    private static final int ISLAND_SPACING = SkyblockAddonCore.ISLAND_SIZE * 2 + SkyblockAddonCore.ISLAND_BUFFER;

    public static IslandGridSlot containing(final Vec3i islandCenter) {
        return new IslandGridSlot(nearestSlotIndex(islandCenter.getX()), nearestSlotIndex(islandCenter.getZ()));
    }

    private static int nearestSlotIndex(final int blockCoordinate) {
        return Math.floorDiv(blockCoordinate + ISLAND_SPACING / 2, ISLAND_SPACING);
    }
}
