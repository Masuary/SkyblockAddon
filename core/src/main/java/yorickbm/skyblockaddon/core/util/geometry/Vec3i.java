package yorickbm.skyblockaddon.core.util.geometry;

public final class Vec3i {
    public final int x;
    public final int y;
    public final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // --- Getters ---
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    // --- Basic operations ---

    public Vec3i add(Vec3i other) {
        return new Vec3i(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3i subtract(Vec3i other) {
        return new Vec3i(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3i multiply(int scalar) {
        return new Vec3i(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vec3i divide(int scalar) {
        if (scalar == 0) throw new ArithmeticException("Division by zero");
        return new Vec3i(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    // --- Utility methods ---

    public int dot(Vec3i other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public int manhattanLength() {
        return Math.abs(x) + Math.abs(y) + Math.abs(z);
    }

    public Vec3i negate() {
        return new Vec3i(-x, -y, -z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec3i)) return false;
        Vec3i v = (Vec3i) obj;
        return x == v.x && y == v.y && z == v.z;
    }

    @Override
    public int hashCode() {
        // simple but effective hash
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "Vec3i(" + x + ", " + y + ", " + z + ")";
    }
}
