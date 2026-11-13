import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.SlimeFileLoader;
import me.petr1furious.potatominigames.core.world.SlimeWorldService;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.games.finals.FinalsMapArena;
import me.petr1furious.potatominigames.games.finals.FinalsMaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Real ASP templates, exact selection clipping, asynchronous clone ownership and optional scale timing. */
public final class FinalsSlimeMapsSmoke {
    public static void run(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
        Runner runner = new Runner(smoke, main, done, failed);
        runner.later(1, runner::start);
    }

    private interface Check { void run() throws Exception; }

    private static final class Runner {
        final JavaPlugin smoke, main;
        final Runnable done;
        final Consumer<Throwable> failed;
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final String mapName = "slimeproof_" + suffix;
        final List<BukkitTask> tasks = new ArrayList<>();
        final List<SessionWorlds> sessions = new ArrayList<>();
        final List<World> editors = new ArrayList<>();
        final List<String> timings = new ArrayList<>();
        FinalsMaps maps;
        SessionWorlds worlds;
        SessionServices services;
        FinalsMapArena arena;
        World editor;
        Chunk actorChunk;
        ArmorStand actor;
        Path template;
        byte[] templateBytes;
        String stairData;
        int assertions, heartbeats;
        boolean finished;
        CountDownLatch blockedRead;

        Runner(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
            this.smoke = smoke; this.main = main; this.done = done; this.failed = failed;
        }

        void start() {
            tasks.add(Bukkit.getScheduler().runTaskTimer(smoke, () -> heartbeats++, 1, 1));
            later(Boolean.getBoolean("finals.smoke.slimeBenchmark") ? 12_000 : 2_400,
                    () -> { throw new AssertionError("Slime native fixture timed out"); });
            editor = makeEditor("small", false);
            for (int x = -18; x <= 19; x++) for (int z = 22; z <= 58; z++)
                editor.getBlockAt(x, 64, z).setType(Material.STONE, false);
            editor.getBlockAt(-5, editor.getMinHeight(), 40).setType(Material.COPPER_BLOCK, false);
            editor.getBlockAt(-5, editor.getMaxHeight() - 1, 40).setType(Material.LAPIS_BLOCK, false);
            editor.getBlockAt(-18, 65, 40).setType(Material.COMMAND_BLOCK, false);
            editor.getBlockAt(19, 65, 40).setType(Material.DIAMOND_BLOCK, false);
            editor.getBlockAt(-10, 65, 30).setType(Material.CHEST, false);
            Chest chest = (Chest) editor.getBlockAt(-10, 65, 30).getState();
            chest.customName(Component.text("Slime cargo")); chest.update(true, false);
            ItemStack item = new ItemStack(Material.DIAMOND, 3);
            item.editMeta(meta -> meta.displayName(Component.text("Preserved treasure")));
            chest.getBlockInventory().setItem(0, item);
            editor.getBlockAt(-10, 65, 31).setType(Material.OAK_SIGN, false);
            Sign sign = (Sign) editor.getBlockAt(-10, 65, 31).getState();
            sign.getSide(Side.FRONT).line(0, Component.text("Slime snapshot")); sign.update(true, false);
            stairData = "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]";
            editor.getBlockAt(-17, 65, 32).setBlockData(Bukkit.createBlockData(stairData), false);
            // No players keep this editor active. Keep the original entity wrapper valid
            // across asynchronous saves instead of conflating chunk visibility with removal.
            actorChunk = editor.getChunkAt(-1, 2);
            require(actorChunk.addPluginChunkTicket(smoke), "The source entity chunk stays loaded during asynchronous checks");
            actor = editor.spawn(new Location(editor, -8.5, 66, 40.5), ArmorStand.class);
            actor.setGravity(false);
            require(actor.isValid(), "The source entity is valid before capture begins");
            maps = new FinalsMaps(main);
            configure(mapName, editor, -17, 18, 23, 57);
            worlds = sessionWorlds(); services = services(worlds);
            int before = heartbeats;
            maps.saveAsync(mapName, () -> guard(() -> {
                require(heartbeats > before, "Saving a Slime snapshot yields to the server scheduler");
                template = templateFor(mapName);
                require(Files.isRegularFile(template) && template.toString().endsWith(".slime"),
                        "New saves produce one prepared Slime template");
                templateBytes = Files.readAllBytes(template);
                require(templateBytes.length > 0, "The prepared Slime file contains serialized world data");
                sourceChecks();
                open(mapName, services, () -> firstClone());
            }), this::finish);
        }

        World makeEditor(String label, boolean city) {
            ChunkGenerator generator = city ? new CityGenerator() : new ChunkGenerator() {
                @Override public Location getFixedSpawnLocation(World world, Random random) {
                    return new Location(world, .5, 65, 40.5);
                }
            };
            World world = Bukkit.createWorld(new WorldCreator("finals_slime_" + label + "_" + suffix)
                    .generateStructures(false).keepSpawnLoaded(TriState.FALSE).generator(generator));
            require(world != null, "Isolated editor world loads");
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
            world.setGameRule(GameRules.COMMAND_BLOCKS_WORK, false);
            editors.add(world); return world;
        }

        void configure(String name, World world, int minX, int maxX, int minZ, int maxZ) {
            int cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
            int red = minX + 4, blue = maxX - 4, a = minZ + 4, b = maxZ - 4;
            // Keep fixture anchors on open ground even in the synthetic urban benchmark.
            for (int[] p : new int[][] {{red, cz}, {blue, cz}, {cx, cz}, {cx, a}, {cx, b}}) {
                world.getBlockAt(p[0], 64, p[1]).setType(Material.STONE, false);
                world.getBlockAt(p[0], 65, p[1]).setType(Material.AIR, false);
                world.getBlockAt(p[0], 66, p[1]).setType(Material.AIR, false);
            }
            maps.create(name, new Location(world, red + .5, 65, cz + .5));
            maps.corner(name, 0, new Location(world, minX, 65, minZ));
            maps.corner(name, 1, new Location(world, maxX, 65, maxZ));
            maps.spawn(name, 0, new Location(world, red + .5, 65, cz + .5, -90, 0));
            maps.spawn(name, 1, new Location(world, blue + .5, 65, cz + .5, 90, 0));
            maps.vault(name, new Location(world, cx + .5, 65, cz + .5));
            maps.station(name, 0, new Location(world, cx + .5, 65, a + .5));
            maps.station(name, 1, new Location(world, cx + .5, 65, b + .5));
        }

        void sourceChecks() {
            require(actor.isValid(), "Saving does not remove source entities");
            require(editor.getBlockAt(-18, 65, 40).getType() == Material.COMMAND_BLOCK,
                    "Selection clipping leaves an exterior unsafe block untouched");
            require(editor.getBlockAt(0, 65, 40).getType() == Material.AIR,
                    "Saving never places objective markers in the editor");
            metadata(editor);
        }

        void metadata(World world) {
            Chest chest = (Chest) world.getBlockAt(-10, 65, 30).getState();
            require(Component.text("Slime cargo").equals(chest.customName()), "Chest names survive the Slime snapshot");
            ItemStack item = chest.getBlockInventory().getItem(0);
            require(item != null && item.getType() == Material.DIAMOND && item.getAmount() == 3
                            && Component.text("Preserved treasure").equals(item.getItemMeta().displayName()),
                    "Chest contents and item metadata survive the Slime snapshot");
            Sign sign = (Sign) world.getBlockAt(-10, 65, 31).getState();
            require(Component.text("Slime snapshot").equals(sign.getSide(Side.FRONT).line(0)),
                    "Native sign text survives the Slime snapshot");
            require(world.getBlockAt(-17, 65, 32).getBlockData().getAsString().equals(stairData),
                    "Directional block states survive clipping a boundary chunk");
        }

        void firstClone() throws Exception {
            World match = arena.world();
            require(match != editor && worlds.owns(match), "The Slime clone belongs only to its match session");
            require(match.getBlockAt(-5, match.getMinHeight(), 40).getType() == Material.COPPER_BLOCK
                            && match.getBlockAt(-5, match.getMaxHeight() - 1, 40).getType() == Material.LAPIS_BLOCK,
                    "Full world height survives prepared-world loading");
            for (int[] p : new int[][] {{-18, 40}, {19, 40}, {0, 22}, {0, 58}})
                require(match.getBlockAt(p[0], 64, p[1]).getType().isAir()
                                && match.getBlockAt(p[0], 65, p[1]).getType().isAir(),
                        "Partial boundary chunks contain no terrain beyond the exact selected rectangle");
            require(match.getBlockAt(-17, 64, 40).getType() == Material.STONE
                            && match.getBlockAt(18, 64, 40).getType() == Material.STONE,
                    "The inclusive rectangle keeps both selected boundary columns");
            require(match.getEntities().isEmpty(), "Prepared templates exclude editor entities");
            require(arena.spawn(0, 0).getYaw() == -90 && arena.vault().getBlock().getType() == Material.EMERALD_BLOCK,
                    "Spawn orientation and objective markers initialize on the loaded clone");
            metadata(match);
            match.getBlockAt(-5, match.getMinHeight(), 40).setType(Material.AIR, false);
            ((Chest) match.getBlockAt(-10, 65, 30).getState()).getBlockInventory().clear();
            sourceChecks();
            UUID firstId = match.getUID();
            worlds.destroyOwn();
            require(Bukkit.getWorld(firstId) == null, "Session cleanup unloads its Slime clone without saving");
            worlds = sessionWorlds(); services = services(worlds);
            open(mapName, services, () -> {
                require(arena.world().getBlockAt(-5, arena.world().getMinHeight(), 40).getType() == Material.COPPER_BLOCK,
                        "A second match restores terrain destroyed in the first");
                metadata(arena.world());
                require(Arrays.equals(templateBytes, Files.readAllBytes(template)), "Playing never rewrites the immutable Slime template");
                failedSave();
            });
        }

        void failedSave() throws Exception {
            byte[] before = Files.readAllBytes(registry());
            editor.getBlockAt(8, 70, 40).setType(Material.COMMAND_BLOCK, false);
            maps.saveAsync(mapName, () -> finish(new AssertionError("Unsafe replacement unexpectedly saved")), error -> guard(() -> {
                require(error instanceof IllegalArgumentException, "Unsafe block produces a handled save failure");
                require(Arrays.equals(before, Files.readAllBytes(registry())), "A failed replacement preserves registry bytes");
                require(Arrays.equals(templateBytes, Files.readAllBytes(template)), "A failed replacement preserves the last playable template");
                editor.getBlockAt(8, 70, 40).setType(Material.AIR, false);
                legacySnapshot(false, () -> legacySnapshot(true, this::coreCloneChecks));
            }));
        }

        void legacySnapshot(boolean tiled, Check next) throws Exception {
            String name = "legacy_" + (tiled ? "tiles_" : "nbt_") + suffix;
            Path old = registry().getParent().resolve("templates").resolve(name + (tiled ? ".tiles" : ".nbt"));
            if (tiled) {
                ClassLoader loader = main.getClass().getClassLoader();
                Class<?> bounds = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsMapData$Bounds", true, loader);
                Constructor<?> constructor = bounds.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,int.class);
                constructor.setAccessible(true);
                Object selection = constructor.newInstance(-17, editor.getMinHeight(), 23, 18, editor.getMaxHeight()-1, 57);
                Class<?> snapshots = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsMapSnapshot", true, loader);
                Method capture = snapshots.getDeclaredMethod("capture", JavaPlugin.class, World.class, bounds, Path.class);
                capture.setAccessible(true);
                Object work = capture.invoke(null, main, editor, selection, old);
                Class<?> workType = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsMapSnapshot$Work", true, loader);
                Method finish = workType.getDeclaredMethod("finish"); finish.setAccessible(true); finish.invoke(work);
            } else {
                var structure = Bukkit.getStructureManager().createStructure();
                structure.fill(new Location(editor, -17, editor.getMinHeight(), 23),
                        new org.bukkit.util.BlockVector(36, editor.getMaxHeight()-editor.getMinHeight(), 35), false);
                Bukkit.getStructureManager().saveStructure(old.toFile(), structure);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(registry().toFile());
            yaml.createSection("maps." + name, tree(yaml.getConfigurationSection("maps." + mapName)));
            yaml.set("maps." + name + ".saved.template", old.getFileName().toString());
            yaml.save(registry().toFile());
            maps = new FinalsMaps(main);
            worlds.destroyOwn(); worlds = sessionWorlds(); services = services(worlds);
            open(name, services, () -> {
                metadata(arena.world());
                Path converted = templateFor(name);
                byte[] bytes = Files.readAllBytes(converted);
                require(!converted.equals(old) && Files.exists(old), "Legacy conversion retains its original snapshot and publishes a Slime template");
                require(arena.world().getBlockAt(-5, arena.world().getMinHeight(), 40).getType() == Material.COPPER_BLOCK,
                        "Legacy conversion preserves the full-height snapshot contents");
                worlds.destroyOwn(); worlds = sessionWorlds(); services = services(worlds);
                maps = new FinalsMaps(main);
                open(name, services, () -> {
                    metadata(arena.world());
                    require(templateFor(name).equals(converted) && Arrays.equals(bytes, Files.readAllBytes(converted)),
                            "The next legacy-map match reuses the converted Slime template without converting again");
                    next.run();
                });
            });
        }

        Map<String,Object> tree(org.bukkit.configuration.ConfigurationSection section) {
            Map<String,Object> result = new LinkedHashMap<>();
            section.getValues(false).forEach((key,value) -> result.put(key,
                    value instanceof org.bukkit.configuration.ConfigurationSection nested ? tree(nested) : value));
            return result;
        }

        void coreCloneChecks() {
            SlimeFileLoader loader = new SlimeFileLoader(template.getParent().toFile());
            String templateName = template.getFileName().toString().replaceFirst("\\.slime$", "");
            SessionWorlds cancelled = sessionWorlds();
            AtomicBoolean began = new AtomicBoolean(), callback = new AtomicBoolean();
            blockedRead = new CountDownLatch(1);
            SlimeLoader gated = new SlimeLoader() {
                public byte[] readWorld(String name) throws UnknownWorldException, IOException {
                    began.set(true);
                    try { if (!blockedRead.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture gate timed out"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                    return loader.readWorld(name);
                }
                public boolean worldExists(String name) { return loader.worldExists(name); }
                public List<String> listWorlds() { return loader.listWorlds(); }
                public void saveWorld(String name, byte[] data) throws IOException { loader.saveWorld(name, data); }
                public void deleteWorld(String name) throws UnknownWorldException, IOException { loader.deleteWorld(name); }
            };
            String cancelledWorld = "finals_cancel_clone_" + suffix;
            cancelled.cloneSlimeAsync(gated, templateName, cancelledWorld,
                    world -> callback.set(true), error -> callback.set(true));
            poll(200, began::get, () -> {
                cancelled.destroyOwn(); blockedRead.countDown();
                later(10, () -> {
                    require(!callback.get() && Bukkit.getWorld(cancelledWorld) == null,
                            "Late asynchronous reads cannot load a clone or call back after session destruction");
                    SessionWorlds collision = sessionWorlds();
                    try {
                        collision.cloneSlimeAsync(loader, templateName, editor.getName(),
                                world -> finish(new AssertionError("Existing-world collision unexpectedly succeeded")),
                                error -> collisionRejected(collision, loader));
                    } catch (IllegalStateException expected) {
                        collisionRejected(collision, loader);
                    }
                });
            });
        }

        void collisionRejected(SessionWorlds collision, SlimeFileLoader loader) {
            guard(() -> {
                require(Bukkit.isPrimaryThread(), "Clone name rejection runs on the server thread");
                require(Bukkit.getWorld(editor.getUID()) == editor && actor.isValid(),
                        "A clone-name collision never unloads or modifies the existing editor");
                collision.cloneSlimeAsync(loader, "missing_" + suffix, "finals_missing_" + suffix,
                        world -> finish(new AssertionError("Missing template unexpectedly loaded")),
                        missing -> guard(() -> {
                            require(Bukkit.isPrimaryThread(), "Asynchronous read failures return on the main thread");
                            require(Bukkit.getWorld("finals_missing_" + suffix) == null, "Missing templates leave no partial world");
                            if (Boolean.getBoolean("finals.smoke.slimeBenchmark")) benchmark();
                            else finish(null);
                        }));
            });
        }

        void benchmark() {
            World city = makeEditor("city", true);
            String name = "slimebench_" + suffix;
            configure(name, city, -301, 230, -283, 212);
            long start = System.nanoTime();
            maps.saveAsync(name, () -> guard(() -> {
                timing("532x496 urban template preparation", start);
                require(Files.size(templateFor(name)) > 0, "Large benchmark creates a nonempty prepared template");
                SessionWorlds coldWorlds = sessionWorlds();
                long cold = System.nanoTime();
                open(name, services(coldWorlds), () -> {
                    timing("532x496 first match clone", cold);
                    require(arena.world().getBlockAt(4, 70, 4).getType() == Material.STONE_BRICKS,
                            "Large benchmark preserves urban walls, not merely an empty world");
                    coldWorlds.destroyOwn();
                    SessionWorlds warmWorlds = sessionWorlds();
                    long warm = System.nanoTime();
                    open(name, services(warmWorlds), () -> {
                        timing("532x496 repeated match clone", warm);
                        require(arena.world().getBlockAt(0, 63, 0).getType() == Material.STONE,
                                "Repeated benchmark clone retains solid terrain layers");
                        warmWorlds.destroyOwn(); finish(null);
                    });
                });
            }), this::finish);
        }

        void timing(String label, long start) {
            String result = label + ": " + String.format(Locale.ROOT, "%.3f s", (System.nanoTime() - start) / 1e9);
            timings.add(result); smoke.getLogger().info("FINALS_SLIME_TIMING: " + result);
        }

        void open(String name, SessionServices target, Check ready) {
            arena = maps.open(target, name);
            arena.buildAsync(main, () -> guard(ready), this::finish);
        }

        SessionWorlds sessionWorlds() {
            SessionWorlds result = new SessionWorlds(new ThrowawayWorldService(main), new SlimeWorldService(main));
            sessions.add(result); return result;
        }

        SessionServices services(SessionWorlds owner) {
            return new SessionServices(main, null, null, null, null, null, null, null, owner, null,
                    new me.petr1furious.potatominigames.core.text.Messages(net.kyori.adventure.audience.Audience::empty,null), null);
        }

        Path registry() { return main.getDataFolder().toPath().resolve("finals/maps.yml"); }

        Path templateFor(String name) {
            String file = YamlConfiguration.loadConfiguration(registry().toFile()).getString("maps." + name + ".saved.template");
            require(file != null && file.endsWith(".slime"), "Registry points at the prepared Slime file");
            return registry().getParent().resolve("templates").resolve(file);
        }

        void poll(int remaining, java.util.function.BooleanSupplier ready, Check next) {
            if (ready.getAsBoolean()) { guard(next); return; }
            require(remaining > 0, "Asynchronous operation reaches its expected intermediate state");
            later(1, () -> poll(remaining - 1, ready, next));
        }

        void later(long delay, Check action) { tasks.add(Bukkit.getScheduler().runTaskLater(smoke, () -> guard(action), delay)); }
        void guard(Check action) { if (!finished) try { action.run(); } catch (Throwable error) { finish(error); } }
        void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }

        void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            if (blockedRead != null) blockedRead.countDown();
            tasks.forEach(BukkitTask::cancel);
            try {
                if (maps != null) maps.close();
                if (arena != null) arena.cancelBuild();
                sessions.forEach(SessionWorlds::destroyOwn);
                if (actorChunk != null) actorChunk.removePluginChunkTicket(smoke);
                for (World source : editors) require(Bukkit.unloadWorld(source, false), "Fixture editor unloads during cleanup");
            } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            if (failure == null) {
                try {
                    Path evidence = smoke.getDataFolder().toPath().resolve("slime-map-result.txt");
                    Files.createDirectories(evidence.getParent());
                    Files.writeString(evidence, "PASS: " + assertions + " native Slime map assertions\n" + String.join("\n", timings) + "\n");
                    smoke.getLogger().info("FINALS_SLIME_MAP_SMOKE_PASS: " + assertions + " assertions");
                } catch (Throwable error) { failure = error; }
            }
            if (failure == null) done.run(); else failed.accept(failure);
        }
    }

    /** Four terrain layers plus repeated multi-storey buildings and open streets across 1,088 chunks. */
    private static final class CityGenerator extends ChunkGenerator {
        @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, .5, 65, .5); }
        @Override public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            data.setRegion(0, 61, 0, 16, 65, 16, Material.STONE);
            if (Math.floorMod(chunkX, 3) == 0 && Math.floorMod(chunkZ, 3) == 0) {
                data.setRegion(4, 65, 4, 12, 77, 5, Material.STONE_BRICKS);
                data.setRegion(4, 65, 11, 12, 77, 12, Material.STONE_BRICKS);
                data.setRegion(4, 65, 5, 5, 77, 11, Material.STONE_BRICKS);
                data.setRegion(11, 65, 5, 12, 77, 11, Material.STONE_BRICKS);
                data.setRegion(4, 71, 4, 12, 72, 12, Material.OAK_PLANKS);
                data.setRegion(4, 77, 4, 12, 78, 12, Material.SMOOTH_STONE);
            }
        }
    }
}
