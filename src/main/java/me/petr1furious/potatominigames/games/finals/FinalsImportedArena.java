package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.Random;

/** A saved structure placed in a session-owned void world, with protected setup anchors. */
final class FinalsImportedArena implements FinalsMapArena {
    private final World world;
    private final Saved map;
    private final Structure structure;
    private boolean built;

    FinalsImportedArena(World world, Saved map, Structure structure) {
        this.world = world;
        this.map = map;
        this.structure = structure;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public void build() {
        if (built) return;
        structure.place(
                map.bounds().origin(world),
                false,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                new Random(0));
        marker(map.vault(), Material.EMERALD_BLOCK);
        marker(map.stationA(), Material.GOLD_BLOCK);
        marker(map.stationB(), Material.GOLD_BLOCK);
        Point spawn = map.red().getFirst();
        world.setSpawnLocation(spawn.bx(), spawn.by(), spawn.bz());
        built = true;
    }

    private void marker(Point point, Material material) {
        world.getBlockAt(point.bx(), point.by(), point.bz()).setType(material, false);
    }

    @Override
    public Location center() {
        Bounds b = map.bounds();
        return new Location(
                world,
                (b.minX() + (double) b.maxX() + 1) / 2,
                b.maxY() + 1,
                (b.minZ() + (double) b.maxZ() + 1) / 2);
    }

    @Override
    public double borderSize() {
        Bounds b = map.bounds();
        return Math.max(b.maxX() - b.minX() + 1, b.maxZ() - b.minZ() + 1);
    }

    @Override
    public Location spawn(int team, int memberIndex) {
        if (team < 0 || team > 1) throw new IllegalArgumentException("Team must be 0 or 1");
        List<Point> points = team == 0 ? map.red() : map.blue();
        return points.get(Math.floorMod(memberIndex, points.size())).at(world);
    }

    @Override
    public Location vault() {
        return map.vault().at(world);
    }

    @Override
    public Location station(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Station must be 0 or 1");
        return (index == 0 ? map.stationA() : map.stationB()).at(world);
    }

    @Override
    public boolean contains(Location location) {
        if (location == null
                || !world.equals(location.getWorld())
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())) return false;
        Bounds b = map.bounds();
        return location.getX() >= b.minX()
                && location.getX() < b.maxX() + 1d
                && location.getY() >= b.minY()
                && location.getY() < b.maxY() + 1d
                && location.getZ() >= b.minZ()
                && location.getZ() < b.maxZ() + 1d;
    }

    @Override
    public boolean isCover(Block block) {
        if (block == null || !contains(block.getLocation()) || block.getY() <= map.bounds().minY())
            return false;
        for (Point spawn : concat(map.red(), map.blue())) if (protects(block, spawn)) return false;
        for (Point objective : List.of(map.vault(), map.stationA(), map.stationB())) {
            if (protects(block, objective)) return false;
        }
        return switch (block.getType()) {
            case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, BEDROCK, BARRIER -> false;
            default -> true;
        };
    }

    private boolean protects(Block block, Point anchor) {
        return Math.abs(block.getX() - anchor.bx()) <= 1
                && Math.abs(block.getZ() - anchor.bz()) <= 1
                && block.getY() <= anchor.by() + 1;
    }

    @Override
    public int stationAt(Block block) {
        return matches(block, map.stationA()) ? 0 : matches(block, map.stationB()) ? 1 : -1;
    }

    @Override
    public boolean isVault(Block block) {
        return matches(block, map.vault());
    }

    private boolean matches(Block block, Point point) {
        return block != null
                && world.equals(block.getWorld())
                && block.getX() == point.bx()
                && block.getY() == point.by()
                && block.getZ() == point.bz();
    }
}
