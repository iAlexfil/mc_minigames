package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Identical lanes, a sealed transparent shell and bounded per-attempt block journals. */
final class ParkourArena {
    static final int MAX_CHANGES = 4096;
    static final int CHEST_WATER_SLOT = 13;

    private record Cell(ParkourCourse.Pos pos, Material material, String state) {}

    private final World world;
    private final ParkourCourse course;
    private final List<UUID> lanes;
    private final Map<UUID, Integer> indices = new HashMap<>();
    private final Map<UUID, Map<ParkourCourse.Pos, BlockData>> changes = new HashMap<>();
    private final List<Cell> build = new ArrayList<>();
    private final Map<String, BlockData> states = new HashMap<>();
    private int cursor;

    ParkourArena(World world, Collection<UUID> roster, ParkourCourse course) {
        this.world = world;
        this.course = course;
        lanes = List.copyOf(roster);
        for (int i = 0; i < lanes.size(); i++) {
            UUID id = lanes.get(i);
            indices.put(id, i);
            changes.put(id, new LinkedHashMap<>());
            for (var entry : course.blocks().entrySet()) {
                var p = entry.getKey();
                build.add(
                        new Cell(
                                new ParkourCourse.Pos(
                                        i * ParkourCourse.PITCH + p.x(), p.y(), p.z()),
                                entry.getValue(),
                                course.states().get(p)));
            }
            // Spawn islands are ready before countdown players are teleported into the void world.
            var start = course.island(0);
            for (int x = start.x(); x < start.x() + start.width(); x++)
                for (int z = start.z(); z < start.z() + start.depth(); z++)
                    for (int y = course.island(0).y() - 1; y <= course.island(0).y(); y++)
                        world.getBlockAt(i * ParkourCourse.PITCH + x, y, z)
                                .setType(
                                        course.blocks().get(new ParkourCourse.Pos(x, y, z)), false);
        }
        int right = lanes.size() * ParkourCourse.PITCH - 1;
        for (int lane = 0; lane <= lanes.size(); lane++)
            for (int y = ParkourCourse.BOTTOM; y <= ParkourCourse.TOP; y++)
                for (int z = -1; z <= course.end(); z++)
                    add(lane * ParkourCourse.PITCH - 1, y, z, Material.BARRIER);
        for (int x = 0; x < right; x++) {
            for (int z = -1; z <= course.end(); z++) {
                add(x, ParkourCourse.BOTTOM, z, Material.BARRIER);
                add(x, ParkourCourse.TOP, z, Material.BARRIER);
            }
            for (int y = ParkourCourse.BOTTOM + 1; y < ParkourCourse.TOP; y++) {
                add(x, y, -1, Material.BARRIER);
                add(x, y, course.end(), Material.BARRIER);
            }
        }
    }

    private void add(int x, int y, int z, Material material) {
        build.add(new Cell(new ParkourCourse.Pos(x, y, z), material, null));
    }

    boolean buildSlice(int budget) {
        for (int i = 0; i < budget && cursor < build.size(); i++, cursor++) {
            Cell cell = build.get(cursor);
            var p = cell.pos();
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (cell.state() == null) block.setType(cell.material(), false);
            else
                block.setBlockData(
                        states.computeIfAbsent(cell.state(), org.bukkit.Bukkit::createBlockData),
                        false);
        }
        boolean ready = cursor == build.size();
        if (ready) {
            build.clear();
            cursor = 0;
        }
        return ready;
    }

    float progress() {
        return build.isEmpty() ? 1 : (float) cursor / build.size();
    }

    UUID owner(Location at) {
        if (at == null || !world.equals(at.getWorld())) return null;
        int lane = Math.floorDiv(at.getBlockX(), ParkourCourse.PITCH);
        return lane >= 0
                        && lane < lanes.size()
                        && Math.floorMod(at.getBlockX(), ParkourCourse.PITCH) < ParkourCourse.WIDTH
                ? lanes.get(lane)
                : null;
    }

    boolean attempt(UUID id, int checkpoint, Location at) {
        Integer index = indices.get(id);
        return index != null
                && id.equals(owner(at))
                && course.attempt(
                        checkpoint, at.getX() - index * ParkourCourse.PITCH, at.getY(), at.getZ());
    }

    Location checkpoint(UUID id, int checkpoint) {
        var island = course.island(checkpoint);
        Location at =
                new Location(
                        world,
                        indices.get(id) * ParkourCourse.PITCH + island.centerX(),
                        island.y() + 1,
                        island.z() + 3.5);
        if (checkpoint < course.stages()) {
            var next = course.island(checkpoint + 1);
            at.setDirection(
                    new org.bukkit.util.Vector(
                            next.centerX() - island.centerX(), 0, next.z() - island.z()));
        }
        return at;
    }

    Location chestLocation(UUID id, int checkpoint) {
        Integer lane = indices.get(id);
        var pos = course.dropChest(checkpoint);
        return lane == null || pos == null
                ? null
                : new Location(world, lane * ParkourCourse.PITCH + pos.x(), pos.y(), pos.z());
    }

    boolean isChest(UUID id, int checkpoint, Location at) {
        Location expected = chestLocation(id, checkpoint);
        return expected != null
                && at != null
                && world.equals(at.getWorld())
                && at.getBlockX() == expected.getBlockX()
                && at.getBlockY() == expected.getBlockY()
                && at.getBlockZ() == expected.getBlockZ();
    }

    void refillChest(UUID id, int checkpoint) {
        Location at = chestLocation(id, checkpoint);
        if (at == null) return;
        if (!(at.getBlock().getState() instanceof Chest chest))
            throw new IllegalStateException("Missing parkour drop chest");
        var inventory = chest.getBlockInventory();
        inventory.clear();
        inventory.setItem(CHEST_WATER_SLOT, new ItemStack(Material.WATER_BUCKET));
    }

    boolean landed(UUID id, int checkpoint, Location at) {
        Integer index = indices.get(id);
        return index != null
                && id.equals(owner(at))
                && course.island(checkpoint)
                        .landing(at.getX() - index * ParkourCourse.PITCH, at.getY(), at.getZ());
    }

    @SuppressWarnings("deprecation")
    boolean landed(Player player, int checkpoint) {
        Location at = player.getLocation();
        return player.isOnGround()
                && (checkpoint == 0
                        || !course.stage(checkpoint - 1).vehicle()
                        || !player.isInsideVehicle())
                && !player.isGliding()
                && landed(player.getUniqueId(), checkpoint, at)
                && !at.clone().subtract(0, .05, 0).getBlock().isPassable();
    }

    double distance(UUID id, int checkpoint, Location at) {
        return course.distance(
                checkpoint, at.getX() - indices.get(id) * ParkourCourse.PITCH, at.getZ());
    }

    boolean mutable(UUID id, int checkpoint, Block block) {
        return attempt(id, checkpoint, block.getLocation())
                && block.getY() <= ParkourCourse.BUILD_TOP
                && course.buildPosition(
                        checkpoint,
                        new ParkourCourse.Pos(
                                block.getX() - indices.get(id) * ParkourCourse.PITCH,
                                block.getY(),
                                block.getZ()))
                && !course.blocks()
                        .containsKey(
                                new ParkourCourse.Pos(
                                        block.getX() - indices.get(id) * ParkourCourse.PITCH,
                                        block.getY(),
                                        block.getZ()));
    }

    boolean remember(UUID id, int checkpoint, Block block, BlockData before) {
        if (!mutable(id, checkpoint, block)) return false;
        return rememberBaseline(id, block, before);
    }

    private boolean rememberBaseline(UUID id, Block block, BlockData before) {
        var journal = changes.get(id);
        var pos = new ParkourCourse.Pos(block.getX(), block.getY(), block.getZ());
        if (journal.containsKey(pos)) return true;
        if (journal.size() >= MAX_CHANGES) return false;
        journal.put(pos, before.clone());
        return true;
    }

    boolean changed(UUID id, Block block) {
        return changes.containsKey(id)
                && changes.get(id)
                        .containsKey(
                                new ParkourCourse.Pos(block.getX(), block.getY(), block.getZ()));
    }

    void reset(UUID id) {
        var journal = changes.get(id);
        if (journal == null) return;
        for (var entry : journal.entrySet()) {
            var p = entry.getKey();
            world.getBlockAt(p.x(), p.y(), p.z()).setBlockData(entry.getValue(), false);
        }
        journal.clear();
    }

    Location viewpoint() {
        return new Location(
                world,
                (lanes.size() * ParkourCourse.PITCH - 1) / 2.0,
                Math.min(
                        ParkourCourse.TOP - 4,
                        java.util.stream.IntStream.rangeClosed(0, course.stages())
                                        .map(i -> course.island(i).y())
                                        .max()
                                        .orElse(80)
                                + 18),
                course.end() / 2.0,
                0,
                35);
    }

    void clear() {
        for (UUID id : lanes) reset(id);
        build.clear();
    }
}
