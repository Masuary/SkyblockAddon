package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

public record IslandSlot(int x, int z) {
    public static final int GRID_SPACING = SkyblockAddonCore.ISLAND_SIZE * 2 + SkyblockAddonCore.ISLAND_BUFFER;

    public static IslandSlot nearestTo(final int blockX, final int blockZ) {
        return new IslandSlot(snapToGrid(blockX), snapToGrid(blockZ));
    }

    public static IslandSlot ofCenter(final Vec3i center) {
        return new IslandSlot(center.getX(), center.getZ());
    }

    public static IslandSlot parse(final String text) {
        final String[] coordinates = text.split(",");
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("Island slot '" + text + "' must be written as \"x,z\"");
        }
        try {
            return nearestTo(Integer.parseInt(coordinates[0].trim()), Integer.parseInt(coordinates[1].trim()));
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Island slot '" + text + "' must be written as \"x,z\" with whole block coordinates", exception);
        }
    }

    public String format() {
        return x + "," + z;
    }

    private static int snapToGrid(final int blockCoordinate) {
        return Math.toIntExact(Math.floorDiv((long) blockCoordinate + GRID_SPACING / 2, GRID_SPACING) * GRID_SPACING);
    }
}
