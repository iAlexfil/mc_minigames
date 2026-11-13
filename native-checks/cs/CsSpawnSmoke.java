import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.lang.reflect.*;
import java.util.*;

/** Builds the actual imported map and exercises both production spawn selectors on Paper. */
final class CsSpawnSmoke {
    private static int assertions;

    static int run(ClassLoader loader, World world) throws Exception {
        assertions = 0;
        Class<?> type =
                Class.forName("me.petr1furious.potatominigames.games.cs.CsArena", true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor(World.class);
        constructor.setAccessible(true);
        Object arena = constructor.newInstance(world);
        Method build = method(type, "buildSlice", int.class);
        while (!(boolean) build.invoke(arena, 4096)) {}

        Method spawn = method(type, "spawn", int.class, int.class);
        Method shuffle = method(type, "shuffleSpawns", Random.class);
        Random random = new Random(1432);
        List<Location> opponents = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            shuffle.invoke(arena, random);
            for (int side = 0; side < 2; side++) {
                Set<Location> positions = new HashSet<>();
                for (int slot = 0; slot < (side == 0 ? 15 : 5); slot++) {
                    Location at = (Location) spawn.invoke(arena, side, slot);
                    safe(at);
                    require(at.getY() == 82, "Classic starts on the actual street deck");
                    require(positions.add(at), "Shuffled classic slots do not overlap");
                    opponents.add(at);
                }
            }
        }
        Location first = (Location) spawn.invoke(arena, 0, 0);
        var previous = first.getBlock().getBlockData();
        try {
            first.getBlock().setType(Material.STONE, false);
            Location fallback = (Location) spawn.invoke(arena, 0, 0);
            safe(fallback);
            require(!fallback.equals(first), "Classic skips a newly obstructed slot");
        } finally {
            first.getBlock().setBlockData(previous, false);
        }

        List<?> candidates = (List<?>) method(type, "deathmatchSpawns").invoke(null);
        require(candidates.size() >= 24, "Deathmatch keeps distributed surface candidates");
        List<Location> locations = new ArrayList<>();
        for (Object point : candidates) {
            Class<?> record = point.getClass();
            Location at =
                    new Location(
                            world,
                            (double) method(record, "x").invoke(point),
                            (double) method(record, "y").invoke(point),
                            (double) method(record, "z").invoke(point));
            safe(at);
            locations.add(at);
        }
        Method deathmatch = method(type, "deathmatchSpawn", Collection.class, Random.class);
        for (int life = 0; life < 20; life++) {
            Location at = (Location) deathmatch.invoke(arena, opponents, random);
            safe(at);
            double distance = nearest(at, opponents);
            for (Location candidate : locations)
                require(
                        distance + .00001 >= nearest(candidate, opponents),
                        "Deathmatch chooses the safest eligible surface by opponent distance");
            opponents.add(at);
        }
        Location selected = (Location) deathmatch.invoke(arena, List.of(), new Random(775));
        previous = selected.getBlock().getBlockData();
        try {
            selected.getBlock().setType(Material.STONE, false);
            Location fallback = (Location) deathmatch.invoke(arena, List.of(), new Random(775));
            safe(fallback);
            require(
                    fallback.getBlockX() != selected.getBlockX()
                            || fallback.getBlockZ() != selected.getBlockZ(),
                    "Deathmatch skips an obstructed candidate instead of ending the match");
        } finally {
            selected.getBlock().setBlockData(previous, false);
        }
        return assertions;
    }

    private static double nearest(Location at, Collection<Location> opponents) {
        double result = Double.POSITIVE_INFINITY;
        for (Location opponent : opponents) {
            double dx = at.getX() - opponent.getX(), dz = at.getZ() - opponent.getZ();
            result = Math.min(result, dx * dx + dz * dz);
        }
        return result;
    }

    private static void safe(Location at) {
        require(at.getY() >= 79, "Respawn is above the imported foundation levels");
        require(
                at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable(),
                "Native feet and head collision checks accept the respawn");
        require(
                !at.clone().subtract(0, .05, 0).getBlock().isPassable(),
                "Native floor check finds support, including slabs and stairs");
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
