import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual Paper player metadata packets, with only connected player transport substituted. */
public final class FinalsTeammateGlowSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsTeammateGlowSmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        Endpoint alice = new Endpoint(world), bob = new Endpoint(world);
        Endpoint eve = new Endpoint(world), frank = new Endpoint(world), outsider = new Endpoint(world);
        List<Endpoint> endpoints = List.of(alice, bob, eve, frank, outsider);
        List<Player> players = endpoints.stream().map(endpoint -> endpoint.player).toList();
        Map<UUID, Integer> teams = Map.of(alice.id, 0, bob.id, 0, eve.id, 1, frank.id, 1);
        Map<UUID, Object> handles = new HashMap<>();
        for (Endpoint endpoint : endpoints) handles.put(endpoint.id, nativePlayer(world, endpoint.id));
        List<Delivery> deliveries = new ArrayList<>();
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsTeammateGlow",
                true, main.getClass().getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(Function.class, BiConsumer.class);
        constructor.setAccessible(true);
        Object helper = constructor.newInstance(
                (Function<Player, Object>) player -> handles.get(player.getUniqueId()),
                (BiConsumer<Player, Object>) (viewer, packet) -> deliveries.add(new Delivery(viewer, packet)));
        Method update = method(type, "update", World.class, Collection.class, Map.class);
        Method remove = method(type, "remove", UUID.class);
        Method clear = method(type, "clear");
        Object bobHandle = handles.get(bob.id);
        try {
            flags(bobHandle, (byte) 0x23); // fire + crouching + invisible, deliberately no glow
            invoke(update, helper, world, players, teams);
            require(deliveries.size() == 4, "Only the two same-team pairs receive private glow packets");
            for (Delivery delivery : deliveries) {
                require(delivery.packet.getClass().getName().equals(
                        "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket"),
                        "The adapter creates actual native entity metadata packets");
                int target = packetId(delivery.packet);
                UUID targetId = handles.entrySet().stream().filter(entry -> {
                    try { return entityId(entry.getValue()) == target; }
                    catch (Exception error) { throw new RuntimeException(error); }
                }).findFirst().orElseThrow().getKey();
                require(!targetId.equals(delivery.viewer.getUniqueId()), "Private glow never targets the viewer");
                require(teams.get(targetId).equals(teams.get(delivery.viewer.getUniqueId())),
                        "A metadata override is sent exclusively to the target's teammates");
                require(!delivery.viewer.getUniqueId().equals(outsider.id), "Outsiders receive no player overrides");
                require(packetItems(delivery.packet).size() == 1,
                        "Private glow packets contain only the shared-flags metadata entry");
                require(metadataId(delivery.packet) == 0, "The override addresses the actual shared-flags index");
                require((packetFlags(delivery.packet) & 0x40) != 0, "Ally metadata enables native outline rendering");
            }
            require(packetFlags(delivery(deliveries, alice.player, bobHandle).packet) == (byte) 0x63,
                    "Private glow preserves fire, crouching and invisibility flags");
            require(flags(bobHandle) == (byte) 0x23,
                    "Building and delivering private glow never mutates the server player's metadata");

            deliveries.clear();
            flags(bobHandle, (byte) 0xa9); // new native flags must be read, not overwritten by a stale snapshot
            invoke(update, helper, world, players, teams);
            require(deliveries.size() == 4, "Refreshes re-send private outlines for tracking and native metadata changes");
            require(packetFlags(delivery(deliveries, alice.player, bobHandle).packet) == (byte) 0xe9,
                    "Each refresh preserves the latest native flags including falling and swimming");
            require(flags(bobHandle) == (byte) 0xa9, "A refresh still leaves native metadata untouched");

            deliveries.clear();
            flags(bobHandle, (byte) 0x63); // a separate mechanic has now applied global glow
            invoke(clear, helper);
            require(deliveries.size() == 4, "Cleanup restores each previously overridden viewer-target pair");
            require(packetFlags(delivery(deliveries, alice.player, bobHandle).packet) == (byte) 0x63,
                    "Cleanup preserves independently applied global glow and all other current flags");
            deliveries.clear();
            invoke(clear, helper);
            require(deliveries.isEmpty(), "Repeated cleanup sends no stale metadata overrides");

            flags(bobHandle, (byte) 0x23);
            invoke(update, helper, world, players, teams);
            deliveries.clear();
            bob.dead = true;
            invoke(update, helper, world, players, teams);
            require(packetFlags(delivery(deliveries, alice.player, bobHandle).packet) == (byte) 0x23,
                    "Death restores the native outline flag while persistent teammate labels remain separate");
            deliveries.clear();
            bob.dead = false;
            bob.mode = GameMode.SPECTATOR;
            invoke(update, helper, world, players, teams);
            require(deliveries.stream().noneMatch(item -> item.viewer == alice.player
                            && packetIdUnchecked(item.packet) == entityIdUnchecked(bobHandle)),
                    "Respawning spectator cameras receive no fake glowing player body");
            bob.mode = GameMode.SURVIVAL;
            invoke(update, helper, world, players, teams);
            deliveries.clear();
            invoke(remove, helper, bob.id);
            require(deliveries.size() == 2, "Departure resets its owned view and its teammates' target override");
            require(packetFlags(delivery(deliveries, alice.player, bobHandle).packet) == (byte) 0x23,
                    "A departing target loses its remaining private glow override");
            require(deliveries.stream().noneMatch(item -> item.viewer == eve.player || item.viewer == frank.player),
                    "Removing a red participant never alters the blue team's metadata views");
            deliveries.clear();
            alice.online = false;
            invoke(clear, helper);
            require(deliveries.stream().noneMatch(item -> item.viewer == alice.player),
                    "Cleanup never sends restoration packets to offline viewers");
            smoke.getLogger().info("FINALS_TEAMMATE_GLOW_SMOKE_PASS: " + assertions + " native assertions");
            return assertions;
        } finally {
            invoke(clear, helper);
        }
    }

    private record Delivery(Player viewer, Object packet) {}

    private static Delivery delivery(List<Delivery> deliveries, Player viewer, Object target) throws Exception {
        int id = entityId(target);
        return deliveries.stream().filter(item -> item.viewer == viewer && packetIdUnchecked(item.packet) == id)
                .findFirst().orElseThrow(() -> new AssertionError("Missing private metadata delivery"));
    }

    private static Object nativePlayer(World world, UUID id) throws Exception {
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = profileType.getConstructor(UUID.class, String.class).newInstance(id, "GlowSmoke");
        Class<?> information = Class.forName("net.minecraft.server.level.ClientInformation");
        Object info = information.getMethod("createDefault").invoke(null);
        return Class.forName("net.minecraft.server.level.ServerPlayer").getConstructor(
                Class.forName("net.minecraft.server.MinecraftServer"),
                Class.forName("net.minecraft.server.level.ServerLevel"), profileType, information)
                .newInstance(server, level, profile, info);
    }

    private static Object[] flagsData(Object handle) throws Exception {
        Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
        Field field = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
        field.setAccessible(true);
        return new Object[]{entity.getMethod("getEntityData").invoke(handle), field.get(null)};
    }

    private static void flags(Object handle, byte flags) throws Exception {
        Object[] data = flagsData(handle);
        data[0].getClass().getMethod("set", Class.forName("net.minecraft.network.syncher.EntityDataAccessor"), Object.class)
                .invoke(data[0], data[1], Byte.valueOf(flags));
    }

    private static byte flags(Object handle) throws Exception {
        Object[] data = flagsData(handle);
        return (Byte) data[0].getClass().getMethod("get", Class.forName("net.minecraft.network.syncher.EntityDataAccessor"))
                .invoke(data[0], data[1]);
    }

    private static int entityId(Object handle) throws Exception { return (Integer) handle.getClass().getMethod("getId").invoke(handle); }
    private static int packetId(Object packet) throws Exception { return (Integer) packet.getClass().getMethod("id").invoke(packet); }
    private static List<?> packetItems(Object packet) throws Exception { return (List<?>) packet.getClass().getMethod("packedItems").invoke(packet); }
    private static int metadataId(Object packet) throws Exception { return (Integer) packetItems(packet).getFirst().getClass().getMethod("id").invoke(packetItems(packet).getFirst()); }
    private static byte packetFlags(Object packet) throws Exception { return (Byte) packetItems(packet).getFirst().getClass().getMethod("value").invoke(packetItems(packet).getFirst()); }
    private static int packetIdUnchecked(Object packet) {
        try { return packetId(packet); } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static int entityIdUnchecked(Object handle) {
        try { return entityId(handle); } catch (Exception error) { throw new RuntimeException(error); }
    }

    private static final class Endpoint {
        final UUID id = UUID.randomUUID();
        final Player player;
        boolean dead, online = true;
        GameMode mode = GameMode.SURVIVAL;

        Endpoint(World world) {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> world;
                        case "isOnline" -> online;
                        case "isDead" -> dead;
                        case "getGameMode" -> mode;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new AssertionError("Unexpected player transport call: " + method.getName());
                    });
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
