package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** A saved structure placed in a session-owned void world, with protected setup anchors. */
final class FinalsImportedArena implements FinalsMapArena {
    private final World world;
    private final Saved map;
    private final Structure structure;
    private List<BlockState> vaultBlocks;
    private List<BlockState> cashoutBlocks;
    private int activeVault = -1;
    private int[] activeCashouts;
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
        vaultBlocks =
                map.vaults().stream()
                        .map(point ->
                                world.getBlockAt(point.bx(), point.by(), point.bz()).getState())
                        .toList();
        cashoutBlocks =
                map.cashouts().stream()
                        .map(point ->
                                world.getBlockAt(point.bx(), point.by(), point.bz()).getState())
                        .toList();
        activeCashouts =
                FinalsCashoutLocations.initial(map.cashouts().size(), ThreadLocalRandom.current());
        for (int index : activeCashouts) marker(map.cashouts().get(index), Material.GOLD_BLOCK);
        Point spawn = map.red().getFirst();
        world.setSpawnLocation(spawn.bx(), spawn.by(), spawn.bz());
        built = true;
        nextVault();
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
        if (activeVault < 0)
            throw new IllegalStateException("Build the arena before using its vault");
        return map.vaults().get(activeVault).at(world);
    }

    @Override
    public List<Location> vaultLocations() {
        return map.vaults().stream().map(point -> point.at(world)).toList();
    }

    @Override
    public void nextVault() {
        if (!built) throw new IllegalStateException("Build the arena before selecting its vault");
        int next =
                FinalsVaultLocations.nextIndex(
                        map.vaults().size(), activeVault, ThreadLocalRandom.current());
        if (next == activeVault) return;
        if (activeVault >= 0) vaultBlocks.get(activeVault).update(true, false);
        activeVault = next;
        marker(map.vaults().get(activeVault), Material.EMERALD_BLOCK);
    }

    @Override
    public Location station(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before using its cashouts");
        return map.cashouts().get(activeCashouts[index]).at(world);
    }

    @Override
    public List<Location> cashoutLocations() {
        return map.cashouts().stream().map(point -> point.at(world)).toList();
    }

    @Override
    public void nextCashout(int station) {
        if (station < 0 || station > 1)
            throw new IllegalArgumentException("Station must be 0 or 1");
        if (activeCashouts == null)
            throw new IllegalStateException("Build the arena before replacing its cashouts");
        int current = activeCashouts[station];
        int next =
                FinalsCashoutLocations.nextIndex(
                        map.cashouts().size(),
                        current,
                        activeCashouts[1 - station],
                        ThreadLocalRandom.current());
        if (next == current) return;
        cashoutBlocks.get(current).update(true, false);
        activeCashouts[station] = next;
        marker(map.cashouts().get(next), Material.GOLD_BLOCK);
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
        for (Point objective : concat(map.vaults(), map.cashouts())) {
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
        if (activeCashouts == null) return -1;
        return matches(block, map.cashouts().get(activeCashouts[0]))
                ? 0
                : matches(block, map.cashouts().get(activeCashouts[1])) ? 1 : -1;
    }

    @Override
    public boolean isVault(Block block) {
        return activeVault >= 0 && matches(block, map.vaults().get(activeVault));
    }

    private boolean matches(Block block, Point point) {
        return block != null
                && world.equals(block.getWorld())
                && block.getX() == point.bx()
                && block.getY() == point.by()
                && block.getZ() == point.bz();
    }
}
