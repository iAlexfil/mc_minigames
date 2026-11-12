import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Native Q packets, including an empty-hand press that never creates a Bukkit item-drop event. */
public final class FinalsDropInputSmoke {
    private final JavaPlugin smoke;
    private final Runnable done;
    private final Consumer<Throwable> failed;
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final EmbeddedChannel outsider = new EmbeddedChannel();
    private final AtomicInteger throwsQueued = new AtomicInteger();
    private final AtomicInteger throwsDelivered = new AtomicInteger();
    private final AtomicBoolean onlyMainThread = new AtomicBoolean(true);
    private AutoCloseable tap;
    private int assertions;
    private boolean finished;

    public static void run(JavaPlugin smoke, JavaPlugin main, Runnable done, Consumer<Throwable> failed) {
        new FinalsDropInputSmoke(smoke, done, failed).start();
    }

    private FinalsDropInputSmoke(JavaPlugin smoke, Runnable done, Consumer<Throwable> failed) {
        this.smoke = smoke;
        this.done = done;
        this.failed = failed;
    }

    private void start() {
        guard(() -> {
            channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
            Class<?> input = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsDropInput");
            Method attach = input.getDeclaredMethod("attach", Object.class, Runnable.class, Consumer.class);
            attach.setAccessible(true);
            Runnable throwBox = () -> {
                if (!Bukkit.isPrimaryThread()) onlyMainThread.set(false);
                throwsDelivered.incrementAndGet();
            };
            Consumer<Runnable> dispatch = task -> {
                throwsQueued.incrementAndGet();
                Bukkit.getScheduler().runTask(smoke, task);
            };
            tap = (AutoCloseable) attach.invoke(null, channel, throwBox, dispatch);
            channel.runPendingTasks();
            require(channel.pipeline().names().stream().anyMatch(name -> name.startsWith("potato-finals-drop-")),
                    "A participant receives a native input handler");
            // Several packets in one network batch schedule only one main-thread callback.
            channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ITEM));
            channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ITEM));
            require(throwsQueued.get() == 1 && throwsDelivered.get() == 0,
                    "An empty-hand Q packet queues one deferred callback, bounded across a burst");
            require(channel.readInbound() instanceof ServerboundPlayerActionPacket
                            && channel.readInbound() instanceof ServerboundPlayerActionPacket,
                    "Q packets still reach vanilla handling unchanged");
            later(() -> {
                require(throwsDelivered.get() == 1 && onlyMainThread.get(),
                        "Empty-hand Q reaches gameplay only on the main server thread");
                Bukkit.getScheduler().runTaskAsynchronously(smoke, () -> {
                    try {
                        channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS));
                        Bukkit.getScheduler().runTask(smoke, () -> later(this::afterControlQ));
                    } catch (Throwable error) {
                        Bukkit.getScheduler().runTask(smoke, () -> finish(error));
                    }
                });
            });
        });
    }

    private void afterControlQ() throws Exception {
        require(throwsDelivered.get() == 2 && onlyMainThread.get(),
                "Ctrl-Q arriving from a network thread is delivered on the main thread");
        require(channel.readInbound() instanceof ServerboundPlayerActionPacket,
                "Ctrl-Q still reaches the normal native packet handler");
        channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND));
        channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK));
        outsider.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ITEM));
        later(() -> {
            require(throwsQueued.get() == 2 && throwsDelivered.get() == 2,
                    "Reload, mining and players outside the session never throw the cashbox");
            require(channel.readInbound() != null && channel.readInbound() != null
                            && outsider.readInbound() != null,
                    "Unrelated input and nonparticipant channels remain intact");
            channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ITEM));
            require(throwsQueued.get() == 3, "A later Q press can schedule a new callback");
            tap.close();
            channel.runPendingTasks();
            require(channel.pipeline().names().stream().noneMatch(name -> name.startsWith("potato-finals-drop-")),
                    "Leaving or ending the session removes its input handler");
            later(() -> {
                require(throwsDelivered.get() == 2, "A callback queued before cleanup cannot throw afterward");
                channel.writeInbound(packet(ServerboundPlayerActionPacket.Action.DROP_ITEM));
                later(() -> {
                    require(throwsQueued.get() == 3 && throwsDelivered.get() == 2,
                            "Input after removal cannot reach the ended session");
                    smoke.getLogger().info("FINALS_DROP_INPUT_SMOKE_PASS: " + assertions + " assertions");
                    finish(null);
                });
            });
        });
    }

    private static ServerboundPlayerActionPacket packet(ServerboundPlayerActionPacket.Action action) {
        return new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private void later(Check check) {
        Bukkit.getScheduler().runTaskLater(smoke, () -> guard(check), 2);
    }

    private void guard(Check check) {
        if (finished) return;
        try { check.run(); } catch (Throwable error) { finish(error); }
    }

    private void finish(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            if (tap != null) tap.close();
            channel.runPendingTasks();
            channel.finishAndReleaseAll();
            outsider.finishAndReleaseAll();
        } catch (Throwable cleanup) {
            if (error == null) error = cleanup;
            else error.addSuppressed(cleanup);
        }
        if (error == null) done.run();
        else failed.accept(error);
    }

    @FunctionalInterface private interface Check { void run() throws Exception; }
}
