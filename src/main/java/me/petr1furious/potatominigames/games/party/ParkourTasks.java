package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.ParkourCourse.*;

import org.bukkit.Material;

import java.util.Map;

/** Native terrain for movement tasks; every structure stays between its two checkpoint rows. */
final class ParkourTasks {
    static void build(Kind kind, Island from, Island to, Map<Pos, Material> plan) {
        int center = (int) from.centerX();
        switch (kind) {
            case LOW_CEILING -> {
                for (int z = from.z() + 7; z < to.z(); z++)
                    for (int x = 0; x < WIDTH; x++) {
                        if (Math.abs(x - center) <= 1 && (z - from.z() - 7) % 3 < 2)
                            plan.put(new Pos(x, from.y(), z), Material.LIGHT_BLUE_CONCRETE);
                        for (int y = from.y() + 1; y <= from.y() + 8; y++)
                            if (Math.abs(x - center) > 1 || y >= from.y() + 3)
                                plan.put(new Pos(x, y, z), OBSTACLE_BLOCK);
                    }
            }
            case RIPTIDE_LAUNCH -> {
                for (int x = Math.max(from.x(), center - 1);
                        x <= Math.min(from.x() + from.width() - 1, center + 1);
                        x++)
                    for (int z = from.z() + 4; z < from.z() + 7; z++)
                        for (int y = from.y() + 1; y <= from.y() + 2; y++)
                            plan.put(new Pos(x, y, z), Material.WATER);
            }
            case HONEY_TRAVERSE -> {
                int side = from.centerX() < WIDTH / 2.0 ? 0 : WIDTH - 1;
                for (int z = from.z() + 5; z < to.z(); z++)
                    for (int y = to.y() - 2; y <= from.y() + 2; y++)
                        plan.put(new Pos(side, y, z), Material.HONEY_BLOCK);
            }
            case BOAT_WATER -> {
                for (int z = from.z() + 7; z < to.z(); z++)
                    for (int x = 0; x < WIDTH; x++) {
                        plan.put(new Pos(x, from.y() - 1, z), OBSTACLE_BLOCK);
                        plan.put(new Pos(x, from.y(), z), Material.WATER);
                    }
                for (int x = Math.max(from.x(), center - 1);
                        x <= Math.min(from.x() + from.width() - 1, center + 1);
                        x++)
                    for (int z = from.z() + 4; z < from.z() + 7; z++)
                        plan.put(new Pos(x, from.y(), z), Material.WATER);
                for (int i = 0; i < 3; i++) {
                    int gate = center + (i % 2 == 0 ? (center < WIDTH / 2 ? 8 : -8) : 0);
                    int z = from.z() + 14 + i * 8;
                    for (int x = 0; x < WIDTH; x++)
                        if (Math.abs(x - gate) > 2)
                            for (int y = from.y() + 1; y <= from.y() + 4; y++)
                                plan.put(new Pos(x, y, z), Material.LIGHT_BLUE_CONCRETE);
                }
            }
            case BOAT_ICE -> iceRoad(plan, from, to);
            case WIND_HOPS -> {
                for (int x = center - 1; x <= center + 1; x++)
                    for (int z = from.z() + 13; z < from.z() + 16; z++)
                        plan.put(new Pos(x, from.y() + 3, z), Material.LIGHT_BLUE_CONCRETE);
            }
            default -> {}
        }
    }

    private static void iceRoad(Map<Pos, Material> plan, Island from, Island to) {
        int center = (int) from.centerX(), direction = center < WIDTH / 2 ? 1 : -1;
        int start = from.z() + 7;
        // Four cross-lane runs, joined by eight right-angle turns. Keep neighbouring
        // parallel stretches far enough apart that jumping across is not a shortcut.
        int[][] points = {
            {0, 0}, {0, 7}, {14, 7}, {14, 18}, {4, 18},
            {4, 29}, {18, 29}, {18, 40}, {0, 40}, {0, to.z() - start - 1}
        };
        for (int segment = 1; segment < points.length; segment++) {
            int[] a = points[segment - 1], b = points[segment];
            for (int offset = Math.min(a[0], b[0]); offset <= Math.max(a[0], b[0]); offset++)
                for (int row = Math.min(a[1], b[1]); row <= Math.max(a[1], b[1]); row++)
                    for (int x = center + direction * offset - 1;
                            x <= center + direction * offset + 1;
                            x++)
                        for (int z = Math.max(start, start + row - 1);
                                z <= Math.min(to.z() - 1, start + row + 1);
                                z++) plan.put(new Pos(x, from.y(), z), Material.BLUE_ICE);
        }
        for (int x = Math.max(from.x(), center - 1);
                x <= Math.min(from.x() + from.width() - 1, center + 1);
                x++)
            for (int z = from.z() + 4; z < start; z++)
                plan.put(new Pos(x, from.y(), z), Material.BLUE_ICE);
    }

    private ParkourTasks() {}
}
