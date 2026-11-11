package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.*;

final class HoleWallLayoutCheck {
    static void run() {
        snakeWidths();
        donutDimensions();
        islandWavesAndMaterials();
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
                if (layout == HoleWallLayout.DONUT) {
                    check(!cells.contains("0:0"), "Donut keeps its central hole");
                }
                for (String key : cells) {
                    var xy = key.split(":");
                    Material actual =
                            fixture.world
                                    .getBlockAt(
                                            Integer.parseInt(xy[0]), 80, Integer.parseInt(xy[1]))
                                    .getType();
                    check(
                            actual == Material.SMOOTH_QUARTZ
                                    || actual == Material.YELLOW_TERRACOTTA,
                            "Platforms have no permanent colored edge markers");
                }
                previous = cells;
            }
            if (layout != HoleWallLayout.PLUS) patterns(layout);
        }
        Set<HoleWallLayout> selected = EnumSet.noneOf(HoleWallLayout.class);
        var random = new Random(42);
        for (int i = 0; i < 60; i++) selected.add(HoleWallLayout.choose(random));
        check(selected.size() == 3, "All three layouts can be selected");
    }

    private static void islandWavesAndMaterials() {
        for (var layout : HoleWallLayout.values()) {
            for (int seed = 0; seed < 8; seed++) {
                int islandCount = 0;
                var flow = new HoleWallFlow(2400, new Random(seed), layout);
                for (int tick = 0; tick < 2400; tick++) {
                    var frame = flow.tick(tick);
                    for (var wall : frame.started()) {
                        boolean islands =
                                wall.pattern.gates().stream()
                                        .allMatch(
                                                g -> g.start() >= 5 || g.start() + g.width() <= -4);
                        if (islands) {
                            islandCount++;
                            check(
                                    layout == HoleWallLayout.PLUS && tick >= 1600,
                                    "Island-only walls are exclusive to the final third of plus");
                            check(
                                    wall.moveAt - tick == 20,
                                    "Island walls start moving after one second");
                            for (int x = -4; x <= 4; x++)
                                for (int z = -4; z <= 4; z++)
                                    if (layout.onFloor(x, z, 4, 1)) {
                                        var body =
                                                new BoundingBox(
                                                        x + .2, 81, z + .2, x + .8, 82.8, z + .8);
                                        check(
                                                HoleWallPhysics.collides(
                                                        wall.pattern.cells(),
                                                        wall.direction,
                                                        -10,
                                                        10,
                                                        body,
                                                        body),
                                                "No surviving core floor cell bypasses an island"
                                                        + " wall");
                                    }
                            for (var gate : wall.pattern.gates()) {
                                double u = gate.start() + gate.width() / 2;
                                check(
                                        layout.supports(u, 5.5, 4, 1)
                                                && layout.supports(u, -5.5, 4, 1),
                                        "Island routes land on permanent corner platforms");
                                var body =
                                        wall.direction.dx == 0
                                                ? new BoundingBox(
                                                        u - .3,
                                                        81,
                                                        .2,
                                                        u + .3,
                                                        81 + gate.bodyHeight(),
                                                        .8)
                                                : new BoundingBox(
                                                        .2,
                                                        81,
                                                        u - .3,
                                                        .8,
                                                        81 + gate.bodyHeight(),
                                                        u + .3);
                                check(
                                        !HoleWallPhysics.collides(
                                                wall.pattern.cells(),
                                                wall.direction,
                                                -1,
                                                1,
                                                body,
                                                body),
                                        "Island passage physically fits the player");
                            }
                        }
                    }
                    if (flow.active().stream()
                            .anyMatch(w ->
                                    w.pattern.gates().stream()
                                            .allMatch(g ->
                                                    g.start() >= 5 || g.start() + g.width() <= -4)))
                        check(flow.active().size() == 1, "Island jumps never overlap another wall");
                }
                check(
                        layout != HoleWallLayout.PLUS || islandCount >= 2,
                        "Final third includes repeated mandatory island jumps");
            }
            for (int seed = 0; seed < 8; seed++)
                for (int stage = 1; stage <= layout.patternCount(); stage++) {
                    var p = layout.pattern(stage, 1, new Random(seed));
                    var full =
                            p.cells().stream()
                                    .map(c -> c.block().material())
                                    .filter(m ->
                                            Set.of(
                                                            Material.STONE_BRICKS,
                                                            Material.DARK_PRISMARINE,
                                                            Material.PRISMARINE_BRICKS,
                                                            Material.PURPUR_BLOCK)
                                                    .contains(m))
                                    .findFirst();
                    for (var cell : p.cells())
                        if (cell.block().shape() == HoleWallBlock.Shape.UPPER_SLAB
                                || cell.block().shape() == HoleWallBlock.Shape.LOWER_SLAB) {
                            Material expected =
                                    switch (full.orElseThrow()) {
                                        case STONE_BRICKS -> Material.STONE_BRICK_SLAB;
                                        case DARK_PRISMARINE -> Material.DARK_PRISMARINE_SLAB;
                                        case PRISMARINE_BRICKS -> Material.PRISMARINE_BRICK_SLAB;
                                        case PURPUR_BLOCK -> Material.PURPUR_SLAB;
                                        default -> throw new AssertionError();
                                    };
                            check(
                                    cell.block().material() == expected,
                                    "Slabs match their wall material");
                        }
                }
        }
    }

    private static void snakeWidths() {
        var snake = HoleWallLayout.SNAKE;
        for (int radius : new int[] {8, 6, 4}) {
            int sideWidth = radius == 8 ? 3 : 2;
            int middleWidth = radius == 8 ? 3 : radius == 6 ? 2 : 1;
            int upper = 0, lower = 0, middle = 0, left = 0, right = 0;
            for (int i = -8; i <= 8; i++) {
                if (snake.onFloor(0, i, radius, 1)) {
                    if (i < -2) upper++;
                    else if (i > 2) lower++;
                    else middle++;
                }
                if (snake.onFloor(i, 2, radius, 1)) left++;
                if (snake.onFloor(i, -2, radius, 1)) right++;
            }
            check(
                    upper == sideWidth
                            && lower == sideWidth
                            && left == sideWidth
                            && right == sideWidth,
                    "All snake side tracks and bends narrow to two blocks at the first shrink");
            check(middle == middleWidth, "Snake center narrows from three to two to one block");
        }
    }

    private static void donutDimensions() {
        var donut = HoleWallLayout.DONUT;
        for (int radius : new int[] {8, 6, 4}) {
            int edge = radius / 2 + 2;
            check(donut.onFloor(edge, 0, radius, 1), "Donut retains its circular cardinal rim");
            check(
                    !donut.onFloor(edge + 1, 0, radius, 1),
                    "Donut diameters are 13, 11 and 9 blocks");
            check(
                    !donut.onFloor(edge, 2, radius, 1),
                    "Circular rim curves immediately beyond its short cardinal tip");
            check(donut.onFloor(2, 0, radius, 1), "Donut hole is only three blocks wide");
            check(!donut.onFloor(1, 1, radius, 1), "Three-block central opening stays empty");
            for (int x = -8; x <= 8; x++)
                for (int z = -8; z <= 8; z++) {
                    boolean floor = donut.onFloor(x, z, radius, 1);
                    check(
                            floor == donut.onFloor(-x, z, radius, 1)
                                    && floor == donut.onFloor(z, x, radius, 1),
                            "Donut remains symmetric on both axes and diagonals");
                }
        }
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
