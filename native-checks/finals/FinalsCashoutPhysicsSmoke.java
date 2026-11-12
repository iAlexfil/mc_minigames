import java.lang.reflect.*;
import java.util.*;
import me.petr1furious.potatominigames.games.finals.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real native bodies and collision shapes; no network connection is needed. */
public final class FinalsCashoutPhysicsSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsCashoutPhysicsSmoke().check(smoke);
    }

    private int check(JavaPlugin smoke) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        Arena arena = new Arena(world);
        List<BlockState> originals = new ArrayList<>();
        for (int x : new int[]{700, 705, 710, 715}) {
            for (int y = 94; y <= 105; y++) {
                Block block = world.getBlockAt(x, y, 700);
                originals.add(block.getState());
                block.setType(Material.AIR, false);
            }
            world.getBlockAt(x, 100, 700).setType(Material.STONE, false);
        }
        world.getBlockAt(700, 96, 700).setBlockData(Bukkit.createBlockData("minecraft:stone_slab[type=bottom]"), false);
        Set<UUID> before = new HashSet<>();
        world.getEntities().forEach(entity -> before.add(entity.getUniqueId()));
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsCashoutPhysics");
        Constructor<?> constructor = type.getDeclaredConstructor(FinalsMapArena.class);
        constructor.setAccessible(true);
        Object physics = constructor.newInstance(arena);
        Method tick = method(type, "tick"), clear = method(type, "clear");
        Method replace = method(type, "replace", int.class);
        Method station = method(type, "station", Entity.class);
        Method aimed = method(type, "aimedStation", Player.class, double.class);
        try {
            List<Entity> initial = created(world, before);
            require(initial.stream().filter(BlockDisplay.class::isInstance).count() == 2
                    && initial.stream().filter(Interaction.class::isInstance).count() == 2,
                    "Exactly two native display bodies and two click interactions are created");
            require(initial.stream().allMatch(entity -> !entity.isPersistent() && !entity.hasGravity()),
                    "Cashout entities remain session-owned and use controlled gravity");
            require(arena.station(0).getBlock().getType() == Material.AIR
                    && arena.station(1).getBlock().getType() == Material.AIR,
                    "Spawning a body never writes gold blocks into the map");
            List<Entity> unchangedSlot = initial.stream().filter(entity -> {
                try { return ((Integer) invoke(station, physics, entity)) == 1; }
                catch (Exception error) { throw new RuntimeException(error); }
            }).toList();
            invoke(tick, physics);
            require(arena.station(0).getY() == 101 && arena.station(1).getY() == 101,
                    "Supported bodies remain stationary");
            world.getBlockAt(700, 100, 700).setType(Material.AIR, false);
            invoke(tick, physics);
            require(Math.abs(arena.station(0).getY() - 100.92) < 1e-7,
                    "Destroying support starts native body gravity on the next tick");
            for (int index = 0; index < 60; index++) invoke(tick, physics);
            require(Math.abs(arena.station(0).getY() - 96.5) < 1e-7,
                    "Swept native voxel collision lands precisely on a lower half slab");
            require(arena.station(1).getY() == 101 && unchangedSlot.stream().allMatch(Entity::isValid),
                    "Falling one body leaves the other station and its entities unchanged");
            require(world.getBlockAt(700, 96, 700).getType() == Material.STONE_SLAB
                    && world.getBlockAt(700, 97, 700).getType() == Material.AIR,
                    "Landing neither replaces terrain nor places a gold block");
            require(created(world, before).size() == 4,
                    "Movement reuses native entities without spawning dropped items");
            for (Entity entity : initial) {
                if (((Integer) invoke(station, physics, entity)) == 0 && entity instanceof Interaction)
                    require(Math.abs(entity.getLocation().getY() - 96.5) < 1e-7,
                            "The clickable interaction follows the falling display exactly");
            }
            Location eye = new Location(world, 700.5, 97, 697);
            Player viewer = viewer(eye, new Vector(0, 0, 1));
            require(((Integer) invoke(aimed, physics, viewer, 4.5)) == 0,
                    "A ray selects the station at its actual fallen position");
            Block obstruction = world.getBlockAt(700, 97, 699);
            BlockState obstructionBefore = obstruction.getState();
            try {
                obstruction.setType(Material.STONE, false);
                require(((Integer) invoke(aimed, physics, viewer, 4.5)) == -1,
                        "Solid terrain prevents clicking a cashout through a wall");
            } finally { obstructionBefore.update(true, false); }
            world.getBlockAt(700, 96, 700).setType(Material.AIR, false);
            for (int index = 0; index < 70; index++) invoke(tick, physics);
            require(arena.station(0).getY() == 101 && arena.station(0).getX() == 710.5,
                    "A body lost below map bounds recovers at a supported free candidate");
            require(!arena.station(0).equals(arena.station(1)),
                    "Void recovery never overlaps the other live station");
            Set<UUID> slotOne = new HashSet<>();
            unchangedSlot.forEach(entity -> slotOne.add(entity.getUniqueId()));
            arena.nextCashout(0);
            invoke(replace, physics, 0);
            require(unchangedSlot.stream().allMatch(Entity::isValid)
                    && created(world, before).stream().filter(entity -> slotOne.contains(entity.getUniqueId())).count() == 2,
                    "Rotation replaces only the completed body and interaction");
            require(created(world, before).size() == 4, "Rotation leaves exactly two cashout bodies");
            Class<?> sessionType = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsSession");
            Constructor<?> sessionConstructor = Arrays.stream(sessionType.getDeclaredConstructors())
                    .filter(candidate -> candidate.getParameterCount() == 2).findFirst().orElseThrow();
            sessionConstructor.setAccessible(true);
            Object session = sessionConstructor.newInstance(new Object[]{null, null});
            Field owned = sessionType.getDeclaredField("cashouts");
            owned.setAccessible(true);
            owned.set(session, physics);
            invoke(method(sessionType, "onEnd", me.petr1furious.potatominigames.core.game.EndReason.class),
                    session, me.petr1furious.potatominigames.core.game.EndReason.ABORTED);
            require(created(world, before).isEmpty(), "Session end cleanup removes all native cashout entities");
            invoke(clear, physics);
            require(created(world, before).isEmpty(), "Repeated cashout cleanup remains safe");
            smoke.getLogger().info("FINALS_CASHOUT_PHYSICS_SMOKE_PASS: " + assertions + " assertions");
            return assertions;
        } finally {
            invoke(clear, physics);
            originals.forEach(state -> state.update(true, false));
        }
    }

    private static List<Entity> created(World world, Set<UUID> before) {
        return world.getEntities().stream().filter(entity -> !before.contains(entity.getUniqueId()) && entity.isValid()).toList();
    }

    private static Player viewer(Location eye, Vector direction) {
        eye.setDirection(direction);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getEyeLocation" -> eye.clone();
                    case "getWorld" -> eye.getWorld();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError("Unexpected player call: " + method.getName());
                });
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

    private static final class Arena implements FinalsMapArena {
        final World world;
        final Location[] slots;
        Arena(World world) {
            this.world = world;
            slots = new Location[]{new Location(world, 700.5, 101, 700.5), new Location(world, 705.5, 101, 700.5)};
        }
        public World world() { return world; }
        public void build() {}
        public Location center() { return new Location(world, 705, 101, 700); }
        public Location spawn(int team, int member) { return new Location(world, 715.5, 101, 700.5); }
        public Location vault() { return center(); }
        public List<Location> vaultLocations() { return List.of(center()); }
        public void nextVault() {}
        public Location station(int station) { return slots[station].clone(); }
        public void moveStation(int station, Location location) { slots[station] = location.clone(); }
        public List<Location> cashoutLocations() {
            return List.of(new Location(world, 700.5, 101, 700.5), new Location(world, 705.5, 101, 700.5),
                    new Location(world, 710.5, 101, 700.5));
        }
        public void nextCashout(int slot) { slots[slot] = new Location(world, 700.5, 101, 700.5); }
        public boolean contains(Location point) {
            return world.equals(point.getWorld()) && point.getY() >= 95 && point.getY() < 110;
        }
        public boolean isCover(Block block) { return true; }
        public int stationAt(Block block) { return -1; }
        public boolean isVault(Block block) { return false; }
        public double borderSize() { return 48; }
    }
}
