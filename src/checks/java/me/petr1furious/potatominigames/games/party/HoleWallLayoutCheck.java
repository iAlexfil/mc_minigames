package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.*;

final class HoleWallLayoutCheck {
    static void run() {
        for (var layout : HoleWallLayout.values()) {
            Set<String> previous = null;
            var fixture = new PartyArenaCheck.WorldMap();
            var arena = new HoleWallArena(fixture.world, layout);
            arena.build();
            Set<String> spawns = new HashSet<>();
            for (int i = 0; i < 12; i++) {
                var p = arena.spawn(i, 12);
                check(
                        layout.onFloor(p.getBlockX(), p.getBlockZ(), 8, 2),
                        "Every map spawn is on real floor");
                check(
                        spawns.add(p.getBlockX() + ":" + p.getBlockZ()),
                        "All twelve spawns are distinct");
            }
            for (int radius : new int[] {8, 6, 4}) {
                int arm = radius == 8 ? 2 : 1;
                Set<String> cells = new HashSet<>();
                for (int x = -8; x <= 8; x++)
                    for (int z = -8; z <= 8; z++)
                        if (layout.onFloor(x, z, radius, arm)) cells.add(x + ":" + z);
                if (previous != null) {
                    check(
                            previous.containsAll(cells) && previous.size() > cells.size(),
                            "Shrinking removes floor, never moves or adds it");
                    arena.warn(radius, arm);
                    for (String key : previous)
                        if (!cells.contains(key)) {
                            var xy = key.split(":");
                            check(
                                    fixture.world
                                                    .getBlockAt(
                                                            Integer.parseInt(xy[0]),
                                                            80,
                                                            Integer.parseInt(xy[1]))
                                                    .getType()
                                            == Material.RED_CONCRETE,
                                    "Every disappearing cell warns red");
                        }
                    arena.shrink(radius, arm);
                }
                for (int x = -8; x <= 8; x++)
                    for (int z = -8; z <= 8; z++)
                        check(
                                (fixture.world.getBlockAt(x, 80, z).getType() != Material.AIR)
                                        == cells.contains(x + ":" + z),
                                "Painted floor matches chosen layout");
                if (layout != HoleWallLayout.PLUS)
                    check(connected(cells), "New layouts remain walk-connected after every shrink");
                if (layout == HoleWallLayout.DONUT)
                    check(!cells.contains("0:0"), "Donut keeps its central hole");
                previous = cells;
            }
            if (layout != HoleWallLayout.PLUS) patterns(layout);
        }
        Set<HoleWallLayout> selected = EnumSet.noneOf(HoleWallLayout.class);
        var random = new Random(42);
        for (int i = 0; i < 60; i++) selected.add(HoleWallLayout.choose(random));
        check(selected.size() == 3, "All three layouts can be selected");
    }

    private static boolean connected(Set<String> cells) {
        var seen = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(cells.iterator().next());
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            if (!cells.contains(key) || !seen.add(key)) continue;
            var xy = key.split(":");
            int x = Integer.parseInt(xy[0]), z = Integer.parseInt(xy[1]);
            queue.add((x - 1) + ":" + z);
            queue.add((x + 1) + ":" + z);
            queue.add(x + ":" + (z - 1));
            queue.add(x + ":" + (z + 1));
        }
        return seen.size() == cells.size();
    }

    private static boolean grounded(List<HoleWallPattern.Cell> cells) {
        var all = new HashSet<String>();
        var reached = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        for (var c : cells) {
            String key = c.u() + ":" + c.y();
            all.add(key);
            if (c.y() == 0) queue.add(key);
        }
        while (!queue.isEmpty()) {
            var key = queue.removeFirst();
            if (!all.contains(key) || !reached.add(key)) continue;
            var xy = key.split(":");
            int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
            queue.add((x - 1) + ":" + y);
            queue.add((x + 1) + ":" + y);
            queue.add(x + ":" + (y - 1));
            queue.add(x + ":" + (y + 1));
        }
        return reached.size() == all.size();
    }

    private static void patterns(HoleWallLayout layout) {
        for (int mirror = 0; mirror < 2; mirror++)
            for (int stage = 1; stage <= layout.patternCount(); stage++) {
                final boolean reflection = mirror == 1;
                var pattern =
                        layout.pattern(
                                stage,
                                1,
                                new Random(stage) {
                                    public boolean nextBoolean() {
                                        return reflection;
                                    }
                                });
                check(pattern.gates().size() >= 2, "Every map wall offers multiple routes");
                check(
                        grounded(pattern.cells()),
                        "Every wall fragment is attached to a grounded part");
                for (var gate : pattern.gates())
                    for (var direction : HoleWallPhysics.Direction.values()) {
                        double u = gate.start() + gate.width() / 2, y = 81 + gate.bottom();
                        var body =
                                direction.dx == 0
                                        ? new BoundingBox(
                                                u - .3, y, .2, u + .3, y + gate.bodyHeight(), .8)
                                        : new BoundingBox(
                                                .2, y, u - .3, .8, y + gate.bodyHeight(), u + .3);
                        check(
                                !HoleWallPhysics.collides(
                                        pattern.cells(), direction, -1, 1, body, body),
                                "Every advertised gate physically fits its movement profile");
                        boolean floor = false;
                        for (int depth = -4; depth <= 4; depth++) {
                            double x = direction.dx == 0 ? u : depth + .5,
                                    z = direction.dx == 0 ? depth + .5 : u;
                            floor |= layout.supports(x, z, 4, 1);
                        }
                        check(
                                floor,
                                "Every gate has supported arrival space on the final platform");
                        if (gate.height() == 1.5) {
                            var standing = body.clone().expand(0, 0, 0, 0, .3, 0);
                            check(
                                    HoleWallPhysics.collides(
                                            pattern.cells(), direction, -1, 1, standing, standing),
                                    "Crouching route remains low after pruning floating"
                                        + " decoration");
                        }
                    }
            }
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                if (layout.onFloor(x, z, 8, 2)) {
                    var body = new BoundingBox(x + .2, 81, z + .2, x + .8, 82.8, z + .8);
                    boolean hit = false;
                    for (int stage = 1; stage <= layout.patternCount(); stage++) {
                        var p = layout.pattern(stage, 1, new Random(stage));
                        hit |=
                                HoleWallPhysics.collides(
                                        p.cells(),
                                        HoleWallPhysics.Direction.NORTH,
                                        -10,
                                        10,
                                        body,
                                        body);
                    }
                    check(hit, "No floor cell lets a player idle through the entire catalog");
                }
        for (int seed = 0; seed < 6; seed++) {
            var stream = new HoleWallFlow(2400, new Random(seed), layout);
            Set<HoleWallPhysics.Direction> late = EnumSet.noneOf(HoleWallPhysics.Direction.class);
            int total = 0;
            for (int tick = 0; tick < 2400; tick++) {
                var frame = stream.tick(tick);
                total += frame.started().size();
                for (var wall : frame.started()) if (tick >= 1600) late.add(wall.direction);
                for (var a : stream.active())
                    for (var b : stream.active())
                        if (a.number < b.number
                                && tick >= a.moveAt
                                && tick >= b.moveAt
                                && Math.abs(a.depth) <= frame.radius() + 2
                                && Math.abs(b.depth) <= frame.radius() + 2) {
                            int dot =
                                    a.direction.dx * b.direction.dx
                                            + a.direction.dz * b.direction.dz;
                            check(dot != -1, "Opposing map walls cannot squeeze the platform");
                            if (dot == 0)
                                check(
                                        layout.crossing(
                                                a.pattern, a.direction, b.pattern, b.direction),
                                        "Concurrent perpendicular map walls have a route over"
                                            + " actual final floor");
                        }
            }
            check(
                    total >= 20 && late.size() == 4,
                    "Each layout retains a frequent four-direction stream");
        }
        var flow = new HoleWallFlow(6000, new Random(42), layout);
        List<net.kyori.adventure.text.Component> titles = new ArrayList<>();
        for (int tick = 0; tick < 6000 && titles.size() < layout.patternCount() * 2; tick++)
            for (var wall : flow.tick(tick).started()) {
                if (titles.size() < 5)
                    check(
                            wall.pattern.gates().stream()
                                    .allMatch(g -> g.height() >= 1.8 && g.width() >= 1),
                            "Easy map opening precedes crouch and precision");
                if (titles.size() < 10)
                    check(
                            wall.pattern.gates().stream().allMatch(g -> g.width() >= 1),
                            "No early precision slits");
                titles.add(wall.pattern.title());
            }
        check(titles.size() == layout.patternCount() * 2, "New map stream keeps spawning");
        for (int start : new int[] {0, layout.patternCount()})
            check(
                    new HashSet<>(titles.subList(start, start + layout.patternCount())).size()
                            == layout.patternCount(),
                    "Each map shuffles a full unique catalog");
    }
}
