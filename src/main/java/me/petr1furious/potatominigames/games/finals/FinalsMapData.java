package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;

/** Serializable positions and bounded geometry; snapshots never depend on the editor world. */
final class FinalsMapData {
    static final long MAX_BLOCKS = 1_000_000;
    static final int MAX_AXIS = 512;

    record Point(double x, double y, double z, float yaw, float pitch) {
        Point {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch)
                    || Math.abs(x) > 29_999_000
                    || Math.abs(z) > 29_999_000
                    || Math.abs(y) > 4096) throw FinalsMaps.failure("invalid_position");
        }

        static Point of(Location location) {
            return new Point(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch());
        }

        int bx() {
            return (int) Math.floor(x);
        }

        int by() {
            return (int) Math.floor(y);
        }

        int bz() {
            return (int) Math.floor(z);
        }

        Location at(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }

        Point centered() {
            return new Point(bx() + .5, by(), bz() + .5, yaw, pitch);
        }

        boolean sameBlock(Point other) {
            return bx() == other.bx() && by() == other.by() && bz() == other.bz();
        }

        double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        void write(ConfigurationSection parent, String key) {
            parent.set(key, List.of(x, y, z, (double) yaw, (double) pitch));
        }

        static Point read(ConfigurationSection parent, String key) {
            List<Double> values = parent.getDoubleList(key);
            return values.size() == 5
                    ? new Point(
                            values.get(0),
                            values.get(1),
                            values.get(2),
                            values.get(3).floatValue(),
                            values.get(4).floatValue())
                    : null;
        }
    }

    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Bounds {
            long sx = (long) maxX - minX + 1,
                    sy = (long) maxY - minY + 1,
                    sz = (long) maxZ - minZ + 1;
            if (sx < 5
                    || sy < 4
                    || sz < 5
                    || sx > MAX_AXIS
                    || sy > MAX_AXIS
                    || sz > MAX_AXIS
                    || sx * sy * sz > MAX_BLOCKS)
                throw FinalsMaps.failure("bounds", MAX_AXIS, MAX_BLOCKS);
        }

        static Bounds between(Point first, Point second) {
            if (first == null || second == null) throw FinalsMaps.failure("corners");
            return new Bounds(
                    Math.min(first.bx(), second.bx()),
                    Math.min(first.by(), second.by()),
                    Math.min(first.bz(), second.bz()),
                    Math.max(first.bx(), second.bx()),
                    Math.max(first.by(), second.by()),
                    Math.max(first.bz(), second.bz()));
        }

        boolean contains(Point p) {
            return p != null
                    && p.x >= minX
                    && p.x < (double) maxX + 1
                    && p.y >= minY
                    && p.y < (double) maxY + 1
                    && p.z >= minZ
                    && p.z < (double) maxZ + 1;
        }

        boolean standingRoom(Point p) {
            return contains(p) && p.by() > minY && p.by() + 1 <= maxY;
        }

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        BlockVector size() {
            return new BlockVector(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }

        Location origin(World world) {
            return new Location(world, minX, minY, minZ);
        }
    }

    static final class Draft {
        String world;
        String folder;
        boolean imported;
        Point first, second, vault, stationA, stationB;
        List<Point> red = new ArrayList<>(), blue = new ArrayList<>();

        Draft copy() {
            Draft result = new Draft();
            result.world = world;
            result.folder = folder;
            result.imported = imported;
            result.first = first;
            result.second = second;
            result.vault = vault;
            result.stationA = stationA;
            result.stationB = stationB;
            result.red = new ArrayList<>(red);
            result.blue = new ArrayList<>(blue);
            return result;
        }

        void write(ConfigurationSection section) {
            section.set("world", world);
            section.set("folder", folder);
            section.set("imported", imported);
            if (first != null) first.write(section, "pos1");
            if (second != null) second.write(section, "pos2");
            if (vault != null) vault.write(section, "vault");
            if (stationA != null) stationA.write(section, "cashout-a");
            if (stationB != null) stationB.write(section, "cashout-b");
            for (int i = 0; i < red.size(); i++) red.get(i).write(section, "red." + i);
            for (int i = 0; i < blue.size(); i++) blue.get(i).write(section, "blue." + i);
        }

        static Draft read(ConfigurationSection section) {
            Draft draft = new Draft();
            draft.world = section.getString("world");
            draft.folder = section.getString("folder");
            draft.imported = section.getBoolean("imported");
            draft.first = Point.read(section, "pos1");
            draft.second = Point.read(section, "pos2");
            draft.vault = Point.read(section, "vault");
            draft.stationA = Point.read(section, "cashout-a");
            draft.stationB = Point.read(section, "cashout-b");
            for (int i = 0; i < 16 && section.contains("red." + i); i++)
                draft.red.add(Point.read(section, "red." + i));
            for (int i = 0; i < 16 && section.contains("blue." + i); i++)
                draft.blue.add(Point.read(section, "blue." + i));
            if (draft.world == null || draft.red.contains(null) || draft.blue.contains(null))
                throw FinalsMaps.failure("invalid_position");
            return draft;
        }
    }

    record Saved(
            Bounds bounds,
            List<Point> red,
            List<Point> blue,
            Point vault,
            Point stationA,
            Point stationB,
            String template) {
        Saved {
            red = List.copyOf(red);
            blue = List.copyOf(blue);
        }

        static Saved from(Draft draft, String template) {
            Bounds bounds = Bounds.between(draft.first, draft.second);
            if (draft.red.isEmpty()
                    || draft.blue.isEmpty()
                    || draft.vault == null
                    || draft.stationA == null
                    || draft.stationB == null) throw FinalsMaps.failure("incomplete");
            List<Point> objectives = List.of(draft.vault, draft.stationA, draft.stationB);
            for (Point point : objectives)
                if (!bounds.standingRoom(point)) throw FinalsMaps.failure("outside");
            for (int i = 0; i < objectives.size(); i++) {
                for (int j = i + 1; j < objectives.size(); j++) {
                    if (objectives.get(i).distanceSquared(objectives.get(j)) < 64)
                        throw FinalsMaps.failure("objectives_close");
                }
            }
            for (Point spawn : concat(draft.red, draft.blue)) {
                if (!bounds.standingRoom(spawn)) throw FinalsMaps.failure("outside");
                for (Point point : objectives)
                    if (spawn.distanceSquared(point) < 9) throw FinalsMaps.failure("spawn_close");
            }
            for (Point red : draft.red)
                for (Point blue : draft.blue) {
                    if (red.distanceSquared(blue) < 16) throw FinalsMaps.failure("teams_close");
                }
            return new Saved(
                    bounds,
                    draft.red,
                    draft.blue,
                    draft.vault,
                    draft.stationA,
                    draft.stationB,
                    template);
        }
    }

    static List<Point> concat(List<Point> first, List<Point> second) {
        List<Point> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private FinalsMapData() {}
}
