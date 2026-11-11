package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Six shared, non-repeating native tasks sampled once from the full obstacle pool. */
final class ParkourCourse {
    static final int LENGTH = 6;
    static final int WIDTH = 27, PITCH = 28, BOTTOM = 0, TOP = 256;
    static final int BUILD_BOTTOM = 8, BUILD_TOP = 248;

    record Pos(int x, int y, int z) {}

    record Island(int x, int z, int y, int width) {
        double centerX() {
            return x + width / 2.0;
        }

        boolean landing(double atX, double feetY, double atZ) {
            return atX >= x
                    && atX < x + width
                    && atZ >= z
                    && atZ < z + 7
                    && Math.abs(feetY - (y + 1)) < .3;
        }

        Island narrow() {
            return new Island((int) (centerX() - 1.5), z, y, 3);
        }
    }

    record Supply(Material material, int count) {}

    enum Kind {
        BRIDGE("bridge", 18, 0, kit(Material.CYAN_WOOL, 22, Material.SHEARS, 1)),
        WIND_WALL("wind_wall", 2, 28, kit(Material.CYAN_WOOL, 36, Material.WIND_CHARGE, 8)),
        WATER_DROP("water_drop", 4, -44, kit(Material.WATER_BUCKET, 1)),
        SCAFFOLD("scaffold", 2, 24, kit(Material.SCAFFOLDING, 30)),
        LADDER_CLIMB("ladder_climb", 1, 20, kit(Material.LADDER, 24)),
        SLIME_BOUNCE("slime_bounce", 15, -20, kit(Material.SLIME_BLOCK, 4)),
        PEARL("pearl", 32, 0, kit(Material.ENDER_PEARL, 3)),
        SWIM("swim", 20, 0, kit(Material.WATER_BUCKET, 1)),
        LADDER_DROP("ladder_drop", 3, -36, kit(Material.LADDER, 8)),
        WEB_DROP("web_drop", 3, -28, kit(Material.COBWEB, 3, Material.SHEARS, 1)),
        HONEY_DROP("honey_drop", 3, -24, kit(Material.HONEY_BLOCK, 4)),
        FLIGHT("flight", 50, 64, kit(Material.ELYTRA, 1, Material.FIREWORK_ROCKET, 6)),
        LADDER_TRAVERSE("ladder_traverse", 0, 0, kit(Material.CYAN_WOOL, 12, Material.LADDER, 12)),
        BLOCK_ASCENT("block_ascent", 14, 14, kit(Material.CYAN_WOOL, 32, Material.SHEARS, 1)),
        POT_ASCENT("pot_ascent", 14, 14, kit(Material.CYAN_WOOL, 16, Material.FLOWER_POT, 18));

        final String id;
        final int gap, rise;
        final List<Supply> supplies;

        Kind(String id, int gap, int rise, List<Supply> supplies) {
            this.id = id;
            this.gap = gap;
            this.rise = rise;
            this.supplies = supplies;
        }

        String key() {
            return "party.rounds.parkour_race.stage." + id;
        }

        private static List<Supply> kit(Material material, int count) {
            return List.of(new Supply(material, count));
        }

        private static List<Supply> kit(Material a, int n, Material b, int m) {
            return List.of(new Supply(a, n), new Supply(b, m));
        }
    }

    private final List<Kind> sequence;
    private final List<Island> islands;
    private final Map<Pos, Material> blocks;

    ParkourCourse(Random random) {
        this(sample(random), random.nextBoolean());
    }

    private static List<Kind> sample(Random random) {
        List<Kind> pool = new ArrayList<>(List.of(Kind.values()));
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, LENGTH));
    }

    ParkourCourse(List<Kind> selected, boolean mirror) {
        if (selected == null
                || selected.size() != LENGTH
                || new HashSet<>(selected).size() != LENGTH
                || selected.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Six distinct tasks required");
        sequence = List.copyOf(selected);
        List<Island> relative = new ArrayList<>();
        int anchor = mirror ? 20 : 0;
        relative.add(new Island(anchor, 0, 0, 7));
        for (Kind stage : sequence) {
            Island from = relative.getLast();
            if (stage == Kind.LADDER_TRAVERSE) {
                from = from.narrow();
                relative.set(relative.size() - 1, from);
                anchor = 20 - anchor;
                relative.add(new Island(anchor + 2, from.z(), from.y(), 3));
            } else
                relative.add(
                        new Island(anchor, from.z() + 7 + stage.gap, from.y() + stage.rise, 7));
        }
        // Translate the whole sampled profile, rather than rejecting down-heavy random sequences.
        int lowest = relative.stream().mapToInt(island -> island.y() - 1).min().orElse(0);
        for (int i = 0; i < sequence.size(); i++)
            if (stage(i) == Kind.SLIME_BOUNCE) lowest = Math.min(lowest, relative.get(i).y() - 37);
        final int offset = 48 - lowest;
        islands =
                relative.stream()
                        .map(i -> new Island(i.x(), i.z(), i.y() + offset, i.width()))
                        .toList();
        Map<Pos, Material> plan = new LinkedHashMap<>();
        for (int i = 0; i < islands.size(); i++) {
            Island island = islands.get(i);
            for (int x = island.x(); x < island.x() + island.width(); x++)
                for (int z = island.z(); z < island.z() + 7; z++) {
                    plan.put(new Pos(x, island.y() - 1, z), Material.SMOOTH_QUARTZ);
                    plan.put(
                            new Pos(x, island.y(), z),
                            z == island.z() + 3
                                    ? (i == stages() ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK)
                                    : Material.QUARTZ_BRICKS);
                }
        }
        for (int i = 0; i < stages(); i++) {
            Kind kind = stage(i);
            Island from = island(i), to = island(i + 1);
            switch (kind) {
                case WIND_WALL, SCAFFOLD, LADDER_CLIMB -> {
                    for (int x = 0; x < WIDTH; x++)
                        for (int y = BUILD_BOTTOM; y <= to.y(); y++)
                            plan.put(new Pos(x, y, to.z() - 1), Material.SMOOTH_QUARTZ);
                }
                case SLIME_BOUNCE -> {
                    int center = (int) from.centerX();
                    for (int x = center - 2; x <= center + 2; x++)
                        for (int z = from.z() + 15; z <= from.z() + 20; z++) {
                            plan.put(new Pos(x, from.y() - 37, z), Material.SMOOTH_QUARTZ);
                            plan.put(new Pos(x, from.y() - 36, z), Material.LIME_CONCRETE);
                        }
                }
                case SWIM -> swimTunnel(plan, from, to);
                case POT_ASCENT -> {
                    int center = (int) from.centerX();
                    for (int x = center - 1; x <= center + 1; x++)
                        for (int z = 0; z < 3; z++) {
                            plan.put(
                                    new Pos(x, from.y(), from.z() + 4 + z),
                                    Material.LIGHT_BLUE_CONCRETE);
                            plan.put(new Pos(x, to.y(), to.z() + z), Material.LIGHT_BLUE_CONCRETE);
                        }
                }
                case LADDER_DROP, HONEY_DROP ->
                        clutchWall(
                                plan,
                                from,
                                to,
                                (int) from.centerX()
                                        + (mirror == (kind == Kind.LADDER_DROP) ? -3 : 3));
                default -> {}
            }
        }
        for (Pos pos : plan.keySet())
            if (pos.x() < 0 || pos.x() >= WIDTH || pos.y() < BUILD_BOTTOM || pos.y() >= TOP - 8)
                throw new IllegalStateException("Sampled course exceeds the sealed arena bounds");
        blocks = Collections.unmodifiableMap(plan);
    }

    private void swimTunnel(Map<Pos, Material> plan, Island from, Island to) {
        int center = (int) from.centerX();
        for (int x = center - 1; x <= center + 1; x++)
            for (int z = from.z() + 4; z < from.z() + 7; z++)
                for (int y = from.y() + 1; y <= from.y() + 2; y++)
                    plan.put(new Pos(x, y, z), Material.WATER);
        for (int z = from.z() + 7; z < to.z(); z++)
            for (int x = 0; x < WIDTH; x++) {
                plan.put(new Pos(x, from.y(), z), Material.SMOOTH_QUARTZ);
                for (int y = from.y() + 1; y <= from.y() + 10; y++)
                    plan.put(
                            new Pos(x, y, z),
                            Math.abs(x - center) <= 1 && y == from.y() + 1
                                    ? Material.WATER
                                    : Material.SMOOTH_QUARTZ);
            }
    }

    private void clutchWall(Map<Pos, Material> plan, Island from, Island to, int side) {
        for (int z = from.z() + 4; z < to.z() + 7; z++)
            for (int y = to.y(); y <= from.y() + 4; y++)
                plan.put(new Pos(side, y, z), Material.SMOOTH_QUARTZ);
    }

    int stages() {
        return sequence.size();
    }

    int end() {
        return islands.getLast().z() + 7;
    }

    Island island(int checkpoint) {
        return islands.get(checkpoint);
    }

    Kind stage(int checkpoint) {
        return sequence.get(checkpoint);
    }

    List<Kind> sequence() {
        return sequence;
    }

    Map<Pos, Material> blocks() {
        return blocks;
    }

    boolean attempt(int checkpoint, double x, double y, double z) {
        if (checkpoint < 0
                || checkpoint >= stages()
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) return false;
        Island from = island(checkpoint), to = island(checkpoint + 1);
        // A future lateral checkpoint can share the goal's Z row: keep it inaccessible
        // until the current island has actually been reached and the old kit removed.
        boolean sharedGoalRow = checkpoint + 2 <= stages() && island(checkpoint + 2).z() == to.z();
        double left = sharedGoalRow ? Math.max(0, Math.min(from.centerX(), to.centerX()) - 4.5) : 0;
        double right =
                sharedGoalRow
                        ? Math.min(WIDTH, Math.max(from.centerX(), to.centerX()) + 4.5)
                        : WIDTH;
        return x >= left
                && x < right
                && y >= BUILD_BOTTOM
                && y < TOP - 1
                && z >= from.z()
                && z < to.z() + 7;
    }

    double distance(int checkpoint, double x, double z) {
        Island from = island(checkpoint), to = island(checkpoint + 1);
        double dx = to.centerX() - from.centerX(),
                dz = to.z() - from.z(),
                length = Math.hypot(dx, dz);
        return Math.max(
                0,
                Math.min(length, ((x - from.centerX()) * dx + (z - from.z() - 3.5) * dz) / length));
    }

    boolean buildPosition(int checkpoint, Pos pos) {
        if (stage(checkpoint) != Kind.POT_ASCENT) return true;
        // Keep the staircase away from free anchors on barriers and neighbouring task walls.
        int center = (int) island(checkpoint).centerX();
        return pos.x() >= center - 1 && pos.x() <= center + 1;
    }

    boolean recoverMaterials(int checkpoint) {
        return stage(checkpoint) != Kind.POT_ASCENT;
    }

    List<Supply> supplies(int checkpoint) {
        return checkpoint >= 0 && checkpoint < stages() ? stage(checkpoint).supplies : List.of();
    }
}
