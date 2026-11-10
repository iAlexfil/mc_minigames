package me.petr1furious.potatominigames.games.party;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Exact coordinates imported from NetherGames Lighthouse; this class never generates islands. */
final class MagicBattleArena {
    record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    record Cell(Pos pos, int palette) {}

    record Spawn(float x, float y, float z, float yaw) {}

    record Blueprint(List<String> palette, List<Cell> blocks, List<Spawn> spawns) {
        static Blueprint load() {
            var stream =
                    MagicBattleArena.class.getResourceAsStream("/maps/magic/lighthouse.bin.gz");
            if (stream == null) throw new IllegalStateException("Missing bundled Lighthouse asset");
            try (var in = new DataInputStream(new GZIPInputStream(stream))) {
                if (!new String(in.readNBytes(9), java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("MAGICMAP1")) throw new IOException("Invalid map signature");
                int count = in.readInt();
                if (count < 1 || count > 1024) throw new IOException("Invalid map palette");
                List<String> palette = new ArrayList<>();
                for (int i = 0; i < count; i++) palette.add(in.readUTF());
                count = in.readInt();
                if (count < 1 || count > 200000) throw new IOException("Invalid map block count");
                List<Cell> blocks = new ArrayList<>();
                Set<Pos> unique = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    Pos pos = new Pos(in.readShort(), in.readShort(), in.readShort());
                    int index = in.readUnsignedShort();
                    if (index >= palette.size()
                            || !unique.add(pos)
                            || Math.abs(pos.x()) > 100
                            || Math.abs(pos.z()) > 100
                            || pos.y() < 0
                            || pos.y() > 160)
                        throw new IOException("Invalid map coordinate or palette reference");
                    blocks.add(new Cell(pos, index));
                }
                count = in.readInt();
                if (count != 12) throw new IOException("Lighthouse needs twelve spawn markers");
                List<Spawn> spawns = new ArrayList<>();
                for (int i = 0; i < count; i++)
                    spawns.add(
                            new Spawn(
                                    in.readFloat(),
                                    in.readFloat(),
                                    in.readFloat(),
                                    in.readFloat()));
                if (in.read() != -1) throw new IOException("Unexpected map trailing data");
                return new Blueprint(
                        List.copyOf(palette), List.copyOf(blocks), List.copyOf(spawns));
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load bundled Lighthouse asset", error);
            }
        }
    }

    private static final class Source {
        static final Blueprint BLUEPRINT = Blueprint.load();
    }

    private final World world;
    private final Blueprint source;
    private final List<BlockData> palette;
    private final Set<Pos> placed = new HashSet<>();
    private final List<Pos> chests = new ArrayList<>();
    private int cursor;

    MagicBattleArena(World world) {
        this.world = world;
        source = Source.BLUEPRINT;
        palette = source.palette().stream().map(Bukkit::createBlockData).toList();
        for (Cell cell : source.blocks())
            if (source.palette().get(cell.palette()).startsWith("minecraft:chest["))
                chests.add(cell.pos());
    }

    World world() {
        return world;
    }

    boolean buildSlice(int budget) {
        for (int i = 0; i < budget && cursor < source.blocks().size(); i++, cursor++) {
            Cell cell = source.blocks().get(cursor);
            Pos p = cell.pos();
            world.getBlockAt(p.x(), p.y(), p.z()).setBlockData(palette.get(cell.palette()), false);
        }
        return cursor == source.blocks().size();
    }

    float progress() {
        return (float) cursor / source.blocks().size();
    }

    Location center() {
        return new Location(world, .5, 64, .5);
    }

    Location spectator() {
        return new Location(world, .5, 105, .5);
    }

    Location spawn(int index) {
        Spawn spawn = source.spawns().get(index);
        int x = (int) Math.floor(spawn.x()), z = (int) Math.floor(spawn.z());
        for (int y = (int) spawn.y() - 1; y >= spawn.y() - 10; y--)
            if (world.getBlockAt(x, y, z).getType().isSolid()
                    && world.getBlockAt(x, y + 1, z).isPassable()
                    && world.getBlockAt(x, y + 2, z).isPassable()) {
                Location at = new Location(world, spawn.x(), y + 1, spawn.z());
                at.setDirection(center().subtract(at).toVector().setY(0));
                return at;
            }
        throw new IllegalStateException("Imported map spawn has no safe surface: " + index);
    }

    boolean inside(Location at) {
        return at != null
                && world.equals(at.getWorld())
                && Math.abs(at.getX()) < 94
                && Math.abs(at.getZ()) < 94
                && at.getY() > 25
                && at.getY() < 112;
    }

    boolean canBuild(Block block) {
        return inside(block.getLocation()) && block.getY() >= 40 && block.getY() <= 100;
    }

    void placed(Block block) {
        placed.add(Pos.of(block));
    }

    boolean breakable(Block block) {
        return placed.contains(Pos.of(block));
    }

    void remove(Block block) {
        if (placed.remove(Pos.of(block))) block.setType(Material.AIR, false);
    }

    List<Pos> chests() {
        return List.copyOf(chests);
    }
}
