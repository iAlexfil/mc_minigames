import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.games.finals.FinalsMapArena;
import me.petr1furious.potatominigames.games.finals.FinalsMaps;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Callback-driven real-server checks; never waits or sleeps on the server thread. */
public final class FinalsAsyncMapSmoke {
    public static void run(
            JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
        Runner runner = new Runner(smoke, main, done, failed);
        runner.later(1, runner::start);
    }

    private interface Check { void run() throws Exception; }

    private static final class Runner {
        private final JavaPlugin smoke, main;
        private final Runnable done;
        private final Consumer<Throwable> failed;
        private final List<BukkitTask> tasks = new ArrayList<>();
        private final String suffix = UUID.randomUUID().toString().substring(0, 8);
        private final String savedName = "asyncproof_" + suffix;
        private final String cancelledName = "asynccancel_" + suffix;
        private final String blockedName = "asyncblocked_" + suffix;
        private FinalsMaps maps;
        private SessionWorlds worlds;
        private SessionServices services;
        private FinalsMapArena arena;
        private World editor;
        private int assertions, heartbeat;
        private boolean finished, captureReturned, buildReturned;
        private boolean cancelledCaptureCallback, cancelledBuildCallback;
        private byte[] registryBeforeCancellation;

        Runner(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
            this.smoke = smoke;
            this.main = main;
            this.done = done;
            this.failed = failed;
        }

        private void start() {
            require(Bukkit.isPrimaryThread(), "Fixture runs through the server scheduler");
            tasks.add(Bukkit.getScheduler().runTaskTimer(smoke, () -> heartbeat++, 1, 1));
            later(1_200, () -> { throw new AssertionError("Async map fixture exceeded 60 seconds"); });
            editor = Bukkit.createWorld(new WorldCreator("finals_async_editor_" + suffix)
                    .generateStructures(false)
                    .keepSpawnLoaded(TriState.FALSE)
                    .generator(new ChunkGenerator() {
                        @Override public Location getFixedSpawnLocation(World world, Random random) {
                            return new Location(world, 4.5, 65, 16.5);
                        }
                    }));
            require(editor != null, "Isolated editor world loads");
            editor.setGameRule(GameRules.SPAWN_MOBS, false);
            editor.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
            for (int x = 0; x <= 32; x++)
                for (int z = 0; z <= 32; z++)
                    editor.getBlockAt(x, 64, z).setType(Material.STONE, false);
            editor.getBlockAt(8, editor.getMinHeight(), 8).setType(Material.COPPER_BLOCK, false);
            editor.getBlockAt(8, editor.getMaxHeight() - 1, 8).setType(Material.LAPIS_BLOCK, false);
            maps = new FinalsMaps(main);
            configure(savedName);
            configure(blockedName);
            checkUnsafePoints();
            worlds = new SessionWorlds(new ThrowawayWorldService(main), null);
            services = new SessionServices(main, null, null, null, null, null, null, null,
                    worlds, null, null, null);
            int started = heartbeat;
            maps.saveAsync(savedName, () -> guard(() -> {
                require(captureReturned, "Capture success callback is deferred until after saveAsync returns");
                require(heartbeat > started, "Server heartbeat runs while native capture is pending");
                require(maps.names().contains(savedName), "Capture commits the saved registry before success");
                require(new FinalsMaps(main).names().contains(savedName), "Async capture survives registry reload");
                buildSavedMap();
            }), this::fail);
            captureReturned = true;
            require(!maps.names().contains(savedName), "Starting capture does not immediately publish a saved map");
            boolean blocked = false;
            try {
                maps.saveAsync(blockedName,
                        () -> fail(new AssertionError("Concurrent capture unexpectedly completed")), this::fail);
            } catch (IllegalArgumentException expected) {
                blocked = true;
            }
            require(blocked, "Different map names cannot capture the same editor world concurrently");
            require(!maps.names().contains(savedName) && !maps.names().contains(blockedName),
                    "Rejecting a competing capture leaves both saved map states unchanged");
        }

        private void checkUnsafePoints() {
            for (int x : new int[] {4, 16, 28})
                editor.getBlockAt(x, 65, 16).setType(Material.STONE, false);
            try {
                boolean rejected = false;
                try {
                    maps.saveAsync(savedName,
                            () -> fail(new AssertionError("Unsafe capture unexpectedly completed")), this::fail);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                    String message = I18n.plain(I18n.message(expected), Locale.ENGLISH);
                    for (String point : List.of("4, 65, 16", "16, 65, 16", "28, 65, 16"))
                        require(message.contains(point), "Unsafe save diagnostics include every blocked coordinate: " + point);
                    for (String label : List.of("red_spawn", "blue_spawn", "vault"))
                        require(message.contains(I18n.plain(I18n.tr("finals.map.point." + label, 1), Locale.ENGLISH)),
                                "Unsafe save diagnostics retain each numbered point label: " + label);
                }
                require(rejected && !maps.names().contains(savedName),
                        "All physically invalid points reject capture before publishing a snapshot");
            } finally {
                for (int x : new int[] {4, 16, 28})
                    editor.getBlockAt(x, 65, 16).setType(Material.AIR, false);
            }
        }

        private void configure(String name) {
            maps.create(name, at(4.5, 65, 16.5));
            maps.corner(name, 0, at(0, 65, 0));
            maps.corner(name, 1, at(32, 65, 32));
            maps.spawn(name, 0, new Location(editor, 4.5, 65, 16.5, -90, 0));
            maps.spawn(name, 1, new Location(editor, 28.5, 65, 16.5, 90, 0));
            maps.vault(name, at(16.5, 65, 16.5));
            maps.station(name, 0, at(16.5, 65, 4.5));
            maps.station(name, 1, at(16.5, 65, 28.5));
        }

        private void buildSavedMap() {
            arena = maps.open(services, savedName);
            int started = heartbeat;
            arena.buildAsync(smoke, () -> guard(() -> {
                require(buildReturned, "Build success callback is deferred until after buildAsync returns");
                require(heartbeat > started, "Server heartbeat runs while native placement is pending");
                World match = arena.world();
                require(match != editor, "Async placement uses a disposable match world");
                require(match.getBlockAt(8, editor.getMinHeight(), 8).getType() == Material.COPPER_BLOCK,
                        "Async placement preserves the world's bottom layer");
                require(match.getBlockAt(8, editor.getMaxHeight() - 1, 8).getType() == Material.LAPIS_BLOCK,
                        "Async placement preserves the world's highest layer");
                Location spawn = arena.spawn(0, 0);
                require(spawn.getYaw() == -90 && spawn.getBlock().getType() == Material.AIR
                                && spawn.clone().subtract(0, 1, 0).getBlock().getType() == Material.STONE,
                        "Success exposes ready spawns, their facing and supporting floor");
                require(arena.vault().getBlock().getType() == Material.EMERALD_BLOCK
                                && arena.station(0).getBlock().getType() == Material.AIR
                                && arena.station(1).getBlock().getType() == Material.AIR,
                        "Success exposes the vault marker and empty anchors for cashout entities");
                cancelCapture();
            }), this::fail);
            buildReturned = true;
        }

        private void cancelCapture() throws Exception {
            configure(cancelledName);
            registryBeforeCancellation = Files.readAllBytes(registry());
            maps.saveAsync(cancelledName, () -> cancelledCaptureCallback = true,
                    error -> cancelledCaptureCallback = true);
            // The capture's first scheduled piece runs before this cancellation task.
            later(1, () -> {
                require(!cancelledCaptureCallback, "Capture remains pending before cancellation");
                maps.close();
                later(5, () -> {
                    require(!cancelledCaptureCallback, "Closing captures prevents both terminal callbacks");
                    require(Arrays.equals(registryBeforeCancellation, Files.readAllBytes(registry())),
                            "Cancelled capture leaves persisted registry bytes unchanged");
                    require(!new FinalsMaps(main).names().contains(cancelledName),
                            "A partial capture never becomes a saved map after reload");
                    Path templates = main.getDataFolder().toPath().resolve("finals/templates");
                    try (var paths = Files.list(templates)) {
                        require(paths.noneMatch(path -> path.getFileName().toString().startsWith(cancelledName + "-")),
                                "Capture cancellation removes unfinished snapshot directories");
                    }
                    maps.saveAsync(cancelledName, () -> guard(() -> {
                        require(maps.names().contains(cancelledName),
                                "Cancelling capture releases the world lease and permits a successful retry");
                        cancelPlacement();
                    }), this::fail);
                });
            });
        }

        private void cancelPlacement() {
            arena = maps.open(services, savedName);
            arena.buildAsync(smoke, () -> cancelledBuildCallback = true,
                    error -> cancelledBuildCallback = true);
            later(1, () -> {
                require(!cancelledBuildCallback, "Placement remains pending before cancellation");
                arena.cancelBuild();
                later(5, () -> {
                    require(!cancelledBuildCallback, "Cancelled placement never calls ready or failure callbacks");
                    require(arena.world().getBlockAt(16, 65, 16).getType() != Material.EMERALD_BLOCK,
                            "Cancelling partial placement never publishes active objective markers");
                    complete();
                });
            });
        }

        private Location at(double x, double y, double z) { return new Location(editor, x, y, z); }
        private Path registry() { return main.getDataFolder().toPath().resolve("finals/maps.yml"); }

        private void later(long ticks, Check check) {
            tasks.add(Bukkit.getScheduler().runTaskLater(smoke, () -> guard(check), ticks));
        }

        private void guard(Check check) {
            if (finished) return;
            try { check.run(); } catch (Throwable failure) { fail(failure); }
        }

        private void require(boolean condition, String label) {
            if (!condition) throw new AssertionError(label);
            assertions++;
        }

        private void complete() { finish(null); }

        private void fail(Throwable failure) { finish(failure); }

        private void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            for (BukkitTask task : tasks) task.cancel();
            try {
                if (maps != null) maps.close();
                if (arena != null) arena.cancelBuild();
                if (worlds != null) worlds.destroyOwn();
                if (editor != null && !Bukkit.unloadWorld(editor, false))
                    throw new IllegalStateException("Async fixture editor failed to unload");
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
            if (failure == null) {
                try {
                    Path result = smoke.getDataFolder().toPath().resolve("async-map-result.txt");
                    Files.createDirectories(result.getParent());
                    Files.writeString(result, "PASS: " + assertions + " native asynchronous map assertions\n");
                    smoke.getLogger().info("FINALS_ASYNC_MAP_SMOKE_PASS: " + assertions + " assertions");
                } catch (Throwable evidenceFailure) { failure = evidenceFailure; }
            }
            if (failure == null) done.run();
            else failed.accept(failure);
        }
    }
}
