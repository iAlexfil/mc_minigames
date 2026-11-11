package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;

import java.util.*;

/** One combined block layer prevents crossing walls from erasing each other's blocks. */
final class HoleWallBlocks {
    record Position(int x, int y, int z) {}

    private final World world;
    private final Map<Position, BlockData> originals = new HashMap<>();
    private Map<Position, String> visible = Map.of();

    HoleWallBlocks(World world) {
        this.world = world;
    }

    static Map<HoleWallFlow.Wall, List<HoleWallPattern.Cell>> visibleCells(
            List<HoleWallFlow.Wall> walls, boolean before) {
        Map<HoleWallFlow.Wall, List<HoleWallPattern.Cell>> result = new LinkedHashMap<>();
        Set<Position> occupied = new HashSet<>();
        for (var wall : walls.reversed()) {
            List<HoleWallPattern.Cell> cells = new ArrayList<>();
            for (var cell : wall.pattern.cells()) {
                var box =
                        HoleWallPhysics.box(cell, wall.direction, before ? wall.from : wall.depth);
                if (occupied.add(
                        new Position(
                                (int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ())))
                    cells.add(cell);
            }
            result.put(wall, cells);
        }
        return result;
    }

    static Map<Position, String> layout(List<HoleWallFlow.Wall> walls) {
        Map<Position, String> result = new LinkedHashMap<>();
        for (var wall : walls)
            for (var cell : wall.pattern.cells()) {
                var box = HoleWallPhysics.box(cell, wall.direction, wall.depth);
                result.put(
                        new Position((int) box.getMinX(), (int) box.getMinY(), (int) box.getMinZ()),
                        cell.block().data(wall.direction));
            }
        return result;
    }

    void render(List<HoleWallFlow.Wall> walls) {
        var next = layout(walls);
        for (var old : visible.keySet())
            if (!next.containsKey(old)) {
                world.getBlockAt(old.x(), old.y(), old.z()).setBlockData(originals.get(old), false);
            }
        for (var entry : next.entrySet()) {
            var position = entry.getKey();
            if (entry.getValue().equals(visible.get(position))) continue;
            var block = world.getBlockAt(position.x(), position.y(), position.z());
            originals.putIfAbsent(position, block.getBlockData());
            block.setBlockData(Bukkit.createBlockData(entry.getValue()), false);
        }
        visible = next;
    }

    void clear() {
        originals.forEach(
                (p, data) -> world.getBlockAt(p.x(), p.y(), p.z()).setBlockData(data, false));
        visible = Map.of();
        originals.clear();
    }
}
