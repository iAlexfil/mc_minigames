package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;

final class ColorFloorArena {
    private final World world;

    ColorFloorArena(World world) {
        this.world = world;
    }

    void show(ColorFloorRound round, boolean drop) {
        for (int x = 0; x < 21; x++)
            for (int z = 0; z < 21; z++)
                world.getBlockAt(x - 10, 80, z - 10)
                        .setType(
                                drop && round.material(x, z) != round.target()
                                        ? Material.AIR
                                        : round.material(x, z),
                                false);
    }

    Location spawn(int i, int count) {
        double a = 2 * Math.PI * i / count;
        return new Location(world, .5 + 8 * Math.cos(a), 81, .5 + 8 * Math.sin(a));
    }

    boolean safe(Location at, ColorFloorRound round) {
        return at.getWorld().equals(world) && round.safe(at.getX() + 10, at.getY(), at.getZ() + 10);
    }
}
