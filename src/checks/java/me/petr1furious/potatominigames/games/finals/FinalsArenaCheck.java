package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Runs the real arena generator against its world-write boundary, without a server. */
public final class FinalsArenaCheck {
    public static void main(String[] args) {
        WorldMap map = new WorldMap();
        FinalsArena arena = new FinalsArena(map.world, new Random(42));
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
                int floorX = x, floorZ = z;
                boolean cashoutSupport = arena.cashoutLocations().stream().anyMatch(point ->
                        point.getBlockX() == floorX && point.getBlockZ() == floorZ);
                require(arena.isCover(map.block(x, 64, z)) == cashoutSupport,
                        "Only cashout pad support can break through the street floor");
                require(!arena.isCover(map.block(x, 63, z)), "The protected bedrock floor survives explosions");
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
                for (Location candidate : arena.vaultLocations()) {
                    require(
                            reachable.contains(
                                    new Cell(
                                            candidate.getBlockX(),
                                            candidate.getBlockY(),
                                            candidate.getBlockZ() + 1)),
                            "Every spawn can reach every possible vault location");
                }
                for (Location candidate : arena.cashoutLocations()) {
                    require(
                            reachable.contains(
                                    new Cell(
                                            candidate.getBlockX(),
                                            candidate.getBlockY(),
                                            candidate.getBlockZ() + 1)),
                            "Every spawn can reach every possible cashout location");
                    for (Location vaultCandidate : arena.vaultLocations())
                        require(
                                candidate.distanceSquared(vaultCandidate) >= 64,
                                "Generated cashouts remain separated from every vault location");
                }
                require(
                        reachable.contains(new Cell(-10, 73, -8)),
                        "The west roof is reachable by stairs");
                require(
                        reachable.contains(new Cell(10, 73, 8)),
                        "The east roof is reachable by stairs");
            }
        }
        Location activeVault = arena.vault();
        Block vault =
                map.block(
                        activeVault.getBlockX(), activeVault.getBlockY(), activeVault.getBlockZ());
        require(arena.isVault(vault), "The active vault is recognized");
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
                        cell.y >= 64 && Math.abs(cell.x) < 24 && Math.abs(cell.z) < 24,
                        "Only interior structures are destructible");
                require(
                        cell.y == 64 || arena.isCover(map.block(-cell.x, cell.y, cell.z)),
                        "Cover is fair for both teams");
            }
        }
        require(cover > 500, "Buildings and low cover can be destroyed");
        Block coverBlock = map.block(7, 65, 6);
        require(arena.isCover(coverBlock), "Building walls are cover");
        coverBlock.setType(Material.AIR, false);
        require(!arena.isCover(coverBlock), "Destroyed cover cannot be broken again");
        randomVaults(map, arena);
        completedCashoutMoves(map, arena);
        Location station = arena.station(0);
        Block support = map.block(station.getBlockX(), station.getBlockY() - 1, station.getBlockZ());
        require(arena.isCover(support), "Generated cashout support can be demolished");
        support.setType(Material.AIR, false);
        require(!arena.isCover(support) && map.type(station.getBlockX(), 63, station.getBlockZ()) == Material.BEDROCK,
                "Demolishing cashout support leaves a protected lower landing floor");
        System.out.println("Finals arena checks passed");
    }

    private static void completedCashoutMoves(WorldMap map, FinalsArena arena) {
        List<Location> candidates = arena.cashoutLocations();
        require(candidates.size() > 2, "The generated map has spare cashout locations");
        Set<Location> visited = new HashSet<>();
        for (int round = 0; round < 100; round++) {
            int slot = round % 2;
            Location completed = arena.station(slot), other = arena.station(1 - slot);
            arena.build();
            require(
                    completed.equals(arena.station(slot)) && other.equals(arena.station(1 - slot)),
                    "Rebuilding does not move either active cashout");
            arena.nextCashout(slot);
            Location replacement = arena.station(slot);
            visited.add(replacement);
            require(
                    !completed.equals(replacement),
                    "The completed station moves to a fresh location");
            require(other.equals(arena.station(1 - slot)), "The other station stays in place");
            require(!replacement.equals(other), "The two active stations cannot overlap");
            require(
                    candidates.contains(replacement),
                    "Replacement cashouts use configured candidates");
            require(
                    map.type(completed.getBlockX(), completed.getBlockY(), completed.getBlockZ())
                            == Material.AIR,
                    "The previous station marker is removed");
            int active = 0;
            for (Location candidate : candidates) {
                Block block =
                        map.block(
                                candidate.getBlockX(),
                                candidate.getBlockY(),
                                candidate.getBlockZ());
                int expectedSlot =
                        candidate.equals(arena.station(0))
                                ? 0
                                : candidate.equals(arena.station(1)) ? 1 : -1;
                require(
                        arena.stationAt(block) == expectedSlot,
                        "Only active cashout spots accept deposits");
                require(
                        block.getType() == Material.AIR,
                        "Cashout bodies never place gold blocks in the terrain");
                if (expectedSlot >= 0) active++;
                require(!arena.isCover(block), "Inactive cashout locations remain protected");
                require(
                        map.type(
                                        candidate.getBlockX(),
                                        candidate.getBlockY() - 1,
                                        candidate.getBlockZ())
                                != Material.AIR,
                        "Every candidate retains its supporting floor");
            }
            require(active == 2, "There are exactly two active cashouts after each replacement");
            arena.station(slot).add(50, 50, 50);
            arena.cashoutLocations().getFirst().add(50, 50, 50);
            require(
                    replacement.equals(arena.station(slot)),
                    "Callers cannot mutate active cashout coordinates");
        }
        require(visited.size() == candidates.size(), "Every configured cashout can become active");
        int[] legacy = FinalsCashoutLocations.initial(2, new Random(1));
        require(
                legacy[0] == 0 && legacy[1] == 1,
                "Two-location maps retain their original A/B assignments");
        require(
                FinalsCashoutLocations.nextIndex(2, 0, 1, new Random(1)) == 0
                        && FinalsCashoutLocations.nextIndex(2, 1, 0, new Random(1)) == 1,
                "Legacy two-location maps retain two cashouts when one finishes");
        require(
                FinalsCashoutLocations.nextIndex(3, 0, 1, new Random(1)) == 2
                        && FinalsCashoutLocations.nextIndex(3, 2, 1, new Random(1)) == 0
                        && FinalsCashoutLocations.nextIndex(3, 2, 0, new Random(1)) == 1,
                "Three-location maps always choose the only inactive location");
        Set<Integer> firstSlots = new HashSet<>(), secondSlots = new HashSet<>();
        Random random = new Random(112);
        for (int run = 0; run < 100; run++) {
            int[] pair = FinalsCashoutLocations.initial(4, random);
            require(pair[0] != pair[1], "Initial random cashouts never select the same location");
            firstSlots.add(pair[0]);
            secondSlots.add(pair[1]);
        }
        require(
                firstSlots.equals(Set.of(0, 1, 2, 3)) && secondSlots.equals(Set.of(0, 1, 2, 3)),
                "Both station labels can initially use every candidate");
    }

    private static void randomVaults(WorldMap map, FinalsArena arena) {
        require(
                arena.vaultLocations().size() > 1,
                "The generated map offers alternative vault locations");
        Set<Cell> visited = new HashSet<>();
        Location previous = arena.vault();
        arena.build();
        require(
                arena.vault().equals(previous),
                "Building an already built arena does not relocate a live vault");
        for (int round = 0; round < 100; round++) {
            Location active = arena.vault();
            require(
                    arena.vaultLocations().contains(active),
                    "Random vaults stay at configured locations");
            visited.add(new Cell(active.getBlockX(), active.getBlockY(), active.getBlockZ()));
            for (Location candidate : arena.vaultLocations()) {
                Block block =
                        map.block(
                                candidate.getBlockX(),
                                candidate.getBlockY(),
                                candidate.getBlockZ());
                boolean selected = candidate.equals(active);
                require(
                        arena.isVault(block) == selected,
                        "Only the active location can open a vault");
                require(
                        block.getType() == (selected ? Material.EMERALD_BLOCK : Material.AIR),
                        "Moving the vault leaves exactly one emerald marker and clears the previous"
                                + " one");
                require(!arena.isCover(block), "All possible vault locations survive destruction");
                require(
                        map.type(
                                        candidate.getBlockX(),
                                        candidate.getBlockY() - 1,
                                        candidate.getBlockZ())
                                != Material.AIR,
                        "Each random vault has a solid floor");
            }
            arena.vault().add(100, 100, 100);
            arena.vaultLocations().getFirst().add(100, 100, 100);
            require(
                    arena.vault().equals(active),
                    "Callers cannot move the active location by mutating returned locations");
            arena.nextVault();
            require(
                    !arena.vault().equals(active),
                    "Consecutive cashouts do not reuse the same vault location");
        }
        require(
                visited.size() == arena.vaultLocations().size(),
                "Random selection can use every configured location");
        require(
                FinalsVaultLocations.nextIndex(1, -1, new Random(1)) == 0,
                "A legacy one-location map can select its first vault");
        require(
                FinalsVaultLocations.nextIndex(1, 0, new Random(1)) == 0,
                "A legacy one-location map can start another round");
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
