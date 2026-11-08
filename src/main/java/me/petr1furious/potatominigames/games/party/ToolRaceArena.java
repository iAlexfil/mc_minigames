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
        for (int center : columns.values())
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    for (int y = 30; y <= 83; y++) {
                        Material material =
                                (x != 0 || z != 0 || y == 30)
                                        ? Material.BEDROCK
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
