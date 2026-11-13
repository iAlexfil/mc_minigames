package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.*;
import java.util.*;

/** Real helper channels with simulated transports, independent of Bukkit registries. */
public final class FinalsRevivesCheck {
    private static int checks;
    private static Class<?> type;

    public static void main(String[] args) throws Exception {
        try {
            type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsRevives");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "Downed teammates need physical, indefinitely revivable statues", missing);
        }
        Fixture f = new Fixture();
        f.now = 1000;
        call(f.helper, "tick");
        check(
                f.completed == 0 && call(f.helper, "location", f.targetId) != null,
                "Passing the individual respawn unlock never removes a statue or automatically"
                    + " revives it");
        check(
                (boolean) call(f.helper, "interact", f.actor),
                "Sneak-right-click starts a manual revive on the aimed ally");
        f.now = 1079;
        call(f.helper, "tick");
        check(f.completed == 0, "Manual revive requires the entire four-second hold");
        f.now = 1080;
        call(f.helper, "tick");
        check(
                f.completed == 1 && !f.usedDefib,
                "Manual revive completes at four seconds with full-health mode");
        check(
                call(f.helper, "location", f.targetId) == null,
                "Successful revival removes the physical statue");

        f = new Fixture();
        call(f.helper, "interact", f.actor);
        f.sneaking = false;
        f.now = 1;
        call(f.helper, "tick");
        check(
                !(boolean) call(f.helper, "busy", f.actorId),
                "Releasing crouch cancels manual revival");
        f.sneaking = true;
        call(f.helper, "interact", f.actor);
        call(f.helper, "interrupt", f.actorId);
        f.now = 200;
        call(f.helper, "tick");
        check(
                f.completed == 0,
                "Damage or attack interruption cannot later complete a stale channel");

        f = new Fixture();
        f.sneaking = false;
        check(
                (boolean) call(f.helper, "defibrillate", f.actor, f.targetId),
                "Defibrillator accepts a valid aimed downed teammate");
        f.now = 19;
        call(f.helper, "tick");
        check(
                f.completed == 0 && (boolean) call(f.helper, "busy", f.actorId),
                "Defibrillator holds for its full one-second charge");
        f.now = 20;
        call(f.helper, "tick");
        check(
                f.completed == 0 && !(boolean) call(f.helper, "busy", f.actorId),
                "Completed charge releases the actor during the rebuilding delay");
        f.canAct = false;
        call(f.helper, "disconnected", f.actorId, false);
        f.now = 79;
        call(f.helper, "tick");
        check(f.completed == 0, "Rebuilding still needs three full seconds after charging");
        f.now = 80;
        call(f.helper, "tick");
        check(
                f.completed == 1 && f.usedDefib,
                "A committed defibrillator rebuild survives reviver death or temporary disconnect");

        f = new Fixture();
        f.sneaking = false;
        call(f.helper, "defibrillate", f.actor, f.targetId);
        f.slot = 3;
        f.now = 20;
        call(f.helper, "tick");
        check(
                f.completed == 0 && !(boolean) call(f.helper, "busy", f.actorId),
                "Switching away before charging finishes cancels Defibrillator");
        f = new Fixture();
        call(f.helper, "defibrillate", f.actor, f.targetId);
        f.now = 20;
        call(f.helper, "tick");
        call(f.helper, "remove", f.targetId);
        f.now = 80;
        call(f.helper, "tick");
        check(
                f.completed == 0,
                "Target spawn or full wipe invalidates an already committed rebuild");
        f = new Fixture();
        f.targetOnline = false;
        check(
                !(boolean) call(f.helper, "defibrillate", f.actor, f.targetId),
                "Disconnected targets cannot begin a revive");
        check(
                call(f.helper, "location", f.targetId) != null,
                "Temporary disconnect preserves the downed statue");
        f = new Fixture();
        call(f.helper, "defibrillate", f.actor, f.targetId);
        f.now = 20;
        call(f.helper, "tick");
        f.targetOnline = false;
        call(f.helper, "disconnected", f.targetId, false);
        f.now = 80;
        call(f.helper, "tick");
        check(
                f.completed == 0 && call(f.helper, "location", f.targetId) != null,
                "Target disconnect cancels rebuilding while preserving the statue");
        f = new Fixture();
        f.friendly = false;
        check(
                !(boolean) call(f.helper, "defibrillate", f.actor, f.targetId),
                "Enemy statues cannot be revived");
        f = new Fixture();
        call(f.helper, "interact", f.actor);
        f.position.setZ(-10);
        f.now = 80;
        call(f.helper, "tick");
        check(f.completed == 0, "Leaving revive range cancels the held channel");
        f = new Fixture();
        call(f.helper, "interact", f.actor);
        f.canAct = false;
        f.now = 80;
        call(f.helper, "tick");
        check(f.completed == 0, "A menu, objective action or death cancels an uncommitted channel");
        System.out.println("FinalsRevivesCheck: " + checks + " checks passed");
    }

    private static Object call(Object receiver, String name, Object... args) throws Exception {
        for (Method m : receiver.getClass().getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                try {
                    return m.invoke(receiver, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception x) throw x;
                    if (e.getCause() instanceof Error x) throw x;
                    throw e;
                }
            }
        throw new NoSuchMethodException(name);
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    private static final class Fixture {
        final UUID actorId = new UUID(0, 1), targetId = new UUID(0, 2);
        long now;
        int completed, slot = 2;
        boolean usedDefib,
                canAct = true,
                sneaking = true,
                downed = true,
                targetOnline = true,
                friendly = true;
        final World world =
                proxy(
                        World.class,
                        (o, m, a) ->
                                switch (m.getName()) {
                                    case "getMinHeight" -> -64;
                                    case "getMaxHeight" -> 320;
                                    case "equals" -> o == a[0];
                                    case "hashCode" -> 1;
                                    default -> zero(m.getReturnType());
                                });
        final Location position = new Location(world, 0, 64, 0),
                point = new Location(world, 0, 64, 2);
        final Player actor, target;
        final Object helper;

        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            PlayerInventory inventory =
                    proxy(
                            PlayerInventory.class,
                            (o, m, a) ->
                                    m.getName().equals("getHeldItemSlot")
                                            ? slot
                                            : zero(m.getReturnType()));
            actor =
                    proxy(
                            Player.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> actorId;
                                        case "getWorld" -> world;
                                        case "getLocation" -> position.clone();
                                        case "getEyeLocation" -> {
                                            Location eye = position.clone().add(0, 1.62, 0);
                                            eye.setDirection(
                                                    point.clone()
                                                            .add(0, .4, 0)
                                                            .toVector()
                                                            .subtract(eye.toVector()));
                                            yield eye;
                                        }
                                        case "isSneaking" -> sneaking;
                                        case "getInventory" -> inventory;
                                        case "isOnline" -> true;
                                        case "getName" -> "Reviver";
                                        case "locale" -> Locale.ENGLISH;
                                        case "equals" -> o == a[0];
                                        case "hashCode" -> actorId.hashCode();
                                        default -> zero(m.getReturnType());
                                    });
            target =
                    proxy(
                            Player.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> targetId;
                                        case "getWorld" -> world;
                                        case "getLocation" -> point.clone();
                                        case "isOnline" -> targetOnline;
                                        case "getName" -> "Target";
                                        case "locale" -> Locale.ENGLISH;
                                        case "equals" -> o == a[0];
                                        case "hashCode" -> targetId.hashCode();
                                        default -> zero(m.getReturnType());
                                    });
            Class<?> ctx = Class.forName(type.getName() + "$Context");
            Object context =
                    Proxy.newProxyInstance(
                            ctx.getClassLoader(),
                            new Class<?>[] {ctx},
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "world" -> world;
                                        case "tick" -> now;
                                        case "players" -> List.of(actor, target);
                                        case "canAct" -> canAct;
                                        case "downed" -> downed && targetId.equals(a[0]);
                                        case "friendly" -> friendly;
                                        case "inside" -> true;
                                        case "fallback" -> point.clone();
                                        case "complete" -> {
                                            completed++;
                                            usedDefib = (boolean) a[3];
                                            downed = false;
                                            yield true;
                                        }
                                        default -> zero(m.getReturnType());
                                    });
            Constructor<?> ctor = type.getDeclaredConstructor(ctx);
            ctor.setAccessible(true);
            helper = ctor.newInstance(context);
            Class<?> body = Class.forName(type.getName() + "$Statue");
            Constructor<?> create =
                    body.getDeclaredConstructor(UUID.class, String.class, Location.class);
            create.setAccessible(true);
            Object statue = create.newInstance(targetId, "Target", point);
            Item item =
                    proxy(
                            Item.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "isValid" -> true;
                                        case "getLocation" -> point.clone();
                                        case "getWorld" -> world;
                                        case "getUniqueId" -> new UUID(1, 1);
                                        default -> zero(m.getReturnType());
                                    });
            Interaction interaction =
                    proxy(
                            Interaction.class,
                            (o, m, a) ->
                                    switch (m.getName()) {
                                        case "isValid" -> true;
                                        case "teleport" -> true;
                                        case "getUniqueId" -> new UUID(1, 2);
                                        default -> zero(m.getReturnType());
                                    });
            set(statue, "item", item);
            set(statue, "interaction", interaction);
            Field map = type.getDeclaredField("statues");
            map.setAccessible(true);
            ((Map<UUID, Object>) map.get(helper)).put(targetId, statue);
        }
    }
}
