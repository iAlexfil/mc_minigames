package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.lang.reflect.Proxy;
import java.util.*;

/** Exercises actual arena mutations, not copies of the generators. */
public final class PartyArenaCheck {
    public static void main(String[] args) {
        WorldMap w = new WorldMap();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) ids.add(new UUID(0, i + 1));
        BuildMemoryArena memory = new BuildMemoryArena(w.world, ids);
        memory.build();
        for (UUID id : ids) {
            var s = memory.spawn(id);
            int cx = s.getBlockX() - 2, cz = s.getBlockZ() + 3;
            for (int x = 0; x < 5; x++)
                for (int z = 0; z < 5; z++)
                    check(
                            w.world.getBlockAt(cx + x, 80, cz + z).getType()
                                    == Material.LIGHT_BLUE_CONCRETE,
                            "Whole 5x5 footprint is marked");
            check(
                    w.world.getBlockAt(cx, 80, cz - 1).getType() == Material.SMOOTH_QUARTZ,
                    "No isolated gold orientation marker");
        }
        var pattern = BuildMemoryPattern.generate(8, new Random(5));
        for (UUID id : ids) memory.show(id, pattern);
        for (UUID id : ids)
            check(
                    memory.capture(id).equals(pattern.blocks()),
                    "Twelve independent build workspaces");
        memory.clear(ids.getFirst());
        check(memory.capture(ids.getFirst()).isEmpty(), "Model disappears");
        check(
                memory.capture(ids.get(1)).equals(pattern.blocks()),
                "Clearing own model preserves neighbor");
        w = new WorldMap();
        ToolRaceTrack track = new ToolRaceTrack(ids, ToolRaceTrack.generate(new Random(7)));
        ToolRaceArena shafts = new ToolRaceArena(w.world, ids);
        shafts.build(track);
        for (UUID id : ids) {
            var spawn = shafts.spawn(id);
            for (int d = 0; d < 50; d++) {
                Block b = w.world.getBlockAt(spawn.getBlockX(), 80 - d, 0);
                check(b.getType() == track.block(d), "Actual identical fifty-block shafts");
                check(shafts.depth(id, b) == d, "Only own depth");
            }
            check(
                    w.world.getBlockAt(spawn.getBlockX(), 30, 0).getType() == Material.BEDROCK,
                    "Safe bottom");
        }
        check(
                shafts.depth(ids.getFirst(), w.world.getBlockAt(4, 80, 0)) == -1,
                "Cannot mine neighbor");
        w = new WorldMap();
        var arena = new PvpArena(w.world);
        arena.build();
        for (int count = 2; count <= 12; count++)
            for (int i = 0; i < count; i++) {
                var s = arena.spawn(i, count);
                check(
                        w.world.getBlockAt(s.getBlockX(), 81, s.getBlockZ()).getType()
                                == Material.AIR,
                        "FFA spawn cannot be inside cover");
            }
        System.out.println("Party arena geometry passed");
    }

    static final class WorldMap {
        final Map<String, Material> blocks = new HashMap<>();
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (proxy, m, a) ->
                                        switch (m.getName()) {
                                            case "getBlockAt" ->
                                                    block((int) a[0], (int) a[1], (int) a[2]);
                                            case "setSpawnLocation" -> true;
                                            case "equals" -> proxy == a[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + m.getName());
                                        });

        Block block(int x, int y, int z) {
            String key = x + ":" + y + ":" + z;
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class[] {Block.class},
                            (proxy, m, a) ->
                                    switch (m.getName()) {
                                        case "setType" -> {
                                            blocks.put(key, (Material) a[0]);
                                            yield null;
                                        }
                                        case "getType" -> blocks.getOrDefault(key, Material.AIR);
                                        case "getWorld" -> world;
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        default ->
                                                throw new AssertionError("Block: " + m.getName());
                                    });
        }
    }
}
