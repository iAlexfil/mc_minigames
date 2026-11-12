package me.petr1furious.potatominigames.core.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Advanced Slime Paper integration: per-game clones of template worlds. */
public final class SlimeWorldService {
    private final JavaPlugin plugin;
    private final PropertyBag config = new PropertyBag("arena.asp");
    private final Property<Boolean> enable = config.define("enable", PropertyCodec.BOOL, false);
    private final Property<String> templateWorld =
            config.define("templateWorld", PropertyCodec.STRING, "template")
                    .suggest(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
    private final Property<String> gameWorld =
            config.define("gameWorld", PropertyCodec.STRING, "game")
                    .suggest(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
    private final Property<Boolean> resetOnStart =
            config.define("resetOnStart", PropertyCodec.BOOL, true);

    private AdvancedSlimePaperAPI asp;
    private SlimeFileLoader fileLoader;
    private final Map<String, CloneOperation> pendingClones = new ConcurrentHashMap<>();

    /** Cancellation and completion are session-owned; cancel must run on the server thread. */
    public interface CloneWork {
        void cancel();

        boolean isDone();
    }

    public SlimeWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** ASP's file world directory. */
    public SlimeFileLoader fileLoader() {
        if (fileLoader == null) {
            fileLoader = new SlimeFileLoader(new File(Bukkit.getWorldContainer(), "slime_worlds"));
        }
        return fileLoader;
    }

    public boolean templateExists(String templateName) {
        return fileLoader().worldExists(templateName);
    }

    /**
     * In-memory copy, never saved; see {@link #discardClone}. A copy left loaded by an earlier game
     * is discarded first: clones are only ever made by the one session of their game.
     */
    public World cloneTemplate(String templateName, String worldName) {
        init();
        World leftover = Bukkit.getWorld(worldName);
        if (leftover != null) {
            plugin.getLogger().warning("ASP: " + worldName + " was still loaded; discarding it");
            evacuate(leftover);
            Bukkit.unloadWorld(leftover, false);
        }
        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalStateException("A world called " + worldName + " is already loaded");
        }
        return cloneTemplate(fileLoader(), templateName, worldName);
    }

    /** Loads a template from a caller-owned store without replacing an existing world. */
    public World cloneTemplate(SlimeLoader loader, String templateName, String worldName) {
        init();
        requireAvailable(worldName);
        SlimeWorld template;
        try {
            template = asp.readWorld(loader, templateName, true, new SlimePropertyMap());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not read slime template " + templateName, exception);
        }
        return loadClone(template.clone(worldName));
    }

    private World loadClone(SlimeWorld clone) {
        asp.loadWorld(clone, true);
        World world = Bukkit.getWorld(clone.getName());
        if (world == null) {
            throw new IllegalStateException("Slime clone " + clone.getName() + " did not load");
        }
        // Slime worlds default to peaceful.
        world.setDifficulty(Bukkit.getWorlds().getFirst().getDifficulty());
        return world;
    }

    private void requireAvailable(String worldName) {
        if (Bukkit.getWorld(worldName) != null || pendingClones.containsKey(worldName))
            throw new IllegalStateException(
                    "A world called " + worldName + " is already loaded or loading");
    }

    /**
     * Reads, decompresses and clones detached template data off-thread. World registration and
     * terminal callbacks run on the server thread. Cancelling never invokes either callback.
     */
    public CloneWork cloneTemplateAsync(
            SlimeLoader loader,
            String templateName,
            String worldName,
            Consumer<World> success,
            Consumer<RuntimeException> failure) {
        requireServerThread();
        Objects.requireNonNull(loader);
        Objects.requireNonNull(templateName);
        Objects.requireNonNull(worldName);
        Objects.requireNonNull(success);
        Objects.requireNonNull(failure);
        init();
        requireAvailable(worldName);
        CloneOperation operation =
                new CloneOperation(loader, templateName, worldName, success, failure);
        pendingClones.put(worldName, operation);
        try {
            operation.worker = Bukkit.getScheduler().runTaskAsynchronously(plugin, operation::read);
        } catch (RuntimeException error) {
            operation.cancel();
            throw error;
        }
        return operation;
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Slime clone lifecycle must run on the server thread");
    }

    private final class CloneOperation implements CloneWork {
        private final SlimeLoader loader;
        private final String templateName, worldName;
        private final Consumer<World> success;
        private final Consumer<RuntimeException> failure;
        private volatile boolean cancelled, done;
        private BukkitTask worker;
        private volatile BukkitTask delivery;

        CloneOperation(
                SlimeLoader loader,
                String templateName,
                String worldName,
                Consumer<World> success,
                Consumer<RuntimeException> failure) {
            this.loader = loader;
            this.templateName = templateName;
            this.worldName = worldName;
            this.success = success;
            this.failure = failure;
        }

        void read() {
            if (cancelled) return;
            SlimeWorld copy = null;
            RuntimeException problem = null;
            try {
                SlimeWorld template =
                        asp.readWorld(loader, templateName, true, new SlimePropertyMap());
                if (!cancelled) copy = template.clone(worldName);
            } catch (Exception error) {
                problem =
                        new IllegalStateException(
                                "Could not read slime template " + templateName, error);
            }
            if (cancelled) return;
            if (!plugin.isEnabled()) {
                cancelled = true;
                finish();
                return;
            }
            SlimeWorld result = copy;
            RuntimeException error = problem;
            try {
                delivery = Bukkit.getScheduler().runTask(plugin, () -> complete(result, error));
            } catch (org.bukkit.plugin.IllegalPluginAccessException disabled) {
                // Plugin shutdown cancels its sessions; never touch Bukkit worlds on this worker.
                cancelled = true;
                finish();
            }
        }

        void complete(SlimeWorld copy, RuntimeException problem) {
            if (done || cancelled) return;
            if (problem != null) {
                finish();
                failure.accept(problem);
                return;
            }
            // Another plugin may have loaded this name while the template was being read.
            if (Bukkit.getWorld(worldName) != null) {
                finish();
                failure.accept(
                        new IllegalStateException(
                                "A world called " + worldName + " is already loaded"));
                return;
            }
            World loaded = null;
            try {
                loaded = loadClone(copy);
                // WorldLoadEvent listeners can end the session reentrantly during loadWorld.
                if (cancelled) {
                    discardClone(loaded, 3);
                    return;
                }
                finish();
                success.accept(loaded);
            } catch (RuntimeException error) {
                if (loaded == null) loaded = Bukkit.getWorld(worldName);
                if (loaded != null) discardClone(loaded, 3);
                finish();
                if (!cancelled) failure.accept(error);
            }
        }

        private void finish() {
            done = true;
            pendingClones.remove(worldName, this);
        }

        @Override
        public void cancel() {
            requireServerThread();
            if (done) return;
            cancelled = true;
            finish();
            if (worker != null) worker.cancel();
            BukkitTask scheduled = delivery;
            if (scheduled != null) scheduled.cancel();
        }

        @Override
        public boolean isDone() {
            return done;
        }
    }

    /** Unloads without saving, evacuating players first; retried for a few seconds if refused. */
    public void discardClone(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) discardClone(world, 3);
    }

    private void discardClone(World world, int retries) {
        String worldName = world.getName();
        if (Bukkit.getWorld(worldName) != world) {
            return;
        }
        evacuate(world);
        if (Bukkit.unloadWorld(world, false)) {
            return;
        }
        if (retries > 0) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(plugin, () -> discardClone(world, retries - 1), 20L);
        } else {
            plugin.getLogger()
                    .warning(
                            "ASP: failed to unload clone "
                                    + worldName
                                    + "; still in it: "
                                    + world.getPlayers().stream().map(Player::getName).toList());
        }
    }

    /**
     * To the main world's spawn; dead players cannot be teleported, so they are respawned first.
     */
    private static void evacuate(World world) {
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final Player player : world.getPlayers()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.teleport(spawn);
        }
    }

    /** The vanilla world folder must not be loaded; replaces any older template. */
    public void importVanilla(
            File worldFolder, String templateName, int spawnX, int spawnY, int spawnZ)
            throws Exception {
        init();
        // ASP refuses to import over an existing world; the old one is restored on failure.
        byte[] previous =
                fileLoader().worldExists(templateName)
                        ? fileLoader().readWorld(templateName)
                        : null;
        if (previous != null) {
            fileLoader().deleteWorld(templateName);
        }
        try {
            SlimeWorld imported = asp.readVanillaWorld(worldFolder, templateName, fileLoader());
            setSpawn(imported.getPropertyMap(), spawnX, spawnY, spawnZ);
            asp.saveWorld(imported);
        } catch (Exception failure) {
            if (previous != null) {
                fileLoader().saveWorld(templateName, previous);
            }
            throw failure;
        }
    }

    /** Void world; replaces any older template. */
    public void createEmptyTemplate(String templateName, int spawnX, int spawnY, int spawnZ)
            throws Exception {
        init();
        if (fileLoader().worldExists(templateName)) {
            fileLoader().deleteWorld(templateName);
        }
        SlimePropertyMap properties = new SlimePropertyMap();
        setSpawn(properties, spawnX, spawnY, spawnZ);
        asp.saveWorld(asp.createEmptyWorld(templateName, false, properties, fileLoader()));
    }

    private static void setSpawn(SlimePropertyMap properties, int x, int y, int z) {
        properties.setValue(SlimeProperties.SPAWN_X, x);
        properties.setValue(SlimeProperties.SPAWN_Y, y);
        properties.setValue(SlimeProperties.SPAWN_Z, z);
    }

    public PropertyBag config() {
        return config;
    }

    public boolean isEnabled() {
        return enable.get();
    }

    public String gameWorldName() {
        return gameWorld.get();
    }

    /** With slime arenas enabled the cloned world wins over the game's own world setting. */
    public World resolveWorld(String configuredWorldName) {
        String name = enable.get() ? gameWorld.get() : configuredWorldName;
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalStateException("World '" + name + "' is not loaded");
        }
        return world;
    }

    private void init() {
        if (asp == null) {
            asp = AdvancedSlimePaperAPI.instance();
        }
    }

    private void unloadGameWorld() {
        final var worldName = Objects.requireNonNull(gameWorld.get());
        if (asp.getLoadedWorld(worldName) == null) {
            return;
        }
        final var world = Objects.requireNonNull(Bukkit.getWorld(worldName));
        evacuate(world);
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("ASP: failed to unload world " + worldName);
        }
    }

    /** No-op unless enabled (and resetOnStart, unless forced). */
    public void setupWorld(boolean force) {
        if (!enable.get()) {
            return;
        }
        if (!force && !resetOnStart.get()) {
            return;
        }
        init();
        unloadGameWorld();
        final var sourceWorldName = Objects.requireNonNull(templateWorld.get());
        final var destWorldName = Objects.requireNonNull(gameWorld.get());

        final var sourceSlimeWorld = asp.getLoadedWorld(sourceWorldName);
        if (sourceSlimeWorld == null) {
            throw new IllegalStateException(
                    "ASP: template world " + sourceWorldName + " is not loaded");
        }
        final var newSlimeWorld = sourceSlimeWorld.clone(destWorldName);
        asp.loadWorld(newSlimeWorld, true);

        World sourceWorld = Bukkit.getWorld(sourceWorldName);
        World destWorld = Bukkit.getWorld(destWorldName);
        if (sourceWorld == null || destWorld == null) {
            plugin.getLogger().warning("ASP: could not copy world settings after cloning");
            return;
        }
        copyWorldSettings(sourceWorld, destWorld);
    }

    private void copyWorldSettings(World sourceWorld, World destWorld) {
        destWorld.setDifficulty(sourceWorld.getDifficulty());
        for (GameRule<?> gameRule : Registry.GAME_RULE) {
            copyGameRule(sourceWorld, destWorld, gameRule);
        }
    }

    private <T> void copyGameRule(World sourceWorld, World destWorld, GameRule<T> gameRule) {
        try {
            T value = sourceWorld.getGameRuleValue(gameRule);
            if (value != null) {
                destWorld.setGameRule(gameRule, value);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().fine("ASP: skipping unsupported game rule " + gameRule.getKey());
        }
    }
}
