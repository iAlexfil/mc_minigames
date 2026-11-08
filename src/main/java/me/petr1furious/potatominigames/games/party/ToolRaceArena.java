package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.*;

final class ToolRaceArena {
    private final World world;
    private final Map<UUID, Integer> columns = new LinkedHashMap<>();

    ToolRaceArena(World world, Collection<UUID> ids) {
        this.world = world;
        int i = 0;
        for (UUID id : ids) columns.put(id, i++ * 4);
    }

    void build(ToolRaceTrack track) {
        int end = columns.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int x = -2; x <= end + 2; x++)
            for (int z = -3; z <= 3; z++) {
                world.getBlockAt(x, 25, z).setType(Material.SMOOTH_QUARTZ, false);
                if (x == -2 || x == end + 2 || Math.abs(z) == 3)
                    for (int y = 26; y <= 29; y++)
                        world.getBlockAt(x, y, z).setType(Material.GLASS, false);
            }
        for (int center : columns.values())
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    for (int y = 31; y <= 83; y++) {
                        Material material =
                                (x != 0 || z != 0)
                                        ? Material.GLASS
                                        : y <= 80 ? track.block(80 - y) : Material.AIR;
                        world.getBlockAt(center + x, y, z).setType(material, false);
                    }
    }

    Location spawn(UUID id) {
        return new Location(world, columns.get(id) + .5, 81, .5);
    }

    int depth(UUID id, Block block) {
        Integer x = columns.get(id);
        return x != null
                        && block.getWorld().equals(world)
                        && block.getX() == x
                        && block.getZ() == 0
                        && block.getY() <= 80
                        && block.getY() >= 31
                ? 80 - block.getY()
                : -1;
    }
}
