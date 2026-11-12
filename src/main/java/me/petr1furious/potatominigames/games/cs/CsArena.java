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

    private static final double[][][] SPAWNS = {
        {{.5, 78, 50.5}, {2.5, 78, 50.5}, {4.5, 78, 50.5}, {.5, 78, 53.5}, {4.5, 78, 53.5}},
        {{93.5, 75, 39.5}, {96.5, 75, 39.5}, {99.5, 75, 39.5}, {93.5, 75, 41.5}, {97.5, 75, 41.5}}
    };
    private final World world;
    private final Blueprint source = Source.DATA;
    private final List<BlockData> states;
    private final Map<Pos, BlockData> broken = new LinkedHashMap<>();
    private final List<Pos> clip = new ArrayList<>();
    private int cursor;

    CsArena(World world) {
        this.world = world;
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
        var p = SPAWNS[side][Math.floorMod(index, 5)];
        Location at = new Location(world, p[0], p[1], p[2], side == 0 ? -90 : 90, 0);
        if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable())
            throw new IllegalStateException("Imported spawn obstructed");
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
        Location center = spawn(side, 2);
        return world.equals(at.getWorld())
                && Math.abs(at.getY() - center.getY()) < 4
                && Math.abs(at.getX() - center.getX()) <= 10
                && Math.abs(at.getZ() - center.getZ()) <= 9;
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
