package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import me.petr1furious.potatominigames.core.game.EndReason;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.*;
import java.util.*;

/** Calls real event handlers with server-free fixtures; no copy of their permission logic. */
public final class PartyInteractionCheck {
    private static final UUID ID = new UUID(0, 1);
    private static final Player PLAYER =
            (Player)
                    Proxy.newProxyInstance(
                            Player.class.getClassLoader(),
                            new Class[] {Player.class},
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> ID;
                                        case "getName" -> "Test";
                                        case "isDead" -> false;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected player mutation: "
                                                                + m.getName());
                                    });

    public static void main(String[] args) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        for (Checked test :
                List.<Checked>of(
                        PartyInteractionCheck::toolMining,
                        PartyInteractionCheck::memoryMining,
                        PartyInteractionCheck::armsReconnect,
                        PartyInteractionCheck::cancelRecovery,
                        PartyInteractionCheck::spectatorTeleport,
                        () -> teardownFailure(false),
                        () -> teardownFailure(true))) {
            try {
                test.run();
            } catch (Throwable failure) {
                failures.add(failure);
                failure.printStackTrace();
            }
        }
        check(failures.isEmpty(), "Interaction/lifecycle regressions: " + failures.size());
        System.out.println(
                "Party mining permissions, reconnect exclusion and delayed recovery cancellation"
                        + " passed");
    }

    private static void toolMining() throws Exception {
        var world = new PartyArenaCheck.WorldMap();
        var session = fixture(ToolRaceSession.class);
        set(session, "arena", new ToolRaceArena(world.world, List.of(ID)));
        var track = new ToolRaceTrack(List.of(ID), ToolRaceTrack.generate(new Random(1)));
        set(session, "track", track);
        var hit = interact(Action.LEFT_CLICK_BLOCK, world.block(0, 80, 0));
        session.onPlayerInteract(hit);
        check(!hit.isCancelled(), "Tool race must allow starting a break on the current block");
        hit = interact(Action.LEFT_CLICK_BLOCK, world.block(0, 79, 0));
        session.onPlayerInteract(hit);
        check(hit.isCancelled(), "Cannot skip the current block");
        hit = interact(Action.RIGHT_CLICK_BLOCK, world.block(0, 80, 0));
        session.onPlayerInteract(hit);
        check(hit.isCancelled(), "Tool right-click cannot alter the course");
        var broken = new BlockBreakEvent(world.block(0, 80, 0), PLAYER);
        session.onBlockBreak(broken);
        check(
                !broken.isCancelled() && !broken.isDropItems() && track.progress(ID) == 1,
                "Allowed interaction reaches a real break with one progress step and no drops");
        broken = new BlockBreakEvent(world.block(0, 80, 0), PLAYER);
        session.onBlockBreak(broken);
        check(
                broken.isCancelled() && track.progress(ID) == 1,
                "Repeated break cannot skip a block");
        set(session, "playing", false);
        hit = interact(Action.LEFT_CLICK_BLOCK, world.block(0, 79, 0));
        session.onPlayerInteract(hit);
        check(hit.isCancelled(), "Countdown protects the course");
    }

    private static void memoryMining() throws Exception {
        var world = new PartyArenaCheck.WorldMap();
        var session = fixture(BuildMemorySession.class);
        set(session, "arena", new BuildMemoryArena(world.world, List.of(ID)));
        phase(session, "BUILD");
        var hit = interact(Action.LEFT_CLICK_BLOCK, world.block(16, 81, 0));
        session.onPlayerInteract(hit);
        check(!hit.isCancelled(), "Memory must let players break misplaced blocks");
        hit = interact(Action.LEFT_CLICK_BLOCK, world.block(16, 80, 0));
        session.onPlayerInteract(hit);
        check(hit.isCancelled(), "Platform stays protected");
        phase(session, "SHOW");
        hit = interact(Action.LEFT_CLICK_BLOCK, world.block(16, 81, 0));
        session.onPlayerInteract(hit);
        check(hit.isCancelled(), "Cannot mine the model while memorizing");
    }

    private static void armsReconnect() throws Exception {
        var session = bare(ArmsRaceSession.class);
        set(session, "context", context());
        set(session, "names", Map.of(ID, "Test"));
        Set<UUID> connected = new HashSet<>();
        Map<UUID, Long> respawns = new HashMap<>();
        set(session, "connected", connected);
        set(session, "respawnAt", respawns);
        set(session, "state", new ArmsRaceState(List.of(ID), 12));
        phase(session, "FIGHT");
        Throwable unexpected = null;
        try {
            session.onParticipantRejoin(PLAYER);
        } catch (Throwable failure) {
            unexpected = failure;
        }
        check(
                connected.isEmpty() && respawns.isEmpty(),
                "Embedded reconnect must not enroll or schedule a respawn");
        if (unexpected != null)
            throw new AssertionError(
                    "Rejoin must leave spectator handling to the parent", unexpected);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cancelRecovery() throws Exception {
        var session = bare(DuelSession.class);
        set(session, "context", context());
        set(session, "ffa", true);
        phase(session, "FIGHT");
        set(session, "names", Map.of());
        set(session, "countdownFreeze", new CountdownFreeze());
        set(session, "projectiles", new ArenaProjectiles());
        set(session, "mounts", new PvpMounts());
        set(session, "harpoon", new PvpHarpoon());
        set(session, "thunder", new PvpThunder());
        set(session, "airstrike", new PvpAirstrike());
        set(session, "shadowArmor", new PvpShadowArmor(player -> false));
        set(session, "bombs", new PvpBombs());
        set(session, "combat", new PvpCombat());
        set(session, "lastHit", new HashMap<>());
        set(session, "deathPause", new DeathPause());
        boolean[] cancelled = {false};
        BukkitTask task =
                (BukkitTask)
                        Proxy.newProxyInstance(
                                BukkitTask.class.getClassLoader(),
                                new Class[] {BukkitTask.class},
                                (o, m, a) ->
                                        switch (m.getName()) {
                                            case "cancel" -> {
                                                cancelled[0] = true;
                                                yield null;
                                            }
                                            case "hashCode" -> 1;
                                            case "equals" -> o == a[0];
                                            default -> throw new AssertionError(m.getName());
                                        });
        // Accept both storage representations so this reproduces the old callback leak.
        Field tasks = field(session.getClass(), "recoveries");
        Object pending =
                Map.class.isAssignableFrom(tasks.getType())
                        ? new HashMap<>(Map.of(ID, task))
                        : new HashSet<>(Set.of(task));
        tasks.set(session, pending);
        session.onParticipantLeave(PLAYER);
        check(cancelled[0], "Leaving must cancel an outstanding spectator recovery");
    }

    private static void spectatorTeleport() throws Exception {
        var session = bare(DuelSession.class);
        set(session, "fighters", Set.of(ID));
        set(session, "withdrawn", Set.of());
        set(session, "eliminated", Set.of(ID));
        // A defeated player remains in the roster but must be able to use spectator follow.
        var location = new Location(null, 0, 88, 0);
        var event = new PlayerTeleportEvent(PLAYER, location, location.clone(), PlayerTeleportEvent.TeleportCause.SPECTATE);
        session.onPlayerTeleport(event);
        check(!event.isCancelled(), "Eliminated spectators can teleport to follow surviving fighters");
    }

    private static PlayerInteractEvent interact(Action action, org.bukkit.block.Block block) {
        return new PlayerInteractEvent(PLAYER, action, null, block, BlockFace.UP);
    }

    private static void teardownFailure(boolean childThrows) throws Exception {
        var session = new PartyGamesSession(null, null);
        var context = context();
        Set<String> owned = new LinkedHashSet<>(Set.of("unavailable-world"));
        set(context, "worlds", owned);
        set(session, "context", context);
        phase(session, "ACTIVE");
        if (childThrows)
            set(
                    session,
                    "child",
                    new PartyRoundSession() {
                        public void onStart() {}

                        public PartyRoundResult roundResult() {
                            return PartyRoundResult.empty();
                        }

                        public void onEnd(EndReason reason) {
                            throw new IllegalStateException("Injected child teardown failure");
                        }
                    });
        // No world service exists here: disposal would throw, exactly at the outer teardown
        // boundary.
        session.onEnd(EndReason.ABORTED);
        check(
                session.delegate() == null
                        && field(session.getClass(), "child").get(session) == null,
                "Failed child must be detached so core restoration can proceed");
        check(
                owned.contains("unavailable-world"),
                "Failed world stays owned for core cleanup after player restoration");
        session.onEnd(EndReason.ABORTED);
    }

    private static <T extends PartySurvivalSession> T fixture(Class<T> type) throws Exception {
        T session = bare(type);
        set(session, "playing", true);
        set(session, "connected", new HashSet<>(Set.of(ID)));
        set(session, "standings", new SurvivalStandings(List.of(ID)));
        return session;
    }

    private static PartyRoundContext context() {
        return new PartyRoundContext(null, List.of(), PartyRoundOptions.party(), (r, s) -> {});
    }

    // Constructors require a live JavaPlugin; fixtures instead inject only collaborators used by
    // the real handler.
    private static <T> T bare(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void phase(Object target, String name) throws Exception {
        Field f = field(target.getClass(), "phase");
        f.set(target, Enum.valueOf((Class) f.getType(), name));
    }

    private interface Checked {
        void run() throws Exception;
    }
}
