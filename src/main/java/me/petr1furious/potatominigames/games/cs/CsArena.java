package me.petr1furious.potatominigames.games.cs;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** LesRaisins Dust II imported verbatim; only invisible outer clipping is added. */
final class CsArena {
    record Pos(int x, int y, int z) {}

    record Cell(Pos pos, int state) {}

    record Blueprint(List<String> palette, List<Cell> cells) {
        static Blueprint load() {
            try (var stream = CsArena.class.getResourceAsStream("/maps/cs/dust2.bin.gz")) {
                if (stream == null) throw new IOException("Missing imported Dust II");
                try (var in = new DataInputStream(new GZIPInputStream(stream))) {
                    if (!Arrays.equals(
                            in.readNBytes(8),
                            "CSMAP001".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        throw new IOException("Invalid CS map signature");
                    int n = in.readInt();
                    if (n < 1 || n > 4096) throw new IOException("Invalid palette");
                    List<String> palette = new ArrayList<>();
                    for (int i = 0; i < n; i++) palette.add(in.readUTF());
                    n = in.readInt();
                    if (n < 1 || n > 500000) throw new IOException("Invalid block count");
                    List<Cell> cells = new ArrayList<>();
                    Set<Pos> seen = new HashSet<>();
                    for (int i = 0; i < n; i++) {
                        Pos p = new Pos(in.readShort(), in.readShort(), in.readShort());
                        int state = in.readUnsignedShort();
                        if (state >= palette.size()
                                || p.x < -25
                                || p.x > 130
                                || p.z < -34
                                || p.z > 119
                                || p.y < 73
                                || p.y > 109
                                || !seen.add(p)) throw new IOException("Invalid imported cell");
                        cells.add(new Cell(p, state));
                    }
                    if (in.read() != -1) throw new IOException("Unexpected map data");
                    return new Blueprint(List.copyOf(palette), List.copyOf(cells));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load imported Dust II", e);
            }
        }
    }

    private static final class Source {
        static final Blueprint DATA = Blueprint.load();
    }

    record Spawn(double x, double y, double z, float yaw) {}

    // CS2's three T groups and CT arc, fitted to the older Minecraft map's walkable floors.
    // See assets/maps/lesraisins-dust2/SPAWNS-Dust2.md for the reference and adaptation.
    private static final List<List<Spawn>> SPAWNS =
            List.of(
                    List.of(
                            new Spawn(1.5, 78, 21.5, -90),
                            new Spawn(-.5, 78, 22.5, -90),
                            new Spawn(-1.5, 78, 24.5, -90),
                            new Spawn(-.5, 78, 25.5, -90),
                            new Spawn(1.5, 78, 26.5, -90),
                            new Spawn(1.5, 78, 27.5, -90),
                            new Spawn(-.5, 78, 28.5, -90),
                            new Spawn(-1.5, 78, 30.5, -90),
                            new Spawn(-.5, 78, 31.5, -90),
                            new Spawn(1.5, 78, 32.5, -90),
                            new Spawn(1.5, 78, 33.5, -90),
                            new Spawn(-.5, 78, 34.5, -90),
                            new Spawn(-1.5, 78, 36.5, -90),
                            new Spawn(-.5, 78, 37.5, -90),
                            new Spawn(1.5, 78, 38.5, -90)),
                    List.of(
                            new Spawn(93.5, 75, 66.5, 180),
                            new Spawn(95.5, 75, 66.5, 180),
                            new Spawn(97.5, 75, 63.5, 180),
                            new Spawn(95.5, 75, 61.5, 180),
                            new Spawn(93.5, 75, 60.5, 180)));
    private static final org.bukkit.util.BoundingBox[] BUY_ZONES = {
        new org.bukkit.util.BoundingBox(-3, 77, 19, 4, 81, 40),
        new org.bukkit.util.BoundingBox(91, 74, 57, 100, 79, 71)
    };
    private final List<List<Spawn>> spawnOrder = new ArrayList<>();

    static List<Spawn> spawnPoints(int side) {
        return SPAWNS.get(side);
    }

    static boolean buyPosition(int side, double x, double y, double z) {
        return BUY_ZONES[side].contains(x, y, z);
    }

    void shuffleSpawns(Random random) {
        for (var points : spawnOrder) Collections.shuffle(points, random);
    }

    private final World world;
    private final Blueprint source = Source.DATA;
    private final List<BlockData> states;
    private final Map<Pos, BlockData> broken = new LinkedHashMap<>();
    private final List<Pos> clip = new ArrayList<>();
    private int cursor;

    CsArena(World world) {
        this.world = world;
        for (var points : SPAWNS) spawnOrder.add(new ArrayList<>(points));
        states = source.palette.stream().map(Bukkit::createBlockData).toList();
        for (int y = 72; y <= 128; y++) {
            for (int x = -26; x <= 131; x++) {
                clip.add(new Pos(x, y, -35));
                clip.add(new Pos(x, y, 120));
            }
            for (int z = -34; z <= 119; z++) {
                clip.add(new Pos(-26, y, z));
                clip.add(new Pos(131, y, z));
            }
        }
    }

    boolean buildSlice(int budget) {
        int total = source.cells.size() + clip.size();
        for (int n = 0; n < budget && cursor < total; n++, cursor++) {
            if (cursor < source.cells.size()) {
                var cell = source.cells.get(cursor);
                var p = cell.pos;
                world.getBlockAt(p.x, p.y, p.z).setBlockData(states.get(cell.state), false);
            } else {
                var p = clip.get(cursor - source.cells.size());
                world.getBlockAt(p.x, p.y, p.z).setType(Material.BARRIER, false);
            }
        }
        return cursor == total;
    }

    float progress() {
        return (float) cursor / (source.cells.size() + clip.size());
    }

    Location spawn(int side, int index) {
        var points = spawnOrder.get(side);
        var p = points.get(Math.floorMod(index, points.size()));
        Location at = new Location(world, p.x(), p.y(), p.z(), p.yaw(), 0);
        if (!at.getBlock().isPassable()
                || !at.clone().add(0, 1, 0).getBlock().isPassable()
                || at.clone().subtract(0, .05, 0).getBlock().isPassable())
            throw new IllegalStateException("Imported spawn obstructed");
        return at;
    }

    private static final class DeathmatchSpawns {
        static final List<Spawn> POINTS = buildDeathmatchSpawns(Source.DATA);
    }

    static List<Spawn> deathmatchSpawns() {
        return DeathmatchSpawns.POINTS;
    }

    private static boolean fullFloor(String state) {
        String name = state.split("\\[", 2)[0].substring("minecraft:".length());
        return Set.of(
                                "stone",
                                "sand",
                                "sandstone",
                                "smooth_sandstone",
                                "cut_sandstone",
                                "gravel",
                                "stone_bricks",
                                "smooth_stone",
                                "cobblestone",
                                "andesite",
                                "diorite",
                                "granite",
                                "polished_andesite",
                                "polished_diorite",
                                "polished_granite")
                        .contains(name)
                || name.endsWith("_concrete")
                || name.endsWith("_terracotta")
                || name.endsWith("_planks");
    }

    private static List<Spawn> buildDeathmatchSpawns(Blueprint source) {
        Map<Pos, String> blocks = new HashMap<>();
        for (var cell : source.cells()) blocks.put(cell.pos(), source.palette().get(cell.state()));
        boolean[] full = new boolean[source.palette().size()];
        for (int i = 0; i < full.length; i++) full[i] = fullFloor(source.palette().get(i));
        Set<Pos> floors = new HashSet<>();
        for (var cell : source.cells()) {
            Pos floor = cell.pos();
            Pos feet = new Pos(floor.x(), floor.y() + 1, floor.z());
            if (feet.y() >= 74
                    && feet.y() <= 89
                    && full[cell.state()]
                    && !blocks.containsKey(feet)
                    && !blocks.containsKey(new Pos(feet.x(), feet.y() + 1, feet.z())))
                floors.add(feet);
        }
        ArrayDeque<Pos> pending = new ArrayDeque<>();
        for (var side : SPAWNS)
            for (var point : side)
                pending.add(
                        new Pos(
                                (int) Math.floor(point.x()),
                                (int) point.y(),
                                (int) Math.floor(point.z())));
        // Both original bomb platforms are known walkable map regions.
        pending.add(new Pos(103, 82, 88));
        pending.add(new Pos(114, 79, 6));
        for (Pos feet : floors)
            if (feet.y() == 82
                            && feet.x() >= 97
                            && feet.x() <= 111
                            && feet.z() >= 82
                            && feet.z() <= 96
                    || feet.y() == 79
                            && feet.x() >= 107
                            && feet.x() <= 122
                            && feet.z() >= 0
                            && feet.z() <= 14) pending.add(feet);
        Set<Pos> connected = new HashSet<>();
        while (!pending.isEmpty()) {
            Pos p = pending.removeFirst();
            if (!floors.contains(p) || !connected.add(p)) continue;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int dy = 1; dy >= -3; dy--) {
                    Pos next = new Pos(p.x() + step[0], p.y() + dy, p.z() + step[1]);
                    if (floors.contains(next)
                            && !connected.contains(next)
                            && (dy <= 0 || !blocks.containsKey(new Pos(p.x(), p.y() + 2, p.z())))
                            && (dy >= 0
                                    || !blocks.containsKey(new Pos(next.x(), p.y(), next.z()))
                                            && !blocks.containsKey(
                                                    new Pos(next.x(), p.y() + 1, next.z()))))
                        pending.add(next);
                }
        }
        List<Spawn> result = new ArrayList<>();
        for (Pos p :
                connected.stream()
                        .sorted(
                                Comparator.comparingInt(Pos::x)
                                        .thenComparingInt(Pos::z)
                                        .thenComparingInt(Pos::y))
                        .toList())
            if (Math.floorMod(p.x(), 6) == 0 && Math.floorMod(p.z(), 6) == 0)
                result.add(new Spawn(p.x() + .5, p.y(), p.z() + .5, 0));
        for (var side : SPAWNS)
            for (var p : side)
                if (result.stream().noneMatch(other -> other.x() == p.x() && other.z() == p.z()))
                    result.add(p);
        return List.copyOf(result);
    }

    Location deathmatchSpawn(Collection<Location> opponents, Random random) {
        List<Spawn> points = new ArrayList<>(deathmatchSpawns());
        Collections.shuffle(points, random);
        Spawn best = points.getFirst();
        double bestDistance = -1;
        for (var point : points) {
            double distance = Double.POSITIVE_INFINITY;
            for (var opponent : opponents) {
                double dx = point.x() - opponent.getX(), dz = point.z() - opponent.getZ();
                distance = Math.min(distance, dx * dx + dz * dz);
            }
            if (distance > bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        Location at = new Location(world, best.x(), best.y(), best.z());
        at.setDirection(center().toVector().subtract(at.toVector()).setY(0));
        if (!at.getBlock().isPassable()
                || !at.clone().add(0, 1, 0).getBlock().isPassable()
                || at.clone().subtract(0, .05, 0).getBlock().isPassable())
            throw new IllegalStateException("Deathmatch spawn obstructed");
        return at;
    }

    Location center() {
        return new Location(world, 52, 85, 43);
    }

    Location spectator() {
        return new Location(world, 52, 115, 43, 0, 70);
    }

    boolean inside(Location at) {
        return at != null
                && world.equals(at.getWorld())
                && at.getX() > -25.7
                && at.getX() < 131
                && at.getZ() > -34.7
                && at.getZ() < 120
                && at.getY() > 72
                && at.getY() < 128;
    }

    boolean buyZone(Location at, int side) {
        return at != null
                && world.equals(at.getWorld())
                && buyPosition(side, at.getX(), at.getY(), at.getZ());
    }

    int site(Location at) {
        if (at == null || !world.equals(at.getWorld())) return -1;
        if (at.getX() >= 97
                && at.getX() <= 111
                && at.getZ() >= 82
                && at.getZ() <= 96
                && at.getY() >= 81
                && at.getY() <= 88) return 0;
        if (at.getX() >= 107
                && at.getX() <= 122
                && at.getZ() >= 0
                && at.getZ() <= 14
                && at.getY() >= 78
                && at.getY() <= 85) return 1;
        return -1;
    }

    void breakGlass(Block block) {
        String name = block.getType().name();
        if (!inside(block.getLocation())
                || !(name.contains("GLASS") || name.contains("FLOWER_POT"))) return;
        var p = new Pos(block.getX(), block.getY(), block.getZ());
        broken.putIfAbsent(p, block.getBlockData().clone());
        block.setType(Material.AIR, false);
    }

    void reset() {
        for (var e : broken.entrySet()) {
            var p = e.getKey();
            world.getBlockAt(p.x, p.y, p.z).setBlockData(e.getValue(), false);
        }
        broken.clear();
    }
}
