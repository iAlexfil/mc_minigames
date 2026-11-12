package me.petr1furious.potatominigames.core.hud;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Paper 26.3 metadata adapter: the native player's glow flag is changed only on ally clients. */
public final class PrivatePlayerGlow {
    private static boolean warned;
    private final JavaPlugin plugin;
    private final Function<Player, Object> handles;
    private final BiConsumer<Player, Object> sender;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private boolean failed;

    private static final class View {
        final Player viewer;
        final Map<UUID, Player> targets = new LinkedHashMap<>();

        View(Player viewer) {
            this.viewer = viewer;
        }
    }

    public PrivatePlayerGlow(JavaPlugin plugin) {
        this.plugin = plugin;
        handles = player -> Bindings.INSTANCE.handle(player);
        sender = (viewer, packet) -> Bindings.INSTANCE.send(viewer, packet);
    }

    /** Native checks substitute player transport while constructing the actual server packets. */
    public PrivatePlayerGlow(Function<Player, Object> handles, BiConsumer<Player, Object> sender) {
        this.plugin = null;
        this.handles = handles;
        this.sender = sender;
    }

    public void update(World world, Collection<Player> players, Map<UUID, Integer> teams) {
        update(world, players, teams, (viewer, target) -> true);
    }

    /** Filter can suppress outlines across smoke, flashes or other private visibility rules. */
    public void update(
            World world,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            BiPredicate<Player, Player> visible) {
        Set<UUID> present = new HashSet<>();
        for (Player viewer : players) {
            UUID id = viewer.getUniqueId();
            Integer team = teams.get(id);
            if (team == null || !viewer.isOnline() || !world.equals(viewer.getWorld())) continue;
            present.add(id);
            View view = views.get(id);
            if (view != null && view.viewer != viewer) {
                reset(view);
                views.remove(id);
            }
            view = views.computeIfAbsent(id, ignored -> new View(viewer));
            Set<UUID> wanted = new HashSet<>();
            for (Player target : players) {
                UUID targetId = target.getUniqueId();
                if (targetId.equals(id)
                        || !team.equals(teams.get(targetId))
                        || !target.isOnline()
                        || !world.equals(target.getWorld())
                        || target.isDead()
                        || target.getGameMode() == GameMode.SPECTATOR
                        || !visible.test(viewer, target)) continue;
                Object handle = handle(target);
                if (handle == null) continue;
                // Refresh at the highlight cadence: newly tracked players and ordinary native
                // metadata updates must not leave allies without their private outline.
                if (!emit(viewer, handle, true)) continue;
                wanted.add(targetId);
                view.targets.put(targetId, target);
            }
            var iterator = view.targets.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (wanted.contains(entry.getKey())) continue;
                restore(view.viewer, entry.getValue());
                iterator.remove();
            }
        }
        var iterator = views.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (present.contains(entry.getKey())) continue;
            reset(entry.getValue());
            iterator.remove();
        }
    }

    public void remove(UUID id) {
        View removed = views.remove(id);
        if (removed != null) reset(removed);
        for (View view : views.values()) {
            Player target = view.targets.remove(id);
            if (target != null) restore(view.viewer, target);
        }
    }

    public void clear() {
        views.values().forEach(this::reset);
        views.clear();
    }

    private void reset(View view) {
        for (Player target : view.targets.values()) restore(view.viewer, target);
        view.targets.clear();
    }

    private void restore(Player viewer, Player target) {
        if (!viewer.isOnline()) return;
        Object handle = handle(target, true);
        if (handle != null) emit(viewer, handle, false);
    }

    private Object handle(Player player) {
        return handle(player, false);
    }

    private Object handle(Player player, boolean restoring) {
        if (failed && !restoring) return null;
        try {
            if (Bindings.INSTANCE == null) {
                unsupported(Bindings.initializationFailure);
                return null;
            }
            return handles.apply(player);
        } catch (RuntimeException | LinkageError error) {
            unsupported(error);
            return null;
        }
    }

    private boolean emit(Player viewer, Object handle, boolean glowing) {
        try {
            sender.accept(viewer, Bindings.INSTANCE.packet(handle, glowing));
            return true;
        } catch (RuntimeException | LinkageError error) {
            unsupported(error);
            return false;
        }
    }

    private void unsupported(Throwable error) {
        // Injected native checks fail loudly; an unsupported runtime must still retain ally
        // labels, objective displays and the rest of the match, including its cleanup.
        if (plugin == null)
            throw new IllegalStateException("Native teammate glow is unavailable", error);
        failed = true;
        if (!warned) {
            warned = true;
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Native teammate outlines are unavailable; the match will continue"
                                + " without outlines",
                            error);
        }
    }

    /** Reflection is limited to verified Mojang-named Paper 26.3 metadata/connection members. */
    private static final class Bindings {
        static Throwable initializationFailure;
        static final Bindings INSTANCE = initialize();
        final Class<?> craftPlayer;
        final Method handle, entityData, getData, createValue, entityId, send;
        final Field connection;
        final Object flagsAccessor;
        final Constructor<?> metadataPacket;

        private static Bindings initialize() {
            try {
                return new Bindings();
            } catch (RuntimeException | LinkageError error) {
                initializationFailure = error;
                return null;
            }
        }

        Bindings() {
            try {
                craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
                Class<?> accessor =
                        Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
                Class<?> data = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
                Class<?> value =
                        Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
                Class<?> player = Class.forName("net.minecraft.server.level.ServerPlayer");
                Class<?> listener =
                        Class.forName(
                                "net.minecraft.server.network.ServerCommonPacketListenerImpl");
                handle = craftPlayer.getMethod("getHandle");
                entityData = entity.getMethod("getEntityData");
                getData = data.getMethod("get", accessor);
                createValue = value.getMethod("create", accessor, Object.class);
                entityId = entity.getMethod("getId");
                Field flags = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
                flags.setAccessible(true);
                flagsAccessor = flags.get(null);
                metadataPacket =
                        Class.forName(
                                        "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                                .getConstructor(int.class, List.class);
                connection = player.getField("connection");
                send =
                        listener.getMethod(
                                "send", Class.forName("net.minecraft.network.protocol.Packet"));
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        Object handle(Player player) {
            if (!craftPlayer.isInstance(player)) return null;
            try {
                return handle.invoke(player);
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        Object packet(Object nativeEntity, boolean glowing) {
            try {
                Object data = entityData.invoke(nativeEntity);
                byte current = (Byte) getData.invoke(data, flagsAccessor);
                // Read the current flags each time, preserving fire, invisibility, crouching,
                // sprinting and any globally applied glow. Never mutate SynchedEntityData.
                byte shown = glowing ? (byte) (current | 0x40) : current;
                Object value = createValue.invoke(null, flagsAccessor, Byte.valueOf(shown));
                return metadataPacket.newInstance(
                        (Integer) entityId.invoke(nativeEntity), List.of(value));
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        void send(Player viewer, Object packet) {
            Object nativeViewer = handle(viewer);
            if (nativeViewer == null) return;
            try {
                Object listener = connection.get(nativeViewer);
                if (listener != null) send.invoke(listener, packet);
            } catch (ReflectiveOperationException error) {
                throw failure(error);
            }
        }

        private static IllegalStateException failure(ReflectiveOperationException error) {
            return new IllegalStateException(
                    "Private player glow requires Paper 26.3 metadata support", error);
        }
    }
}
