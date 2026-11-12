import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper display-name packets, substituting only connected players and packet transport. */
public final class FinalsTabListSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsTabListSmoke().check(smoke, main);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        Endpoint alice = new Endpoint(world, "Alice"), bob = new Endpoint(world, "Bob");
        Endpoint eve = new Endpoint(world, "Eve"), outsider = new Endpoint(world, "Outsider");
        List<Player> players = List.of(alice.player, bob.player, eve.player, outsider.player);
        Map<UUID,Integer> teams = new HashMap<>(Map.of(alice.id, 0, bob.id, 0, eve.id, 1));
        ClassLoader loader = main.getClass().getClassLoader();
        Class<? extends Enum> builds = (Class<? extends Enum>) Class.forName(
                "me.petr1furious.potatominigames.games.finals.FinalsClass", true, loader);
        Map<UUID,Object> classes = new HashMap<>(Map.of(alice.id, Enum.valueOf(builds, "LIGHT"),
                bob.id, Enum.valueOf(builds, "MEDIUM"), eve.id, Enum.valueOf(builds, "HEAVY")));
        Map<UUID,Integer> kills = new HashMap<>(Map.of(alice.id, 2, bob.id, 7, eve.id, 4));
        Set<UUID> respawning = new HashSet<>();
        List<Delivery> deliveries = new ArrayList<>();
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsTabList", true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor(BiConsumer.class);
        constructor.setAccessible(true);
        Object helper = constructor.newInstance((BiConsumer<Player,Object>) (viewer, packet) ->
                deliveries.add(new Delivery(viewer, packet)));
        Method update = method(type, "update", World.class, Collection.class, Map.class, Map.class, Map.class, Set.class);
        Method clear = method(type, "clear"), remove = method(type, "remove", UUID.class);
        try {
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(rows(deliveries).size() == 9, "Only the three contestants receive the three contestant rows");
            for (Delivery delivery : deliveries) {
                require(delivery.viewer != outsider.player, "An outsider never receives private Tab rows");
                Set<?> actions = (Set<?>) delivery.packet.getClass().getMethod("actions").invoke(delivery.packet);
                require(actions.size() == 1 && actions.iterator().next().toString().equals("UPDATE_DISPLAY_NAME"),
                        "Only display names change; identities, latency, chat sessions and game mode remain intact");
            }
            String ally = text(deliveries, alice.player, bob.id);
            require(ally.contains("Bob") && ally.contains("Medium (M)") && ally.contains("7 kills")
                    && ally.contains("24/25 HP"), "The ally's actual row contains class, kills and current/max HP");
            String enemy = text(deliveries, eve.player, bob.id);
            require(enemy.contains("Medium (M)") && enemy.contains("7 kills") && !enemy.contains("HP"),
                    "The same target has public stats but no health in the enemy's actual packet");
            require(text(deliveries, bob.player, bob.id).contains("24/25 HP"), "A contestant can also see their own HP");
            require(bob.healthReads == 2 && eve.healthReads == 1 && outsider.healthReads == 0,
                    "Health is queried only for allied/self rows, never for enemy or outsider rows");
            require(alice.original == null && bob.original == null && eve.original == null,
                    "Private packet rows do not mutate the globally broadcast player list name");

            deliveries.clear();
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(deliveries.isEmpty(), "Unchanged rows are cached between periodic refreshes");
            bob.health = 8.2;
            kills.put(bob.id, 8);
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(text(deliveries, alice.player, bob.id).contains("9/25 HP")
                    && text(deliveries, alice.player, bob.id).contains("8 kills"),
                    "Damage and kill credit immediately update the allied row");
            require(text(deliveries, eve.player, bob.id).contains("8 kills")
                    && !text(deliveries, eve.player, bob.id).contains("HP"), "Enemy rows update kills without leaking damage");

            deliveries.clear();
            int healthBefore = bob.healthReads;
            respawning.add(bob.id);
            bob.mode = GameMode.SPECTATOR;
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(text(deliveries, alice.player, bob.id).contains("Respawning"),
                    "Allied spectators awaiting respawn show a respawning indicator");
            require(bob.healthReads == healthBefore, "Respawning camera health is never queried or disclosed as full HP");
            if (rows(deliveries).stream().anyMatch(row -> row.viewer == eve.player && row.target.equals(bob.id)))
                require(!text(deliveries, eve.player, bob.id).contains("Respawning"),
                        "Enemy rows do not acquire the private respawn indicator");

            deliveries.clear();
            alice.locale = Locale.forLanguageTag("ru-RU");
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(text(deliveries, alice.player, bob.id).contains("Возрождение"),
                    "Native row text is rendered for its recipient's language");
            deliveries.clear();
            respawning.clear();
            bob.mode = GameMode.SURVIVAL;
            bob.health = 35;
            bob.maximum = 35;
            classes.put(bob.id, Enum.valueOf(builds, "HEAVY"));
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(text(deliveries, eve.player, bob.id).contains("Heavy (L)"),
                    "Respawn equipment changes replace the old class for every contestant");
            require(text(deliveries, bob.player, bob.id).contains("35/35 HP"),
                    "New class health is shown after respawn");

            bob.original = Component.text("Custom Bob");
            deliveries.clear();
            invoke(remove, helper, bob.id);
            require(text(deliveries, alice.player, bob.id).equals("Custom Bob"),
                    "Leaving restores the target's current original display name for other viewers");
            require(row(deliveries, bob.player, alice.id) == null,
                    "Leaving restores the departing viewer's other default row names to native null");
            deliveries.clear();
            invoke(update, helper, world, players, teams, classes, kills, respawning);
            require(text(deliveries, bob.player, bob.id).contains("8 kills"),
                    "Rejoining rebuilds private rows without resetting match kills");
            alice.online = false;
            deliveries.clear();
            invoke(clear, helper);
            require(deliveries.stream().noneMatch(delivery -> delivery.viewer == alice.player),
                    "End cleanup never sends packets to offline viewers");
            require(text(deliveries, eve.player, bob.id).equals("Custom Bob"),
                    "Match cleanup restores custom names for enemy viewers too");
            deliveries.clear();
            invoke(clear, helper);
            require(deliveries.isEmpty(), "Repeated cleanup does not send stale updates");
            smoke.getLogger().info("FINALS_TAB_LIST_SMOKE_PASS: " + assertions + " native assertions");
            return assertions;
        } finally {
            invoke(clear, helper);
        }
    }

    private record Delivery(Player viewer, Object packet) {}
    private record Row(Player viewer, UUID target, Object name) {}

    private static List<Row> rows(List<Delivery> deliveries) throws Exception {
        List<Row> result = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            List<?> entries = (List<?>) delivery.packet.getClass().getMethod("entries").invoke(delivery.packet);
            for (Object entry : entries) result.add(new Row(delivery.viewer,
                    (UUID) entry.getClass().getMethod("profileId").invoke(entry),
                    entry.getClass().getMethod("displayName").invoke(entry)));
        }
        return result;
    }

    private static Object row(List<Delivery> deliveries, Player viewer, UUID target) throws Exception {
        Row found = rows(deliveries).stream().filter(row -> row.viewer == viewer && row.target.equals(target))
                .reduce((old, current) -> current).orElseThrow(() -> new AssertionError("Missing Tab row for " + target));
        return found.name;
    }

    private static String text(List<Delivery> deliveries, Player viewer, UUID target) throws Exception {
        Object name = row(deliveries, viewer, target);
        if (name == null) return "";
        Component component = (Component) Class.forName("io.papermc.paper.adventure.PaperAdventure")
                .getMethod("asAdventure", Class.forName("net.minecraft.network.chat.Component")).invoke(null, name);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static final class Endpoint {
        final UUID id = UUID.randomUUID();
        final Player player;
        boolean online = true;
        int healthReads;
        double health = 23.5, maximum = 25;
        Locale locale = Locale.ENGLISH;
        GameMode mode = GameMode.SURVIVAL;
        Component original;

        Endpoint(World world, String name) {
            AttributeInstance attribute = (AttributeInstance) Proxy.newProxyInstance(
                    AttributeInstance.class.getClassLoader(), new Class<?>[]{AttributeInstance.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getValue")) return maximum;
                        throw new AssertionError("Unexpected attribute call: " + method.getName());
                    });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "getWorld" -> world;
                        case "isOnline" -> online;
                        case "isDead" -> false;
                        case "locale" -> locale;
                        case "getGameMode" -> mode;
                        case "getHealth" -> { healthReads++; yield health; }
                        case "getAttribute" -> attribute;
                        case "canSee" -> true;
                        case "playerListName" -> {
                            if (args != null && args.length > 0) throw new AssertionError("Global Tab names must not be changed");
                            yield original;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new AssertionError("Unexpected player transport call: " + method.getName());
                    });
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }
    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }
    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
