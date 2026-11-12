import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;

/**
 * Real arena collision, deployable entities and block restoration; player transport is simulated.
 */
public final class FinalsSpecializationsSmoke {
    private final JavaPlugin smoke, main;
    private final World world = Bukkit.getWorlds().getFirst();
    private final Map<Block, BlockState> original = new LinkedHashMap<>();
    private final List<Entity> tracked = new ArrayList<>();
    private final List<P> players = new ArrayList<>();
    private Object gadgets, effects;
    private long now;
    private int checks;
    private Location damageOrigin;

    private FinalsSpecializationsSmoke(JavaPlugin smoke, JavaPlugin main) {
        this.smoke = smoke;
        this.main = main;
    }

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsSpecializationsSmoke(smoke, main).run();
    }

    private Class<?> type(String name) throws Exception {
        return Class.forName(
                "me.petr1furious.potatominigames.games.finals." + name,
                true,
                main.getClass().getClassLoader());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object kind(String name) throws Exception {
        return Enum.valueOf((Class) type("FinalsGadgetState$Kind"), name);
    }

    private static Object call(Object object, String name, Object... args) throws Exception {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                continue;
            boolean accepts = true;
            for (int i = 0; i < args.length; i++)
                if (args[i] != null
                        && !method.getParameterTypes()[i].isInstance(args[i])
                        && !method.getParameterTypes()[i].isPrimitive()) accepts = false;
            if (!accepts) continue;
            method.setAccessible(true);
            try {
                return method.invoke(object, args);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception x) throw x;
                if (e.getCause() instanceof Error x) throw x;
                throw e;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private Object construct(String name, Object... args) throws Exception {
        for (Constructor<?> ctor : type(name).getDeclaredConstructors())
            if (ctor.getParameterCount() == args.length) {
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            }
        throw new NoSuchMethodException(name);
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private void check(boolean good, String message) {
        checks++;
        if (!good) throw new AssertionError(message);
    }

    private P find(Player player) {
        return players.stream().filter(p -> p.player == player).findFirst().orElseThrow();
    }

    private Block set(int x, int y, int z, Material material) {
        Block b = world.getBlockAt(x, y, z);
        original.putIfAbsent(b, b.getState());
        b.setType(material, false);
        return b;
    }

    private void tick(long tick) throws Exception {
        now = tick;
        call(gadgets, "tick");
    }

    private boolean activate(P player, String kind) throws Exception {
        return (boolean) call(effects, "activate", player.player, kind(kind));
    }

    private List<?> devices() throws Exception {
        return (List<?>) field(effects, "devices");
    }

    private void detonate(P player, String kind, Location point) throws Exception {
        call(gadgets, "detonate", player.player, kind(kind), point);
    }

    private int run() throws Exception {
        P red = new P("EffectRed", 0, 902.5, 101, 902.5),
                ally = new P("EffectAlly", 0, 904.5, 101, 902.5),
                blue = new P("EffectBlue", 1, 906.5, 101, 912.5);
        players.addAll(List.of(red, ally, blue));
        Class<?> contextType = type("FinalsCombatContext");
        Object context =
                Proxy.newProxyInstance(
                        contextType.getClassLoader(),
                        new Class<?>[] {contextType},
                        (o, m, a) ->
                                switch (m.getName()) {
                                    case "plugin" -> main;
                                    case "world" -> world;
                                    case "tick" -> now;
                                    case "players" -> players.stream().map(p -> p.player).toList();
                                    case "active", "canAct" -> find((Player) a[0]).active;
                                    case "friendly" ->
                                            find((Player) a[0]).team == find((Player) a[1]).team;
                                    case "inside" -> {
                                        Location p = (Location) a[0];
                                        yield p.getWorld() == world
                                                && p.getX() >= 895
                                                && p.getX() < 935
                                                && p.getZ() >= 895
                                                && p.getZ() < 935
                                                && p.getY() >= 99
                                                && p.getY() < 115;
                                    }
                                    case "canPlace" -> true;
                                    case "destructible" ->
                                            ((Block) a[0]).getType() == Material.STONE;
                                    case "breakBlock" -> {
                                        Block b = (Block) a[0];
                                        original.putIfAbsent(b, b.getState());
                                        b.setType(Material.AIR, false);
                                        yield null;
                                    }
                                    case "track" -> {
                                        tracked.add((Entity) a[0]);
                                        yield null;
                                    }
                                    case "damage" -> {
                                        P target = find((Player) a[0]);
                                        target.health -= ((Number) a[2]).doubleValue();
                                        damageOrigin =
                                                a.length == 4
                                                        ? ((Location) a[3]).clone()
                                                        : ((Player) a[1]).getLocation();
                                        yield null;
                                    }
                                    case "reveal" -> {
                                        call(gadgets, "reveal", a[0]);
                                        yield null;
                                    }
                                    case "playerClass" ->
                                            Enum.valueOf((Class) type("FinalsClass"), "MEDIUM");
                                    case "deviceDistance" -> call(gadgets, "deviceDistance", a);
                                    case "attackDevice" -> call(gadgets, "attackDevice", a);
                                    case "weaponBlocked" -> call(gadgets, "weaponBlocked", a);
                                    default -> zero(m.getReturnType());
                                });
        gadgets = construct("FinalsGadgets", context);
        effects = field(gadgets, "effects");
        try {
            for (int x = 900; x <= 920; x++)
                for (int z = 900; z <= 920; z++) set(x, 100, z, Material.STONE);
            for (int x = 900; x <= 920; x++)
                for (int y = 101; y <= 106; y++)
                    for (int z = 900; z <= 920; z++) set(x, y, z, Material.AIR);
            red.target = world.getBlockAt(906, 100, 906);
            check(
                    activate(red, "GUARDIAN_TURRET"),
                    "A selected Guardian Turret creates a real deployable");
            Object turret = devices().getFirst();
            Entity display = (Entity) field(turret, "display");
            check(
                    display.isValid() && !display.isPersistent(),
                    "Guardian visual is a real session-owned native entity");
            tick(39);
            check(blue.health == 25, "Turret cannot fire before its arming deadline");
            tick(40);
            check(
                    blue.health < 25 && ally.health == 25,
                    "Armed turret damages visible enemies but not allies");
            check(
                    damageOrigin != null && Math.abs(damageOrigin.getX() - 906.5) < .01,
                    "Turret damage carries its own origin rather than the owner's location");
            double health = blue.health;
            for (int y = 101; y <= 103; y++) set(906, y, 909, Material.STONE);
            tick(50);
            check(blue.health == health, "A real terrain wall prevents turret attacks");
            for (int y = 101; y <= 103; y++) set(906, y, 909, Material.AIR);
            Location shot = new Location(world, 906.5, 101.45, 912.5);
            Vector toward = new Vector(0, 0, -1);
            check(
                    (double) call(gadgets, "deviceDistance", blue.player, shot, toward, 12d) < 6,
                    "Custom gun ray finds the real turret's damage volume");
            check(
                    (boolean) call(gadgets, "attackDevice", blue.player, shot, toward, 12d, 25d)
                            && !display.isValid(),
                    "Weapon damage destroys the native device and removes its visual");

            check(activate(red, "APS_TURRET"), "APS creates a real deployable");
            tick(70);
            Location a = new Location(world, 906.5, 101.5, 911),
                    b = new Location(world, 906.5, 101.5, 902);
            check(
                    !(boolean) call(effects, "intercept", ally.player, a, b),
                    "APS never consumes friendly projectiles");
            for (int i = 0; i < 6; i++)
                check(
                        (boolean) call(effects, "intercept", blue.player, a, b),
                        "APS catches a swept enemy projectile through its radius");
            check(devices().isEmpty(), "APS removes itself after its bounded interception budget");

            blue.location = new Location(world, 906.5, 101, 907.5);
            blue.health = 25;
            check(activate(red, "EXPLOSIVE_MINE"), "An explosive mine can be placed");
            tick(89);
            check(blue.health == 25, "A newly placed mine cannot detonate before arming");
            tick(90);
            check(
                    blue.health < 25 && devices().isEmpty(),
                    "An armed mine triggers from real nearby enemy geometry exactly once");
            red.target = set(906, 100, 906, Material.STONE);
            blue.location = new Location(world, 906.5, 101, 912.5);
            check(activate(red, "BREACH_CHARGE"), "Remote breach charge placement succeeds");
            tick(110);
            check(
                    (boolean) call(effects, "toggle", red.player, kind("BREACH_CHARGE")),
                    "A second activation remotely detonates an armed breach charge");
            check(
                    world.getBlockAt(906, 100, 906).getType() == Material.AIR,
                    "Breach explosion changes real destructible terrain");
            check(
                    !(boolean) call(effects, "toggle", red.player, kind("BREACH_CHARGE")),
                    "A detonated charge cannot explode twice");

            red.location = new Location(world, 902.5, 101, 902.5);
            ally.location = new Location(world, 903.5, 101, 902.5);
            blue.location = new Location(world, 902.5, 101, 912.5);
            Location cloud = new Location(world, 902.5, 102, 907.5);
            call(gadgets, "vanish", blue.player, 200L);
            detonate(red, "SMOKE_GRENADE", cloud);
            tick(112);
            check(
                    red.hidden.contains(blue.id) && !red.hidden.contains(ally.id),
                    "Smoke obscures real enemy sight lines while preserving teammate visibility");
            call(gadgets, "reveal", blue.player);
            check(
                    red.hidden.contains(blue.id),
                    "Ending cloak cannot reveal a player still obscured by smoke");
            call(gadgets, "vanish", blue.player, 200L);
            tick(272);
            check(
                    red.hidden.contains(blue.id),
                    "Smoke expiry preserves an independently active cloak");
            call(gadgets, "reveal", blue.player);
            check(!red.hidden.contains(blue.id), "The final visibility owner restores the enemy");
            detonate(red, "FLASHBANG", blue.location.clone().add(0, 1, 0));
            check(
                    blue.potions.containsKey(PotionEffectType.BLINDNESS) && ally.potions.isEmpty(),
                    "Flash applies real Bukkit blindness only to visible enemies");
            now += 60;
            blue.potions.clear();
            tick(now);
            check(
                    ((Map<?, ?>) field(effects, "flashes")).isEmpty(),
                    "Flash lifecycle forgets its expired effect");
            blue.health = 25;
            ally.location = blue.location.clone().add(1, 0, 0);
            detonate(red, "GAS_GRENADE", blue.location);
            tick(336);
            check(
                    blue.health < 25 && ally.health == 25,
                    "Gas damages enemies through the attributed combat path without friendly"
                        + " damage");
            detonate(red, "PYRO_GRENADE", blue.location);
            check(
                    ((List<?>) field(effects, "clouds")).size() == 1,
                    "Fire consumes an overlapping gas cloud");
            detonate(red, "SMOKE_GRENADE", blue.location);
            check(
                    ((List<?>) field(effects, "clouds")).size() == 1,
                    "Smoke extinguishes an overlapping fire zone");

            call(gadgets, "clear");
            now = 400;
            red.location = new Location(world, 902.5, 101, 902.5, 0, 0);
            for (int x = 901; x <= 903; x++)
                for (int y = 101; y <= 103; y++) set(x, y, 906, Material.STONE);
            check(activate(red, "DEMATERIALIZER"), "Dematerializer opens a real destructible wall");
            Block hole = world.getBlockAt(902, 102, 906);
            check(hole.getType() == Material.AIR, "The selected wall becomes passable terrain");
            blue.location = new Location(world, 902.5, 101.5, 906.5);
            tick(560);
            check(
                    hole.getType() == Material.AIR,
                    "Restoration waits while a player occupies the opening");
            blue.location = new Location(world, 910.5, 101, 912.5);
            tick(561);
            check(
                    hole.getType() == Material.STONE,
                    "The original block restores when the opening becomes clear");
            check(activate(red, "DEMATERIALIZER"), "A second dematerialization succeeds");
            call(gadgets, "putCover", red.player, hole, Material.WHITE_CONCRETE, 200L);
            tick(721);
            check(
                    hole.getType() == Material.WHITE_CONCRETE
                            && ((Map<?, ?>) field(effects, "holes")).containsKey(hole),
                    "A temporary goo block defers restoration without discarding original terrain");
            tick(761);
            tick(762);
            check(
                    hole.getType() == Material.STONE,
                    "The original wall returns after goo placed inside a dematerialized opening"
                        + " expires");
            check(activate(red, "DEMATERIALIZER"), "A third dematerialization succeeds");
            hole.setType(Material.WATER, false);
            tick(922);
            check(
                    hole.getType() == Material.STONE,
                    "Fluid flowing into a dematerialized opening does not discard its original"
                        + " wall");
            check(
                    activate(red, "DEMATERIALIZER"),
                    "A later dematerialization still works after fluid restoration");
            hole.setType(Material.GOLD_BLOCK, false);
            tick(1082);
            check(
                    hole.getType() == Material.GOLD_BLOCK,
                    "Restoration preserves later unrelated terrain edits");

            for (int x = 901; x <= 903; x++)
                for (int y = 101; y <= 103; y++) set(x, y, 906, Material.AIR);
            set(902, 102, 914, Material.STONE);
            red.location = new Location(world, 902.5, 101, 902.5, 0, 0);
            set(903, 102, 903, Material.STONE);
            check(
                    !(boolean) call(effects, "clearMovement", red.player, new Vector(.4, 0, .4)),
                    "Movement checks the complete shoulder/head volume against an off-center voxel"
                        + " obstacle");
            set(903, 102, 903, Material.AIR);
            check(
                    (boolean) call(effects, "clearMovement", red.player, new Vector(.4, 0, .4)),
                    "The same body movement succeeds after its obstacle is removed");
            check(activate(red, "GRAPPLING_HOOK"), "Grapple acquires an actual terrain surface");
            tick(1083);
            check(
                    red.velocity.getZ() > 0 && red.velocity.length() <= .86,
                    "Grapple pulls toward the surface with bounded velocity");
            set(902, 102, 905, Material.STONE);
            red.velocity = new Vector();
            tick(1084);
            check(
                    ((Map<?, ?>) field(effects, "grapples")).isEmpty()
                            && red.velocity.lengthSquared() == 0,
                    "A new wall cancels grapple instead of pulling through it");
            set(902, 102, 905, Material.AIR);
            blue.location = new Location(world, 902.5, 101, 909.5);
            check(activate(red, "WINCH_CLAW"), "Winch acquires a visible enemy");
            tick(1085);
            check(blue.velocity.getZ() < 0, "Winch pulls the enemy toward the owner");
            red.lineOfSight = false;
            tick(1086);
            check(
                    ((Map<?, ?>) field(effects, "winches")).isEmpty(),
                    "Losing line of sight cancels a live winch");
            red.lineOfSight = true;
            check(activate(red, "MESH_SHIELD"), "Mesh specialization activates");
            check(
                    (boolean)
                            call(
                                    gadgets,
                                    "blocksDamage",
                                    red.player,
                                    blue.player,
                                    new Location(world, 902.5, 102, 909.5)),
                    "Mesh blocks an attack whose true origin crosses its front");
            check(
                    !(boolean)
                            call(
                                    gadgets,
                                    "blocksDamage",
                                    red.player,
                                    blue.player,
                                    new Location(world, 902.5, 102, 899.5)),
                    "Mesh cannot block an attack originating behind it");
            red.slot = 0;
            check(
                    !(boolean) call(gadgets, "weaponBlocked", red.player),
                    "Changing held slots immediately releases Mesh weapon restriction");
            call(gadgets, "clear");
            check(
                    tracked.stream().noneMatch(Entity::isValid),
                    "Match cleanup removes every native gadget entity");
            check(
                    players.stream().allMatch(p -> p.hidden.isEmpty()),
                    "Match cleanup restores all smoke and cloak visibility registrations");
            smoke.getLogger().info("FINALS_SPECIALIZATIONS_SMOKE_PASS: " + checks + " assertions");
            return checks;
        } finally {
            if (gadgets != null) call(gadgets, "clear");
            tracked.forEach(Entity::remove);
            original.values().forEach(state -> state.update(true, false));
        }
    }

    private final class P {
        final UUID id = UUID.randomUUID();
        final int team;
        final Player player;
        final Set<UUID> hidden = new HashSet<>();
        final Map<PotionEffectType, PotionEffect> potions = new HashMap<>();
        Location location;
        Vector velocity = new Vector();
        Block target;
        boolean active = true, lineOfSight = true;
        int slot = 4;
        double health = 25;

        P(String name, int team, double x, double y, double z) {
            this.team = team;
            location = new Location(world, x, y, z);
            PlayerInventory inventory =
                    (PlayerInventory)
                            Proxy.newProxyInstance(
                                    PlayerInventory.class.getClassLoader(),
                                    new Class<?>[] {PlayerInventory.class},
                                    (o, m, a) ->
                                            m.getName().equals("getHeldItemSlot")
                                                    ? slot
                                                    : zero(m.getReturnType()));
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (o, m, a) ->
                                            switch (m.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getName" -> name;
                                                case "getWorld" -> world;
                                                case "getLocation" -> location.clone();
                                                case "getEyeLocation" ->
                                                        location.clone().add(0, 1.62, 0);
                                                case "getInventory" -> inventory;
                                                case "getHealth" -> health;
                                                case "setHealth" -> {
                                                    health = (double) a[0];
                                                    yield null;
                                                }
                                                case "getTargetBlockExact" -> target;
                                                case "hasLineOfSight" -> lineOfSight;
                                                case "getVelocity" -> velocity.clone();
                                                case "setVelocity" -> {
                                                    velocity = ((Vector) a[0]).clone();
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
                                                case "getPotionEffect" ->
                                                        potions.get((PotionEffectType) a[0]);
                                                case "addPotionEffect" -> {
                                                    PotionEffect p = (PotionEffect) a[0];
                                                    potions.put(p.getType(), p);
                                                    yield true;
                                                }
                                                case "removePotionEffect" -> {
                                                    potions.remove((PotionEffectType) a[0]);
                                                    yield null;
                                                }
                                                case "hidePlayer" -> {
                                                    hidden.add(((Player) a[1]).getUniqueId());
                                                    yield null;
                                                }
                                                case "showPlayer" -> {
                                                    hidden.remove(((Player) a[1]).getUniqueId());
                                                    yield null;
                                                }
                                                case "isOnline", "isValid" -> true;
                                                case "locale" -> Locale.ENGLISH;
                                                case "equals" -> o == a[0];
                                                case "hashCode" -> id.hashCode();
                                                default -> zero(m.getReturnType());
                                            });
        }
    }
}
