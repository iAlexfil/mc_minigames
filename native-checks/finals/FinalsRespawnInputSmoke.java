import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Real Paper input events reach the tracked frozen spectator, once per jump-key press. */
public final class FinalsRespawnInputSmoke {
    private static int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        assertions = 0;
        Class<?> type =
                Class.forName(
                        "me.petr1furious.potatominigames.games.finals.FinalsRespawnInput",
                        true,
                        main.getClass().getClassLoader());
        Constructor<?> ctor = type.getDeclaredConstructor(JavaPlugin.class, Consumer.class);
        ctor.setAccessible(true);
        List<UUID> presses = new ArrayList<>();
        Consumer<UUID> pressed =
                id -> {
                    require(Bukkit.isPrimaryThread(), "Respawn requests run on the server thread");
                    presses.add(id);
                };
        Object input = ctor.newInstance(main, pressed);
        Fake player = new Fake(UUID.randomUUID());
        Fake outsider = new Fake(UUID.randomUUID());
        try {
            invoke(input, "track", Player.class, player.player);
            send(outsider, true, false);
            send(player, false, true);
            require(
                    presses.isEmpty(),
                    "Outsiders and movement without Space cannot request respawn");
            send(player, true, false);
            require(
                    presses.equals(List.of(player.id)),
                    "A frozen spectator's actual jump input requests respawn");
            send(player, true, true);
            send(player, true, false);
            require(
                    presses.size() == 1,
                    "Holding Space across other input changes cannot repeat the request");
            player.input = new Keys(false, false);
            invoke(input, "track", Player.class, player.player);
            send(player, true, true);
            require(
                    presses.size() == 1,
                    "Re-tracking during spawn preserves the edge before Paper publishes current"
                        + " input");
            send(player, false, false);
            send(player, true, false);
            require(presses.size() == 2, "Releasing and pressing Space makes a fresh request");
            Fake replacement = new Fake(player.id);
            replacement.input = new Keys(true, false);
            invoke(input, "track", Player.class, replacement.player);
            send(player, false, false);
            send(player, true, false);
            send(replacement, true, true);
            require(
                    presses.size() == 2,
                    "Old connections and an already-held key on rejoin cannot respawn");
            send(replacement, false, false);
            send(replacement, true, false);
            require(presses.size() == 3, "The replacement connection receives fresh Space presses");
            invoke(input, "remove", UUID.class, player.id);
            send(replacement, false, false);
            send(replacement, true, false);
            require(presses.size() == 3, "Leaving removes respawn input immediately");
            invoke(input, "track", Player.class, outsider.player);
            invoke(input, "clear", null, null);
            send(outsider, false, false);
            send(outsider, true, false);
            invoke(input, "track", Player.class, player.player);
            send(player, false, false);
            send(player, true, false);
            require(
                    presses.size() == 3,
                    "Session cleanup unregisters the listener and prevents reactivation");
            smoke.getLogger()
                    .info("FINALS_RESPAWN_INPUT_SMOKE_PASS: " + assertions + " assertions");
            return assertions;
        } finally {
            invoke(input, "clear", null, null);
        }
    }

    private static void invoke(Object object, String name, Class<?> parameter, Object value)
            throws Exception {
        Method method =
                parameter == null
                        ? object.getClass().getDeclaredMethod(name)
                        : object.getClass().getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        if (parameter == null) method.invoke(object);
        else method.invoke(object, value);
    }

    private static void send(Fake player, boolean jump, boolean forward) {
        Input keys = new Keys(jump, forward);
        Bukkit.getPluginManager().callEvent(new PlayerInputEvent(player.player, keys));
        player.input = keys;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private record Keys(boolean isJump, boolean isForward) implements Input {
        public boolean isBackward() {
            return false;
        }

        public boolean isLeft() {
            return false;
        }

        public boolean isRight() {
            return false;
        }

        public boolean isSneak() {
            return false;
        }

        public boolean isSprint() {
            return false;
        }
    }

    private static final class Fake {
        final UUID id;
        final Player player;
        Input input = new Keys(false, false);

        Fake(UUID id) {
            this.id = id;
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getCurrentInput" -> input;
                                                case "getGameMode" -> GameMode.SPECTATOR;
                                                case "getName", "toString" -> "RespawnInputFixture";
                                                case "hashCode" -> System.identityHashCode(proxy);
                                                case "equals" -> proxy == args[0];
                                                case "isOnline" -> true;
                                                default -> null;
                                            });
        }
    }
}
