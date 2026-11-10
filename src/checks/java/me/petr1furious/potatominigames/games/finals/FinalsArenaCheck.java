package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Runs the real arena generator against its world-write boundary, without a server. */
public final class FinalsArenaCheck {
    public static void main(String[] args) {
        WorldMap map = new WorldMap();
        FinalsArena arena = new FinalsArena(map.world);
        arena.build();
        Location center = arena.center();
        require(
                center.getX() == .5 && center.getZ() == .5,
                "World border stays centered over the symmetric arena");
        require(
                map.type(center.getBlockX(), center.getBlockY(), center.getBlockZ())
                        == Material.AIR,
                "The arena center is a safe standing location");
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                require(map.type(x, 64, z) != Material.AIR, "The arena floor has no holes");
                require(!arena.isCover(map.block(x, 64, z)), "The floor survives explosions");
            }
        }
        for (int team = 0; team < 2; team++) {
            Set<String> spawns = new HashSet<>();
            for (int member = 0; member < 8; member++) {
                Location spawn = arena.spawn(team, member);
                require(arena.contains(spawn), "Each spawn lies inside the arena");
                require(
                        spawns.add(spawn.getBlockX() + ":" + spawn.getBlockZ()),
                        "Teammates have separate spawns");
                require(
                        map.type(spawn.getBlockX(), 65, spawn.getBlockZ()) == Material.AIR,
                        "Spawn feet are clear");
                require(
                        map.type(spawn.getBlockX(), 66, spawn.getBlockZ()) == Material.AIR,
                        "Spawn head is clear");
                Set<Cell> reachable = reachable(map, spawn);
                require(reachable.contains(new Cell(0, 65, 1)), "Every spawn can reach the vault");
                require(
                        reachable.contains(new Cell(0, 65, -14)),
                        "Every spawn can reach station A");
                require(reachable.contains(new Cell(0, 65, 14)), "Every spawn can reach station B");
                require(
                        reachable.contains(new Cell(-10, 73, -8)),
                        "The west roof is reachable by stairs");
                require(
                        reachable.contains(new Cell(10, 73, 8)),
                        "The east roof is reachable by stairs");
            }
        }
        Block vault = map.block(0, 65, 0);
        require(arena.isVault(vault), "The central vault is recognized");
        require(!arena.isVault(map.block(0, 64, 0)), "Vault recognition includes height");
        require(!arena.isCover(vault), "The vault survives explosions");
        for (int station = 0; station < 2; station++) {
            Location location = arena.station(station);
            Block block =
                    map.block(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            require(arena.stationAt(block) == station, "The correct cashout is recognized");
            require(!arena.isCover(block), "Cashout stations survive explosions");
        }
        require(arena.stationAt(vault) == -1, "The vault cannot be treated as a station");
        require(
                !arena.contains(new Location(map.world, 25, 65, 0)),
                "Leaving the east bound is detected");
        require(
                !arena.contains(new Location(map.world, 0, 63, 0)),
                "Falling below the floor is detected");
        require(
                !arena.contains(new Location(new WorldMap().world, 0, 65, 0)),
                "Other worlds are excluded");
        require(
                !arena.isCover(new WorldMap().block(7, 65, 6)),
                "Other worlds cannot destroy arena cover");
        int cover = 0;
        for (var entry : map.blocks.entrySet()) {
            Cell cell = entry.getKey();
            require(
                    Math.abs(cell.x) <= 24 && Math.abs(cell.z) <= 24,
                    "Generation stays within its footprint");
            if (arena.isCover(map.block(cell.x, cell.y, cell.z))) {
                cover++;
                require(
                        cell.y > 64 && Math.abs(cell.x) < 24 && Math.abs(cell.z) < 24,
                        "Only interior structures are destructible");
                require(
                        arena.isCover(map.block(-cell.x, cell.y, cell.z)),
                        "Cover is fair for both teams");
            }
        }
        require(cover > 500, "Buildings and low cover can be destroyed");
        Block coverBlock = map.block(7, 65, 6);
        require(arena.isCover(coverBlock), "Building walls are cover");
        coverBlock.setType(Material.AIR, false);
        require(!arena.isCover(coverBlock), "Destroyed cover cannot be broken again");
        System.out.println("Finals arena checks passed");
    }

    private static Set<Cell> reachable(WorldMap map, Location spawn) {
        Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        Cell first = new Cell(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        visited.add(first);
        pending.add(first);
        while (!pending.isEmpty()) {
            Cell cell = pending.remove();
            for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int dy = -1; dy <= 1; dy++) {
                    Cell next = new Cell(cell.x + direction[0], cell.y + dy, cell.z + direction[1]);
                    if (Math.abs(next.x) >= 24
                            || Math.abs(next.z) >= 24
                            || next.y < 65
                            || next.y > 75
                            || map.type(next.x, next.y - 1, next.z) == Material.AIR
                            || map.type(next.x, next.y, next.z) != Material.AIR
                            || map.type(next.x, next.y + 1, next.z) != Material.AIR) continue;
                    if (dy > 0 && map.type(cell.x, cell.y + 2, cell.z) != Material.AIR) continue;
                    if (visited.add(next)) pending.add(next);
                }
            }
        }
        return visited;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private record Cell(int x, int y, int z) {}

    private static final class WorldMap {
        final Map<Cell, Material> blocks = new HashMap<>();
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBlockAt" ->
                                                    block(
                                                            (int) args[0],
                                                            (int) args[1],
                                                            (int) args[2]);
                                            case "setSpawnLocation" -> true;
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected world call: "
                                                                    + method.getName());
                                        });

        Material type(int x, int y, int z) {
            return blocks.getOrDefault(new Cell(x, y, z), Material.AIR);
        }

        Block block(int x, int y, int z) {
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setType" -> {
                                            blocks.put(new Cell(x, y, z), (Material) args[0]);
                                            yield null;
                                        }
                                        case "getType" -> type(x, y, z);
                                        case "getWorld" -> world;
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected block call: "
                                                                + method.getName());
                                    });
        }
    }
}
