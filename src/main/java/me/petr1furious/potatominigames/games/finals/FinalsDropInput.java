package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Receives Q even with an empty hand, for which Bukkit creates no item-drop event. */
final class FinalsDropInput {
    private final JavaPlugin plugin;
    private final Consumer<UUID> drop;
    private final Map<UUID, Tap> taps = new HashMap<>();
    private boolean warned;

    FinalsDropInput(JavaPlugin plugin, Consumer<UUID> drop) {
        this.plugin = plugin;
        this.drop = drop;
    }

    void track(Player player) {
        UUID id = player.getUniqueId();
        remove(id);
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object listener = field(handle, "connection");
            Object connection = field(listener, "connection");
            Object channel = field(connection, "channel");
            taps.put(
                    id,
                    attach(
                            channel,
                            () -> drop.accept(id),
                            task -> Bukkit.getScheduler().runTask(plugin, task)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (!warned) {
                warned = true;
                plugin.getLogger()
                        .warning(
                                "Finals empty-hand Q input is unavailable on this server; "
                                        + "normal item-drop input remains enabled: "
                                        + error.getClass().getSimpleName());
            }
        }
    }

    private static Object field(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    void remove(UUID id) {
        Tap tap = taps.remove(id);
        if (tap != null) tap.close();
    }

    void clear() {
        taps.values().forEach(Tap::close);
        taps.clear();
    }

    static Tap attach(Object channel, Runnable drop, Consumer<Runnable> dispatch)
            throws ReflectiveOperationException {
        return new Tap(channel, drop, dispatch);
    }

    /** Only immutable network objects and a bounded callback queue are accessed off-thread. */
    static final class Tap implements AutoCloseable {
        private final String name = "potato-finals-drop-" + UUID.randomUUID();
        private final AtomicBoolean queued = new AtomicBoolean();
        private final Executor loop;
        private final Object pipeline;
        private final Class<?> pipelineType;
        private volatile boolean closed;

        Tap(Object channel, Runnable drop, Consumer<Runnable> dispatch)
                throws ReflectiveOperationException {
            ClassLoader loader = FinalsDropInput.class.getClassLoader();
            Class<?> channelType = Class.forName("io.netty.channel.Channel", false, loader);
            Class<?> inboundType =
                    Class.forName("io.netty.channel.ChannelInboundHandler", false, loader);
            Class<?> handlerType = Class.forName("io.netty.channel.ChannelHandler", false, loader);
            Class<?> contextType =
                    Class.forName("io.netty.channel.ChannelHandlerContext", false, loader);
            pipelineType = Class.forName("io.netty.channel.ChannelPipeline", false, loader);
            Class<?> packetType =
                    Class.forName(
                            "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket",
                            false,
                            loader);
            Method action = packetType.getMethod("getAction");
            pipeline = channelType.getMethod("pipeline").invoke(channel);
            loop = (Executor) channelType.getMethod("eventLoop").invoke(channel);
            Object handler =
                    Proxy.newProxyInstance(
                            loader,
                            new Class<?>[] {inboundType},
                            (proxy, method, args) -> {
                                String operation = method.getName();
                                if (operation.equals("toString")) return name;
                                if (operation.equals("hashCode"))
                                    return System.identityHashCode(proxy);
                                if (operation.equals("equals")) return proxy == args[0];
                                if (operation.equals("handlerAdded")
                                        || operation.equals("handlerRemoved")) return null;
                                if (operation.equals("channelRead")
                                        && !closed
                                        && packetType.isInstance(args[1])) {
                                    String kind = ((Enum<?>) action.invoke(args[1])).name();
                                    if ((kind.equals("DROP_ITEM") || kind.equals("DROP_ALL_ITEMS"))
                                            && queued.compareAndSet(false, true)) {
                                        try {
                                            dispatch.accept(() -> {
                                                queued.set(false);
                                                if (!closed) drop.run();
                                            });
                                        } catch (RuntimeException error) {
                                            queued.set(false);
                                            // Shutdown can reject scheduling; never disrupt vanilla
                                            // packet handling.
                                        }
                                    }
                                }
                                String forward =
                                        "fire"
                                                + Character.toUpperCase(operation.charAt(0))
                                                + operation.substring(1);
                                Class<?>[] parameters =
                                        java.util.Arrays.copyOfRange(
                                                method.getParameterTypes(),
                                                1,
                                                method.getParameterCount());
                                Object[] forwarded =
                                        java.util.Arrays.copyOfRange(args, 1, args.length);
                                try {
                                    contextType
                                            .getMethod(forward, parameters)
                                            .invoke(args[0], forwarded);
                                } catch (InvocationTargetException error) {
                                    throw error.getCause();
                                }
                                return null;
                            });
            Method add =
                    pipelineType.getMethod("addBefore", String.class, String.class, handlerType);
            loop.execute(() -> {
                if (closed) return;
                try {
                    add.invoke(pipeline, "packet_handler", name, handler);
                } catch (ReflectiveOperationException error) {
                    closed = true;
                    Bukkit.getLogger()
                            .warning(
                                    "Could not attach Finals empty-hand Q input: "
                                            + error.getMessage());
                }
            });
        }

        @Override
        public void close() {
            closed = true;
            try {
                loop.execute(() -> {
                    try {
                        if (pipelineType.getMethod("get", String.class).invoke(pipeline, name)
                                != null)
                            pipelineType.getMethod("remove", String.class).invoke(pipeline, name);
                    } catch (ReflectiveOperationException ignored) {
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // A closed connection has already discarded its pipeline; queued callbacks stay
                // disabled.
            }
        }
    }
}
