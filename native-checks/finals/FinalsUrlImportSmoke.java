import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import me.petr1furious.potatominigames.games.finals.FinalsMaps;
import me.petr1furious.potatominigames.i18n.I18n;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Native scheduler/registry side of URL imports; HTTP body handling is covered by pure checks. */
public final class FinalsUrlImportSmoke {
    public static void run(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
        Runner runner = new Runner(smoke, main, done, failed);
        runner.later(1, runner::start);
    }

    private interface Check { void run() throws Exception; }

    private static final class Runner {
        private static final String PRIVATE_URL = "http://127.0.0.1:9/native-map.zip";
        final JavaPlugin smoke, main;
        final Runnable done;
        final Consumer<Throwable> failed;
        final List<BukkitTask> tasks = new ArrayList<>();
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        FinalsMaps maps;
        World editor;
        byte[] originalRegistry;
        Set<String> originalImports;
        int assertions, heartbeats, callbacks;
        boolean returned, finished;

        Runner(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
            this.smoke = smoke; this.main = main; this.done = done; this.failed = failed;
        }

        void start() throws Exception {
            tasks.add(Bukkit.getScheduler().runTaskTimer(smoke, () -> heartbeats++, 1, 1));
            later(400, () -> { throw new AssertionError("URL import fixture timed out"); });
            sourceParsing();
            editor = Bukkit.getWorlds().getFirst();
            maps = new FinalsMaps(main);
            originalRegistry = registryBytes();
            originalImports = importFiles();
            String name = "urlpending_" + suffix;
            int before = heartbeats;
            maps.importUrlAsync(name, PRIVATE_URL,
                    () -> finish(new AssertionError("A private download address was accepted")),
                    error -> guard(() -> {
                        require(returned, "URL failures are delivered after importUrlAsync returns");
                        require(Bukkit.isPrimaryThread(), "URL failure callbacks run on the server thread");
                        require(heartbeats > before, "The server scheduler advances during URL validation");
                        require(!maps.drafts().contains(name), "A rejected URL never registers an editor");
                        require(Arrays.equals(originalRegistry, registryBytes()), "Rejected URLs preserve the registry bytes");
                        require(originalImports.equals(importFiles()), "Rejected URLs leave no downloaded archive");
                        // The completed operation must release its name immediately.
                        maps.create(name, editor.getSpawnLocation());
                        require(maps.drafts().contains(name), "A failed URL import releases its reserved map name");
                        cancellation();
                    }));
            returned = true;
            require(!maps.drafts().contains(name), "A pending download is not a usable map draft");
            rejectsName(name, () -> maps.create(name, editor.getSpawnLocation()));
            rejectsName(name, () -> maps.importWorld(name, "does-not-exist.zip"));
            rejectsName(name, () -> maps.importUrlAsync(name, PRIVATE_URL, () -> callbacks++, error -> callbacks++));
        }

        void cancellation() throws Exception {
            String name = "urlcancel_" + suffix;
            byte[] before = registryBytes();
            int worldCount = Bukkit.getWorlds().size();
            maps.importUrlAsync(name, PRIVATE_URL, () -> callbacks++, error -> callbacks++);
            maps.close();
            later(10, () -> {
                require(callbacks == 0, "Closing maps suppresses both terminal callbacks of a pending URL import");
                require(Arrays.equals(before, registryBytes()), "Cancelled URL imports leave the registry unchanged");
                require(!new FinalsMaps(main).drafts().contains(name), "Cancelled downloads cannot become drafts after registry reload");
                require(Bukkit.getWorlds().size() == worldCount, "Cancelled downloads do not load an editor world");
                require(originalImports.equals(importFiles()), "Cancellation leaves no temporary archive behind");
                finish(null);
            });
        }

        void rejectsName(String name, Check action) throws Exception {
            try { action.run(); }
            catch (IllegalArgumentException expected) {
                require(expected.getMessage().equals(I18n.exception("finals.map.error.importing", name).getMessage()),
                        "A pending URL import reserves its name against another map operation");
                return;
            }
            throw new AssertionError("A second map operation claimed a pending URL import name");
        }

        void sourceParsing() throws Exception {
            Class<?> commands = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsMapCommands");
            Method parse = commands.getDeclaredMethod("importSource", String.class);
            parse.setAccessible(true);
            String url = "https://example.com/maps/a.zip?token=x%2Fy&download=1#part";
            require(parse.invoke(null, url).equals(url), "Unquoted URLs preserve separators, query parameters and fragments");
            require(parse.invoke(null, '"' + url + '"').equals(url), "Quoted URLs remain supported");
            require(parse.invoke(null, "\"folder map.zip\"").equals("folder map.zip"), "Quoted local paths retain their spaces");
            require(parse.invoke(null, "folder/map.zip").equals("folder/map.zip"), "Unquoted local import paths remain supported");
            try { parse.invoke(null, "\"map.zip\" unexpected"); }
            catch (InvocationTargetException expected) {
                require(expected.getCause() instanceof IllegalArgumentException,
                        "Trailing text after a quoted import source is rejected");
                return;
            }
            throw new AssertionError("Quoted import source accepted trailing text");
        }

        byte[] registryBytes() throws Exception {
            Path registry = main.getDataFolder().toPath().resolve("finals/maps.yml");
            return Files.exists(registry) ? Files.readAllBytes(registry) : new byte[0];
        }

        Set<String> importFiles() throws Exception {
            Path imports = main.getDataFolder().toPath().resolve("finals/imports");
            try (var paths = Files.walk(imports)) {
                return paths.filter(Files::isRegularFile).map(imports::relativize)
                        .map(Path::toString).collect(Collectors.toSet());
            }
        }

        void require(boolean condition, String label) {
            if (!condition) throw new AssertionError(label);
            assertions++;
        }

        void later(long ticks, Check check) {
            tasks.add(Bukkit.getScheduler().runTaskLater(smoke, () -> guard(check), ticks));
        }

        void guard(Check check) {
            if (finished) return;
            try { check.run(); } catch (Throwable error) { finish(error); }
        }

        void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            tasks.forEach(BukkitTask::cancel);
            if (maps != null) maps.close();
            try {
                Path evidence = smoke.getDataFolder().toPath().resolve("url-import-result.txt");
                Files.createDirectories(evidence.getParent());
                Files.writeString(evidence, failure == null
                        ? "PASS: " + assertions + " native URL import lifecycle assertions\n"
                        : "FAIL after " + assertions + " assertions: " + failure + "\n");
            } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            if (failure != null) failed.accept(failure);
            else {
                smoke.getLogger().info("FINALS_URL_IMPORT_SMOKE_PASS: " + assertions + " assertions");
                done.run();
            }
        }
    }
}
