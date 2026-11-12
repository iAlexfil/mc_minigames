package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Session-owned, private objective outlines and ally name markers. */
final class FinalsHighlights {
    private final JavaPlugin plugin;
    private final Map<UUID, View> views = new LinkedHashMap<>();

    record Marker(
            String key, Location location, Component label, NamedTextColor color, Material block) {}

    private record Overlay(TextDisplay label, BlockDisplay outline) {
        boolean valid() {
            return label.isValid() && (outline == null || outline.isValid());
        }

        void remove() {
            label.remove();
            if (outline != null) outline.remove();
        }
    }

    private static final class View {
        final Player player;
        final Map<String, Overlay> overlays = new LinkedHashMap<>();

        View(Player player) {
            this.player = player;
        }

        void clear() {
            overlays.values().forEach(Overlay::remove);
            overlays.clear();
        }
    }

    FinalsHighlights(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void update(
            FinalsMapArena arena,
            FinalsState.Phase phase,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Predicate<Player> active) {
        Set<UUID> present = new HashSet<>();
        for (Player player : players) {
            List<Marker> markers = markers(arena, phase, player, players, teams, active);
            if (markers.isEmpty()) continue;
            UUID id = player.getUniqueId();
            present.add(id);
            View view = views.get(id);
            if (view != null && view.player != player) {
                view.clear();
                views.remove(id);
            }
            view = views.computeIfAbsent(id, ignored -> new View(player));
            refresh(view, markers);
        }
        views.entrySet()
                .removeIf(entry -> {
                    if (present.contains(entry.getKey())) return false;
                    entry.getValue().clear();
                    return true;
                });
    }

    static List<Marker> markers(
            FinalsMapArena arena,
            FinalsState.Phase phase,
            Player viewer,
            Collection<Player> players,
            Map<UUID, Integer> teams,
            Predicate<Player> active) {
        Integer team = teams.get(viewer.getUniqueId());
        if (phase == FinalsState.Phase.FINISHED
                || team == null
                || !viewer.isOnline()
                || !arena.world().equals(viewer.getWorld())) return List.of();
        List<Marker> result = new ArrayList<>();
        if (phase == null
                || phase == FinalsState.Phase.VAULT_CLOSED
                || phase == FinalsState.Phase.VAULT_OPENING) {
            result.add(
                    new Marker(
                            "vault",
                            arena.vault().clone(),
                            I18n.tr("finals.highlight.vault"),
                            NamedTextColor.GREEN,
                            Material.EMERALD_BLOCK));
        }
        for (int station = 0; station < 2; station++) {
            result.add(
                    new Marker(
                            "cashout-" + station,
                            arena.station(station).clone(),
                            I18n.tr("finals.highlight.cashout", station == 0 ? "A" : "B"),
                            NamedTextColor.GOLD,
                            Material.GOLD_BLOCK));
        }
        for (Player target : players) {
            UUID id = target.getUniqueId();
            if (id.equals(viewer.getUniqueId())
                    || !team.equals(teams.get(id))
                    || !target.isOnline()
                    || !arena.world().equals(target.getWorld())
                    || !active.test(target)) continue;
            result.add(
                    new Marker(
                            "teammate-" + id,
                            target.getLocation().clone().add(0, 2.4, 0),
                            I18n.tr("finals.highlight.teammate", target.getName()),
                            team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE,
                            null));
        }
        return List.copyOf(result);
    }

    private void refresh(View view, List<Marker> markers) {
        Set<String> wanted = new HashSet<>();
        for (Marker marker : markers) {
            wanted.add(marker.key());
            Overlay overlay = view.overlays.get(marker.key());
            if (overlay != null && !overlay.valid()) {
                overlay.remove();
                view.overlays.remove(marker.key());
                overlay = null;
            }
            Component text = I18n.render(marker.label().color(marker.color()), view.player);
            if (overlay == null) {
                overlay = create(view.player, marker, text);
                view.overlays.put(marker.key(), overlay);
            } else {
                Location position = labelPosition(marker);
                if (!overlay.label().getLocation().equals(position))
                    overlay.label().teleport(position);
                if (!overlay.label().text().equals(text)) overlay.label().text(text);
                if (overlay.outline() != null) {
                    Location block = blockPosition(marker);
                    if (!overlay.outline().getLocation().equals(block))
                        overlay.outline().teleport(block);
                }
            }
        }
        view.overlays
                .entrySet()
                .removeIf(entry -> {
                    if (wanted.contains(entry.getKey())) return false;
                    entry.getValue().remove();
                    return true;
                });
    }

    private Overlay create(Player viewer, Marker marker, Component text) {
        TextDisplay label =
                marker.location()
                        .getWorld()
                        .spawn(
                                labelPosition(marker),
                                TextDisplay.class,
                                entity -> {
                                    configure(entity);
                                    entity.setBillboard(Display.Billboard.CENTER);
                                    entity.setSeeThrough(true);
                                    entity.setShadowed(true);
                                    entity.text(text);
                                });
        BlockDisplay outline = null;
        try {
            if (marker.block() != null) {
                outline =
                        marker.location()
                                .getWorld()
                                .spawn(
                                        blockPosition(marker),
                                        BlockDisplay.class,
                                        entity -> {
                                            configure(entity);
                                            entity.setBlock(marker.block().createBlockData());
                                            entity.setGlowing(true);
                                            entity.setGlowColorOverride(
                                                    Color.fromRGB(marker.color().value()));
                                            entity.setTransformationMatrix(
                                                    new Matrix4f()
                                                            .translation(-.01f, -.01f, -.01f)
                                                            .scale(1.02f));
                                        });
            }
            viewer.showEntity(plugin, label);
            if (outline != null) viewer.showEntity(plugin, outline);
            return new Overlay(label, outline);
        } catch (RuntimeException error) {
            label.remove();
            if (outline != null) outline.remove();
            throw error;
        }
    }

    private static void configure(Display display) {
        // Apply privacy inside the spawn callback, before the entity is tracked.
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setViewRange(4f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTeleportDuration(2);
    }

    private static Location labelPosition(Marker marker) {
        return marker.location().clone().add(0, marker.block() == null ? 0 : 2, 0);
    }

    private static Location blockPosition(Marker marker) {
        Location point = marker.location();
        return new Location(point.getWorld(), point.getBlockX(), point.getY(), point.getBlockZ());
    }

    void hideTeammate(UUID id) {
        for (View view : views.values()) {
            Overlay overlay = view.overlays.remove("teammate-" + id);
            if (overlay != null) overlay.remove();
        }
    }

    void remove(UUID id) {
        View view = views.remove(id);
        if (view != null) view.clear();
        hideTeammate(id);
    }

    void clear() {
        views.values().forEach(View::clear);
        views.clear();
    }
}
