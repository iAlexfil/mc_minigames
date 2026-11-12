package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Random;

/** Immutable, section-sized native structures; no whole-arena structure is held in memory. */
final class FinalsMapSnapshot {
    private static final int TILE = 16;

    interface Work {
        /** Processes one bounded piece. Returns true when all work is complete. */
        boolean step();

        /** True when only background work can advance this operation. */
        default boolean waiting() {
            return false;
        }

        default void cancel() {}

        default void finish() {
            while (!step()) {}
        }
    }

    private final Path path;
    private final Bounds bounds;
    private final Structure legacy;

    private FinalsMapSnapshot(Path path, Bounds bounds, Structure legacy) {
        this.path = path;
        this.bounds = bounds;
        this.legacy = legacy;
    }

    static FinalsMapSnapshot legacy(Bounds bounds, Structure structure) {
        return new FinalsMapSnapshot(null, bounds, structure);
    }

    static FinalsMapSnapshot open(Path templates, Saved saved) {
        String name = saved.template();
        if (name == null || !name.matches("[a-z0-9_-]+\\.(nbt|tiles)"))
            throw FinalsMaps.failure("template");
        Path path = templates.resolve(name);
        try {
            if (Files.isSymbolicLink(path)) throw FinalsMaps.failure("template");
            if (name.endsWith(".nbt")) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw FinalsMaps.failure("template");
                Structure structure = Bukkit.getStructureManager().loadStructure(path.toFile());
                if (structure == null
                        || !structure.getSize().equals(saved.bounds().size())
                        || structure.getEntityCount() != 0) throw FinalsMaps.failure("template");
                return legacy(saved.bounds(), structure);
            }
            Path manifest = path.resolve("manifest.yml");
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
                throw FinalsMaps.failure("template");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(manifest.toFile());
            Bounds b = saved.bounds();
            if (yaml.getInt("version") != 1
                    || yaml.getInt("tile") != TILE
                    || !yaml.getIntegerList("bounds")
                            .equals(
                                    java.util.List.of(
                                            b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(),
                                            b.maxZ()))) throw FinalsMaps.failure("template");
            return new FinalsMapSnapshot(path, b, null);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw FinalsMaps.failure("template");
        }
    }

    static Work capture(JavaPlugin plugin, World world, Bounds bounds, Path directory) {
        try {
            Files.createDirectory(directory);
        } catch (IOException error) {
            throw FinalsMaps.failure("io");
        }
        return new Tiles(plugin, world, bounds) {
            @Override
            void process(Location origin, BlockVector size, String filename) {
                int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
                for (int x = ox; x < ox + size.getBlockX(); x++)
                    for (int y = oy; y < oy + size.getBlockY(); y++)
                        for (int z = oz; z < oz + size.getBlockZ(); z++)
                            rejectUnsafe(world.getBlockAt(x, y, z).getType());
                Structure structure = Bukkit.getStructureManager().createStructure();
                structure.fill(origin, size, false);
                try {
                    Bukkit.getStructureManager()
                            .saveStructure(directory.resolve(filename).toFile(), structure);
                } catch (IOException error) {
                    throw FinalsMaps.failure("io");
                }
            }

            @Override
            void complete() {
                Bounds b = bounds;
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("version", 1);
                yaml.set("tile", TILE);
                yaml.set(
                        "bounds",
                        java.util.List.of(
                                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                try {
                    yaml.save(directory.resolve("manifest.yml").toFile());
                } catch (IOException error) {
                    throw FinalsMaps.failure("io");
                }
            }
        };
    }

    Work placement(JavaPlugin plugin, World world) {
        if (legacy != null) {
            return new Work() {
                private boolean done;

                @Override
                public boolean step() {
                    if (!done) {
                        place(legacy, bounds.origin(world));
                        done = true;
                    }
                    return true;
                }
            };
        }
        return new Tiles(plugin, world, bounds) {
            @Override
            void process(Location origin, BlockVector size, String filename) {
                Path piece = path.resolve(filename);
                try {
                    if (!Files.isRegularFile(piece, LinkOption.NOFOLLOW_LINKS))
                        throw FinalsMaps.failure("template");
                    Structure structure =
                            Bukkit.getStructureManager().loadStructure(piece.toFile());
                    if (structure == null
                            || !structure.getSize().equals(size)
                            || structure.getEntityCount() != 0)
                        throw FinalsMaps.failure("template");
                    place(structure, origin);
                } catch (IOException error) {
                    throw FinalsMaps.failure("template");
                }
            }
        };
    }

    private static void place(Structure structure, Location origin) {
        structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random(0));
    }

    private static void rejectUnsafe(Material material) {
        switch (material) {
            case COMMAND_BLOCK,
                    CHAIN_COMMAND_BLOCK,
                    REPEATING_COMMAND_BLOCK,
                    STRUCTURE_BLOCK,
                    JIGSAW,
                    NETHER_PORTAL,
                    END_PORTAL,
                    END_GATEWAY ->
                    throw FinalsMaps.failure("unsafe_block");
            default -> {}
        }
    }

    /** Walk chunk columns lazily and release only chunk loads owned by this operation. */
    private abstract static class Tiles implements Work {
        final World world;
        final Bounds bounds;
        private final JavaPlugin plugin;
        private int cx, cz, y;
        private Chunk chunk;
        private boolean unload, ticket, done;

        Tiles(JavaPlugin plugin, World world, Bounds bounds) {
            this.plugin = plugin;
            this.world = world;
            this.bounds = bounds;
            cx = Math.floorDiv(bounds.minX(), TILE);
            cz = Math.floorDiv(bounds.minZ(), TILE);
            y = bounds.minY();
        }

        @Override
        public final boolean step() {
            if (done) return true;
            try {
                if (chunk == null) {
                    unload = !world.isChunkLoaded(cx, cz);
                    chunk = world.getChunkAt(cx, cz);
                    ticket = plugin != null && chunk.addPluginChunkTicket(plugin);
                }
                int x = Math.max(bounds.minX(), cx * TILE);
                int z = Math.max(bounds.minZ(), cz * TILE);
                int sx = Math.min(bounds.maxX() - x + 1, (cx + 1) * TILE - x);
                int sz = Math.min(bounds.maxZ() - z + 1, (cz + 1) * TILE - z);
                int sy = Math.min(TILE, bounds.maxY() - y + 1);
                process(
                        new Location(world, x, y, z),
                        new BlockVector(sx, sy, sz),
                        cx + "_" + y + "_" + cz + ".nbt");
                y += sy;
                if (y > bounds.maxY()) {
                    release();
                    y = bounds.minY();
                    if (++cz > Math.floorDiv(bounds.maxZ(), TILE)) {
                        cz = Math.floorDiv(bounds.minZ(), TILE);
                        cx++;
                    }
                    if (cx > Math.floorDiv(bounds.maxX(), TILE)) {
                        complete();
                        done = true;
                    }
                }
                return done;
            } catch (RuntimeException error) {
                cancel();
                throw error;
            }
        }

        abstract void process(Location origin, BlockVector size, String filename);

        void complete() {}

        private void release() {
            if (chunk == null) return;
            if (ticket) chunk.removePluginChunkTicket(plugin);
            if (unload) world.unloadChunkRequest(cx, cz);
            chunk = null;
            ticket = false;
        }

        @Override
        public final void cancel() {
            release();
            done = true;
        }
    }
}
