package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;

public final class HoleWallCheck {
    public static void main(String[] args) throws Exception {
        centerRequiresAction();
        directionCoverage();
        openings();
        collisions();
        floor();
        displayLifecycle();
        pushingAndRouting();
        wallStream();
        check(
                PartyGamesState.MODES.contains("hole_in_the_wall"),
                "New mode eligible for party selection");
        var game = new PartyGame((PartyConfig) null);
        check(
                game.modes().contains("hole_in_the_wall")
                        && game.maxPlayers("hole_in_the_wall") == 12,
                "New mode in menu and commands");
        System.out.println(
                "Hole in the Wall openings, continuous collision, platform and registration"
                        + " passed");
    }

    private static void centerRequiresAction() {
        for (int radius : new int[] {1, 2})
            for (int seed = 0; seed < 20; seed++) {
                int mustMove = 0, canJump = 0;
                for (int stage = 1; stage <= 10; stage++) {
                    var pattern = HoleWallPattern.generate(stage, radius, new Random(seed));
                    var standing = new BoundingBox(.2, 81, .2, .8, 82.8, .8);
                    var jumping = standing.clone().shift(0, 1.05, 0);
                    for (var dir : HoleWallPhysics.Direction.values())
                        check(
                                HoleWallPhysics.collides(
                                        pattern.cells(), dir, -2, 2, standing, standing),
                                "Every wall requires action instead of standing still at center;"
                                        + " stage="
                                        + stage);
                    if (HoleWallPhysics.collides(
                            pattern.cells(),
                            HoleWallPhysics.Direction.NORTH,
                            -2,
                            2,
                            jumping,
                            jumping)) mustMove++;
                    else canJump++;
                }
                check(
                        mustMove >= 6 && canJump >= 1,
                        "Most walls force lateral movement, with some timed center jumps");
            }
    }

    private static void directionCoverage() {
        for (int seed = 0; seed < 20; seed++) {
            var flow = new HoleWallFlow(2400, new Random(seed));
            List<HoleWallPhysics.Direction> sequence = new ArrayList<>();
            Set<HoleWallPhysics.Direction> late = EnumSet.noneOf(HoleWallPhysics.Direction.class);
            for (int tick = 0; tick < 2400; tick++)
                for (var wall : flow.tick(tick).started()) {
                    sequence.add(wall.direction);
                    if (tick >= 1600) late.add(wall.direction);
                }
            for (int start = 0; start + 4 <= sequence.size(); start += 4)
                check(
                        new HashSet<>(sequence.subList(start, start + 4)).size() == 4,
                        "Every direction stays in rotation, instead of locking into two sides");
            check(late.size() == 4, "All four sides still appear in the final third");
        }
    }

    private static void openings() {
        Set<List<HoleWallPattern.Cell>> patterns = new HashSet<>();
        boolean jumps = false;
        Set<Integer> sizes = new HashSet<>();
        for (int radius : new int[] {1, 2, 6, 8})
            for (int stage = 1; stage <= 20; stage++)
                for (int seed = 0; seed < 8; seed++) {
                    var pattern = HoleWallPattern.generate(stage, radius, new Random(seed));
                    patterns.add(pattern.cells());
                    sizes.add(pattern.cells().size());
                    check(!pattern.gates().isEmpty(), "Every wall has a passage");
                    check(
                            new HashSet<>(pattern.cells()).size() == pattern.cells().size(),
                            "No duplicate display cells");
                    for (var gate : pattern.gates()) {
                        check(
                                gate.start() >= -radius
                                        && gate.start() + gate.width() <= radius + 1,
                                "Opening stays above current floor");
                        check(
                                gate.bottom() == 0 || gate.bottom() == 1,
                                "Jump never exceeds one block");
                        jumps |= gate.bottom() == 1;
                        for (var dir : HoleWallPhysics.Direction.values()) {
                            double u = gate.start() + .5, y = 81 + gate.bottom() + .01;
                            BoundingBox body =
                                    dir.dx == 0
                                            ? new BoundingBox(u - .3, y, .2, u + .3, y + 1.8, .8)
                                            : new BoundingBox(.2, y, u - .3, .8, y + 1.8, u + .3);
                            check(
                                    !HoleWallPhysics.collides(
                                            pattern.cells(), dir, -1, 1, body, body),
                                    "Actual player fits while wall passes in every direction");
                        }
                    }
                }
        check(
                jumps && patterns.size() > 50 && sizes.size() >= 8,
                "Ten varied obstacle silhouettes, not just rectangular walls with moved holes");
    }

    private static void wallStream() {
        for (int seconds : new int[] {45, 120, 300})
            for (int seed = 0; seed < 12; seed++) {
                var flow = new HoleWallFlow(seconds * 20L, new Random(seed));
                int count = 0, maxActive = 0, oldRadius = 8;
                long warningAt = -1;
                boolean crossing = false;
                for (long tick = 0; tick < seconds * 20L; tick++) {
                    var frame = flow.tick(tick);
                    count += frame.started().size();
                    int expectedRadius =
                            tick >= seconds * 40L / 3 ? 4 : tick >= seconds * 20L / 3 ? 6 : 8;
                    check(
                            frame.radius() == expectedRadius,
                            "Ends disappear exactly at each timed third, even with active walls");
                    check(
                            frame.armRadius() == (tick < seconds * 10L ? 2 : 1),
                            "Arms narrow from five to three blocks exactly halfway");
                    check(
                            frame.warningArmRadius() == (tick < seconds * 10L - 60 ? 2 : 1),
                            "Width change is warned three seconds before halfway");
                    if (frame.warningRadius() < oldRadius && warningAt < 0) warningAt = tick;
                    if (frame.radius() < oldRadius) {
                        check(
                                tick - warningAt >= 60,
                                "Shrink always has at least three seconds of warning");
                        oldRadius = frame.radius();
                        warningAt = -1;
                    }
                    var active = flow.active();
                    maxActive = Math.max(maxActive, active.size());
                    check(active.size() <= 4, "Bounded concurrent display walls");
                    if (tick == 80)
                        check(
                                count >= 2,
                                "Second wall appears within four seconds, before first exits");
                    for (var a : active) {
                        for (var gate : a.pattern.gates())
                            check(
                                    gate.start() >= -frame.armRadius()
                                            && gate.start() + gate.width() <= frame.armRadius() + 1,
                                    "Even existing wall gates fit the floor when the arms narrow");
                        for (var b : active)
                            if (a != b) {
                                int dot =
                                        a.direction.dx * b.direction.dx
                                                + a.direction.dz * b.direction.dz;
                                boolean aOnFloor =
                                        tick >= a.moveAt && Math.abs(a.depth) <= frame.radius() + 2;
                                boolean bOnFloor =
                                        tick >= b.moveAt && Math.abs(b.depth) <= frame.radius() + 2;
                                check(
                                        dot != -1 || !aOnFloor || !bOnFloor,
                                        "Opposing walls never squeeze the playable platform");
                                if (dot == 0 && tick >= a.moveAt && tick >= b.moveAt)
                                    crossing = true;
                                for (var ga : a.pattern.gates())
                                    for (var gb : b.pattern.gates()) {
                                        double feet = Math.max(ga.bottom(), gb.bottom()) + .01;
                                        check(
                                                feet + 1.8
                                                        <= Math.min(
                                                                ga.bottom() + ga.height(),
                                                                gb.bottom() + gb.height()),
                                                "Crossing gates have compatible player-height"
                                                        + " clearance");
                                    }
                            }
                    }
                }
                check(
                        crossing && maxActive >= 3,
                        "Multiple walls move at once from different axes");
                check(count >= seconds / 6, "Wall stream remains frequent through shrinking");
                check(oldRadius == 4, "Continuous stream cannot starve floor shrink");
                flow.clear();
                check(flow.active().isEmpty(), "Round cleanup forgets all wall state");
            }
    }

    private static void collisions() {
        var cells = List.of(new HoleWallPattern.Cell(0, 0), new HoleWallPattern.Cell(0, 1));
        for (var dir : HoleWallPhysics.Direction.values()) {
            var body = new BoundingBox(.2, 81, .2, .8, 82.8, .8);
            check(
                    HoleWallPhysics.collides(cells, dir, -2, 2, body, body),
                    "Fast wall cannot tunnel through player");
            check(
                    !HoleWallPhysics.collides(cells, dir, -4, -3, body, body),
                    "Distant wall does not hit");
            var before = body.clone().shift(-dir.dx * 2, 0, -dir.dz * 2);
            var after = body.clone().shift(dir.dx * 2, 0, dir.dz * 2);
            check(
                    HoleWallPhysics.collides(cells, dir, 0, 0, before, after),
                    "Player cannot move through a stationary solid cell between ticks");
            var jumped = body.clone().shift(0, 2.1, 0);
            check(
                    !HoleWallPhysics.collides(cells, dir, -2, 2, jumped, jumped),
                    "Clearance above an obstacle works");
        }
        check(
                HoleWallPhysics.speed(0, 2400) < HoleWallPhysics.speed(2400, 2400),
                "Walls accelerate");
    }

    private static void floor() {
        var fixture = new PartyArenaCheck.WorldMap();
        var arena = new HoleWallArena(fixture.world);
        arena.build();
        check(
                fixture.blocks.values().stream().filter(m -> m != Material.AIR).count() == 145,
                "Smaller 17 by 17 plus with five-wide arms");
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                check(
                        (fixture.world.getBlockAt(x, 80, z).getType() != Material.AIR)
                                == (Math.abs(x) <= 2 || Math.abs(z) <= 2),
                        "No solid corners outside plus");
        arena.warn(6, 2);
        check(
                fixture.world.getBlockAt(8, 80, 0).getType() == Material.RED_CONCRETE,
                "Doomed arm visibly warns before removal");
        check(
                fixture.world.getBlockAt(6, 80, 0).getType() != Material.RED_CONCRETE,
                "Safe floor stays recognizable");
        check(
                fixture.world.getBlockAt(8, 80, 8).getType() == Material.AIR,
                "Warning cannot create corner blocks");
        arena.shrink(6, 2);
        arena.warn(6, 1);
        check(
                fixture.world.getBlockAt(2, 80, 5).getType() == Material.RED_CONCRETE,
                "Width warning marks the outer arm strip");
        check(
                fixture.world.getBlockAt(1, 80, 5).getType() != Material.RED_CONCRETE,
                "The remaining three-wide strip is safe");
        arena.shrink(6, 1);
        arena.shrink(4, 1);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                check(
                        (fixture.world.getBlockAt(x, 80, z).getType() != Material.AIR)
                                == (Math.abs(x) <= 4
                                        && Math.abs(z) <= 4
                                        && (Math.abs(x) <= 1 || Math.abs(z) <= 1)),
                        "Shrinking preserves plus shape with three-wide arms");
        for (int i = 0; i < 12; i++) {
            var p = arena.spawn(i, 12);
            check(
                    fixture.world.getBlockAt(p.getBlockX(), 80, p.getBlockZ()).getType()
                            != Material.AIR,
                    "Spawn remains over floor");
        }
    }

    private static void pushingAndRouting() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        PartyConfig config =
                (PartyConfig)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(PartyConfig.class);
        Field duration = PartyConfig.class.getDeclaredField("holeWallSeconds");
        duration.setAccessible(true);
        duration.set(config, new PropertyBag("fixture").define("duration", PropertyCodec.INT, 120));
        var context =
                new PartyRoundContext(
                        null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
        var round = PartyRoundFactory.create("hole_in_the_wall", null, config, context);
        check(round instanceof HoleWallSession, "Embedded factory selects the new session");
        check(
                new PartyGame(config).createSession(null, "hole_in_the_wall")
                        instanceof HoleWallSession,
                "Standalone factory selects the same game");
        Method push =
                HoleWallSession.class.getDeclaredMethod(
                        "push",
                        Player.class,
                        HoleWallFlow.Wall.class,
                        BoundingBox.class,
                        double.class);
        push.setAccessible(true);
        UUID playerId = new UUID(0, 1);
        for (var dir : HoleWallPhysics.Direction.values()) {
            var wall =
                    new HoleWallFlow.Wall(1, HoleWallPattern.generate(1, 2, new Random(0)), dir, 0);
            wall.from = 0;
            wall.depth = 0;
            Location[] position = {new Location(null, .5, 81, .5)};
            Vector[] velocity = {new Vector(0, .3, 0)};
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class[] {Player.class},
                                    (o, m, a) ->
                                            switch (m.getName()) {
                                                case "getUniqueId" -> playerId;
                                                case "getLocation" -> position[0].clone();
                                                case "getVelocity" -> velocity[0].clone();
                                                case "getBoundingBox" -> body(position[0]);
                                                case "teleport" -> {
                                                    position[0] = ((Location) a[0]).clone();
                                                    yield true;
                                                }
                                                case "setFallDistance" -> null;
                                                case "setVelocity" -> {
                                                    velocity[0] = ((Vector) a[0]).clone();
                                                    yield null;
                                                }
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected player method: "
                                                                        + m.getName());
                                            });
            push.invoke(round, player, wall, body(position[0]), .2);
            check(
                    velocity[0].getX() * dir.dx + velocity[0].getZ() * dir.dz > 0,
                    "Collision pushes along wall travel");
            check(
                    velocity[0].getY() == .3 && position[0].getY() == 81,
                    "Pushing preserves jump velocity and height");
            check(
                    !HoleWallPhysics.collides(
                            List.of(new HoleWallPattern.Cell(0, 0)),
                            dir,
                            0,
                            0,
                            body(position[0]),
                            body(position[0])),
                    "Player is resolved outside solid wall");
            var behind = new Location(null, .5 - dir.dx * .85, 81, .5 - dir.dz * .85);
            position[0] = new Location(null, .5 - dir.dx * .57, 81, .5 - dir.dz * .57);
            wall.depth = .2;
            check(
                    HoleWallPhysics.collides(
                            List.of(new HoleWallPattern.Cell(0, 0)),
                            dir,
                            0,
                            .2,
                            body(behind),
                            body(position[0])),
                    "Sprinting into rear face is a real collision");
            push.invoke(round, player, wall, body(behind), .2);
            check(
                    position[0].getX() * dir.dx + position[0].getZ() * dir.dz
                            < .2 + (dir.dx + dir.dz) * .5,
                    "Rear contact cannot teleport a player through a solid wall");
            check(
                    !HoleWallPhysics.collides(
                            List.of(new HoleWallPattern.Cell(0, 0)),
                            dir,
                            .2,
                            .2,
                            body(position[0]),
                            body(position[0])),
                    "Rear contact resolves outside trailing face");
            List<HoleWallPattern.Cell> solid = new ArrayList<>();
            for (int u = -3; u <= 3; u++)
                for (int y = 0; y < 2; y++) solid.add(new HoleWallPattern.Cell(u, y));
            var full = new HoleWallPattern(solid, List.of(), net.kyori.adventure.text.Component.empty());
            var first = new HoleWallFlow.Wall(1, full, dir, 0);
            first.from = first.depth = 0;
            var perpendicular =
                    dir.dx == 0 ? HoleWallPhysics.Direction.EAST : HoleWallPhysics.Direction.NORTH;
            var second = new HoleWallFlow.Wall(2, full, perpendicular, 0);
            second.from = second.depth = 0;
            position[0] = new Location(null, .5, 81, .5);
            velocity[0] = new Vector(0, .3, 0);
            check(
                    ((HoleWallSession) round).resolveContacts(player, List.of(first, second), 100),
                    "Crossing walls both participate in actual session collision");
            for (var crossed : List.of(first, second))
                check(
                        !HoleWallPhysics.collides(
                                solid,
                                crossed.direction,
                                0,
                                0,
                                body(position[0]),
                                body(position[0])),
                        "Resolved player remains outside both crossing walls");
            check(
                    velocity[0].getX() != 0 && velocity[0].getZ() != 0 && velocity[0].getY() == .3,
                    "Perpendicular pushes combine without erasing jump or earlier push");
        }
        var fixture = new DisplayWorld(-1);
        Field displaysField = HoleWallSession.class.getDeclaredField("displays");
        displaysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<HoleWallFlow.Wall, HoleWallDisplay> displays =
                (Map<HoleWallFlow.Wall, HoleWallDisplay>) displaysField.get(round);
        for (var dir : HoleWallPhysics.Direction.values()) {
            var pattern = HoleWallPattern.generate(3, 2, new Random(0));
            var wall = new HoleWallFlow.Wall(1, pattern, dir, 0);
            displays.put(wall, new HoleWallDisplay(fixture.world, pattern, dir, 0));
        }
        ((HoleWallSession) round).cleanup();
        ((HoleWallSession) round).cleanup();
        check(
                displays.isEmpty() && fixture.removed.size() == fixture.positions.size(),
                "Aborting concurrent walls removes every display");
    }

    private static BoundingBox body(Location p) {
        return new BoundingBox(
                p.getX() - .3,
                p.getY(),
                p.getZ() - .3,
                p.getX() + .3,
                p.getY() + 1.8,
                p.getZ() + .3);
    }

    private static void displayLifecycle() {
        var pattern = HoleWallPattern.generate(3, 6, new Random(4));
        for (var dir : HoleWallPhysics.Direction.values()) {
            var fixture = new DisplayWorld(-1);
            var wall = new HoleWallDisplay(fixture.world, pattern, dir, -16);
            wall.move(2.5);
            check(
                    fixture.positions.size() == pattern.cells().size(),
                    "Exactly one display per solid cell");
            for (int i = 0; i < fixture.positions.size(); i++) {
                var b = HoleWallPhysics.box(pattern.cells().get(i), dir, 2.5);
                var p = fixture.positions.get(i);
                check(
                        p.getX() == b.getMinX()
                                && p.getY() == b.getMinY()
                                && p.getZ() == b.getMinZ(),
                        "Visible wall matches collision geometry");
            }
            wall.clear();
            wall.clear();
            check(
                    fixture.removed.size() == fixture.positions.size(),
                    "Clearing wall removes every display exactly once");
        }
        var fixture = new DisplayWorld(4);
        try {
            new HoleWallDisplay(fixture.world, pattern, HoleWallPhysics.Direction.NORTH, -16);
            throw new AssertionError("Expected spawn failure");
        } catch (IllegalStateException expected) {
            check(fixture.removed.size() == 4, "Partial spawn failure cleans existing displays");
        }
    }

    private static final class DisplayWorld {
        final List<Location> positions = new ArrayList<>();
        final Set<Integer> removed = new HashSet<>();
        int failAfter;

        DisplayWorld(int failAfter) {
            this.failAfter = failAfter;
        }

        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (o, m, a) -> {
                                    if (!m.getName().equals("spawn"))
                                        throw new AssertionError(
                                                "Unexpected world method: " + m.getName());
                                    if (positions.size() == failAfter)
                                        throw new IllegalStateException("Simulated spawn failure");
                                    int id = positions.size();
                                    positions.add(((Location) a[0]).clone());
                                    // Display appearance needs a server; this fixture tests
                                    // movement and ownership.
                                    return Proxy.newProxyInstance(
                                            BlockDisplay.class.getClassLoader(),
                                            new Class[] {BlockDisplay.class},
                                            (display, method, args) ->
                                                    switch (method.getName()) {
                                                        case "teleport" -> {
                                                            positions.set(
                                                                    id,
                                                                    ((Location) args[0]).clone());
                                                            yield true;
                                                        }
                                                        case "remove" -> {
                                                            check(
                                                                    removed.add(id),
                                                                    "Display removed once");
                                                            yield null;
                                                        }
                                                        default ->
                                                                throw new AssertionError(
                                                                        "Unexpected display method:"
                                                                                + " "
                                                                                + method.getName());
                                                    });
                                });
    }
}
