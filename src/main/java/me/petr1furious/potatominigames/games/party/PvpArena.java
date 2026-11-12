package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Symmetric 49x49 battle maps. The selected layout survives every round reset. */
final class PvpArena extends DuelArena {
    static final int FLOOR = 80;
    static final int HALF = 24;
    static final int BORDER_SIZE = HALF * 2 + 1;
    static final int MAX_BUILD_Y = FLOOR + 5;
    static final int WALL_HEIGHT = 9;

    enum Layout {
        COURTYARD("courtyard"),
        DESERT_RUINS("desert_ruins");

        private final String id;

        Layout(String id) {
            this.id = id;
        }

        static Layout pick(Random random) {
            Layout[] pool = values();
            return pool[random.nextInt(pool.length)];
        }

        Component title() {
            return I18n.tr("party.duels.arena." + id);
        }
    }

    record Position(int x, int y, int z) {
        static Position of(Block b) {
            return new Position(b.getX(), b.getY(), b.getZ());
        }
    }

    private final World world;
    private final Layout layout;
    private final Set<Position> placed = new HashSet<>();

    PvpArena(World world) {
        this(world, Layout.COURTYARD);
    }

    PvpArena(World world, Random random) {
        this(world, Layout.pick(random));
    }

    PvpArena(World world, Layout layout) {
        this.world = world;
        this.layout = Objects.requireNonNull(layout);
    }

    Layout layout() {
        return layout;
    }

    void build() {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                set(x, FLOOR, z, floor(x, z));
                if (Math.abs(x) == 25 || Math.abs(z) == 25) {
                    for (int y = FLOOR + 1; y <= FLOOR + WALL_HEIGHT; y++) {
                        set(
                                x,
                                y,
                                z,
                                y == FLOOR + 1
                                        ? (layout == Layout.COURTYARD
                                                ? Material.POLISHED_ANDESITE
                                                : Material.CUT_RED_SANDSTONE)
                                        : Material.BARRIER);
                    }
                }
            }
        }
        if (layout == Layout.COURTYARD) courtyard();
        else desertRuins();
        world.setSpawnLocation(0, FLOOR + 1, 0);
    }

    private Material floor(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (layout == Layout.COURTYARD)
            return ax <= 2 || az <= 2 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
        int ring = Math.max(ax, az);
        if (ring == 6 || ring == 16 || ring == 25) return Material.CUT_RED_SANDSTONE;
        if (ax <= 1 || az <= 1) return Material.CUT_SANDSTONE;
        return Material.SMOOTH_SANDSTONE;
    }

    private void courtyard() {
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                // Short cover with gaps around both ends; no protected high camping spots.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int y = 1; y <= 2; y++)
                        set(sx * 10 + dx, FLOOR + y, sz * 8, Material.MOSSY_STONE_BRICKS);
                }
                for (int dz = -1; dz <= 1; dz++)
                    set(sx * 18, FLOOR + 1, sz * 15 + dz, Material.STONE_BRICKS);
                set(sx * 4, FLOOR + 1, sz * 5, Material.CHISELED_STONE_BRICKS);
                set(sx * 4, FLOOR + 2, sz * 5, Material.CHISELED_STONE_BRICKS);
            }
        }
    }

    private void desertRuins() {
        // Broken inner walls leave wide cardinal entrances and several diagonal routes.
        for (int side : new int[] {-1, 1})
            for (int offset = -5; offset <= 5; offset++) {
                if (Math.abs(offset) <= 2) continue;
                int height = Math.abs(offset) == 5 ? 1 : 2;
                for (int y = 1; y <= height; y++) {
                    set(offset, FLOOR + y, side * 9, Material.CUT_SANDSTONE);
                    set(side * 9, FLOOR + y, offset, Material.CUT_SANDSTONE);
                }
            }
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) {
                // Four L-shaped ruins sit inside radius 18, leaving every outer spawn clear.
                for (int along = 8; along <= 12; along++)
                    for (int y = 1; y <= (along == 8 ? 1 : 2); y++) {
                        Material material =
                                y == 2 ? Material.CHISELED_SANDSTONE : Material.SANDSTONE;
                        set(sx * along, FLOOR + y, sz * 12, material);
                        set(sx * 12, FLOOR + y, sz * along, material);
                    }
                set(sx * 4, FLOOR + 1, sz * 4, Material.SMOOTH_RED_SANDSTONE);
            }
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    Location center() {
        return new Location(world, .5, FLOOR + 1, .5);
    }

    Location spawn(boolean east) {
        return new Location(world, east ? 19.5 : -18.5, FLOOR + 1, .5, east ? 90 : -90, 0);
    }

    Location spectator() {
        return new Location(world, .5, FLOOR + 8, .5, 0, 60);
    }

    boolean canPlace(Block block) {
        return block.getWorld().equals(world)
                && Math.abs(block.getX()) <= HALF
                && Math.abs(block.getZ()) <= HALF
                && block.getY() > FLOOR
                && block.getY() <= MAX_BUILD_Y;
    }

    void track(Block block) {
        placed.add(Position.of(block));
    }

    boolean isPlaced(Block block) {
        return placed.contains(Position.of(block));
    }

    void broken(Block block) {
        placed.remove(Position.of(block));
    }

    void reset() {
        for (Position p : placed) set(p.x(), p.y(), p.z(), Material.AIR);
        placed.clear();
        for (var entity : world.getEntities()) {
            if (entity instanceof Projectile || entity instanceof Item) entity.remove();
        }
    }

    boolean contains(Location loc) {
        return loc.getWorld().equals(world)
                && loc.getX() >= -24.7
                && loc.getX() <= 25.7
                && loc.getZ() >= -24.7
                && loc.getZ() <= 25.7
                && loc.getY() >= FLOOR
                && loc.getY() < FLOOR + WALL_HEIGHT;
    }
}
