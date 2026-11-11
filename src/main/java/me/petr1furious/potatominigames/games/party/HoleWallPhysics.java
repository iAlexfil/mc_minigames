package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.util.BoundingBox;

import java.util.List;

final class HoleWallPhysics {
    enum Direction {
        NORTH(0, 1, "north"),
        EAST(-1, 0, "east"),
        SOUTH(0, -1, "south"),
        WEST(1, 0, "west");
        final int dx, dz;
        final Component title;

        Direction(int dx, int dz, String title) {
            this.dx = dx;
            this.dz = dz;
            this.title = I18n.tr("party.rounds.hole_wall.direction." + title);
        }
    }

    static double speed(long elapsed, long duration) {
        return .14 + .18 * Math.clamp((double) elapsed / Math.max(1, duration), 0, 1);
    }

    static BoundingBox box(HoleWallPattern.Cell cell, Direction direction, double depth) {
        double x = direction.dx == 0 ? cell.u() : direction.dx * depth;
        double z = direction.dz == 0 ? cell.u() : direction.dz * depth;
        return new BoundingBox(x, 81 + cell.y(), z, x + 1, 82 + cell.y(), z + 1);
    }

    static List<BoundingBox> shapes(HoleWallPattern.Cell cell, Direction direction, double depth) {
        var origin = box(cell, direction, depth);
        return cell.block().boxes(direction).stream()
                .map(b -> b.shift(origin.getMinX(), origin.getMinY(), origin.getMinZ()))
                .toList();
    }

    static boolean supports(
            List<HoleWallPattern.Cell> cells, Direction direction, double depth, BoundingBox body) {
        boolean supported = false;
        for (var cell : cells)
            for (var solid : shapes(cell, direction, depth)) {
                if (solid.overlaps(body.clone().expand(-.001))) return false;
                if (Math.abs(body.getMinY() - solid.getMaxY()) <= .08
                        && body.getMaxX() > solid.getMinX()
                        && body.getMinX() < solid.getMaxX()
                        && body.getMaxZ() > solid.getMinZ()
                        && body.getMinZ() < solid.getMaxZ()) supported = true;
            }
        return supported;
    }

    static boolean collides(
            List<HoleWallPattern.Cell> cells,
            Direction direction,
            double from,
            double to,
            BoundingBox previous,
            BoundingBox current) {
        double vx = current.getMinX() - previous.getMinX() - direction.dx * (to - from);
        double vy = current.getMinY() - previous.getMinY();
        double vz = current.getMinZ() - previous.getMinZ() - direction.dz * (to - from);
        for (var cell : cells)
            for (var solid : shapes(cell, direction, from)) {
                // Landing or resting on a top face is safe; only side contacts launch the player.
                if (previous.getMinY() >= solid.getMaxY() - .001
                        && current.getMinY() >= solid.getMaxY() - .08) continue;
                if (swept(previous, solid, vx, vy, vz)) return true;
                // Native block collision stops an approaching player exactly at the side face.
                // Include that contact, but do not launch an idle neighbor or a player on top.
                boolean approaching =
                        (previous.getMaxX() <= solid.getMinX() + .001 && vx > 1e-7)
                                || (previous.getMinX() >= solid.getMaxX() - .001 && vx < -1e-7)
                                || (previous.getMaxZ() <= solid.getMinZ() + .001 && vz > 1e-7)
                                || (previous.getMinZ() >= solid.getMaxZ() - .001 && vz < -1e-7);
                if (approaching && swept(previous, solid.clone().expand(.015, 0, .015), vx, vy, vz))
                    return true;
            }
        return false;
    }

    /** Relative swept AABB: both actor and wall motion count, including motion through a cell. */
    private static boolean swept(
            BoundingBox body, BoundingBox solid, double vx, double vy, double vz) {
        double entry = 0, exit = 1;
        double[] min = {body.getMinX(), body.getMinY(), body.getMinZ()},
                max = {body.getMaxX(), body.getMaxY(), body.getMaxZ()};
        double[] low = {solid.getMinX(), solid.getMinY(), solid.getMinZ()},
                high = {solid.getMaxX(), solid.getMaxY(), solid.getMaxZ()},
                v = {vx, vy, vz};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(v[axis]) < 1e-9) {
                if (max[axis] <= low[axis] || min[axis] >= high[axis]) return false;
            } else {
                double a = (low[axis] - max[axis]) / v[axis],
                        b = (high[axis] - min[axis]) / v[axis];
                entry = Math.max(entry, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (entry >= exit) return false;
            }
        }
        return entry < exit;
    }
}
