package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Private Tab rows. Health is neither read for enemies nor written to a global player name. */
final class FinalsTabList {
    private final JavaPlugin plugin;
    private final BiConsumer<Player, Object> sender;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private boolean failed;
    private static boolean warned;

    private record Row(Player target, Component text) {}

    private static final class View {
        final Player viewer;
        final Map<UUID, Row> rows = new LinkedHashMap<>();
        int refresh;

        View(Player viewer) {
            this.viewer = viewer;
        }
    }

    FinalsTabList(JavaPlugin plugin) {
        this.plugin = plugin;
        sender = (viewer, packet) -> Bindings.INSTANCE.send(viewer, packet);
    }

    /** Native checks observe real update packets without a connected Minecraft client. */
    FinalsTabList(BiConsumer<Player, Object> sender) {
        plugin = null;
        this.sender = sender;
    }

    void update(
            World world,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Map<UUID, FinalsClass> classes,
            Map<UUID, Integer> kills,
            Set<UUID> respawning) {
        update(world, players, teams, classes, kills, respawning, null);
    }

    void update(
            World world,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Map<UUID, FinalsClass> classes,
            Map<UUID, Integer> kills,
            Set<UUID> respawning,
            UUID gameMaster) {
        if (failed) {
            clear();
            return;
        }
        Set<UUID> present = new HashSet<>();
        for (Player viewer : players) {
            UUID viewerId = viewer.getUniqueId();
            boolean masterViewer = viewerId.equals(gameMaster);
            Integer viewerTeam = masterViewer ? null : teams.get(viewerId);
            if ((!masterViewer && viewerTeam == null)
                    || !viewer.isOnline()
                    || !world.equals(viewer.getWorld())) continue;
            present.add(viewerId);
            View view = views.get(viewerId);
            if (view != null && view.viewer != viewer) {
                reset(view);
                views.remove(viewerId);
            }
            view = views.computeIfAbsent(viewerId, ignored -> new View(viewer));
            boolean refresh = ++view.refresh % 4 == 0;
            Set<UUID> wanted = new HashSet<>();
            for (Player target : players) {
                UUID targetId = target.getUniqueId();
                boolean masterTarget = targetId.equals(gameMaster);
                Integer team = masterTarget ? null : teams.get(targetId);
                if ((!masterTarget && team == null)
                        || !target.isOnline()
                        || !world.equals(target.getWorld())) continue;
                Component text;
                if (masterTarget) {
                    // The game master has a role, not a combat loadout or team health row.
                    text = gameMasterEntry(viewer.locale(), target.getName());
                } else {
                    FinalsClass build = classes.getOrDefault(targetId, FinalsClass.MEDIUM);
                    boolean ally = viewerTeam != null && viewerTeam.equals(team);
                    boolean waiting = ally && (respawning.contains(targetId) || target.isDead());
                    Double health = null, maximum = null;
                    if (ally && !waiting) {
                        // Keep these reads inside the ally branch: enemy health never enters a row.
                        health = target.getHealth();
                        var attribute = target.getAttribute(Attribute.MAX_HEALTH);
                        maximum = attribute == null ? build.health() : attribute.getValue();
                    }
                    text =
                            entry(
                                    viewer.locale(),
                                    target.getName(),
                                    team,
                                    build,
                                    kills.getOrDefault(targetId, 0),
                                    health,
                                    maximum,
                                    waiting);
                }
                Row previous = view.rows.get(targetId);
                if (refresh
                        || previous == null
                        || previous.target() != target
                        || !previous.text().equals(text)) {
                    if (!emit(viewer, targetId, text)) continue;
                }
                wanted.add(targetId);
                view.rows.put(targetId, new Row(target, text));
            }
            var rows = view.rows.entrySet().iterator();
            while (rows.hasNext()) {
                var row = rows.next();
                if (wanted.contains(row.getKey())) continue;
                restore(view.viewer, row.getValue().target());
                rows.remove();
            }
        }
        var iterator = views.entrySet().iterator();
        while (iterator.hasNext()) {
            var view = iterator.next();
            if (present.contains(view.getKey())) continue;
            reset(view.getValue());
            iterator.remove();
        }
        if (failed) clear();
    }

    static Component gameMasterEntry(Locale locale, String name) {
        return I18n.render(
                I18n.tr("finals.tab.game_master", Component.text(name, NamedTextColor.GOLD))
                        .color(NamedTextColor.GRAY),
                locale);
    }

    static Component entry(
            Locale locale,
            String name,
            int team,
            FinalsClass build,
            int kills,
            Double health,
            Double maximum,
            boolean respawning) {
        Component result =
                I18n.tr(
                                "finals.tab.player",
                                Component.text(
                                        name, team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE),
                                build.display(),
                                Math.max(0, kills))
                        .color(NamedTextColor.GRAY);
        if (respawning) {
            result = result.append(I18n.tr("finals.tab.respawning").color(NamedTextColor.GRAY));
        } else if (health != null) {
            double limit =
                    maximum != null && Double.isFinite(maximum) && maximum > 0
                            ? maximum
                            : build.health();
            int max = Math.max(1, (int) Math.ceil(limit));
            int current = Double.isFinite(health) ? Math.clamp((int) Math.ceil(health), 0, max) : 0;
            result =
                    result.append(
                            I18n.tr("finals.tab.health", current, max).color(NamedTextColor.GREEN));
        }
        return I18n.render(result, locale);
    }

    void remove(UUID id) {
        View view = views.remove(id);
        if (view != null) reset(view);
        for (View remaining : views.values()) {
            Row removed = remaining.rows.remove(id);
            if (removed != null) restore(remaining.viewer, removed.target());
        }
    }

    void clear() {
        views.values().forEach(this::reset);
        views.clear();
    }

    private void reset(View view) {
        for (Row row : view.rows.values()) restore(view.viewer, row.target());
        view.rows.clear();
    }

    private boolean emit(Player viewer, UUID id, Component text) {
        try {
            if (Bindings.INSTANCE == null) {
                unsupported(Bindings.initializationFailure);
                return false;
            }
            sender.accept(viewer, Bindings.INSTANCE.packet(id, Bindings.INSTANCE.nativeText(text)));
            return true;
        } catch (RuntimeException | LinkageError error) {
            unsupported(error);
            return false;
        }
    }

    private void restore(Player viewer, Player target) {
        if (!viewer.isOnline()) return;
        // Restoration deliberately bypasses the failed flag and reads the current global name,
        // allowing another plugin's later changes and the native null/default formatting to
        // survive.
        try {
            if (Bindings.INSTANCE == null) return;
            sender.accept(
                    viewer,
                    Bindings.INSTANCE.packet(
                            target.getUniqueId(), Bindings.INSTANCE.originalName(target)));
        } catch (RuntimeException | LinkageError error) {
            unsupported(error);
        }
    }

    private void unsupported(Throwable error) {
        if (plugin == null)
            throw new IllegalStateException("Native Finals Tab rows are unavailable", error);
        failed = true;
        if (!warned) {
            warned = true;
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Private Finals Tab rows are unavailable on this server",
                            error);
        }
    }

    /**
     * Only UPDATE_DISPLAY_NAME is emitted; other player-info fields and global names stay intact.
     */
    private static final class Bindings {
        static Throwable initializationFailure;
        static final Bindings INSTANCE = initialize();
        final Class<?> craftPlayer;
        final Method handle, send, asVanilla, originalName;
        final Field connection;
        final Constructor<?> entry, packet;
        final EnumSet<?> actions;
        final Object gameMode;

        private static Bindings initialize() {
            try {
                return new Bindings();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                initializationFailure = error;
                return null;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Bindings() throws ReflectiveOperationException {
            craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> nativePlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> component = Class.forName("net.minecraft.network.chat.Component");
            Class<?> action =
                    Class.forName(
                            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> nativeEntry =
                    Class.forName(
                            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
            handle = craftPlayer.getMethod("getHandle");
            connection = nativePlayer.getField("connection");
            send =
                    Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                            .getMethod(
                                    "send", Class.forName("net.minecraft.network.protocol.Packet"));
            originalName = nativePlayer.getMethod("getTabListDisplayName");
            asVanilla =
                    Class.forName("io.papermc.paper.adventure.PaperAdventure")
                            .getMethod("asVanilla", Component.class);
            actions = EnumSet.of((Enum) action.getField("UPDATE_DISPLAY_NAME").get(null));
            gameMode = gameType.getField("SURVIVAL").get(null);
            entry =
                    nativeEntry.getConstructor(
                            UUID.class,
                            Class.forName("com.mojang.authlib.GameProfile"),
                            boolean.class,
                            int.class,
                            gameType,
                            component,
                            boolean.class,
                            int.class,
                            Class.forName("net.minecraft.network.chat.RemoteChatSession$Data"));
            packet =
                    Class.forName(
                                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
                            .getConstructor(EnumSet.class, List.class);
        }

        Object nativeText(Component text) {
            try {
                return text == null ? null : asVanilla.invoke(null, text);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        Object originalName(Player target) {
            try {
                return craftPlayer.isInstance(target)
                        ? originalName.invoke(handle.invoke(target))
                        : nativeText(target.playerListName());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        Object packet(UUID id, Object text) {
            try {
                Object row = entry.newInstance(id, null, true, 0, gameMode, text, true, 0, null);
                return packet.newInstance(actions.clone(), List.of(row));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        void send(Player viewer, Object packet) {
            if (!craftPlayer.isInstance(viewer)) return;
            try {
                Object listener = connection.get(handle.invoke(viewer));
                if (listener != null) send.invoke(listener, packet);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }
}
