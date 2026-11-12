package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.ParkourCourse.*;

import org.bukkit.Material;

import java.util.Map;

/** Native terrain for movement tasks; every structure stays between its two checkpoint rows. */
final class ParkourTasks {
    static void build(
            Kind kind, Island from, Island to, Map<Pos, Material> plan, Map<Pos, String> states) {
        int center = (int) from.centerX();
        switch (kind) {
            case PARKOUR_JUMPS, LEAPING_PILLARS -> {
                int[] heights =
                        kind == Kind.LEAPING_PILLARS ? new int[] {2, 4, 2} : new int[] {1, 0, 1};
                for (int i = 0; i < 3; i++) {
                    int offset = kind == Kind.LEAPING_PILLARS ? 0 : (i % 2 == 0 ? -1 : 1);
                    for (int x = center + offset - 1; x <= center + offset + 1; x++)
                        for (int z = from.z() + 9 + i * 6; z < from.z() + 13 + i * 6; z++)
                            plan.put(
                                    new Pos(x, from.y() + heights[i], z),
                                    Material.LIGHT_BLUE_CONCRETE);
                }
            }
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
            case BOAT_WATER, FROST_WALKER -> {
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
                if (kind == Kind.BOAT_WATER)
                    for (int i = 0; i < 3; i++) {
                        int gate = center + (i % 2 == 0 ? (center < WIDTH / 2 ? 8 : -8) : 0);
                        int z = from.z() + 14 + i * 8;
                        for (int x = 0; x < WIDTH; x++)
                            if (Math.abs(x - gate) > 2)
                                for (int y = from.y() + 1; y <= from.y() + 4; y++)
                                    plan.put(new Pos(x, y, z), Material.LIGHT_BLUE_CONCRETE);
                    }
            }
            case BOAT_ICE, SOUL_SPEED -> {
                int half = kind == Kind.BOAT_ICE ? 2 : 1;
                for (int z = from.z() + 7; z < to.z(); z++) {
                    double t = (double) (z - from.z() - 7) / Math.max(1, to.z() - from.z() - 8);
                    int xCenter =
                            center
                                    + (center < WIDTH / 2 ? 1 : -1)
                                            * (int) Math.round(8 * Math.sin(Math.PI * t));
                    Material floor =
                            kind == Kind.BOAT_ICE
                                    ? Material.BLUE_ICE
                                    : (z % 8 < 4 ? Material.SOUL_SAND : Material.SOUL_SOIL);
                    for (int x = xCenter - half; x <= xCenter + half; x++)
                        plan.put(new Pos(x, from.y(), z), floor);
                }
                for (int x = Math.max(from.x(), center - half);
                        x <= Math.min(from.x() + from.width() - 1, center + half);
                        x++)
                    for (int z = from.z() + 4; z < from.z() + 7; z++)
                        plan.put(
                                new Pos(x, from.y(), z),
                                kind == Kind.BOAT_ICE ? Material.BLUE_ICE : Material.SOUL_SOIL);
            }
            case MINECART_RIDE -> {
                for (int z = from.z() + 4; z < to.z() + 3; z++) {
                    plan.put(new Pos(center, from.y(), z), Material.REDSTONE_BLOCK);
                    Pos rail = new Pos(center, from.y() + 1, z);
                    plan.put(rail, Material.POWERED_RAIL);
                    states.put(
                            rail,
                            "minecraft:powered_rail[shape=north_south,powered=true,waterlogged=false]");
                }
            }
            case WIND_HOPS -> {
                for (int x = center - 1; x <= center + 1; x++)
                    for (int z = from.z() + 13; z < from.z() + 16; z++)
                        plan.put(new Pos(x, from.y() + 3, z), Material.LIGHT_BLUE_CONCRETE);
            }
            default -> {}
        }
    }

    private ParkourTasks() {}
}
