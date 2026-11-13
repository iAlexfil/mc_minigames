import java.lang.reflect.*;
import java.util.*;
import me.petr1furious.potatominigames.games.finals.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
        for (int x : new int[]{700, 705, 710, 715, 740}) {
            for (int y = 94; y <= 105; y++) {
                Block block = world.getBlockAt(x, y, 700);
                originals.add(block.getState());
                block.setType(Material.AIR, false);
            }
            world.getBlockAt(x, 100, 700).setType(Material.STONE, false);
        }
        // Reproduce a configured anchor already inside its solid support. The very
        // first display packet must use the corrected surface height.
        arena.moveStation(0, new Location(world, 700.5, 100.75, 700.5, 87.30008f, 76.34995f));
        arena.moveStation(1, new Location(world, 705.5, 101, 700.5, 179.84937f, 71.54993f));
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
        Method pull = method(type, "moveToward", int.class, Location.class, double.class);
        try {
            List<Entity> initial = created(world, before);
            alignedDisplay(physics, station, initial, 0, arena.station(0), "Initial Nuketown cashout A");
            alignedDisplay(physics, station, initial, 1, arena.station(1), "Initial Nuketown cashout B");
            require(arena.station(0).getY() == 101,
                    "An initially buried cashout is lifted onto solid terrain before spawning");
            require(initial.stream().filter(entity -> entity.getLocation().getBlockX() == 700)
                            .allMatch(entity -> entity.getLocation().getY() == 101),
                    "The initial display and click interaction are both outside the ground before the first tick");
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
            alignedDisplay(physics, station, initial, 0, arena.station(0), "Falling cashout");
            for (int index = 0; index < 60; index++) invoke(tick, physics);
            require(Math.abs(arena.station(0).getY() - 96.5) < 1e-7,
                    "Swept native voxel collision lands precisely on a lower half slab");
            alignedDisplay(physics, station, initial, 0, arena.station(0), "Cashout landed on a half slab");
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
            for (String surface : new String[]{"minecraft:stone", "minecraft:oak_fence",
                    "minecraft:stone_slab[type=bottom]"}) {
                world.getBlockAt(700, 96, 700).setBlockData(Bukkit.createBlockData(surface), false);
                arena.moveStation(0, new Location(world, 700.5, 104, 700.5, 37, 90));
                invoke(replace, physics, 0);
                alignedDisplay(physics, station, created(world, before), 0, arena.station(0),
                        "Replacement from a downward-looking editor");
                for (int index = 0; index < 100; index++) invoke(tick, physics);
                double expectedY = surface.contains("fence") ? 97.5 : surface.contains("slab") ? 96.5 : 97;
                require(Math.abs(arena.station(0).getY() - expectedY) < 1e-7,
                        "A long fall settles on the exact surface and remains there: " + surface);
                alignedDisplay(physics, station, created(world, before), 0, arena.station(0),
                        "Replacement landed on " + surface);
                var bodyBox = new org.bukkit.util.BoundingBox(700, expectedY, 700, 701, expectedY + 1, 701);
                for (var shape : world.getBlockAt(700, 96, 700).getCollisionShape().getBoundingBoxes())
                    require(!bodyBox.overlaps(shape.clone().shift(700, 96, 700)),
                            "The settled body does not intersect its landing surface: " + surface);
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
            world.getBlockAt(700, 96, 700).setBlockData(Bukkit.createBlockData("minecraft:stone_slab[type=bottom]"), false);
            arena.moveStation(0, new Location(world, 700.5, 96.25, 700.5));
            invoke(replace, physics, 0);
            require(arena.station(0).getY() == 96.5,
                    "Fractional slab penetration is corrected before a replacement body is spawned");
            world.getBlockAt(700, 97, 700).setType(Material.STONE, false);
            invoke(tick, physics);
            require(arena.station(0).getY() == 98,
                    "Terrain rising into the upper half of a live body lifts it clear");
            world.getBlockAt(700, 97, 700).setType(Material.OAK_FENCE, false);
            invoke(tick, physics);
            require(arena.station(0).getY() == 98.5,
                    "The native one-and-a-half-block fence shape supports the complete body");
            world.getBlockAt(700, 99, 700).setType(Material.STONE, false);
            invoke(tick, physics);
            require(arena.station(0).getY() == 100,
                    "Clearance resolution includes terrain intersecting the top of the body");
            world.getBlockAt(740, 96, 700).setBlockData(Bukkit.createBlockData("minecraft:stone_slab[type=bottom]"), false);
            arena.moveStation(0, new Location(world, 740.5, 96.25, 700.5));
            invoke(replace, physics, 0);
            require(arena.station(0).getY() == 96.5,
                    "A different candidate's fractional slab anchor is normalized before spawning");
            arena.moveStation(0, new Location(world, 740.5, 109.5, 700.5));
            invoke(replace, physics, 0);
            require(arena.station(0).getY() == 101 && arena.station(0).getX() == 710.5,
                    "An anchor whose body protrudes beyond the map is replaced by a supported in-bounds candidate");
            require(created(world, before).size() == 4,
                    "Penetration repairs and candidate changes retain exactly two body pairs");
            for (int x = 709; x <= 714; x++) for (int y = 99; y <= 103; y++)
                for (int z = 699; z <= 702; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!(x == 710 && z == 700)) originals.add(block.getState());
                    block.setType(y == 100 ? Material.STONE : Material.AIR, false);
                }
            for (int y = 101; y <= 102; y++) for (int z = 699; z <= 702; z++)
                world.getBlockAt(712, y, z).setType(Material.STONE, false);
            Location beforePull = arena.station(0), otherBeforePull = arena.station(1);
            List<Entity> pullEntities = created(world, before).stream().filter(entity -> {
                try { return ((Integer) invoke(station, physics, entity)) == 0; }
                catch (Exception error) { throw new RuntimeException(error); }
            }).toList();
            Location goal = new Location(world, 714.5, 101, 700.5, 130, 65);
            require((boolean) invoke(pull, physics, 0, goal, 100d),
                    "Winch pulls the real cashout body across a supporting floor");
            require(arena.station(0).distance(beforePull) > .34
                            && arena.station(0).distance(beforePull) <= .350001,
                    "Even a huge requested pull distance is capped at0.35 blocks per step");
            for (int index = 0; index < 12; index++) invoke(pull, physics, 0, goal, .35d);
            require(arena.station(0).getX() > 711.49 && arena.station(0).getX() <= 711.5,
                    "Repeated Winch steps stop the complete body before the native stone wall");
            for (Entity entity : pullEntities) {
                Location position = entity.getLocation();
                require(entity.isValid() && position.getYaw() == 0 && position.getPitch() == 0,
                        "Pulled cashout entities are reused and remain axis-aligned");
                close(position.getY(), arena.station(0).getY(), "Pulled native body height stays synchronized");
                close(position.getX() + (entity instanceof BlockDisplay ? .5 : 0), arena.station(0).getX(),
                        "Pulled native display and interaction share the arena's live center");
                close(position.getZ() + (entity instanceof BlockDisplay ? .5 : 0), arena.station(0).getZ(),
                        "Pulled native display and interaction share the arena's live depth");
            }
            require(arena.station(1).equals(otherBeforePull) && unchangedSlot.stream().allMatch(Entity::isValid),
                    "Pulling one cashout preserves the other cashout and its entity pair");
            require(!(boolean) invoke(pull, physics, 0, arena.station(0).clone().add(0, -2, 0), .35d)
                            && arena.station(0).getY() == 101,
                    "Winch cannot sink the cashout into its supporting floor");
            world.getBlockAt(711, 102, 700).setType(Material.STONE, false);
            require(!(boolean) invoke(pull, physics, 0, arena.station(0).clone().add(0, 2, 0), .35d)
                            && arena.station(0).getY() == 101,
                    "Winch cannot lift the full body through a native ceiling");
            require(created(world, before).size() == 4, "Winch movement never duplicates native cashout entities");
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

    private void alignedDisplay(Object physics, Method station, List<Entity> entities, int slot,
                                Location point, String phase) throws Exception {
        BlockDisplay display = null;
        Interaction interaction = null;
        for (Entity entity : entities) {
            if (((Integer) invoke(station, physics, entity)) != slot) continue;
            if (entity instanceof BlockDisplay block) display = block;
            if (entity instanceof Interaction click) interaction = click;
        }
        require(display != null && interaction != null, phase + ": native display and interaction exist");
        Location position = display.getLocation();
        require(position.getYaw() == 0 && position.getPitch() == 0,
                phase + ": saved editor facing cannot rotate or bury the visible body");
        var transform = display.getTransformation();
        Matrix4f matrix = new Matrix4f()
                .rotateY((float) Math.toRadians(-position.getYaw()))
                .rotateX((float) Math.toRadians(position.getPitch()))
                .translate(transform.getTranslation()).rotate(transform.getLeftRotation())
                .scale(transform.getScale()).rotate(transform.getRightRotation());
        double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
            Vector3f corner = matrix.transformPosition(new Vector3f(x, y, z));
            double[] coordinates = {position.getX() + corner.x, position.getY() + corner.y,
                    position.getZ() + corner.z};
            for (int axis = 0; axis < 3; axis++) {
                minimum[axis] = Math.min(minimum[axis], coordinates[axis]);
                maximum[axis] = Math.max(maximum[axis], coordinates[axis]);
            }
        }
        require(Math.abs(minimum[0] - (point.getX() - .5)) < 1e-6
                        && Math.abs(minimum[1] - point.getY()) < 1e-6
                        && Math.abs(minimum[2] - (point.getZ() - .5)) < 1e-6
                        && Math.abs(maximum[0] - (point.getX() + .5)) < 1e-6
                        && Math.abs(maximum[1] - (point.getY() + 1)) < 1e-6
                        && Math.abs(maximum[2] - (point.getZ() + .5)) < 1e-6,
                phase + ": all eight rendered corners match the simulated one-block body");
        var bounds = interaction.getBoundingBox();
        require(Math.abs(bounds.getMinX() - minimum[0]) < 1e-6
                        && Math.abs(bounds.getMinY() - minimum[1]) < 1e-6
                        && Math.abs(bounds.getMinZ() - minimum[2]) < 1e-6
                        && Math.abs(bounds.getMaxX() - maximum[0]) < 1e-6
                        && Math.abs(bounds.getMaxY() - maximum[1]) < 1e-6
                        && Math.abs(bounds.getMaxZ() - maximum[2]) < 1e-6,
                phase + ": native click bounds coincide with the rendered body");
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

    private void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-7, message + ": expected " + expected + ", got " + actual);
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
