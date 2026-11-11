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
        geometry();
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
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        ParkourRace race = new ParkourRace(List.of(a, b, c), ParkourCourse.LENGTH);
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
        for (int checkpoint = 2; checkpoint <= ParkourCourse.LENGTH; checkpoint++)
            check(race.reach(a, checkpoint, checkpoint * 100), "Sequential progress");
        check(
                race.finished(a) && race.time(a) == ParkourCourse.LENGTH * 100L && !race.reset(a),
                "Finish time is elapsed race time, never a reset section timer");
        for (int checkpoint = 1; checkpoint <= ParkourCourse.LENGTH; checkpoint++)
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
        return new ParkourCourse(List.of(kinds), false);
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
        for (int seed = 0; seed < 128; seed++) {
            ParkourCourse sampled = new ParkourCourse(new Random(seed));
            check(
                    sampled.stages() == 6
                            && sampled.sequence().size() == 6
                            && new java.util.HashSet<>(sampled.sequence()).size() == 6,
                    "Each race samples exactly six tasks without replacement");
            seen.addAll(sampled.sequence());
            orders.add(sampled.sequence());
            check(
                    sampled.blocks().equals(new ParkourCourse(new Random(seed)).blocks()),
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
                seen.size() == ParkourCourse.Kind.values().length && seen.size() == 15,
                "Every original and new building task is eligible");
        check(orders.size() > 100, "The six-task order varies between games");
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
                extras.blocks().get(new ParkourCourse.Pos(center, swim.y() + 1, swim.z() + 10))
                                == Material.WATER
                        && extras.blocks()
                                        .get(
                                                new ParkourCourse.Pos(
                                                        center, swim.y() + 2, swim.z() + 10))
                                == Material.SMOOTH_QUARTZ,
                "Swim geometry follows its sampled index");
        var bounce = extras.island(1);
        check(
                extras.blocks()
                                .get(
                                        new ParkourCourse.Pos(
                                                (int) bounce.centerX(),
                                                bounce.y() - 36,
                                                bounce.z() + 18))
                        == Material.LIME_CONCRETE,
                "Rebound pad follows the sampled source island");
    }

    private static void staircase() {
        for (boolean mirror : List.of(false, true)) {
            ParkourCourse course =
                    new ParkourCourse(
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
        ParkourRace race = new ParkourRace(List.of(id), ParkourCourse.LENGTH);
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
