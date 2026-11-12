package me.petr1furious.potatominigames.games.party;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Fair geometry, bounded rollback, sequential checkpoint timing and real menu/factory integration.
 */
public final class ParkourRaceCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        model();
        sampling();
        neighbouringTasks();
        expandedTasks();
        frostRollback();
        vehicleIsolation();
        navigation();
        geometry();
        swimRoute();
        staircase();
        flightBoundary();
        rollback();
        damage();
        integration();
        System.out.println(
                "Parkour Sprint: shared course, sealed visible lanes, finite vanilla kits,"
                        + " sequential checkpoints, timed ranking and attempt rollback passed");
    }

    private static void model() {
        for (int stages : List.of(7, 8)) model(stages);
    }

    private static void model(int stages) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        ParkourRace race = new ParkourRace(List.of(a, b, c), stages);
        check(
                race.result(Set.of(a, b, c)).places().equals(List.of(List.of(a, b, c))),
                "Starting racers tie");
        check(
                !race.reach(a, 2, 0) && !race.reach(new UUID(9, 9), 1, 0),
                "Cannot skip checkpoints or join a running race");
        race.position(a, 10);
        check(
                race.result(Set.of(a, b)).place(a) == 1 && race.result(Set.of(a, b)).place(b) == 2,
                "Live distance ranks racers within a section");
        race.reset(a);
        check(
                race.result(Set.of(a, b)).places().equals(List.of(List.of(a, b))),
                "Reset discards progress after the last checkpoint");
        check(race.reach(a, 1, 50) && !race.reach(a, 1, 51), "Checkpoint activates once");
        race.position(a, 8);
        race.reset(a);
        check(
                race.checkpoint(a) == 1 && race.result(Set.of(a, b)).place(a) == 1,
                "Reset preserves the reached checkpoint");
        for (int checkpoint = 2; checkpoint <= stages; checkpoint++)
            check(race.reach(a, checkpoint, checkpoint * 100), "Sequential progress");
        check(
                race.finished(a) && race.time(a) == stages * 100L && !race.reset(a),
                "Finish time is elapsed race time, never a reset section timer");
        for (int checkpoint = 1; checkpoint <= stages; checkpoint++)
            race.reach(b, checkpoint, checkpoint * 100);
        check(
                race.result(Set.of(a, b, c)).places().equals(List.of(List.of(a, b), List.of(c))),
                "Equal finish ticks share positions");
        check(
                race.result(Set.of(b, c)).awards().get(b) == 3,
                "Withdrawn players cannot occupy podium places");
        race.close();
        check(!race.reach(c, 1, 700) && !race.reset(c), "No progress or reset after cleanup");
    }

    private static ParkourCourse course(ParkourCourse.Kind... kinds) {
        return layout(List.of(kinds), false);
    }

    private static ParkourCourse layout(List<ParkourCourse.Kind> selected, boolean mirror) {
        // Keep focused fixture sections at their original indices; append neutral tasks.
        var full = new java.util.ArrayList<>(selected);
        for (var kind :
                List.of(
                        ParkourCourse.Kind.PEARL,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.BOAT_ICE,
                        ParkourCourse.Kind.BOAT_WATER,
                        ParkourCourse.Kind.MINECART_RIDE,
                        ParkourCourse.Kind.FROST_WALKER,
                        ParkourCourse.Kind.SOUL_SPEED,
                        ParkourCourse.Kind.SWIM))
            if (full.size() < ParkourCourse.MIN_LENGTH && !full.contains(kind)) full.add(kind);
        return new ParkourCourse(full, mirror);
    }

    private static ParkourCourse buildingCourse() {
        return course(
                ParkourCourse.Kind.BRIDGE,
                ParkourCourse.Kind.WIND_WALL,
                ParkourCourse.Kind.WATER_DROP,
                ParkourCourse.Kind.SCAFFOLD,
                ParkourCourse.Kind.LADDER_CLIMB,
                ParkourCourse.Kind.LADDER_TRAVERSE);
    }

    private static void sampling() {
        Set<ParkourCourse.Kind> seen = new java.util.HashSet<>();
        Set<List<ParkourCourse.Kind>> orders = new java.util.HashSet<>();
        Set<Integer> lengths = new java.util.HashSet<>();
        for (int seed = 0; seed < 256; seed++) {
            ParkourCourse sampled = new ParkourCourse(new Random(seed * 0x9E3779B97F4A7C15L));
            check(
                    sampled.stages() >= 7
                            && sampled.stages() <= 8
                            && sampled.sequence().size() == sampled.stages()
                            && new java.util.HashSet<>(sampled.sequence()).size()
                                    == sampled.stages(),
                    "Each race samples seven or eight tasks without replacement");
            var families =
                    new java.util.EnumMap<ParkourCourse.Family, Integer>(
                            ParkourCourse.Family.class);
            for (var kind : sampled.sequence()) families.merge(kind.family(), 1, Integer::sum);
            check(
                    families.size() >= 4 && families.values().stream().allMatch(n -> n <= 2),
                    "random races combine at least four movement families without overloading one"
                            + " mechanic");
            lengths.add(sampled.stages());
            seen.addAll(sampled.sequence());
            orders.add(sampled.sequence());
            check(
                    sampled.blocks()
                                    .equals(
                                            new ParkourCourse(
                                                            new Random(seed * 0x9E3779B97F4A7C15L))
                                                    .blocks())
                            && sampled.states()
                                    .equals(
                                            new ParkourCourse(
                                                            new Random(seed * 0x9E3779B97F4A7C15L))
                                                    .states()),
                    "All lanes reproduce the same selected order and geometry");
            for (var pos : sampled.blocks().keySet())
                check(
                        pos.y() >= ParkourCourse.BUILD_BOTTOM && pos.y() < ParkourCourse.TOP - 8,
                        "Random vertical profiles fit inside the sealed arena");
            for (int checkpoint = 0; checkpoint <= sampled.stages(); checkpoint++) {
                var island = sampled.island(checkpoint);
                int x = (int) island.centerX();
                check(
                        sampled.blocks().get(new ParkourCourse.Pos(x, island.y(), island.z() + 3))
                                != null,
                        "Every randomized checkpoint has a permanent deck");
                check(
                        !sampled.blocks()
                                        .containsKey(
                                                new ParkourCourse.Pos(
                                                        x, island.y() + 1, island.z() + 3))
                                && !sampled.blocks()
                                        .containsKey(
                                                new ParkourCourse.Pos(
                                                        x, island.y() + 2, island.z() + 3)),
                        "Every randomized checkpoint has a clear spawn body");
            }
        }
        check(
                seen.size() == ParkourCourse.Kind.values().length && seen.size() >= 30,
                "Every original and new building task is eligible");
        check(lengths.equals(Set.of(7, 8)), "Both course lengths are generated");
        check(orders.size() > 100, "The seven/eight-task order varies between games");
        ParkourCourse lateral = buildingCourse();
        var from = lateral.island(5);
        var to = lateral.island(6);
        check(
                Math.abs(to.centerX() - from.centerX()) == 20
                        && to.z() == from.z()
                        && from.width() == 3
                        && to.width() == 3,
                "Ladder traverse moves sideways twenty blocks between narrow ledges");
        check(
                lateral.supplies(5)
                        .equals(
                                List.of(
                                        new ParkourCourse.Supply(Material.CYAN_WOOL, 12),
                                        new ParkourCourse.Supply(Material.LADDER, 12))),
                "The traverse grants exactly twelve blocks and twelve ladders");
        check(
                lateral.distance(5, to.centerX(), to.z() + 3.5) == 20,
                "Sideways distance counts in live race ranking");
    }

    private static void neighbouringTasks() {
        for (var first : ParkourCourse.Kind.values())
            for (var second : ParkourCourse.Kind.values()) {
                if (first == second) continue;
                List<ParkourCourse.Kind> selected =
                        new java.util.ArrayList<>(List.of(first, second));
                for (var kind : ParkourCourse.Kind.values())
                    if (selected.size() < ParkourCourse.MIN_LENGTH && !selected.contains(kind))
                        selected.add(kind);
                for (boolean mirror : List.of(false, true)) {
                    ParkourCourse course = layout(selected, mirror);
                    for (int stage = 0; stage < course.stages(); stage++)
                        if (course.stage(stage) == ParkourCourse.Kind.LADDER_TRAVERSE) {
                            var from = course.island(stage);
                            var to = course.island(stage + 1);
                            for (int x = (int) Math.min(from.centerX(), to.centerX());
                                    x <= Math.max(from.centerX(), to.centerX());
                                    x++)
                                for (int y = from.y() + 1; y <= from.y() + 2; y++)
                                    check(
                                            !course.blocks()
                                                    .containsKey(
                                                            new ParkourCourse.Pos(
                                                                    x, y, from.z() + 3)),
                                            "neighbouring task terrain must not block the lateral"
                                                    + " route");
                        }
                    if (first == ParkourCourse.Kind.LADDER_DROP
                            || first == ParkourCourse.Kind.HONEY_DROP) {
                        var from = course.island(0);
                        var goal = course.island(1);
                        int wall =
                                from.centerX() < ParkourCourse.WIDTH / 2.0
                                        ? 0
                                        : ParkourCourse.WIDTH - 1;
                        for (int y = goal.y(); y <= from.y() + 4; y++) {
                            var anchor = new ParkourCourse.Pos(wall, y, from.z() + 7);
                            check(
                                    (course.blocks().get(anchor) == Material.DEEPSLATE_TILES)
                                            == (y < goal.y() + 3),
                                    "Clutch anchors exist only in the bottom three rows");
                            check(
                                    course.buildPosition(0, anchor) == (y < goal.y() + 3),
                                    "Invisible dividers cannot support a series of upper catches");
                        }
                        check(
                                !course.buildPosition(
                                        0, new ParkourCourse.Pos(wall, goal.y() - 1, from.z() + 7)),
                                "Clutch placement stays at the final catch rather than below it");
                    }
                    if ((first == ParkourCourse.Kind.LADDER_DROP
                                    || first == ParkourCourse.Kind.HONEY_DROP)
                            && second == ParkourCourse.Kind.LADDER_TRAVERSE) {
                        var from = course.island(0);
                        var goal = course.island(1);
                        int wall =
                                from.centerX() < ParkourCourse.WIDTH / 2.0
                                        ? 0
                                        : ParkourCourse.WIDTH - 1;
                        check(
                                course.blocks()
                                                .get(
                                                        new ParkourCourse.Pos(
                                                                wall, goal.y() + 1, goal.z() - 1))
                                        == Material.LIGHT_BLUE_CONCRETE,
                                "clutch wall has a visible terminal stripe before the goal");
                        for (int z = goal.z(); z < goal.z() + 7; z++)
                            check(
                                    !course.blocks()
                                            .containsKey(
                                                    new ParkourCourse.Pos(wall, goal.y() + 1, z)),
                                    "descent wall stops before every row of the next checkpoint");
                    }
                }
            }
    }

    private static void navigation() {
        check(
                ParkourGuide.direction(0, 0, 10).equals("↑")
                        && ParkourGuide.direction(0, -10, 0).equals("→")
                        && ParkourGuide.direction(0, 10, 0).equals("←")
                        && ParkourGuide.direction(0, 0, -10).equals("↓"),
                "navigation arrow follows the player's view rather than a fixed compass");
        check(
                ParkourGuide.direction(-90, 10, 0).equals("↑")
                        && ParkourGuide.direction(360, 0, 10).equals("↑"),
                "yaw wrapping and sideways courses point ahead when aligned");
        check(
                ParkourGuide.direction(45, 0, 0).equals("●"),
                "vertical-only target is not given a misleading horizontal direction");
        for (boolean mirror : List.of(false, true)) {
            ParkourCourse course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.LADDER_TRAVERSE,
                                    ParkourCourse.Kind.BRIDGE,
                                    ParkourCourse.Kind.WIND_WALL,
                                    ParkourCourse.Kind.WATER_DROP,
                                    ParkourCourse.Kind.PEARL,
                                    ParkourCourse.Kind.FLIGHT),
                            mirror);
            UUID id = new UUID(8, 1);
            FakeWorld world = new FakeWorld();
            ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
            Location start = arena.checkpoint(id, 0), target = arena.checkpoint(id, 1);
            check(
                    start.getDirection()
                                    .dot(target.toVector().subtract(start.toVector()).normalize())
                            > .99,
                    "clock/spawn faces the next lateral checkpoint in both mirrored layouts");
        }
        Location rotated = new Location(null, 23.5, 100, 50.5, 90, 0);
        Location frame = ParkourGuide.frameOrigin(rotated, 3);
        check(
                frame.getYaw() == 0 && frame.getX() == 22 && frame.getZ() == 47,
                "narrow goal overlay remains aligned when the following task goes sideways");
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (double rise : new double[] {-44, 0, 64}) {
                var message =
                        me.petr1furious.potatominigames.i18n.I18n.tr(
                                "party.rounds.parkour_race.navigation",
                                "↑",
                                2,
                                7,
                                50,
                                ParkourGuide.height(rise));
                for (var locale :
                        List.of(java.util.Locale.ENGLISH, java.util.Locale.forLanguageTag("ru")))
                    check(
                            !me.petr1furious.potatominigames.i18n.I18n.plain(message, locale)
                                    .contains("potatominigames."),
                            "navigation and vertical hints render in both languages");
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void expandedTasks() {
        var course =
                course(
                        ParkourCourse.Kind.LEAPING_PILLARS,
                        ParkourCourse.Kind.RIPTIDE_LAUNCH,
                        ParkourCourse.Kind.BOAT_WATER,
                        ParkourCourse.Kind.MINECART_RIDE,
                        ParkourCourse.Kind.SLOW_FALLING,
                        ParkourCourse.Kind.WIND_HOPS);
        for (int stage = 0; stage < course.stages(); stage++) {
            var from = course.island(stage);
            var to = course.island(stage + 1);
            check(
                    course.attempt(stage, to.centerX(), to.y() + 1, to.z() + 3.5),
                    "every new task goal remains in the current attempt, not beyond its boundary");
            if (course.stage(stage) == ParkourCourse.Kind.BOAT_WATER) {
                for (int row = 0; row < 3; row++) {
                    int z = from.z() + 14 + row * 8;
                    int open = 0;
                    for (int x = 0; x < ParkourCourse.WIDTH; x++)
                        if (!course.blocks().containsKey(new ParkourCourse.Pos(x, from.y() + 2, z)))
                            open++;
                    check(open == 5, "boat gates have five clear columns for hull and passenger");
                }
            }
        }
        check(!course.states().isEmpty(), "rail task includes native block states");
        for (var state : course.states().entrySet()) {
            check(
                    course.blocks().get(state.getKey()) == Material.POWERED_RAIL,
                    "native rail properties only apply to rail blocks");
            check(
                    state.getValue().contains("shape=north_south")
                            && state.getValue().contains("powered=true"),
                    "cart rails remain straight and powered on initial construction");
        }
        var leap = course.island(0);
        check(
                course.blocks()
                                .get(
                                        new ParkourCourse.Pos(
                                                (int) leap.centerX(), leap.y() + 2, leap.z() + 9))
                        == Material.LIGHT_BLUE_CONCRETE,
                "leaping task has a two-block first rise rather than another ordinary bridge");
        var trident = course.supplies(1).getFirst();
        check(
                trident.material() == Material.TRIDENT
                        && "riptide".equals(trident.enchantment())
                        && trident.level() == 3,
                "water launch receives a real vanilla Riptide trident");
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (var kind : ParkourCourse.Kind.values())
                for (String suffix : List.of(".name", ".hint")) {
                    var text = me.petr1furious.potatominigames.i18n.I18n.tr(kind.key() + suffix);
                    for (var locale :
                            List.of(
                                    java.util.Locale.ENGLISH,
                                    java.util.Locale.forLanguageTag("ru")))
                        check(
                                !me.petr1furious.potatominigames.i18n.I18n.plain(text, locale)
                                        .contains("potatominigames."),
                                "every task name and mechanic hint is localized");
                }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void frostRollback() {
        var course =
                course(
                        ParkourCourse.Kind.FROST_WALKER,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.WIND_WALL,
                        ParkourCourse.Kind.WATER_DROP,
                        ParkourCourse.Kind.SCAFFOLD,
                        ParkourCourse.Kind.PEARL);
        UUID owner = new UUID(10, 1), other = new UUID(10, 2);
        FakeWorld world = new FakeWorld();
        ParkourArena arena = new ParkourArena(world.world, List.of(owner, other), course);
        while (!arena.buildSlice(4096)) {}
        var from = course.island(0);
        Block water = world.block((int) from.centerX(), from.y(), from.z() + 9);
        check(
                arena.rememberFrost(owner, 0, water),
                "frost walker can journal protected source water in its own section");
        check(
                !arena.rememberFrost(other, 0, water) && !arena.rememberFrost(owner, 1, water),
                "freeze permission does not cross lanes or tasks");
        water.setType(Material.FROSTED_ICE, false);
        check(
                !arena.mutable(owner, 0, water),
                "temporary frost does not make protected lake terrain mineable");
        arena.reset(owner);
        check(
                water.getType() == Material.WATER && !arena.changed(owner, water),
                "clock/checkpoint restores frozen terrain to the original water");
    }

    @SuppressWarnings("unchecked")
    private static void vehicleIsolation() throws ReflectiveOperationException {
        UUID owner = new UUID(11, 1), other = new UUID(11, 2);
        FakeWorld world = new FakeWorld();
        var course =
                course(
                        ParkourCourse.Kind.BOAT_ICE,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.WIND_WALL,
                        ParkourCourse.Kind.WATER_DROP,
                        ParkourCourse.Kind.SCAFFOLD,
                        ParkourCourse.Kind.PEARL);
        ParkourArena arena = new ParkourArena(world.world, List.of(owner, other), course);
        boolean[] ejected = {false}, removed = {false}, stopped = {false};
        var boat =
                (org.bukkit.entity.Boat)
                        Proxy.newProxyInstance(
                                org.bukkit.entity.Boat.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Boat.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getWorld" -> world.world;
                                            case "isValid" -> !removed[0];
                                            case "eject" -> {
                                                ejected[0] = true;
                                                yield true;
                                            }
                                            case "remove" -> {
                                                removed[0] = true;
                                                yield null;
                                            }
                                            case "setVelocity" -> {
                                                stopped[0] = true;
                                                yield null;
                                            }
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected vehicle call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(owner, other));
        session.connected.addAll(List.of(owner, other));
        field(PartySurvivalSession.class, "playing").set(session, true);
        field(ParkourRaceSession.class, "course").set(session, course);
        field(ParkourRaceSession.class, "arena").set(session, arena);
        field(ParkourRaceSession.class, "race")
                .set(session, new ParkourRace(List.of(owner, other), course.stages()));
        ((Map<UUID, Set<org.bukkit.entity.Entity>>)
                        field(ParkourRaceSession.class, "entities").get(session))
                .put(owner, new java.util.HashSet<>(Set.of(boat)));
        for (UUID id : List.of(owner, other)) {
            Player p =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getWorld" -> world.world;
                                                case "getGameMode" -> GameMode.SURVIVAL;
                                                case "isDead" -> false;
                                                default ->
                                                        throw new AssertionError(
                                                                "Unexpected vehicle passenger call:"
                                                                        + " "
                                                                        + method.getName());
                                            });
            var enter = new org.bukkit.event.vehicle.VehicleEnterEvent(boat, p);
            session.vehicleEnter(enter);
            check(
                    enter.isCancelled() == !id.equals(owner),
                    "vehicle accepts its racing owner and rejects a rival passenger");
        }
        var move =
                new org.bukkit.event.vehicle.VehicleMoveEvent(
                        boat, arena.checkpoint(owner, 0), arena.checkpoint(other, 0));
        session.vehicleMove(move);
        check(
                stopped[0]
                        && ((Set<UUID>) field(ParkourRaceSession.class, "failed").get(session))
                                .contains(owner),
                "vehicle motion outside the owner's attempt is stopped and queues a reset");
        var clear = ParkourRaceSession.class.getDeclaredMethod("clearEntities", UUID.class);
        clear.setAccessible(true);
        clear.invoke(session, owner);
        check(
                ejected[0] && removed[0],
                "attempt cleanup ejects passengers before removing their vehicle");
    }

    private static void geometry() {
        ParkourCourse course = buildingCourse();
        for (int stage = 0; stage < course.stages(); stage++) {
            var from = course.island(stage);
            var to = course.island(stage + 1);
            List<ParkourCourse.Supply> supplies = course.supplies(stage);
            check(supplies.size() >= 1 && supplies.size() <= 2, "Kits remain task-specific");
            for (var item : supplies)
                check(item.count() > 0 && item.count() <= 64, "Finite vanilla stacks");
            check(
                    course.attempt(stage, from.centerX(), from.y() + 1, from.z() + 3.5),
                    "Every spawn is inside its active task");
            check(
                    !course.attempt(stage, ParkourCourse.WIDTH, from.y() + 1, from.z()),
                    "Neighbour lane excluded");
            check(
                    !course.attempt(stage, to.centerX(), to.y() + 1, to.z() + 7),
                    "Future section excluded");
        }
        check(
                course.supplies(2)
                        .equals(List.of(new ParkourCourse.Supply(Material.WATER_BUCKET, 1))),
                "Water drops have no bypass kit");
        FakeWorld world = new FakeWorld();
        UUID a = new UUID(1, 1), b = new UUID(1, 2);
        ParkourArena arena = new ParkourArena(world.world, List.of(a, b), course);
        var start = course.island(0);
        check(
                world.type((int) start.centerX(), start.y(), 3) == Material.GOLD_BLOCK
                        && world.type((int) start.centerX() + ParkourCourse.PITCH, start.y(), 3)
                                == Material.GOLD_BLOCK,
                "Spawn islands precede sliced construction");
        boolean ready = false;
        while (!ready) {
            int before = world.writes;
            ready = arena.buildSlice(211);
            check(world.writes - before <= 211, "Construction respects per-tick budget");
        }
        for (var entry : course.blocks().entrySet()) {
            var pos = entry.getKey();
            check(
                    world.type(pos.x(), pos.y(), pos.z()) == entry.getValue()
                            && world.type(pos.x() + ParkourCourse.PITCH, pos.y(), pos.z())
                                    == entry.getValue(),
                    "Lanes are identical");
        }
        for (int y = ParkourCourse.BOTTOM; y <= ParkourCourse.TOP; y++)
            for (int z = -1; z <= course.end(); z++)
                check(
                        world.type(ParkourCourse.WIDTH, y, z) == Material.BARRIER,
                        "Continuous invisible divider");
        check(
                world.type(3, ParkourCourse.TOP, 12) == Material.BARRIER
                        && world.type(3, ParkourCourse.BOTTOM, 12) == Material.BARRIER
                        && world.type(3, 70, -1) == Material.BARRIER
                        && world.type(3, 70, course.end()) == Material.BARRIER,
                "Shell prevents bypass above, below or around lanes");
        check(
                arena.owner(new Location(world.world, ParkourCourse.WIDTH + .5, 70, 3)) == null
                        && arena.owner(arena.checkpoint(b, 0)).equals(b),
                "Divider cells have no owner");
        check(
                !arena.mutable(a, 0, world.block((int) start.centerX(), start.y(), 3)),
                "Course floor is protected");
        ParkourCourse extras =
                course(
                        ParkourCourse.Kind.SWIM,
                        ParkourCourse.Kind.SLIME_BOUNCE,
                        ParkourCourse.Kind.BLOCK_ASCENT,
                        ParkourCourse.Kind.POT_ASCENT,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.PEARL);
        var swim = extras.island(0);
        int center = (int) swim.centerX();
        check(
                extras.blocks().get(new ParkourCourse.Pos(center, swim.y() + 1, swim.z() + 8))
                                == Material.WATER
                        && extras.blocks()
                                        .get(
                                                new ParkourCourse.Pos(
                                                        center, swim.y() + 2, swim.z() + 8))
                                == Material.DEEPSLATE_TILES,
                "Swim geometry follows its sampled index");
        var bounce = extras.island(1);
        check(
                extras.blocks()
                                .get(
                                        new ParkourCourse.Pos(
                                                (int) bounce.centerX(),
                                                bounce.y() - 36,
                                                bounce.z() + 18))
                        == Material.LIGHT_BLUE_CONCRETE,
                "Rebound pad follows the sampled source island");
    }

    private static void swimRoute() {
        for (boolean mirror : List.of(false, true)) {
            var course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.BRIDGE,
                                    ParkourCourse.Kind.WIND_WALL,
                                    ParkourCourse.Kind.WATER_DROP,
                                    ParkourCourse.Kind.SWIM,
                                    ParkourCourse.Kind.BLOCK_ASCENT,
                                    ParkourCourse.Kind.PEARL),
                            mirror);
            var from = course.island(3);
            int center = (int) from.centerX(), start = from.z() + 7, end = course.island(4).z() - 1;
            var pending = new java.util.ArrayDeque<ParkourCourse.Pos>();
            var seen = new java.util.HashSet<ParkourCourse.Pos>();
            pending.add(new ParkourCourse.Pos(center, from.y() + 1, start));
            while (!pending.isEmpty()) {
                var pos = pending.removeFirst();
                if (pos.z() < start
                        || pos.z() > end
                        || !seen.add(pos)
                        || course.blocks().get(pos) != Material.WATER) continue;
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    pending.add(
                            new ParkourCourse.Pos(pos.x() + step[0], pos.y(), pos.z() + step[1]));
            }
            check(
                    seen.contains(new ParkourCourse.Pos(center, from.y() + 1, end)),
                    "Swimmers retain a connected route through every baffle in both lane layouts");
            int gates = 0, lastOpening = 0;
            for (int z = start; z <= end; z++) {
                int blocked = 0, opening = 0;
                for (int x = center - 1; x <= center + 1; x++)
                    if (course.blocks().get(new ParkourCourse.Pos(x, from.y() + 1, z))
                            == Material.LIGHT_BLUE_CONCRETE) blocked++;
                    else opening = x;
                if (blocked == 0) continue;
                check(
                        blocked == 2
                                && Math.abs(opening - center) == 1
                                && (gates == 0 || lastOpening != opening),
                        "Each baffle leaves one full-width water cell, alternating left and right");
                lastOpening = opening;
                gates++;
            }
            check(gates == 4, "The tunnel requires four swimming detours");
        }
    }

    private static void staircase() {
        for (boolean mirror : List.of(false, true)) {
            ParkourCourse course =
                    layout(
                            List.of(
                                    ParkourCourse.Kind.BLOCK_ASCENT,
                                    ParkourCourse.Kind.POT_ASCENT,
                                    ParkourCourse.Kind.LADDER_TRAVERSE,
                                    ParkourCourse.Kind.SWIM,
                                    ParkourCourse.Kind.LADDER_DROP,
                                    ParkourCourse.Kind.PEARL),
                            mirror);
            var from = course.island(1);
            var to = course.island(2);
            int center = (int) from.centerX();
            check(
                    to.z() - from.z() - 7 == 14 && to.y() - from.y() == 14,
                    "Flower-pot staircase combines forward travel with a full-height ascent");
            check(
                    course.supplies(1)
                            .equals(
                                    List.of(
                                            new ParkourCourse.Supply(Material.CYAN_WOOL, 16),
                                            new ParkourCourse.Supply(Material.FLOWER_POT, 18))),
                    "The pot task grants small flower pots and a limited block budget");
            check(
                    course.recoverMaterials(0) && !course.recoverMaterials(1),
                    "Pot supports cannot be recycled into an unlimited blocks-only route");
            UUID id = new UUID(7, 1);
            FakeWorld world = new FakeWorld();
            ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
            while (!arena.buildSlice(4096)) {}
            for (int step = 1; step <= 14; step++) {
                Block pot = world.block(center, from.y() + step, from.z() + 5 + step);
                Block wool = world.block(center, from.y() + step, from.z() + 6 + step);
                check(
                        world.type(center, from.y() + step - 1, from.z() + 5 + step)
                                == (step == 1 ? Material.LIGHT_BLUE_CONCRETE : Material.CYAN_WOOL),
                        "Each small pot has the previous stair block beneath it");
                check(
                        arena.remember(id, 1, pot, pot.getBlockData()),
                        "Native pot placements fit the active section, including a narrow goal");
                pot.setType(Material.FLOWER_POT, false);
                check(
                        arena.remember(id, 1, wool, wool.getBlockData()),
                        "The adjacent diagonal wool block fits without modifying course geometry");
                wool.setType(Material.CYAN_WOOL, false);
            }
            check(
                    !arena.mutable(id, 1, world.block(center + 2, from.y() + 1, from.z() + 6))
                            && !arena.mutable(
                                    id, 1, world.block(center - 2, from.y() + 1, from.z() + 6)),
                    "Placement cannot use side barriers or walls as free staircase anchors");
            check(
                    world.type(center, to.y(), to.z()) == Material.LIGHT_BLUE_CONCRETE,
                    "The destination marks the permitted central construction columns");
            arena.reset(id);
            check(
                    world.type(center, to.y(), to.z() - 1) == Material.AIR,
                    "Clock rollback removes the final pot stair before refreshing resources");
        }
    }

    private static void rollback() {
        UUID id = new UUID(2, 1);
        FakeWorld world = new FakeWorld();
        ParkourCourse course = buildingCourse();
        ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
        while (!arena.buildSlice(4096)) {}
        Block block = world.block(3, course.island(0).y() + 2, 12);
        check(arena.remember(id, 0, block, block.getBlockData()), "Record before placement");
        block.setType(Material.CYAN_WOOL, false);
        arena.remember(id, 0, block, block.getBlockData());
        block.setType(Material.WATER, false);
        arena.reset(id);
        check(
                block.getType() == Material.AIR && !arena.changed(id, block),
                "Repeated changes restore the original baseline");
        arena.remember(id, 0, block, block.getBlockData());
        block.setType(Material.CYAN_WOOL, false);
        arena.reset(id);
        Block next = world.block(3, course.island(1).y() + 2, course.island(1).z() + 4);
        arena.remember(id, 1, next, next.getBlockData());
        next.setType(Material.LADDER, false);
        arena.reset(id);
        check(
                block.getType() == Material.AIR && next.getType() == Material.AIR,
                "Task transition cannot carry blocks or fluids");
        int accepted = 0;
        for (int y = 220; y < 240; y++)
            for (int x = 0; x < ParkourCourse.WIDTH; x++)
                for (int z = 0; z < 32; z++) {
                    Block at = world.block(x, y, z);
                    if (arena.remember(id, 0, at, at.getBlockData())) accepted++;
                }
        check(accepted == ParkourArena.MAX_CHANGES, "Journal remains bounded in wider lanes");
        arena.reset(id);
        check(arena.remember(id, 0, block, block.getBlockData()), "Reset frees the journal budget");
        arena.clear();
    }

    @SuppressWarnings("unchecked")
    private static void flightBoundary() throws ReflectiveOperationException {
        UUID id = new UUID(3, 1);
        FakeWorld world = new FakeWorld();
        ParkourCourse course =
                course(
                        ParkourCourse.Kind.FLIGHT,
                        ParkourCourse.Kind.LADDER_TRAVERSE,
                        ParkourCourse.Kind.BRIDGE,
                        ParkourCourse.Kind.WATER_DROP,
                        ParkourCourse.Kind.SCAFFOLD,
                        ParkourCourse.Kind.PEARL);
        ParkourArena arena = new ParkourArena(world.world, List.of(id), course);
        Location[] at = {arena.checkpoint(id, 0)};
        boolean[] grounded = {false}, gliding = {true};
        Player p =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getWorld" -> world.world;
                                            case "getLocation" -> at[0].clone();
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "isDead" -> false;
                                            case "isOnGround" -> grounded[0];
                                            case "isGliding" -> gliding[0];
                                            case "setGliding" -> {
                                                gliding[0] = (boolean) args[0];
                                                yield null;
                                            }
                                            case "setVelocity" -> null;
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected flight call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(id));
        session.connected.add(id);
        field(PartySurvivalSession.class, "playing").set(session, true);
        ParkourRace race = new ParkourRace(List.of(id), course.stages());
        field(ParkourRaceSession.class, "course").set(session, course);
        field(ParkourRaceSession.class, "arena").set(session, arena);
        field(ParkourRaceSession.class, "race").set(session, race);
        Set<UUID> failed = (Set<UUID>) field(ParkourRaceSession.class, "failed").get(session);
        Set<UUID> skipped = (Set<UUID>) field(ParkourRaceSession.class, "skipped").get(session);
        var goal = course.island(1);
        var through =
                new org.bukkit.event.player.PlayerMoveEvent(
                        p,
                        at[0],
                        new Location(world.world, goal.centerX(), goal.y() + 15, goal.z() + 8));
        session.onPlayerMove(through);
        check(
                through.isCancelled()
                        && !gliding[0]
                        && failed.contains(id)
                        && race.checkpoint(id) == 0,
                "Rocket-speed movement past an unlanded checkpoint is rejected immediately");
        failed.clear();
        skipped.clear();
        gliding[0] = true;
        var future = arena.checkpoint(id, 2);
        var sideways = new org.bukkit.event.player.PlayerMoveEvent(p, at[0], future);
        session.onPlayerMove(sideways);
        check(
                sideways.isCancelled() && failed.contains(id),
                "Cannot fly sideways onto a future checkpoint in the same Z row");
        failed.clear();
        skipped.clear();
        at[0] = arena.checkpoint(id, 1);
        world.block(at[0].getBlockX(), goal.y(), at[0].getBlockZ())
                .setType(Material.GOLD_BLOCK, false);
        grounded[0] = true;
        gliding[0] = true;
        check(
                !arena.landed(p, 1),
                "A gliding player cannot claim the checkpoint even with a ground flag");
        grounded[0] = false;
        gliding[0] = false;
        check(!arena.landed(p, 1), "A fly-by at deck height is not a landing");
        grounded[0] = true;
        check(arena.landed(p, 1), "Real grounded landing is accepted");
        race.reach(id, 1, 10);
        var stale = new org.bukkit.event.entity.EntityToggleGlideEvent(p, true);
        session.glide(stale);
        check(stale.isCancelled(), "Old elytra cannot restart gliding on the next non-flight task");
        check(
                arena.attempt(id, 1, future),
                "Lateral checkpoint becomes accessible only after the preceding landing");
    }

    private static java.lang.reflect.Field field(Class<?> type, String name)
            throws ReflectiveOperationException {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static void damage() throws ReflectiveOperationException {
        UUID id = new UUID(4, 1);
        FakeWorld world = new FakeWorld();
        Player p =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getWorld" -> world.world;
                                            case "getHealth" -> 20.0;
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "isDead" -> false;
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected clutch player call: "
                                                                    + method.getName());
                                        });
        ParkourRaceSession session =
                new ParkourRaceSession(null, null, PartyRoundContext.standalone(null));
        session.world = world.world;
        session.standings = new SurvivalStandings(List.of(id, new UUID(4, 2)));
        session.connected.add(id);
        var playing = PartySurvivalSession.class.getDeclaredField("playing");
        playing.setAccessible(true);
        playing.set(session, true);
        var raceField = ParkourRaceSession.class.getDeclaredField("race");
        raceField.setAccessible(true);
        ParkourRace race = new ParkourRace(List.of(id), ParkourCourse.MIN_LENGTH);
        race.reach(id, 1, 1);
        race.reach(id, 2, 2);
        raceField.set(session, race);
        var failedField = ParkourRaceSession.class.getDeclaredField("failed");
        failedField.setAccessible(true);
        Set<UUID> failures = (Set<UUID>) failedField.get(session);
        EntityDamageEvent lethal =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, null, 41);
        session.onEntityDamage(lethal);
        check(
                lethal.isCancelled() && failures.contains(id) && race.checkpoint(id) == 2,
                "Failed water drops queue a reset without granting the destination checkpoint or"
                        + " killing the player");
        failures.clear();
        EntityDamageEvent cushioned =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, null, 4);
        session.onEntityDamage(cushioned);
        check(
                !cushioned.isCancelled() && failures.isEmpty(),
                "Native nonlethal landing mitigation remains meaningful");
        EntityDamageEvent collision =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FLY_INTO_WALL, null, 25);
        session.onEntityDamage(collision);
        check(
                collision.isCancelled() && failures.contains(id),
                "Lethal flight collisions also restart the attempt");
        EntityDamageEvent pvp =
                new EntityDamageEvent(p, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, 5);
        session.onEntityDamage(pvp);
        check(pvp.isCancelled(), "Combat damage cannot interfere with the traversal task");
    }

    private static void integration() {
        PartyGame game = new PartyGame((PartyConfig) null);
        check(
                game.modes().contains("parkour_race")
                        && game.maxPlayers("parkour_race") == 12
                        && game.icon("parkour_race") == Material.CLOCK,
                "Standalone menu mode is registered");
        check(
                game.createSession(null, "parkour_race") instanceof ParkourRaceSession,
                "Queue creates the right session");
        PartyRoundContext context =
                new PartyRoundContext(
                        null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
        check(
                PartyRoundFactory.create("parkour_race", null, null, context)
                                instanceof ParkourRaceSession
                        && PartyGamesState.MODES.contains("parkour_race"),
                "Embedded and standalone share the race implementation");
        try (var stream = ParkourRaceCheck.class.getResourceAsStream("/config.yml")) {
            var queues =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
            check(
                    queues.getBoolean("queues.party.parkour_race.enabled")
                            && queues.getInt("queues.party.parkour_race.minPlayers") == 2
                            && queues.getInt("queues.party.parkour_race.maxPlayers") == 12,
                    "Default queue is enabled");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        try (var stream = ParkourRaceCheck.class.getResourceAsStream("/games/party.yml")) {
            var config =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(stream, StandardCharsets.UTF_8));
            check(
                    config.getInt("parkourRace.durationSeconds") == 240
                            && List.of(config.getString("partyGames.modes").split(","))
                                    .contains("parkour_race"),
                    "Four-minute default and mixed-match pool are configured");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static BlockData data(Material material) {
        return (BlockData)
                Proxy.newProxyInstance(
                        BlockData.class.getClassLoader(),
                        new Class<?>[] {BlockData.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getMaterial" -> material;
                                    case "clone" -> data(material);
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected block data call: "
                                                            + method.getName());
                                });
    }

    private static final class FakeWorld {
        final Map<ParkourCourse.Pos, BlockData> blocks = new HashMap<>();
        int writes;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBlockAt" -> {
                                                if (args[0] instanceof Location at)
                                                    yield block(
                                                            at.getBlockX(),
                                                            at.getBlockY(),
                                                            at.getBlockZ());
                                                yield block(
                                                        (int) args[0],
                                                        (int) args[1],
                                                        (int) args[2]);
                                            }
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "Unexpected world call: "
                                                                    + method.getName());
                                        });

        Material type(int x, int y, int z) {
            var value = blocks.get(new ParkourCourse.Pos(x, y, z));
            return value == null ? Material.AIR : value.getMaterial();
        }

        Block block(int x, int y, int z) {
            var pos = new ParkourCourse.Pos(x, y, z);
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        case "getWorld" -> world;
                                        case "getType" -> type(x, y, z);
                                        case "isPassable" ->
                                                type(x, y, z) == Material.AIR
                                                        || type(x, y, z) == Material.WATER;
                                        case "getLocation" -> new Location(world, x, y, z);
                                        case "getBlockData" ->
                                                blocks.getOrDefault(pos, data(Material.AIR));
                                        case "setType" -> {
                                            blocks.put(pos, data((Material) args[0]));
                                            writes++;
                                            yield null;
                                        }
                                        case "setBlockData" -> {
                                            blocks.put(pos, (BlockData) args[0]);
                                            writes++;
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected block call: "
                                                                + method.getName());
                                    });
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
