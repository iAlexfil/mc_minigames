package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Proxy;
import java.util.*;

public final class AnvilsCheck {
    public static void main(String[] args) throws Exception {
        AnvilRound r = new AnvilRound(12);
        r.start(0);
        check(!r.raining() && r.shelters() == 14, "Move before first rain");
        r.advance(199);
        check(!r.raining(), "Full ten seconds to cross the larger arena");
        r.advance(200);
        check(r.raining(), "Rain begins");
        r.advance(320);
        check(!r.raining() && r.wave() == 2 && r.shelters() == 13, "Rain lasts six seconds");
        r.advance(520);
        r.advance(640);
        r.advance(840);
        check(!r.pvpEnabled(), "No PvP through third rain");
        r.advance(960);
        check(r.pvpEnabled(), "PvP from fourth wave");
        platformGeometry();
        check(
                AnvilRain.batchSize(1) * 20 / AnvilRain.spawnInterval(1) >= 100,
                "Dense rain already from the first wave");
        check(AnvilRain.batchSize(10) > AnvilRain.batchSize(1), "Rain intensifies");
        var a = AnvilArena.layout(8, new Random(1));
        var b = AnvilArena.layout(7, new Random(2));
        check(!a.containsAll(b), "New shelter layout is not just removed old shelter");
        for (int seed = 0; seed < 50; seed++)
            for (var s : AnvilArena.layout(14, new Random(seed)))
                check(
                        s.x() >= -21 && s.x() + 2 <= 21 && s.z() >= -21 && s.z() + 2 <= 21,
                        "Shelters stay on floor");
        BoundingBox slab = new BoundingBox(0, 83, 0, 1, 83.5, 1),
                body = new BoundingBox(.2, 81, .2, .8, 82.8, .8);
        check(
                AnvilRain.impact(.5, .5, 90, 80, slab) > AnvilRain.impact(.5, .5, 90, 80, body),
                "Slab intercepts before player");
        check(Double.isNaN(AnvilRain.impact(2, .5, 90, 80, slab)), "No imaginary radius shelter");
        check(
                AnvilRain.impact(.5, .5, 84, 81, body) > 0,
                "Swept collision cannot tunnel through body");
        fullPlatformRain();
        System.out.println("Anvil waves passed");
    }

    private static void platformGeometry() {
        var fixture = new PartyArenaCheck.WorldMap();
        var arena = new AnvilArena(fixture.world);
        arena.build();
        int floor = 0;
        for (int x = -22; x <= 21; x++)
            for (int z = -22; z <= 21; z++) {
                var material = fixture.world.getBlockAt(x, 80, z).getType();
                if (x >= -21 && x <= 20 && z >= -21 && z <= 20) {
                    check(
                            material == org.bukkit.Material.SMOOTH_STONE,
                            "Entire 42 by 42 floor exists");
                    floor++;
                    check(
                            !Double.isNaN(
                                    AnvilRain.impact(
                                            x + .5,
                                            z + .5,
                                            84,
                                            79,
                                            arena.collisionBoxes().getLast())),
                            "Collision covers every floor cell");
                } else
                    for (int y = 80; y <= 84; y++)
                        check(
                                fixture.world.getBlockAt(x, y, z).getType()
                                        == org.bukkit.Material.BARRIER,
                                "Continuous outer boundary");
            }
        check(floor == 1764, "Four times the previous 441-block area");
        for (int i = 0; i < 12; i++) {
            var spawn = arena.spawn(i, 12);
            check(
                    fixture.world.getBlockAt(spawn.getBlockX(), 80, spawn.getBlockZ()).getType()
                            == org.bukkit.Material.SMOOTH_STONE,
                    "Spawn over floor");
        }
    }

    private static void fullPlatformRain() throws Exception {
        for (int seed = 0; seed < 8; seed++) {
            RainWorld fixture = new RainWorld();
            var rain =
                    new AnvilRain(
                            fixture.world,
                            new AnvilArena(fixture.world),
                            new Random(seed),
                            p -> {
                                throw new AssertionError("No players");
                            });
            var sound = AnvilRain.class.getDeclaredField("nextImpactSound");
            sound.setAccessible(true);
            sound.setLong(rain, Long.MAX_VALUE);
            for (int wave : new int[] {1, 4, 10}) {
                fixture.impacts.clear();
                fixture.landed = 0;
                fixture.spawned = 0;
                // Even the shortest configurable wave must cover the whole floor before cleanup.
                // Omit five audio ticks: Bukkit's sound registry requires a server. This gives
                // the rain fewer simulation steps than production, a stricter coverage deadline.
                for (int tick = 0; tick < 100; tick++)
                    if (tick % 20 != 0) rain.tick(tick, wave, List.of());
                check(
                        fixture.impacts.size() == 1764,
                        "Every floor cell is struck before wave ends; seed="
                                + seed
                                + ", wave="
                                + wave
                                + ", hit="
                                + fixture.impacts.size());
                check(
                        fixture.spawned >= 9500,
                        "Rain density per square block is preserved on the larger platform");
                check(
                        fixture.landed >= 6000,
                        "Dense rain reaches the floor instead of only spawning overhead");
                check(rain.activeCount() <= 5120, "Airborne display count remains bounded");
                rain.clear();
                check(rain.activeCount() == 0, "No anvils survive into relocation");
            }
        }
    }

    private static final class RainWorld {
        final Set<String> impacts = new HashSet<>();
        int spawned, landed;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (proxy, m, a) ->
                                        switch (m.getName()) {
                                            // Display appearance initialization is outside this
                                            // physics/coverage fixture.
                                            case "spawn" -> {
                                                spawned++;
                                                yield display(((Location) a[0]).clone());
                                            }
                                            case "playSound" -> null;
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected world call: "
                                                                    + m.getName());
                                        });

        BlockDisplay display(Location initial) {
            Location[] position = {initial};
            boolean[] removed = {false};
            return (BlockDisplay)
                    Proxy.newProxyInstance(
                            BlockDisplay.class.getClassLoader(),
                            new Class[] {BlockDisplay.class},
                            (proxy, m, a) ->
                                    switch (m.getName()) {
                                        case "teleport" -> {
                                            position[0] = ((Location) a[0]).clone();
                                            yield true;
                                        }
                                        case "remove" -> {
                                            check(!removed[0], "Display removed once");
                                            removed[0] = true;
                                            if (position[0].getY() < 83) {
                                                landed++;
                                                int x = position[0].getBlockX(),
                                                        z = position[0].getBlockZ();
                                                check(
                                                        x >= -21 && x <= 20 && z >= -21 && z <= 20,
                                                        "Impact stays inside platform");
                                                impacts.add(x + ":" + z);
                                            }
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected display call: " + m.getName());
                                    });
        }
    }
}
