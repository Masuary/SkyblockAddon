package yorickbm.skyblockaddon.util.geometry;

import net.minecraft.core.Vec3i;

import java.util.*;

public record Square(Vec3i corner1, Vec3i corner2) implements yorickbm.skyblockaddon.core.util.geometry.Square {
    /**
     * Enum to use for edge determination to allow for better readability.
     */
    public enum Edge {
        A, B, C, D, NONE
    }

    /**
     * Enum to use for axis determination to allow for better readability.
     */
    public enum Axis {
        X, Z
    }

    /**
     * Constructs a Square object with two corners.
     *
     * @param corner1 - The first corner.
     * @param corner2 - The second corner.
     */
    public Square {
    }

    /**
     * Determines on which edge a location is.
     *
     * @param location - The location to check.
     * @return The edge on which the location lies.
     */
    public Edge determineEdge(final Vec3i location) {
        final int x = location.getX();
        final int z = location.getZ();

        if (z == corner1.getZ() && x >= corner1.getX() && x <= corner2.getX()) {
            return Edge.A;
        } else if (x == corner2.getX() && z >= corner1.getZ() && z <= corner2.getZ()) {
            return Edge.B;
        } else if (z == corner2.getZ() && x >= corner1.getX() && x <= corner2.getX()) {
            return Edge.C;
        } else if (x == corner1.getX() && z >= corner1.getZ() && z <= corner2.getZ()) {
            return Edge.D;
        } else {
            return Edge.NONE;
        }
    }

    /**
     * Gets corners for a given edge.
     *
     * @param edge - The edge for which to get corners.
     * @return An array of corners for the specified edge.
     */
    public Vec3i[] getEdgeCorners(final Edge edge) {
        return switch (edge) {
            case A -> new Vec3i[]{new Vec3i(corner2.getX(), corner1.getY(), corner1.getZ()), corner1};
            case B -> new Vec3i[]{new Vec3i(corner2.getX(), corner1.getY(), corner1.getZ()), corner2};
            case C -> new Vec3i[]{new Vec3i(corner1.getX(), corner1.getY(), corner2.getZ()), corner2};
            case D -> new Vec3i[]{corner1, new Vec3i(corner1.getX(), corner1.getY(), corner2.getZ())};
            default -> new Vec3i[]{};
        };
    }

    /**
     * Gets edges associated with a corner.
     *
     * @param corner - The corner for which to get edges.
     * @return A set of edges associated with the specified corner.
     */
    public Set<Edge> getEdgesForCorner(final Vec3i corner) {
        final Set<Edge> edges = new HashSet<>();

        edges.add(determineEdge(new Vec3i(corner.getX() + 1, corner.getY(), corner.getZ())));
        edges.add(determineEdge(new Vec3i(corner.getX() - 1, corner.getY(), corner.getZ())));
        edges.add(determineEdge(new Vec3i(corner.getX(), corner.getY(), corner.getZ() + 1)));
        edges.add(determineEdge(new Vec3i(corner.getX(), corner.getY(), corner.getZ() - 1)));

        edges.removeIf(edge -> edge == Edge.NONE);

        return edges;
    }

    /**
     * Calculates points along an edge based on specified conditions.
     *
     * @param location - The current location.
     * @param amount   - The number of points to calculate.
     * @return A list of calculated points.
     */
    public List<Vec3i> calculatePoints(final Vec3i location, final int amount) {
        final Edge edge = determineEdge(location);
        final Axis axis = (edge == Edge.A || edge == Edge.C) ? Axis.X : Axis.Z;
        final Vec3i[] edgeCorners = getEdgeCorners(edge);
        final int halfAmount = amount / 2;

        final List<Vec3i> points = new ArrayList<>();

        //Positive iterator
        getPoints(points, edge, axis, edgeCorners[0], location, halfAmount, false);

        // Negative iterator
        getPoints(points, edge, axis, edgeCorners[1], location, halfAmount, true);

        return points;
    }

    /**
     * Recursively calculates points along an edge based on specified conditions.
     *
     * @param points     - The array to which calculated points are added.
     * @param edge       - The current edge.
     * @param axis       - The axis along which to calculate points.
     * @param corner     - The corner associated with the edge.
     * @param location   - The current location.
     * @param amount     - The remaining number of points to calculate.
     * @param isNegative - Whether to iterate in the negative direction.
     */
    private void getPoints(final List<Vec3i> points, final Edge edge, final Axis axis, final Vec3i corner, final Vec3i location, final int amount, final boolean isNegative) {

        final int x = location.getX();
        final int z = location.getZ();

        for (int i = 1; i <= amount; i++) {
            final Vec3i point = new Vec3i(
                    x + ((isNegative ? -1 : 1) * (axis == Axis.X ? i : 0)),
                    location.getY(),
                    z + ((isNegative ? -1 : 1) * (axis == Axis.Z ? i : 0))
            );

            if ((isNegative ? point.getX() < corner.getX() || point.getZ() < corner.getZ() : point.getX() > corner.getX() || point.getZ() > corner.getZ())) {

                final Set<Edge> attachedTo = getEdgesForCorner(corner);
                final Edge newEdge = attachedTo.stream().filter(e -> e != edge).findFirst().orElse(Edge.NONE);

                final Vec3i[] newCorners = getEdgeCorners(newEdge);
                final Vec3i newCorner = Arrays.stream(newCorners).filter(e -> !e.equals(corner)).findFirst().orElse(Vec3i.ZERO);
                if (newCorner == Vec3i.ZERO) break; // We could not get the new corner!! Prevent error

                final Axis newAxis = (axis == Axis.X) ? Axis.Z : Axis.X;
                final boolean newIsNegative = (newAxis == Axis.X) ? newCorner.getX() < corner.getX() : newCorner.getZ() < corner.getZ();

                getPoints(
                        points,
                        newEdge,
                        newAxis,
                        new Vec3i(newCorner.getX(), location.getY(), newCorner.getZ()),
                        new Vec3i(corner.getX(), location.getY(), corner.getZ()),
                        (amount - i) + 1,
                        newIsNegative
                );

                break;
            } else {
                points.add(point);
            }
        }
    }
}