package me.petr1furious.potatominigames.core.world;

import com.infernalsuite.asp.api.loaders.SlimeLoader;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A session's worlds, for event routing and teardown. Everything resolved here is owned by the
 * session.
 */
public final class SessionWorlds {
    private final ThrowawayWorldService throwaway;
    private final SlimeWorldService slime;
    private final Set<String> throwawayIds = new LinkedHashSet<>();
    private final Set<String> ownedWorlds = new LinkedHashSet<>();
    private final Set<String> slimeClones = new LinkedHashSet<>();
    private final Set<SlimeWorldService.CloneWork> pendingClones = new LinkedHashSet<>();
    private boolean destroyed;

    public SessionWorlds(ThrowawayWorldService throwaway, SlimeWorldService slime) {
        this.throwaway = throwaway;
        this.slime = slime;
    }

    /** Owns the whole set, including dimensions made later. */
    public World create(String id, World.Environment... eagerDimensions) {
        throwawayIds.add(id);
        return throwaway.create(id, eagerDimensions);
    }

    public World createVoid(String id) {
        throwawayIds.add(id);
        return throwaway.createVoid(id);
    }

    /** Selectively releases a round's empty void world, never another session's arena. */
    public void destroyVoid(String id) {
        if (!throwawayIds.contains(id)) return;
        World world = org.bukkit.Bukkit.getWorld(throwaway.baseName(id));
        if (world != null && !world.getPlayers().isEmpty())
            throw new IllegalStateException("Round world still has players");
        throwaway.destroy(id);
        if (org.bukkit.Bukkit.getWorld(throwaway.baseName(id)) != null)
            throw new IllegalStateException("Round world failed to unload");
        throwawayIds.remove(id);
    }

    /**
     * Seeds of the generated worlds; empty when the session only plays on templates or shared maps.
     */
    public List<Long> seeds() {
        List<Long> seeds = new ArrayList<>();
        for (String id : throwawayIds) {
            Long seed = throwaway.seed(id);
            if (seed != null && !seeds.contains(seed)) {
                seeds.add(seed);
            }
        }
        return seeds;
    }

    public void setupWorld(boolean force) {
        slime.setupWorld(force);
    }

    public World resolveWorld(String configuredWorldName) {
        World world = slime.resolveWorld(configuredWorldName);
        ownedWorlds.add(world.getName());
        return world;
    }

    /** In-memory copy, unloaded without saving on cleanup. */
    public World cloneSlime(String templateName, String worldName) {
        World world = slime.cloneTemplate(templateName, worldName);
        slimeClones.add(world.getName());
        ownedWorlds.add(world.getName());
        return world;
    }

    /** Same disposable clones as the shared arena store, using a game-owned template store. */
    public World cloneSlime(SlimeLoader loader, String templateName, String worldName) {
        if (destroyed)
            throw new IllegalStateException("Session worlds have already been destroyed");
        World world = slime.cloneTemplate(loader, templateName, worldName);
        if (destroyed) {
            slime.discardClone(world.getName());
            throw new IllegalStateException("Session ended while its world was loading");
        }
        slimeClones.add(world.getName());
        ownedWorlds.add(world.getName());
        return world;
    }

    /**
     * Cancels unfinished reads at teardown. A successful callback receives a fully loaded world
     * that is already owned by this session, including during reentrant teardown from callbacks.
     */
    public SlimeWorldService.CloneWork cloneSlimeAsync(
            SlimeLoader loader,
            String templateName,
            String worldName,
            Consumer<World> success,
            Consumer<RuntimeException> failure) {
        if (destroyed)
            throw new IllegalStateException("Session worlds have already been destroyed");
        SlimeWorldService.CloneWork[] handle = new SlimeWorldService.CloneWork[1];
        handle[0] =
                slime.cloneTemplateAsync(
                        loader,
                        templateName,
                        worldName,
                        world -> {
                            pendingClones.remove(handle[0]);
                            if (destroyed) {
                                slime.discardClone(world.getName());
                                return;
                            }
                            slimeClones.add(world.getName());
                            ownedWorlds.add(world.getName());
                            try {
                                success.accept(world);
                            } catch (RuntimeException error) {
                                slimeClones.remove(world.getName());
                                ownedWorlds.remove(world.getName());
                                throw error;
                            }
                        },
                        error -> {
                            pendingClones.remove(handle[0]);
                            if (!destroyed) failure.accept(error);
                        });
        pendingClones.add(handle[0]);
        return new SlimeWorldService.CloneWork() {
            public void cancel() {
                handle[0].cancel();
                pendingClones.remove(handle[0]);
            }

            public boolean isDone() {
                return handle[0].isDone();
            }
        };
    }

    public void claim(World world) {
        ownedWorlds.add(world.getName());
    }

    public boolean owns(World world) {
        if (world == null) {
            return false;
        }
        if (ownedWorlds.contains(world.getName())) {
            return true;
        }
        String base = throwaway.baseOf(world);
        return base != null
                && throwawayIds.contains(base.substring(ThrowawayWorldService.PREFIX.length()));
    }

    /** Shared worlds are left loaded. */
    public void destroyOwn() {
        destroyed = true;
        for (SlimeWorldService.CloneWork work : List.copyOf(pendingClones)) work.cancel();
        pendingClones.clear();
        for (String id : throwawayIds) {
            throwaway.destroy(id);
        }
        throwawayIds.clear();
        for (String name : slimeClones) {
            slime.discardClone(name);
        }
        slimeClones.clear();
        ownedWorlds.clear();
    }
}
