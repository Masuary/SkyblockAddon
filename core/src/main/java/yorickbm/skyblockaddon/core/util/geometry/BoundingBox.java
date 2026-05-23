package yorickbm.skyblockaddon.core.util.geometry;

public final class BoundingBox {
    public final double minX, minY, minZ;
    public final double maxX, maxY, maxZ;

    public BoundingBox(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Minimum coordinates must be <= maximum coordinates");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static BoundingBox fromCorners(Vec3i a, Vec3i b) {
        int minX = Math.min(a.x, b.x);
        int minY = Math.min(a.y, b.y);
        int minZ = Math.min(a.z, b.z);

        int maxX = Math.max(a.x, b.x);
        int maxY = Math.max(a.y, b.y);
        int maxZ = Math.max(a.z, b.z);

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // --- Basic operations ---

    public boolean isInside(Vec3i point) {
        return contains(point.getX(), point.getY(), point.getZ());
    }

    /** Returns the center of the bounding box as a Vec3i (rounded down) */
    public Vec3i getCenter() {
        int centerX = (int) Math.floor((minX + maxX) / 2.0);
        int centerY = (int) Math.floor((minY + maxY) / 2.0);
        int centerZ = (int) Math.floor((minZ + maxZ) / 2.0);
        return new Vec3i(centerX, centerY, centerZ);
    }

    /** Returns true if this box intersects the other box */
    public boolean intersects(BoundingBox other) {
        return this.maxX > other.minX && this.minX < other.maxX &&
                this.maxY > other.minY && this.minY < other.maxY &&
                this.maxZ > other.minZ && this.minZ < other.maxZ;
    }

    /** Returns true if the point is inside this box */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    /** Returns a new bounding box moved by the given offsets */
    public BoundingBox offset(double dx, double dy, double dz) {
        return new BoundingBox(minX + dx, minY + dy, minZ + dz,
                maxX + dx, maxY + dy, maxZ + dz);
    }

    /** Returns the union of this box and another (smallest box containing both) */
    public BoundingBox union(BoundingBox other) {
        return new BoundingBox(
                Math.min(this.minX, other.minX),
                Math.min(this.minY, other.minY),
                Math.min(this.minZ, other.minZ),
                Math.max(this.maxX, other.maxX),
                Math.max(this.maxY, other.maxY),
                Math.max(this.maxZ, other.maxZ)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BoundingBox)) return false;
        BoundingBox b = (BoundingBox) obj;
        return Double.compare(minX, b.minX) == 0 &&
                Double.compare(minY, b.minY) == 0 &&
                Double.compare(minZ, b.minZ) == 0 &&
                Double.compare(maxX, b.maxX) == 0 &&
                Double.compare(maxY, b.maxY) == 0 &&
                Double.compare(maxZ, b.maxZ) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(minX);
        bits = bits * 31 + Double.doubleToLongBits(minY);
        bits = bits * 31 + Double.doubleToLongBits(minZ);
        bits = bits * 31 + Double.doubleToLongBits(maxX);
        bits = bits * 31 + Double.doubleToLongBits(maxY);
        bits = bits * 31 + Double.doubleToLongBits(maxZ);
        return (int)(bits ^ (bits >>> 32));
    }

    @Override
    public String toString() {
        return "BoundingBox[min=(" + minX + ", " + minY + ", " + minZ + "), " +
                "max=(" + maxX + ", " + maxY + ", " + maxZ + ")]";
    }
}
