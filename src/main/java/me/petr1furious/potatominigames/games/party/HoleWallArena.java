package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;

final class HoleWallArena {
    static final int ARM_RADIUS = 2;
    private final World world;
    private final HoleWallLayout layout;
    private int radius = 8, armRadius = ARM_RADIUS;

    HoleWallArena(World world) {
        this(world, HoleWallLayout.PLUS);
    }

    HoleWallArena(World world, HoleWallLayout layout) {
        this.world = world;
        this.layout = layout;
    }

    int radius() {
        return radius;
    }

    int armRadius() {
        return armRadius;
    }

    void build() {
        paint();
    }

    void warn(int nextRadius, int nextArmRadius) {
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                if (layout.onFloor(x, z, radius, armRadius)
                        && !layout.onFloor(x, z, nextRadius, nextArmRadius))
                    world.getBlockAt(x, 80, z).setType(Material.RED_CONCRETE, false);
    }

    void shrink(int nextRadius, int nextArmRadius) {
        if (nextRadius < 4 || nextRadius > radius || nextArmRadius < 1 || nextArmRadius > armRadius)
            return;
        if (nextRadius == radius && nextArmRadius == armRadius) return;
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                if (layout.onFloor(x, z, radius, armRadius)
                        && !layout.onFloor(x, z, nextRadius, nextArmRadius))
                    world.getBlockAt(x, 80, z).setType(Material.AIR, false);
        radius = nextRadius;
        armRadius = nextArmRadius;
        paint();
    }

    private void paint() {
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                if (!layout.onFloor(x, z, radius, armRadius)) continue;
                Material material = Material.SMOOTH_QUARTZ;
                if (layout == HoleWallLayout.PLUS && cornerIsland(x, z))
                    material = Material.YELLOW_TERRACOTTA;
                world.getBlockAt(x, 80, z).setType(material, false);
            }
    }

    static boolean onFloor(int x, int z, int radius, int armRadius) {
        return HoleWallLayout.PLUS.onFloor(x, z, radius, armRadius);
    }

    private static boolean cornerIsland(int x, int z) {
        // Fixed islands never move or grow during a round. Even after arms narrow, the
        // three empty cells between arm and island can be crossed with a running jump.
        return Math.abs(x) >= 5 && Math.abs(x) <= 6 && Math.abs(z) >= 5 && Math.abs(z) <= 6;
    }

    Location spawn(int index, int count) {
        if (layout != HoleWallLayout.PLUS) {
            var used = new java.util.HashSet<String>();
            int chosenX = 0, chosenZ = 0;
            for (int i = 0; i <= index; i++) {
                double angle = 2 * Math.PI * i / Math.max(1, count),
                        tx = 4 * Math.cos(angle),
                        tz = 4 * Math.sin(angle),
                        best = Double.MAX_VALUE;
                for (int x = -8; x <= 8; x++)
                    for (int z = -8; z <= 8; z++) {
                        if (!layout.onFloor(x, z, radius, armRadius) || used.contains(x + ":" + z))
                            continue;
                        double score = (x - tx) * (x - tx) + (z - tz) * (z - tz);
                        if (score < best) {
                            best = score;
                            chosenX = x;
                            chosenZ = z;
                        }
                    }
                used.add(chosenX + ":" + chosenZ);
            }
            return new Location(world, chosenX + .5, 81, chosenZ + .5);
        }
        int lane = index / 4 - 1, side = index % 4;
        double distance = Math.min(4, radius - 1);
        return switch (side) {
            case 0 -> new Location(world, .5 + lane, 81, .5 - distance);
            case 1 -> new Location(world, .5 + distance, 81, .5 + lane);
            case 2 -> new Location(world, .5 + lane, 81, .5 + distance);
            default -> new Location(world, .5 - distance, 81, .5 + lane);
        };
    }
}
