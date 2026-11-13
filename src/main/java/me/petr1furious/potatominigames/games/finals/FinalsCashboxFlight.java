package me.petr1furious.potatominigames.games.finals;

import org.bukkit.util.Vector;

import java.util.List;

/** Swept item-volume contact with the two current, axis-aligned cashout bodies. */
final class FinalsCashboxFlight {
    private static final double EPSILON = 1e-7;

    /**
     * Returns the first contacted station index, or -1. Positions are item centers and station
     * bottom centers; maxDistance limits travel from {@code from} in world blocks, including a
     * terrain obstruction. Positive infinity allows the whole movement segment.
     */
    static int hit(
            Vector from,
            Vector to,
            Vector halfSize,
            List<Vector> stationBottomCenters,
            double maxDistance) {
        if (!(maxDistance >= 0)) return -1;
        double[] start = {from.getX(), from.getY(), from.getZ()};
        double[] movement = {to.getX() - start[0], to.getY() - start[1], to.getZ() - start[2]};
        double distance =
                Math.sqrt(
                        movement[0] * movement[0]
                                + movement[1] * movement[1]
                                + movement[2] * movement[2]);
        double limit = distance == 0 ? 0 : Math.min(1, maxDistance / distance);
        double first = Double.POSITIVE_INFINITY;
        int found = -1;
        for (int slot = 0; slot < Math.min(2, stationBottomCenters.size()); slot++) {
            Vector station = stationBottomCenters.get(slot);
            // Expand the station by the item's actual half-extents, then sweep its center.
            double[] minimum = {
                station.getX() - .5 - halfSize.getX() - EPSILON,
                station.getY() - halfSize.getY() - EPSILON,
                station.getZ() - .5 - halfSize.getZ() - EPSILON
            };
            double[] maximum = {
                station.getX() + .5 + halfSize.getX() + EPSILON,
                station.getY() + 1 + halfSize.getY() + EPSILON,
                station.getZ() + .5 + halfSize.getZ() + EPSILON
            };
            double contact = contact(start, movement, minimum, maximum, limit);
            if (contact < first) {
                first = contact;
                found = slot;
            }
        }
        return found;
    }

    private static double contact(
            double[] start, double[] movement, double[] minimum, double[] maximum, double limit) {
        double entry = 0, exit = limit;
        for (int axis = 0; axis < 3; axis++) {
            if (movement[axis] == 0) {
                if (start[axis] < minimum[axis] || start[axis] > maximum[axis])
                    return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (minimum[axis] - start[axis]) / movement[axis];
            double b = (maximum[axis] - start[axis]) / movement[axis];
            entry = Math.max(entry, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (entry > exit) return Double.POSITIVE_INFINITY;
        }
        return entry;
    }

    private FinalsCashboxFlight() {}
}
