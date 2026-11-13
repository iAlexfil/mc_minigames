import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.games.finals.FinalsMapArena;
import me.petr1furious.potatominigames.games.finals.FinalsMapCommands;
import me.petr1furious.potatominigames.games.finals.FinalsMaps;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Shares the imported folder fixture from FinalsMapsSmoke; no second city import is needed. */
public final class FinalsSharedWorldSmoke {
    public static void run(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
        Runner runner = new Runner(smoke, main, done, failed);
        runner.later(1, runner::start);
    }

    private interface Check { void run() throws Exception; }

    private static final class Runner {
        final JavaPlugin smoke, main;
        final Runnable done;
        final Consumer<Throwable> failed;
        final List<BukkitTask> tasks = new ArrayList<>();
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final String first = "shared_a_" + suffix, second = "shared_b_" + suffix;
        FinalsMaps maps;
        FinalsMapArena arena;
        SessionWorlds worlds;
        World editor;
        String editorName, editorFolder;
        Path firstTemplate, secondTemplate;
        byte[] secondBytes;
        int assertions;
        boolean finished;

        Runner(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
            this.smoke = smoke; this.main = main; this.done = done; this.failed = failed;
        }

        void start() throws Exception {
            later(1200, () -> { throw new AssertionError("Shared-world fixture timed out"); });
            maps = new FinalsMaps(main);
            String source = System.getProperty("finals.smoke.sharedSource", "folderimport");
            editor = maps.editorLocation(source).getWorld();
            require(editor != null, "The existing city editor opens");
            editorName = editor.getName();
            editorFolder = registry().getString("maps." + source + ".draft.folder");
            maps.create(first, editor.getSpawnLocation());
            unloadEditor();
            maps = new FinalsMaps(main);
            // This is the original regression: standing aliases used to lose imported/folder.
            editor = maps.editorLocation(first).getWorld();
            require(editor.getName().equals(editorName), "A standing-created area reopens its imported city after unload and registry reload");
            require(editorFolder.equals(registry().getString("maps." + first + ".draft.folder")),
                    "A standing-created area persists the actual imported editor folder");
            unloadEditor();
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            var commandRoot = Commands.literal("finals");
            new FinalsMapCommands(maps, null).register(commandRoot);
            dispatcher.register(commandRoot);
            CommandSourceStack console = (CommandSourceStack) Proxy.newProxyInstance(
                    CommandSourceStack.class.getClassLoader(), new Class<?>[] {CommandSourceStack.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getSender" -> Bukkit.getConsoleSender();
                        case "getExecutor" -> null;
                        case "toString" -> "Shared-world fixture console";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new AssertionError("Unexpected command-source call: " + method.getName());
                    });
            require(dispatcher.execute("finals map create " + second + " " + source, console) == 1,
                    "Console can create another area using the optional source-map command argument");
            require(Bukkit.getWorld(editorName) == null, "Creating another area does not load or copy its city terrain");
            ConfigurationSection blank = registry().getConfigurationSection("maps." + second + ".draft");
            require(blank.getString("world").equals(editorName) && blank.getBoolean("imported")
                            && editorFolder.equals(blank.getString("folder")),
                    "Explicit area creation shares the imported city identity");
            require(!blank.contains("pos1") && !blank.contains("pos2")
                            && blank.getConfigurationSection("vaults").getKeys(false).isEmpty()
                            && blank.getConfigurationSection("cashouts").getKeys(false).isEmpty()
                            && !blank.contains("red") && !blank.contains("blue"),
                    "A new area starts without inherited bounds, objectives or spawn points");
            editor = maps.editorLocation(second).getWorld();
            require(maps.editorLocation(first).getWorld() == editor, "Both map names resolve to the same loaded editor world");
            configure(first, 84, 184, Material.COPPER_BLOCK);
            configure(second, 108, 208, Material.LAPIS_BLOCK);
            maps.saveAsync(first, () -> guard(() -> maps.saveAsync(second, () -> guard(this::saved), this::finish)), this::finish);
        }

        void configure(String name, int x, int z, Material marker) {
            for (int bx = x; bx <= x + 20; bx++) for (int bz = z; bz <= z + 20; bz++) {
                editor.getBlockAt(bx, 64, bz).setType(Material.STONE, false);
                editor.getBlockAt(bx, 65, bz).setType(Material.AIR, false);
                editor.getBlockAt(bx, 66, bz).setType(Material.AIR, false);
            }
            editor.getBlockAt(x + 1, 65, z + 1).setType(marker, false);
            maps.corner(name, 0, at(x, 65, z)); maps.corner(name, 1, at(x + 20, 65, z + 20));
            maps.spawn(name, 0, at(x + 1.5, 65, z + 10.5)); maps.spawn(name, 1, at(x + 19.5, 65, z + 10.5));
            maps.vault(name, at(x + 10.5, 65, z + 10.5));
            maps.station(name, 0, at(x + 10.5, 65, z + 1.5)); maps.station(name, 1, at(x + 10.5, 65, z + 19.5));
        }

        void saved() throws Exception {
            firstTemplate = template(first); secondTemplate = template(second);
            secondBytes = Files.readAllBytes(secondTemplate);
            require(!firstTemplate.equals(secondTemplate), "Areas in one city save into independent prepared templates");
            // Reproduce aliases made by old builds without losing their configured area or saved map.
            unloadEditor();
            YamlConfiguration yaml = registry();
            yaml.set("maps." + first + ".draft.imported", false);
            yaml.set("maps." + first + ".draft.folder", null);
            yaml.save(registryPath().toFile());
            maps = new FinalsMaps(main);
            editor = maps.editorLocation(first).getWorld();
            require(editor.getName().equals(editorName), "Previously created aliases recover missing import metadata from their shared city");
            require(maps.names().contains(first) && maps.names().contains(second), "Alias repair retains both saved maps");
            open(first, () -> {
                checkArea(arena.world(), 85, 185, Material.COPPER_BLOCK, 109, 209);
                boolean rejected = false;
                String temporary = "shared_tmp_" + suffix;
                try { maps.create(temporary, arena.spawn(0, 0)); }
                catch (IllegalArgumentException expected) { rejected = true; }
                require(rejected && !maps.drafts().contains(temporary),
                        "Disposable Slime match worlds cannot become persistent editor areas");
                require(arena.spawn(0, 0).getBlockX() == 85 && arena.vault().getBlockX() == 94,
                        "Alias repair preserves configured spawns and objectives");
                arena.world().getBlockAt(85, 65, 185).setType(Material.AIR, false);
                closeMatch();
                open(second, () -> {
                    checkArea(arena.world(), 109, 209, Material.LAPIS_BLOCK, 85, 185);
                    require(editor.getBlockAt(85, 65, 185).getType() == Material.COPPER_BLOCK,
                            "Destruction in one area leaves the shared editor unchanged");
                    closeMatch();
                    editor.getBlockAt(85, 65, 185).setType(Material.DIAMOND_BLOCK, false);
                    maps.saveAsync(first, () -> guard(this::resaved), this::finish);
                });
            });
        }

        void resaved() throws Exception {
            require(!template(first).equals(firstTemplate), "Resaving an area publishes a new template for that area");
            require(template(second).equals(secondTemplate) && Arrays.equals(secondBytes, Files.readAllBytes(secondTemplate)),
                    "Resaving one area leaves the other area's template byte-identical");
            maps = new FinalsMaps(main);
            open(first, () -> {
                checkArea(arena.world(), 85, 185, Material.DIAMOND_BLOCK, 109, 209);
                closeMatch();
                open(second, () -> {
                    checkArea(arena.world(), 109, 209, Material.LAPIS_BLOCK, 85, 185);
                    finish(null);
                });
            });
        }

        void checkArea(World world, int x, int z, Material material, int outsideX, int outsideZ) {
            require(world != editor && world.getBlockAt(x, 65, z).getType() == material,
                    "Each area loads its own immutable terrain into a disposable match world");
            require(world.getBlockAt(outsideX, 64, outsideZ).getType().isAir(),
                    "The other city area's terrain is outside this map's saved bounds");
        }

        void open(String name, Check ready) {
            worlds = new SessionWorlds(new ThrowawayWorldService(main), new SlimeWorldService(main));
            SessionServices services = new SessionServices(main, null, null, null, null, null, null,
                    null, worlds, null, null, null);
            arena = maps.open(services, name);
            arena.buildAsync(main, () -> guard(ready), this::finish);
        }

        void closeMatch() { if (arena != null) arena.cancelBuild(); if (worlds != null) worlds.destroyOwn(); arena = null; worlds = null; }
        void unloadEditor() { require(Bukkit.unloadWorld(editor, true), "The shared editor unloads with its terrain saved"); editor = null; }
        Location at(double x, double y, double z) { return new Location(editor, x, y, z); }
        Path registryPath() { return main.getDataFolder().toPath().resolve("finals/maps.yml"); }
        YamlConfiguration registry() { return YamlConfiguration.loadConfiguration(registryPath().toFile()); }
        Path template(String name) { return main.getDataFolder().toPath().resolve("finals/templates").resolve(registry().getString("maps." + name + ".saved.template")); }
        void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); assertions++; }
        void later(long ticks, Check check) { tasks.add(Bukkit.getScheduler().runTaskLater(smoke, () -> guard(check), ticks)); }
        void guard(Check check) { if (!finished) try { check.run(); } catch (Throwable failure) { finish(failure); } }

        void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            tasks.forEach(BukkitTask::cancel);
            try {
                if (maps != null) maps.close(); closeMatch();
                if (editor != null) unloadEditor();
            } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            try {
                Path evidence = smoke.getDataFolder().toPath().resolve("shared-world-result.txt");
                Files.createDirectories(evidence.getParent());
                Files.writeString(evidence, failure == null ? "PASS: " + assertions + " shared-city map assertions\n"
                        : "FAIL after " + assertions + " assertions: " + failure + "\n");
            } catch (Throwable evidence) { if (failure == null) failure = evidence; else failure.addSuppressed(evidence); }
            if (failure != null) failed.accept(failure);
            else { smoke.getLogger().info("FINALS_SHARED_WORLD_SMOKE_PASS: " + assertions + " assertions"); done.run(); }
        }
    }
}
