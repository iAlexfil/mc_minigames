package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Server-free resource and protection-boundary regression checks. */
public final class FinalsGadgetsCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        FinalsGadgetState state = new FinalsGadgetState();
        UUID first = new UUID(0, 1), second = new UUID(0, 2);
        var dash = FinalsGadgetState.Kind.EVASIVE_DASH;
        var frag = FinalsGadgetState.Kind.FRAG_GRENADE;
        check(state.charges(first, dash, 0) == 3, "dash starts with three charges");
        check(state.use(first, dash, 0), "first dash works");
        check(!state.use(first, dash, 1), "repeated same input cannot use twice");
        check(
                state.use(first, dash, 6) && state.use(first, dash, 12),
                "remaining dash charges work");
        check(!state.use(first, dash, 18), "empty gadget cannot activate");
        check(state.charges(second, dash, 18) == 3, "player cooldowns are isolated");
        check(state.use(first, frag, 18), "other gadget cooldowns are isolated");
        check(state.charges(first, dash, 89) == 0, "charge waits full recharge duration");
        check(state.charges(first, dash, 90) == 1, "first charge recharges exactly at deadline");
        check(state.charges(first, dash, 270) == 3, "skipped ticks catch up to capacity");
        check(state.remaining(first, dash, 500) == 0, "full resource has no phantom timer");
        state.use(first, dash, 500);
        check(state.remaining(first, dash, 500) == 90, "new use starts new recharge");
        state.forget(first);
        check(
                state.charges(first, dash, 501) == 3 && state.use(first, dash, 501),
                "respawn resets charges and input cooldown");
        state.clear();
        check(state.charges(first, frag, 502) == 2, "match cleanup removes resources");

        check(
                FinalsGadgetState.crossesDome(0, 0, 0, 5, 0, 0, 0, 0, 0, 4),
                "outside attacker cannot shoot protected interior");
        check(
                !FinalsGadgetState.crossesDome(0, 0, 0, 3, 0, 0, 0, 0, 0, 4),
                "entering the dome defeats protection");
        check(
                !FinalsGadgetState.crossesDome(5, 0, 0, 8, 0, 0, 0, 0, 0, 4),
                "outside victim is not protected");
        check(
                !FinalsGadgetState.crossesDome(0, 5, 0, 5, 0, 0, 0, 0, 0, 4),
                "protection is a sphere not an infinite column");
        check(
                FinalsGadgetState.crossesDome(4, 0, 0, 4.01, 0, 0, 0, 0, 0, 4),
                "sphere edge is stable");
        check(
                FinalsGadgetState.shouldRestoreGlow(false, true, false),
                "owned sonar glow is restored");
        check(
                !FinalsGadgetState.shouldRestoreGlow(true, true, false),
                "preexisting glow is preserved");
        check(
                !FinalsGadgetState.shouldRestoreGlow(false, true, true),
                "new carrier glow is preserved");
        check(
                !FinalsGadgetState.shouldRestoreGlow(false, false, false),
                "already changed glow is left alone");
        meshLifecycle();
        visibilityLifecycle();
        carrierAndSonarTransitions();
        domeEligibility();
        temporaryCoverOwnership();
        beamCancellation();
        System.out.println("FinalsGadgetsCheck: " + checks + " checks passed");
    }

    private static void meshLifecycle() throws Exception {
        Fixture f = new Fixture();
        java.lang.reflect.Field field;
        try {
            field = FinalsGadgets.class.getDeclaredField("effects");
        } catch (NoSuchFieldException missing) {
            throw new AssertionError(
                    "Selectable specializations need bounded active effects", missing);
        }
        field.setAccessible(true);
        Object effects = field.get(f.gadgets);
        invoke(
                effects,
                "activate",
                new Class<?>[] {Player.class, FinalsGadgetState.Kind.class},
                f.red.player,
                FinalsGadgetState.Kind.MESH_SHIELD);
        Method blocks =
                FinalsGadgets.class.getDeclaredMethod(
                        "blocksDamage", Player.class, Player.class, Location.class);
        check(
                (boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 0, 66, 8)),
                "Mesh blocks an actual projectile origin in front of its owner");
        check(
                !(boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 0, 66, -8)),
                "Mesh allows attacks from behind");
        check(
                !(boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 8, 66, 0)),
                "Mesh is a finite forward surface, not spherical immunity");
        f.red.location.setPitch(90);
        check(
                (boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 0, 66, 8)),
                "Looking down does not silently disable a yaw-facing Mesh Shield");
        f.red.location.setPitch(0);
        check(
                (boolean)
                        invoke(
                                f.gadgets,
                                "weaponBlocked",
                                new Class<?>[] {Player.class},
                                f.red.player),
                "Mesh requires the owner to keep holding their specialization");
        f.red.slot = 0;
        check(
                !(boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 0, 66, 8)),
                "Switching away immediately stops Mesh protection");
        f.red.slot = 4;
        f.gadgets.forget(f.red.id);
        check(
                !(boolean)
                        blocks.invoke(
                                f.gadgets,
                                f.redMate.player,
                                f.blue.player,
                                new Location(f.world, 0, 66, 8)),
                "Death cleanup removes Mesh protection");
    }

    private static void visibilityLifecycle() throws Exception {
        Fixture f = new Fixture();
        invoke(f.gadgets, "vanish", new Class<?>[] {Player.class}, f.red.player);
        check(
                f.blue.hidden.contains(f.red.id),
                "vanishing hides the complete player and equipment from opponents");
        check(
                f.redMate.hidden.isEmpty(),
                "teammates retain visibility of their own cloaked teammate");
        FakePlayer late = f.player(3, 1);
        f.now = 1;
        f.gadgets.tick();
        check(late.hidden.contains(f.red.id), "new opponent viewers also receive invisibility");
        f.players.remove(late);
        f.gadgets.forget(late.id);
        check(
                late.hidden.isEmpty() && f.blue.hidden.contains(f.red.id),
                "viewer quit releases its own visibility registration only");
        f.players.add(late);
        f.gadgets.tick();
        check(late.hidden.contains(f.red.id), "reconnected viewers are hidden again");
        f.gadgets.reveal(f.red.player);
        check(
                f.blue.hidden.isEmpty() && late.hidden.isEmpty(),
                "attacking reveals to every recorded viewer");
        invoke(f.gadgets, "vanish", new Class<?>[] {Player.class}, f.red.player);
        f.now = 101;
        f.gadgets.tick();
        check(f.blue.hidden.isEmpty(), "vanishing expires after five seconds");
        invoke(f.gadgets, "vanish", new Class<?>[] {Player.class}, f.red.player);
        f.carriers.add(f.red.id);
        f.gadgets.tick();
        check(f.blue.hidden.isEmpty(), "picking up the objective immediately defeats invisibility");
        f.carriers.clear();
        invoke(f.gadgets, "vanish", new Class<?>[] {Player.class}, f.red.player);
        invoke(f.gadgets, "vanish", new Class<?>[] {Player.class}, f.blue.player);
        f.gadgets.forget(f.red.id);
        check(
                f.blue.hidden.isEmpty() && f.redMate.hidden.contains(f.blue.id),
                "death cleanup does not reveal another player's cloak");
        f.gadgets.clear();
        check(f.red.hidden.isEmpty(), "match cleanup restores remaining cloaks");

        f.blue.glowing = false;
        invoke(f.gadgets, "revealBySonar", new Class<?>[] {Player.class}, f.blue.player);
        check(f.blue.glowing, "sonar actually enables enemy glow");
        f.carriers.add(f.blue.id);
        f.now += 100;
        f.gadgets.tick();
        check(f.blue.glowing, "sonar expiry preserves a newer cashbox carrier glow");
        f.carriers.clear();
        f.blue.glowing = false;
        invoke(f.gadgets, "revealBySonar", new Class<?>[] {Player.class}, f.blue.player);
        f.now += 100;
        f.gadgets.tick();
        check(!f.blue.glowing, "normal sonar expiry restores the previous visible state");
    }

    private static void carrierAndSonarTransitions() throws Exception {
        Fixture f = new Fixture();
        f.carriers.add(f.blue.id);
        f.blue.glowing = true;
        invoke(f.gadgets, "revealBySonar", new Class<?>[] {Player.class}, f.blue.player);
        f.carriers.remove(f.blue.id);
        f.blue.glowing = f.gadgets.isSonarRevealed(f.blue.player);
        check(f.blue.glowing, "dropping an already carried cashbox preserves newly applied sonar");
        f.now = 100;
        check(
                !f.gadgets.isSonarRevealed(f.blue.player),
                "sonar query respects the exact expiry before cleanup");
        f.gadgets.tick();
        check(!f.blue.glowing, "carrier glow is not restored forever after sonar expiry");
        invoke(f.gadgets, "revealBySonar", new Class<?>[] {Player.class}, f.blue.player);
        f.carriers.add(f.blue.id);
        f.blue.glowing = true;
        f.carriers.remove(f.blue.id);
        f.blue.glowing = f.gadgets.isSonarRevealed(f.blue.player);
        check(f.blue.glowing, "picking up and dropping the cashbox does not end an earlier sonar");
        f.now = 200;
        f.gadgets.tick();
        check(!f.blue.glowing, "sonar started before pickup still expires normally after drop");
    }

    private static void domeEligibility() throws Exception {
        Fixture f = new Fixture();
        f.blue.location.setX(8);
        addDome(f, 160);
        check(
                f.gadgets.blocksDamage(f.redMate.player, f.blue.player),
                "dome protects teammates from exterior enemies");
        f.blue.location.setX(2);
        check(
                !f.gadgets.blocksDamage(f.redMate.player, f.blue.player),
                "enemies can enter and fight inside the dome");
        f.blue.location.setX(8);
        f.carriers.add(f.redMate.id);
        check(
                !f.gadgets.blocksDamage(f.redMate.player, f.blue.player),
                "dome never makes a cashbox carrier invulnerable");
        f.carriers.clear();
        f.now = 160;
        check(
                !f.gadgets.blocksDamage(f.redMate.player, f.blue.player),
                "expired domes stop protection even before cleanup tick");
        addDome(f, 320);
        f.gadgets.forget(f.red.id);
        check(
                !f.gadgets.blocksDamage(f.redMate.player, f.blue.player),
                "owner death removes dome protection");
    }

    private static void temporaryCoverOwnership() throws Exception {
        Fixture f = new Fixture();
        FakeBlock first = new FakeBlock(f.world), changed = new FakeBlock(f.world);
        var types = new Class<?>[] {Player.class, Block.class, Material.class, long.class};
        invoke(f.gadgets, "putCover", types, f.red.player, first.block, Material.SLIME_BLOCK, 30L);
        check(
                f.gadgets.isTemporaryCover(first.block),
                "placed cover is registered as destructible");
        f.gadgets.breakTemporary(first.block);
        check(
                first.data == first.original && !f.gadgets.isTemporaryCover(first.block),
                "destroyed cover restores its exact original data");
        invoke(
                f.gadgets,
                "putCover",
                types,
                f.red.player,
                first.block,
                Material.LIGHT_GRAY_CONCRETE,
                30L);
        invoke(
                f.gadgets,
                "putCover",
                types,
                f.red.player,
                changed.block,
                Material.LIGHT_GRAY_CONCRETE,
                30L);
        changed.data = blockData();
        BlockData newer = changed.data;
        f.gadgets.forget(f.red.id);
        check(first.data == first.original, "owner death restores unchanged cover");
        check(changed.data == newer, "owner death never overwrites a later unrelated block edit");
        invoke(f.gadgets, "putCover", types, f.red.player, first.block, Material.SLIME_BLOCK, 30L);
        f.now = 30;
        f.gadgets.tick();
        check(
                first.data == first.original && !f.gadgets.isTemporaryCover(first.block),
                "temporary terrain expires at its deadline");
    }

    private static void beamCancellation() throws Exception {
        Fixture f = new Fixture();
        f.redMate.active = false;
        f.red.health = 10;
        addBeam(f);
        double healed = f.red.health;
        f.red.slot = 0;
        f.now = 8;
        f.gadgets.tick();
        f.red.slot = 4;
        f.now = 12;
        f.gadgets.tick();
        check(
                f.red.health == healed,
                "switching away cancels healing without restarting the channel");
        addBeam(f);
        f.gadgets.forget(f.red.id);
        f.now = 16;
        f.gadgets.tick();
        check(f.red.health == healed, "death cleanup removes active healing channels");
        addBeam(f);
        f.red.active = false;
        f.now = 17;
        f.gadgets.tick();
        check(stateMap(f, "beams").isEmpty(), "inactive owners cannot retain a healing channel");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> stateMap(Fixture f, String field) throws Exception {
        var member = FinalsGadgets.class.getDeclaredField(field);
        member.setAccessible(true);
        return (Map<UUID, Object>) member.get(f.gadgets);
    }

    private static Object effect(String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = Class.forName(FinalsGadgets.class.getName() + "$" + name);
        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    @SuppressWarnings("unchecked")
    private static void addDome(Fixture f, long expires) throws Exception {
        var member = FinalsGadgets.class.getDeclaredField("domes");
        member.setAccessible(true);
        ((List<Object>) member.get(f.gadgets))
                .add(
                        effect(
                                "Dome",
                                new Class<?>[] {Player.class, Location.class, long.class},
                                f.red.player,
                                f.red.location.clone().add(0, .6, 0),
                                expires));
    }

    private static void addBeam(Fixture f) throws Exception {
        stateMap(f, "beams")
                .put(
                        f.red.id,
                        effect(
                                "Beam",
                                new Class<?>[] {Player.class, UUID.class, long.class},
                                f.red.player,
                                f.red.id,
                                f.now + 60));
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static BlockData blockData() {
        return proxy(
                BlockData.class,
                (object, method, args) ->
                        switch (method.getName()) {
                            case "clone" -> object;
                            case "equals" -> object == args[0];
                            case "hashCode" -> System.identityHashCode(object);
                            default -> defaultValue(method.getReturnType());
                        });
    }

    private static final class FakeBlock {
        final BlockData original = blockData();
        BlockData data = original;
        final Block block;

        FakeBlock(World world) {
            block =
                    proxy(
                            Block.class,
                            (object, method, args) ->
                                    switch (method.getName()) {
                                        case "getBlockData" -> data;
                                        case "setBlockData" -> {
                                            data = (BlockData) args[0];
                                            yield null;
                                        }
                                        case "setType" -> {
                                            data = blockData();
                                            yield null;
                                        }
                                        case "getLocation" -> new Location(world, 2, 65, 2);
                                        case "equals" -> object == args[0];
                                        case "hashCode" -> System.identityHashCode(object);
                                        default -> defaultValue(method.getReturnType());
                                    });
        }
    }

    private static final class Fixture {
        long now;
        final List<FakePlayer> players = new ArrayList<>();
        final Set<UUID> carriers = new HashSet<>();
        final World world =
                proxy(
                        World.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "equals" -> object == args[0];
                                    case "hashCode" -> 1;
                                    case "getUID" -> new UUID(0, 99);
                                    case "getMinHeight" -> -64;
                                    case "getMaxHeight" -> 320;
                                    default -> defaultValue(method.getReturnType());
                                });
        final FakePlayer red = player(1, 0), redMate = player(2, 0), blue = player(3, 1);
        final FinalsCombatContext context =
                proxy(
                        FinalsCombatContext.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "world" -> world;
                                    case "tick" -> now;
                                    case "players" ->
                                            players.stream().map(player -> player.player).toList();
                                    case "active", "canAct" -> find((Player) args[0]).active;
                                    case "friendly" ->
                                            find((Player) args[0]).team
                                                    == find((Player) args[1]).team;
                                    case "inside" -> true;
                                    case "carrier" ->
                                            carriers.contains(((Player) args[0]).getUniqueId());
                                    case "playerClass" -> FinalsClass.LIGHT;
                                    default -> defaultValue(method.getReturnType());
                                });
        final FinalsGadgets gadgets = new FinalsGadgets(context);

        FakePlayer player(int id, int team) {
            FakePlayer result = new FakePlayer(world, id + players.size() * 10, team);
            players.add(result);
            return result;
        }

        FakePlayer find(Player player) {
            return players.stream()
                    .filter(candidate -> candidate.player == player)
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final class FakePlayer {
        final UUID id;
        final int team;
        final Set<UUID> hidden = new HashSet<>();
        Location location;
        boolean glowing;
        boolean active = true;
        double health = 20;
        int slot = 4;
        final Player player;

        FakePlayer(World world, int value, int team) {
            this.id = new UUID(1, value);
            this.team = team;
            location = new Location(world, 0, 65, 0);
            PlayerInventory inventory =
                    proxy(
                            PlayerInventory.class,
                            (object, method, args) ->
                                    method.getName().equals("getHeldItemSlot")
                                            ? slot
                                            : defaultValue(method.getReturnType()));
            player =
                    proxy(
                            Player.class,
                            (object, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getLocation" -> location.clone();
                                        case "getEyeLocation" -> location.clone().add(0, 1.62, 0);
                                        case "getWorld" -> world;
                                        case "getHealth" -> health;
                                        case "setHealth" -> {
                                            health = (double) args[0];
                                            yield null;
                                        }
                                        case "getInventory" -> inventory;
                                        case "hasLineOfSight", "isOnline", "isValid" -> true;
                                        case "isGlowing" -> glowing;
                                        case "setGlowing" -> {
                                            glowing = (boolean) args[0];
                                            yield null;
                                        }
                                        case "hidePlayer" -> {
                                            hidden.add(((Player) args[1]).getUniqueId());
                                            yield null;
                                        }
                                        case "showPlayer" -> {
                                            hidden.remove(((Player) args[1]).getUniqueId());
                                            yield null;
                                        }
                                        case "getBoundingBox" ->
                                                new BoundingBox(
                                                        location.getX() - .3,
                                                        location.getY(),
                                                        location.getZ() - .3,
                                                        location.getX() + .3,
                                                        location.getY() + 1.8,
                                                        location.getZ() + .3);
                                        case "equals" -> object == args[0];
                                        case "hashCode" -> id.hashCode();
                                        case "getVelocity" -> new Vector();
                                        default -> defaultValue(method.getReturnType());
                                    });
        }
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
