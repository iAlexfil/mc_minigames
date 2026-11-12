import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;

/** Real attribute modifiers, ray tracing and protected block destruction; fake player transport. */
public final class FinalsGameEventsSmoke {
    private final JavaPlugin main;
    private final World world = Bukkit.getWorlds().getFirst();
    private final Map<Block, BlockState> original = new LinkedHashMap<>();
    private final List<P> players = new ArrayList<>();
    private final List<String> announcements = new ArrayList<>();
    private Object events;
    private long now;
    private int checks, damaged, broken;

    private FinalsGameEventsSmoke(JavaPlugin main) { this.main = main; }

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsGameEventsSmoke(main).run();
    }

    private Class<?> type(String name) throws Exception {
        return Class.forName("me.petr1furious.potatominigames.games.finals." + name,
                true, main.getClass().getClassLoader());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object kind(String name) throws Exception {
        return Enum.valueOf((Class) type("FinalsGameEvents$Kind"), name);
    }

    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            try { return method.invoke(object, args); }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception x) throw x;
                if (e.getCause() instanceof Error x) throw x;
                throw e;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private List<?> strikes() throws Exception {
        Field field = events.getClass().getDeclaredField("strikes");
        field.setAccessible(true);
        return (List<?>) field.get(events);
    }

    private void check(boolean good, String text) {
        checks++;
        if (!good) throw new AssertionError(text);
    }

    private Block set(int x, int y, int z, Material type) {
        Block block = world.getBlockAt(x, y, z);
        original.putIfAbsent(block, block.getState());
        block.setType(type, false);
        return block;
    }

    private boolean inside(Location point) {
        return point.getWorld() == world && point.getX() >= 1300 && point.getX() < 1330
                && point.getZ() >= 1300 && point.getZ() < 1330
                && point.getY() >= 100 && point.getY() < 130;
    }

    private void tick(long value) throws Exception { now = value; call(events, "tick"); }

    private int run() throws Exception {
        try {
            for (int x = 1298; x <= 1331; x++)
                for (int z = 1298; z <= 1331; z++) set(x, 100, z, Material.STONE);
            P red = new P(1310.5, 101, 1310.5), blue = new P(1325.5, 101, 1325.5),
                    spectator = new P(1310.5, 101, 1310.5), downed = new P(1310.5, 101, 1310.5);
            spectator.mode = GameMode.SPECTATOR;
            downed.active = false;
            players.addAll(List.of(red, blue, spectator, downed));
            Class<?> ctx = type("FinalsGameEvents$Context");
            Object context = Proxy.newProxyInstance(ctx.getClassLoader(), new Class<?>[] {ctx}, (o, m, a) ->
                    switch (m.getName()) {
                        case "plugin" -> main;
                        case "world" -> world;
                        case "tick" -> now;
                        case "players" -> players.stream().map(p -> p.player).toList();
                        case "active" -> players.stream().filter(p -> p.player == a[0]).findFirst().orElseThrow().active;
                        case "inside" -> inside((Location) a[0]);
                        case "destructible" -> ((Block) a[0]).getType() == Material.STONE;
                        case "breakBlock" -> {
                            Block b = (Block) a[0];
                            check(inside(b.getLocation().add(.5, .5, .5)), "Every destroyed block is inside the map");
                            original.putIfAbsent(b, b.getState());
                            b.setType(Material.AIR, false);
                            broken++;
                            yield null;
                        }
                        case "damage" -> {
                            P p = players.stream().filter(candidate -> candidate.player == a[0]).findFirst().orElseThrow();
                            p.health -= ((Number) a[1]).doubleValue();
                            damaged++;
                            check(inside((Location) a[2]), "Environmental damage keeps the in-map blast origin");
                            yield null;
                        }
                        case "announce" -> { announcements.add((String) a[0]); yield null; }
                        default -> zero(m.getReturnType());
                    });
            Constructor<?> constructor = type("FinalsGameEvents").getDeclaredConstructor(ctx, java.util.random.RandomGenerator.class);
            constructor.setAccessible(true);
            events = constructor.newInstance(context, new Random(2));

            NamespacedKey unrelated = new NamespacedKey("finals_smoke", "gravity");
            red.gravity().addTransientModifier(new AttributeModifier(unrelated, .2, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            double base = red.gravity().getValue(), spectatorBase = spectator.gravity().getValue();
            check((Boolean) call(events, "start", kind("LOW_GRAVITY")), "The game master can start low gravity");
            check(Math.abs(red.gravity().getValue() - base * .35) < 1e-8, "Gravity is reduced without replacing existing modifiers");
            check(red.fallDistance == 0, "Low gravity clears accumulated fall damage while active");
            check(spectator.gravity().getValue() == spectatorBase, "A true spectator never gets an event modifier");
            check(downed.gravity().getValue() == spectatorBase, "Downed players never get an event modifier");
            check(!(Boolean) call(events, "start", kind("ORBITAL_LASERS")), "A second event cannot replace or extend an active event");
            check((Integer) call(events, "remainingSeconds") == 30, "Events start with thirty seconds");
            red.online = false;
            tick(1);
            check(Math.abs(red.gravity().getValue() - base) < 1e-8, "Disconnect immediately removes only the event modifier");
            red.online = true;
            tick(2);
            check(red.gravity().getValue() < base, "A returning combatant receives the still-active effect");
            red.dead = true;
            tick(3);
            check(red.gravity().getValue() == base, "Death removes temporary gravity before spectator respawn");
            red.dead = false;
            tick(599);
            check((Integer) call(events, "remainingSeconds") == 1, "The last tick still displays one second");
            tick(600);
            check(call(events, "activeKind") == null && (Integer) call(events, "remainingSeconds") == 0,
                    "An event expires at exactly thirty seconds");
            check(red.gravity().getValue() == base && red.gravity().getModifier(unrelated) != null,
                    "Expiry preserves unrelated gravity modifiers");
            check(announcements.equals(List.of("event.started", "event.ended")), "Start and expiry each announce exactly once");

            now = 1000;
            blue.active = false;
            call(events, "start", kind("METEOR_SHOWER"));
            tick(1020);
            check(strikes().size() == 1, "Meteor shower schedules a telegraphed surface impact");
            Object strike = strikes().getFirst();
            Location meteor = ((Location) call(strike, "point")).clone();
            long impact = (Long) call(strike, "impact");
            check(impact == 1060 && inside(meteor), "Meteor warning gives two seconds and remains inside the arena");
            check(Math.abs(meteor.getY() - 101.1) < .01, "Meteor ray tracing lands on the actual floor surface");
            red.location = meteor.clone();
            blue.location = new Location(world, 1329, 101, 1329);
            spectator.location = meteor.clone();
            downed.location = meteor.clone();
            int beforeDamage = damaged, beforeBroken = broken;
            tick(1059);
            check(damaged == beforeDamage && broken == beforeBroken, "The warning cannot damage players or cover early");
            tick(1060);
            check(broken > beforeBroken, "Meteor impact destroys eligible cover");
            check(red.health < 40 && spectator.health == 40 && downed.health == 40,
                    "Environmental damage reaches combatants while excluding spectators and downed players");
            check(world.getBlockAt(1299, 100, 1300).getType() == Material.STONE, "Meteor never changes exterior map blocks");
            call(events, "stop");
            beforeBroken = broken;
            tick(2000);
            check(strikes().isEmpty() && broken == beforeBroken, "Stopping cancels all queued impacts");

            red.location = new Location(world, 1310.5, 101, 1310.5);
            blue.active = false;
            red.health = 40;
            now = 3000;
            call(events, "start", kind("ORBITAL_LASERS"));
            tick(3059);
            check(strikes().isEmpty(), "Orbital lasers allow three seconds before marking stationary contestants");
            red.location.add(1, 0, 0);
            tick(3060);
            tick(3119);
            check(strikes().isEmpty(), "Moving resets stationary acquisition");
            tick(3120);
            check(strikes().size() == 1, "Remaining still triggers one fixed-position warning");
            Location laser = ((Location) call(strikes().getFirst(), "point")).clone();
            check((Long) call(strikes().getFirst(), "impact") == 3160, "Orbital strike leaves two seconds to escape");
            red.location.add(7, 0, 0);
            tick(3160);
            check(red.health == 40, "Moving away from the warned location avoids the laser blast");
            call(events, "clear");
            int count = announcements.size();
            tick(4000);
            check(announcements.size() == count && strikes().isEmpty(), "Silent cleanup leaves no delayed effects or announcements");

            now = 5000;
            call(events, "start", kind("METEOR_SHOWER"));
            Block outside = set(1299, 101, 1310, Material.STONE);
            Block protectedBlock = set(1301, 101, 1310, Material.BEDROCK);
            Block inside = set(1300, 101, 1310, Material.STONE);
            red.location = new Location(world, 1300.5, 102, 1310.5);
            call(events, "blast", new Location(world, 1300.5, 102, 1310.5), 3.5d, 12d);
            check(inside.getType().isAir(), "Boundary blast can destroy allowed interior cover");
            check(outside.getType() == Material.STONE, "Boundary blast cannot destroy exterior blocks even when destructible says yes");
            check(protectedBlock.getType() == Material.BEDROCK, "Boundary blast respects arena protected materials");
            beforeBroken = broken;
            call(events, "blast", new Location(world, 1299.5, 102, 1310.5), 3.5d, 12d);
            check(broken == beforeBroken, "An outside impact is entirely rejected");
            call(events, "clear");
            call(events, "start", kind("LOW_GRAVITY"));
            check(red.gravity().getValue() < base, "Low gravity can start after another event ends");
            count = announcements.size();
            call(events, "clear");
            check(red.gravity().getValue() == base && announcements.size() == count,
                    "Match teardown restores attributes without an end announcement");
            return checks;
        } finally {
            if (events != null) call(events, "clear");
            players.forEach(p -> p.body.remove());
            original.values().forEach(state -> state.update(true, false));
        }
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    private final class P {
        final UUID id = UUID.randomUUID();
        final Cow body;
        final Player player;
        Location location;
        GameMode mode = GameMode.ADVENTURE;
        boolean active = true, online = true, dead;
        float fallDistance = 5;
        double health = 40;

        P(double x, double y, double z) {
            location = new Location(world, x, y, z);
            body = world.spawn(new Location(world, 1350, 101, 1350), Cow.class, cow -> {
                cow.setAI(false); cow.setGravity(false); cow.setInvulnerable(true);
                cow.setPersistent(false);
            });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (o, m, a) -> switch (m.getName()) {
                        case "getUniqueId" -> id;
                        case "getLocation" -> location.clone();
                        case "getWorld" -> location.getWorld();
                        case "getGameMode" -> mode;
                        case "isOnline" -> online;
                        case "isDead" -> dead;
                        case "getAttribute" -> body.getAttribute((Attribute) a[0]);
                        case "setFallDistance" -> { fallDistance = (float) a[0]; yield null; }
                        case "getFallDistance" -> fallDistance;
                        case "equals" -> o == a[0];
                        case "hashCode" -> id.hashCode();
                        case "locale" -> Locale.ENGLISH;
                        default -> zero(m.getReturnType());
                    });
        }

        AttributeInstance gravity() { return body.getAttribute(Attribute.GRAVITY); }
    }
}
