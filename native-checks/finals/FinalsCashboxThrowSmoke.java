import java.lang.reflect.*;
import java.util.*;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.games.finals.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Native item launch through the real session drop handler, without a network player. */
public final class FinalsCashboxThrowSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsCashboxThrowSmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        Location feet = new Location(world, 740.5, 100, 740.5, 0, 0);
        List<BlockState> terrain = new ArrayList<>();
        for (int y = 100; y <= 103; y++) for (int z = 740; z <= 744; z++) {
            var block = world.getBlockAt(740, y, z);
            terrain.add(block.getState());
            block.setType(Material.AIR, false);
        }
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        Map<UUID, Integer> roster = Map.of(owner, 0, other, 1);
        Player player = player(owner, feet), noncarrier = player(other, feet);
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsSession");
        ActiveSession active = bare(ActiveSession.class);
        set(active, "phase", GamePhase.RUNNING);
        SessionServices services = new SessionServices(main, active, null, null, null,
                null, null, null, null, null, new Messages(Audience::empty, null), null);
        Constructor<?> constructor = type.getDeclaredConstructor(SessionServices.class,
                Class.forName("me.petr1furious.potatominigames.games.finals.FinalsConfig"));
        constructor.setAccessible(true);
        Object session = constructor.newInstance(services, null);
        FinalsMapArena arena = (FinalsMapArena) Proxy.newProxyInstance(
                FinalsMapArena.class.getClassLoader(), new Class<?>[]{FinalsMapArena.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "world" -> world;
                    case "contains" -> world.equals(((Location) args[0]).getWorld());
                    case "vault" -> feet.clone();
                    default -> throw new AssertionError("Unexpected arena call: " + method.getName());
                });
        set(session, "world", world);
        set(session, "arena", arena);
        set(session, "tick", 100L);
        ((Map<UUID, Integer>) get(session, "roster")).putAll(roster);
        ((Set<UUID>) get(session, "connected")).addAll(roster.keySet());
        Method dropEvent = method(type, "onDropItem", PlayerDropItemEvent.class);
        Method remove = method(type, "removeCashbox");
        Method normalDrop = method(type, "dropCashbox", Player.class);
        Method ensure = method(type, "ensureCashbox");
        Item trigger = world.dropItem(feet.clone().add(0, 3, 0), new ItemStack(Material.STICK));
        try {
            FinalsState state = carried(roster, owner);
            set(session, "state", state);
            invoke(dropEvent, session, new PlayerDropItemEvent(noncarrier, trigger));
            require(get(session, "cashbox") == null && owner.equals(state.carrier()),
                    "Noncarrier Q cannot launch or steal the current carrier's cashbox");
            PlayerDropItemEvent event = new PlayerDropItemEvent(player, trigger);
            invoke(dropEvent, session, event);
            Item launched = (Item) get(session, "cashbox");
            require(event.isCancelled(), "Q cancels native inventory dropping");
            require(state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE && state.carrier() == null,
                    "Q transitions the actual carrier into an available cashbox");
            require(launched != null && launched.isValid() && launched.getType() == org.bukkit.entity.EntityType.ITEM,
                    "The session creates a real native item entity");
            Vector velocity = launched.getVelocity();
            close(velocity.getX(), 0, "Throw remains aligned with aim X");
            close(velocity.getY(), .12, "Horizontal throws receive the upward arc");
            close(velocity.getZ(), 1.3, "Native item receives aimed forward force");
            require(launched.hasGravity(), "Native item gravity remains enabled");
            require(!launched.canPlayerPickup() && !launched.canMobPickup(),
                    "Native pickups cannot bypass session objective rules");
            close(launched.getLocation().getZ(), 741.4, "Unobstructed release starts ahead of the eyes");
            require(owner.equals(get(session, "recentDropper"))
                    && ((Long) get(session, "pickupAfter")) == 120L,
                    "The actual launch records its owner and exact 20-tick pickup guard");
            invoke(dropEvent, session, new PlayerDropItemEvent(player, trigger));
            invoke(dropEvent, session, new PlayerDropItemEvent(noncarrier, trigger));
            require(get(session, "cashbox") == launched && launched.isValid(),
                    "Repeated Q and noncarrier Q cannot replace or duplicate a launch");
            invoke(remove, session);

            world.getBlockAt(740, 101, 741).setType(Material.STONE, false);
            set(session, "state", carried(roster, owner));
            invoke(dropEvent, session, new PlayerDropItemEvent(player, trigger));
            Item clamped = (Item) get(session, "cashbox");
            close(clamped.getLocation().getZ(), 740.8,
                    "The actual native wall clamps release before its collision surface");
            require(clamped.getBoundingBox().getMaxZ() < 741,
                    "The released item's complete hitbox remains in front of the wall");
            require(clamped.getVelocity().getZ() > 1.29 && clamped.hasGravity(),
                    "Wall clamping preserves force and native collision-driven flight");
            invoke(remove, session);

            set(session, "state", carried(roster, owner));
            invoke(normalDrop, session, player);
            invoke(ensure, session);
            Item dropped = (Item) get(session, "cashbox");
            require(dropped != null && dropped.isValid(), "Ordinary carrier loss creates a native item");
            close(dropped.getVelocity().lengthSquared(), 0, "Ordinary drops receive no throw force");
            close(dropped.getLocation().getY(), 100.4, "Ordinary drops release near the player's feet");
            require(dropped.hasGravity(), "Ordinary drops still use native gravity");
            smoke.getLogger().info("FINALS_CASHBOX_THROW_SMOKE_PASS: " + assertions + " assertions");
            return assertions;
        } finally {
            try { invoke(remove, session); }
            finally {
                trigger.remove();
                terrain.forEach(state -> state.update(true, false));
            }
        }
    }

    private static FinalsState carried(Map<UUID, Integer> roster, UUID owner) {
        FinalsState state = new FinalsState(roster, 0);
        if (!state.activateVault(owner, 0) || !state.pickUp(owner, 100))
            throw new AssertionError("Fixture must start with an actual cashbox carrier");
        return state;
    }

    private static Player player(UUID id, Location feet) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setItemInOffHand" -> null;
                    default -> throw new AssertionError("Unexpected inventory call: " + method.getName());
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> feet.clone();
                    case "getEyeLocation" -> feet.clone().add(0, 1.62, 0);
                    case "getWorld" -> feet.getWorld();
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isDead" -> false;
                    case "getInventory" -> inventory;
                    case "displayName" -> Component.text("throw-smoke");
                    case "setGlowing", "removePotionEffect" -> null;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> id.hashCode();
                    default -> throw new AssertionError("Unexpected player call: " + method.getName());
                });
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
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
    private void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-6, message + ": expected " + expected + ", got " + actual);
    }
}
