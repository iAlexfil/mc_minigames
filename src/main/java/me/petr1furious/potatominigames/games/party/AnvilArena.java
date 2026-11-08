package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.util.BoundingBox;

import java.util.*;

final class AnvilArena {
    static final int WIDTH = 42, MIN = -WIDTH / 2, MAX = MIN + WIDTH - 1;

    record Shelter(int x, int z) {}

    private final World world;
    private List<Shelter> shelters = List.of();

    AnvilArena(World world) {
        this.world = world;
    }

    static List<Shelter> layout(int count, Random random) {
        List<Shelter> candidates = new ArrayList<>();
        // Keep even the last shelter within a ten-second sprint from the farthest corner.
        for (int x = MIN + 5; x <= MAX - 6; x += 2)
            for (int z = MIN + 5; z <= MAX - 6; z += 2) candidates.add(new Shelter(x, z));
        Collections.shuffle(candidates, random);
        return List.copyOf(candidates.subList(0, Math.clamp(count, 1, 14)));
    }

    void build() {
        for (int x = MIN; x <= MAX; x++)
            for (int z = MIN; z <= MAX; z++)
                world.getBlockAt(x, 80, z).setType(Material.SMOOTH_STONE, false);
        // Open sky, low boundary only: players cannot leave the arena or reach slab tops.
        for (int x = MIN - 1; x <= MAX + 1; x++)
            for (int z = MIN - 1; z <= MAX + 1; z++)
                if (x == MIN - 1 || x == MAX + 1 || z == MIN - 1 || z == MAX + 1)
                    for (int y = 80; y <= 84; y++)
                        world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
    }

    void relocateShelters(int count, Random random) {
        for (Shelter s : shelters) paint(s, false);
        shelters = layout(count, random);
        for (Shelter s : shelters) paint(s, true);
    }

    private void paint(Shelter s, boolean show) {
        for (int x = s.x; x < s.x + 2; x++)
            for (int z = s.z; z < s.z + 2; z++) {
                var block = world.getBlockAt(x, 83, z);
                if (!show) block.setType(Material.AIR, false);
                else {
                    Slab data = (Slab) Material.SMOOTH_STONE_SLAB.createBlockData();
                    data.setType(Slab.Type.BOTTOM);
                    block.setBlockData(data, false);
                }
            }
    }

    List<BoundingBox> collisionBoxes() {
        List<BoundingBox> boxes = new ArrayList<>();
        for (Shelter s : shelters) boxes.add(new BoundingBox(s.x, 83, s.z, s.x + 2, 83.5, s.z + 2));
        boxes.add(new BoundingBox(MIN, 80, MIN, MAX + 1, 81, MAX + 1));
        return boxes;
    }

    Location spawn(int i, int n) {
        double a = 2 * Math.PI * i / n;
        return new Location(world, 16 * Math.cos(a), 81, 16 * Math.sin(a));
    }
}
