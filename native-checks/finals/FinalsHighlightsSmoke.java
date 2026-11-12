import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.petr1furious.potatominigames.games.finals.*;

/** Native display metadata/lifecycle checks; only player transport is simulated. */
public final class FinalsHighlightsSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsHighlightsSmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        FixtureArena arena = new FixtureArena(world);
        // Fake players do not load their surrounding chunks as real connected players would.
        // Native display spawns need the same loaded arena that the match normally supplies.
        for (int chunkX = 29; chunkX <= 33; chunkX++) for (int chunkZ = 30; chunkZ <= 33; chunkZ++)
            world.getChunkAt(chunkX, chunkZ).load();
        require(arena.sources().stream().allMatch(point -> world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)),
                "Native fixture objective chunks are loaded before display creation");
        FakePlayer alice = new FakePlayer(world, "HighlightAlice", 500, 500);
        FakePlayer bob = new FakePlayer(world, "HighlightBob", 505, 500);
        bob.locale = Locale.forLanguageTag("ru");
        FakePlayer eve = new FakePlayer(world, "HighlightEve", 500, 505);
        FakePlayer frank = new FakePlayer(world, "HighlightFrank", 505, 505);
        FakePlayer dead = new FakePlayer(world, "HighlightDead", 510, 505);
        FakePlayer outsider = new FakePlayer(world, "HighlightOutsider", 510, 500);
        List<FakePlayer> fixtures = List.of(alice, bob, eve, frank, dead, outsider);
        List<Player> players = fixtures.stream().map(fake -> fake.player).toList();
        Map<UUID, Integer> teams = Map.of(alice.id, 0, bob.id, 0, dead.id, 0, eve.id, 1, frank.id, 1);
        Set<UUID> inactive = new HashSet<>(Set.of(dead.id));
        Predicate<Player> active = player -> !inactive.contains(player.getUniqueId());

        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHighlights", true,
                main.getClass().getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(JavaPlugin.class);
        constructor.setAccessible(true);
        Object highlights = constructor.newInstance(main);
        Method update = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                Collection.class, Map.class, Predicate.class);
        Method remove = method(type, "remove", UUID.class);
        Method clear = method(type, "clear");
        SpawnObserver observer = new SpawnObserver();
        Bukkit.getPluginManager().registerEvents(observer, smoke);
        try {
            List<Location> sources = arena.sources();
            invoke(update, highlights, arena, null, players, teams, active);
            String counts = fixtures.stream().map(FakePlayer::describe).collect(java.util.stream.Collectors.joining("; "));
            smoke.getLogger().info("FINALS_HIGHLIGHTS_INITIAL_COUNTS: " + counts
                    + "; native spawn events=" + observer.spawned.size());
            require(alice.active().size() == 7 && bob.active().size() == 7 && eve.active().size() == 7,
                    "Each living viewer receives three private objective pairs and one living ally label: " + counts);
            require(dead.active().size() == 8,
                    "A respawning viewer retains objective pairs and both living teammate labels: " + dead.describe());
            require(outsider.active().isEmpty(), "Players outside the roster receive no match markers");
            require(Collections.disjoint(alice.active(), bob.active())
                            && Collections.disjoint(alice.active(), eve.active()),
                    "Each viewer receives an independent set of native display entities");
            require(alice.blocks().size() == 3 && alice.texts().size() == 4,
                    "Objectives have block and text displays while teammates have only text");
            require(hasLabel(alice, "Vault") && hasLabel(alice, "Cashout A") && hasLabel(alice, "Cashout B"),
                    "English objectives render their localized names and cashout letters");
            require(hasLabel(bob, "Хранилище") && hasLabel(bob, "Касса A") && hasLabel(bob, "Касса B"),
                    "Russian viewers receive rendered Russian objective labels");
            require(hasLabel(alice, bob.name) && !hasLabel(alice, alice.name)
                            && !hasLabel(alice, eve.name) && !hasLabel(alice, frank.name)
                            && !hasLabel(alice, dead.name) && !hasLabel(alice, outsider.name),
                    "Ally labels exclude self, enemies, inactive targets and unregistered players");
            require(hasColor(label(alice, bob.name).text(), NamedTextColor.RED)
                            && hasColor(label(eve, frank.name).text(), NamedTextColor.BLUE),
                    "Private ally labels use the viewer's red or blue team color");
            for (FakePlayer viewer : fixtures) for (Display display : viewer.active()) {
                require(!display.isVisibleByDefault(), "Every native marker is hidden from unrelated clients");
                require(!display.isPersistent(), "Private markers cannot survive a world save");
                require(display.isInvulnerable(), "Private markers cannot be damaged");
                Display.Brightness brightness = display.getBrightness();
                require(brightness != null && brightness.getBlockLight() == 15 && brightness.getSkyLight() == 15,
                        "Native markers remain fully bright in dark or covered locations");
                SpawnMetadata initial = observer.spawned.get(display.getUniqueId());
                if (initial != null) {
                    require(!initial.visible, "Native visibility is private before the entity spawn event");
                    require(!initial.persistent && initial.invulnerable,
                            "Native spawn initialization already sets transient invulnerable metadata");
                }
                if (display instanceof TextDisplay text) {
                    require(text.isSeeThrough(), "Native labels are visible through walls");
                    require(text.isShadowed(), "Native labels retain a readable text shadow");
                    require(text.getBillboard() == Display.Billboard.CENTER, "Native labels face their viewer");
                } else if (display instanceof BlockDisplay block) {
                    boolean vault = block.getBlock().getMaterial() == Material.EMERALD_BLOCK;
                    require(vault || block.getBlock().getMaterial() == Material.GOLD_BLOCK,
                            "Objective highlight geometry uses the vault or cashout block");
                    require(block.isGlowing(), "Native objective block geometry has a glow outline");
                    require(Color.fromRGB((vault ? NamedTextColor.GREEN : NamedTextColor.GOLD).value())
                                    .equals(block.getGlowColorOverride()),
                            "Native objective glow override matches its green or gold marker color");
                } else throw new AssertionError("Unexpected native marker type: " + display.getType());
            }
            require(sameLocations(sources, arena.sources()), "Rendering clones geometry instead of mutating arena locations");

            Set<Display> first = alice.active();
            Set<Display> allFirst = allActive(fixtures);
            int spawnCount = observer.spawned.size();
            invoke(update, highlights, arena, null, players, teams, active);
            require(first.equals(alice.active()) && allFirst.equals(allActive(fixtures)),
                    "Repeated updates reuse all native marker entities without accumulating duplicates");
            require(observer.spawned.size() == spawnCount,
                    "An unchanged update does not spawn replacement or orphan native entities");
            bob.locale = Locale.ENGLISH;
            invoke(update, highlights, arena, null, players, teams, active);
            require(allFirst.equals(allActive(fixtures)) && hasLabel(bob, "Vault") && !hasLabel(bob, "Хранилище"),
                    "Changing client language updates localized text without replacing native entities");
            bob.locale = Locale.forLanguageTag("ru");
            TextDisplay vault = label(alice, "Vault");
            TextDisplay cashoutA = label(alice, "Cashout A");
            TextDisplay cashoutB = label(alice, "Cashout B");
            TextDisplay ally = label(alice, bob.name);
            BlockDisplay vaultBlock = alice.blocks().stream()
                    .filter(block -> block.getBlock().getMaterial() == Material.EMERALD_BLOCK).findFirst().orElseThrow();
            BlockDisplay stationA = nearestBlock(alice, arena.station(0));
            BlockDisplay stationB = nearestBlock(alice, arena.station(1));
            Location oldVault = vault.getLocation(), oldVaultBlock = vaultBlock.getLocation();
            Location oldA = cashoutA.getLocation(), oldABlock = stationA.getLocation();
            Location oldB = cashoutB.getLocation(), oldBBlock = stationB.getLocation();
            Location oldAlly = ally.getLocation();
            arena.nextVault();
            arena.nextCashout(0);
            bob.location.add(3, 1, -2);
            List<Location> rotatedSources = arena.sources();
            invoke(update, highlights, arena, FinalsState.Phase.VAULT_CLOSED, players, teams, active);
            require(first.equals(alice.active()), "Objective rotation and ally movement teleport existing native displays");
            moved(vault, oldVault, 8, 0, 4, "Vault text follows the new objective location");
            moved(vaultBlock, oldVaultBlock, 8, 0, 4, "Vault glow geometry follows the new objective location");
            moved(cashoutA, oldA, -6, 0, 8, "Completed cashout text follows its replacement candidate");
            moved(stationA, oldABlock, -6, 0, 8, "Completed cashout geometry follows its replacement candidate");
            moved(cashoutB, oldB, 0, 0, 0, "Rotating one cashout preserves the other text location");
            moved(stationB, oldBBlock, 0, 0, 0, "Rotating one cashout preserves the other geometry location");
            moved(ally, oldAlly, 3, 1, -2, "Private ally text follows the target player's position");
            require(sameLocations(rotatedSources, arena.sources()), "Refreshes preserve mutable objective source locations");

            invoke(update, highlights, arena, FinalsState.Phase.VAULT_OPENING, players, teams, active);
            require(alice.active().equals(first), "Opening a vault keeps the same vault display pair visible");
            for (FinalsState.Phase phase : List.of(FinalsState.Phase.CASHBOX_AVAILABLE,
                    FinalsState.Phase.CARRIED, FinalsState.Phase.CASHOUT)) {
                invoke(update, highlights, arena, phase, players, teams, active);
                require(alice.active().size() == 5 && alice.blocks().size() == 2 && !hasLabel(alice, "Vault"),
                        "A vault display disappears once its cashbox is available, carried or deposited: " + phase);
            }
            require(!vault.isValid() && !vaultBlock.isValid(), "Hidden vault phases remove both native vault entities");
            invoke(update, highlights, arena, FinalsState.Phase.VAULT_CLOSED, players, teams, active);
            require(alice.active().size() == 7 && hasLabel(alice, "Vault"), "The next closed vault receives a fresh display pair");
            inactive.add(bob.id);
            invoke(update, highlights, arena, FinalsState.Phase.VAULT_CLOSED, players, teams, active);
            require(!hasLabel(alice, bob.name) && alice.active().size() == 6 && !ally.isValid(),
                    "An inactive target immediately loses its ally marker without losing objective markers");
            inactive.remove(bob.id);
            invoke(update, highlights, arena, FinalsState.Phase.VAULT_CLOSED, players, teams, active);
            require(hasLabel(alice, bob.name) && alice.active().size() == 7, "A revived teammate regains their private ally label");

            Set<Display> bobDisplays = bob.active();
            TextDisplay bobMarker = label(alice, bob.name);
            Set<Display> eveDisplays = eve.active();
            invoke(remove, highlights, bob.id);
            require(bob.active().isEmpty() && bobDisplays.stream().noneMatch(Entity::isValid),
                    "Removing a participant destroys every display owned by that viewer");
            require(!bobMarker.isValid() && !hasLabel(alice, bob.name) && alice.active().size() == 6,
                    "Removing a participant also destroys their private ally marker in remaining views");
            require(eveDisplays.equals(eve.active()), "Removing a red participant preserves the blue team's displays");
            List<Player> remaining = players.stream().filter(player -> !player.getUniqueId().equals(bob.id)).toList();
            invoke(update, highlights, arena, FinalsState.Phase.FINISHED, remaining, teams, active);
            require(allActive(fixtures).isEmpty(), "A finished match clears all native objective and teammate markers");
            invoke(update, highlights, arena, null, remaining, teams, active);
            Set<Display> beforeClear = allActive(fixtures);
            require(!beforeClear.isEmpty(), "The same helper can create countdown overlays after clearing a finished match");
            invoke(clear, highlights);
            require(allActive(fixtures).isEmpty() && beforeClear.stream().noneMatch(Entity::isValid),
                    "Explicit cleanup removes every remaining real native display entity");
            require(observer.spawned.values().stream().noneMatch(initial -> initial.entity.isValid()),
                    "Cleanup also leaves no orphan entities among all observed native display spawns");
            invoke(clear, highlights);
            invoke(remove, highlights, UUID.randomUUID());
            require(allActive(fixtures).isEmpty(), "Repeated cleanup and removal of an unknown participant remain safe");
            smoke.getLogger().info("FINALS_HIGHLIGHTS_SMOKE_PASS: " + assertions + " native assertions; "
                    + observer.spawned.size() + " display spawn events observed");
            return assertions;
        } finally {
            try { invoke(clear, highlights); }
            finally { HandlerList.unregisterAll(observer); }
        }
    }

    private void moved(Entity entity, Location before, double dx, double dy, double dz, String message) {
        Location after = entity.getLocation();
        require(after.getWorld().equals(before.getWorld()) && Math.abs(after.getX() - before.getX() - dx) < .0001
                && Math.abs(after.getY() - before.getY() - dy) < .0001
                && Math.abs(after.getZ() - before.getZ() - dz) < .0001, message);
    }

    private static BlockDisplay nearestBlock(FakePlayer viewer, Location point) {
        return viewer.blocks().stream().min(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(point)))
                .orElseThrow();
    }

    private static boolean sameLocations(List<Location> first, List<Location> second) { return first.equals(second); }

    private static Set<Display> allActive(Collection<FakePlayer> players) {
        Set<Display> result = new LinkedHashSet<>();
        for (FakePlayer player : players) result.addAll(player.active());
        return result;
    }

    private static String plain(TextDisplay display) {
        return PlainTextComponentSerializer.plainText().serialize(display.text());
    }

    private static boolean hasLabel(FakePlayer viewer, String fragment) {
        return viewer.texts().stream().anyMatch(text -> plain(text).contains(fragment));
    }

    private static TextDisplay label(FakePlayer viewer, String fragment) {
        List<TextDisplay> matches = viewer.texts().stream().filter(text -> plain(text).contains(fragment)).toList();
        if (matches.size() != 1) throw new AssertionError("Expected one label containing " + fragment + ", got " + matches.size());
        return matches.getFirst();
    }

    private static boolean hasColor(Component component, TextColor color) {
        return color.equals(component.color()) || component.children().stream().anyMatch(child -> hasColor(child, color));
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    public static final class SpawnObserver implements Listener {
        final Map<UUID, SpawnMetadata> spawned = new HashMap<>();
        @EventHandler(priority = EventPriority.MONITOR)
        public void onSpawn(EntitySpawnEvent event) {
            if (event.getEntity() instanceof Display display)
                spawned.put(display.getUniqueId(), new SpawnMetadata(display, display.isVisibleByDefault(),
                        display.isPersistent(), display.isInvulnerable()));
        }
    }

    private record SpawnMetadata(Display entity, boolean visible, boolean persistent, boolean invulnerable) {}

    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        final String name;
        final Player player;
        final Set<Display> shown = new LinkedHashSet<>();
        Location location;
        Locale locale = Locale.ENGLISH;

        FakePlayer(World world, String name, double x, double z) {
            this.name = name;
            location = new Location(world, x, 65, z);
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "displayName" -> Component.text(name);
                        case "getWorld" -> location.getWorld();
                        case "getLocation" -> location.clone();
                        case "getEyeLocation" -> location.clone().add(0, 1.62, 0);
                        case "locale" -> locale;
                        case "isOnline", "isValid" -> true;
                        case "isDead" -> false;
                        case "showEntity" -> {
                            if (!(args[1] instanceof Display display))
                                throw new AssertionError("Only private display entities may be shown to a viewer");
                            shown.add(display);
                            yield null;
                        }
                        case "hideEntity" -> { shown.remove(args[1]); yield null; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        case "toString" -> "HighlightPlayer[" + name + "]";
                        default -> throw new AssertionError("Unexpected player transport call: " + method.getName());
                    });
        }

        Set<Display> active() {
            Set<Display> active = new LinkedHashSet<>();
            for (Display display : shown) if (display.isValid()) active.add(display);
            return active;
        }
        List<TextDisplay> texts() { return active().stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast).toList(); }
        List<BlockDisplay> blocks() { return active().stream().filter(BlockDisplay.class::isInstance).map(BlockDisplay.class::cast).toList(); }
        String describe() {
            return name + "[shown=" + shown.size() + ",valid=" + active().size() + ",text=" + texts().size()
                    + ",block=" + blocks().size() + ",invalid="
                    + shown.stream().filter(display -> !display.isValid()).map(display -> display.getType()
                            + "@" + display.getLocation().getBlockX() + "," + display.getLocation().getBlockZ()
                            + "(chunkLoaded=" + display.getWorld().isChunkLoaded(display.getLocation().getBlockX() >> 4,
                                    display.getLocation().getBlockZ() >> 4) + ")").toList() + "]";
        }
    }

    /** Deliberately exposes mutable source Locations to catch accidental add()/subtract() mutation. */
    private static final class FixtureArena implements FinalsMapArena {
        final World world;
        final Location vault;
        final Location[] stations;
        FixtureArena(World world) {
            this.world = world;
            vault = new Location(world, 500, 65, 500);
            stations = new Location[]{new Location(world, 480, 65, 500), new Location(world, 520, 65, 500)};
        }
        List<Location> sources() { return List.of(vault.clone(), stations[0].clone(), stations[1].clone()); }
        public World world() { return world; }
        public void build() {}
        public Location center() { return new Location(world, 500, 65, 500); }
        public Location spawn(int team, int index) { return new Location(world, 500, 65, 500 + team * 5); }
        public Location vault() { return vault; }
        public List<Location> vaultLocations() { return List.of(vault); }
        public void nextVault() { vault.add(8, 0, 4); }
        public Location station(int station) { return stations[station]; }
        public List<Location> cashoutLocations() { return List.of(stations); }
        public void nextCashout(int station) { stations[station].add(-6, 0, 8); }
        public boolean contains(Location point) { return world.equals(point.getWorld()); }
        public boolean isCover(Block block) { return false; }
        public int stationAt(Block block) { return -1; }
        public boolean isVault(Block block) { return false; }
        public double borderSize() { return 100; }
    }
}
