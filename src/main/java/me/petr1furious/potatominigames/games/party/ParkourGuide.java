package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Private, non-colliding markers distinguish the actual goal from walls and intermediate pads. */
final class ParkourGuide {
    private record Marker(int stage, BlockDisplay frame, TextDisplay label) {
        boolean valid() {
            return frame.isValid() && label.isValid();
        }

        void remove() {
            frame.remove();
            label.remove();
        }
    }

    private final Plugin plugin;
    private final ParkourArena arena;
    private final ParkourCourse course;
    private final Map<UUID, Marker> markers = new HashMap<>();

    ParkourGuide(Plugin plugin, ParkourArena arena, ParkourCourse course) {
        this.plugin = plugin;
        this.arena = arena;
        this.course = course;
    }

    void update(Player p, int stage) {
        if (stage < 0 || stage >= course.stages()) {
            remove(p.getUniqueId());
            return;
        }
        Location target = arena.checkpoint(p.getUniqueId(), stage + 1);
        if (!target.getWorld().equals(p.getWorld())) {
            remove(p.getUniqueId());
            return;
        }
        Marker marker = markers.get(p.getUniqueId());
        if (marker == null || marker.stage != stage || !marker.valid()) {
            remove(p.getUniqueId());
            var island = course.island(stage + 1);
            var frame =
                    target.getWorld()
                            .spawn(
                                    frameOrigin(target, island.width()),
                                    BlockDisplay.class,
                                    display -> {
                                        display.setVisibleByDefault(false);
                                        display.setPersistent(false);
                                        display.setInvulnerable(true);
                                        display.setBlock(
                                                Material.LIME_STAINED_GLASS.createBlockData());
                                        display.setGlowing(true);
                                        display.setGlowColorOverride(Color.LIME);
                                        display.setBrightness(new Display.Brightness(15, 15));
                                        display.setViewRange(4);
                                        display.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        new Vector3f(island.width(), .1f, 7),
                                                        new Quaternionf()));
                                    });
            var label =
                    target.getWorld()
                            .spawn(
                                    target.clone().add(0, 2.4, 0),
                                    TextDisplay.class,
                                    display -> {
                                        display.setVisibleByDefault(false);
                                        display.setPersistent(false);
                                        display.setInvulnerable(true);
                                        display.setBillboard(Display.Billboard.CENTER);
                                        display.setSeeThrough(true);
                                        display.setShadowed(true);
                                        display.setViewRange(4);
                                        display.setBrightness(new Display.Brightness(15, 15));
                                    });
            marker = new Marker(stage, frame, label);
            markers.put(p.getUniqueId(), marker);
            p.showEntity(plugin, frame);
            p.showEntity(plugin, label);
        }
        marker.label.text(
                I18n.render(
                        I18n.tr("party.rounds.parkour_race.target", stage + 2, course.stages() + 1),
                        p));
        Location at = p.getLocation();
        p.sendActionBar(
                I18n.tr(
                        "party.rounds.parkour_race.navigation",
                        direction(
                                at.getYaw(), target.getX() - at.getX(), target.getZ() - at.getZ()),
                        stage + 2,
                        course.stages() + 1,
                        (int) Math.ceil(at.distance(target)),
                        height(target.getY() - at.getY())));
    }

    static Location frameOrigin(Location target, int width) {
        // Checkpoint yaw faces the following task; the floor overlay must stay axis-aligned.
        return new Location(
                target.getWorld(),
                target.getX() - width / 2.0,
                target.getY() + .025,
                target.getZ() - 3.5);
    }

    static Component height(double delta) {
        return Math.abs(delta) < 1
                ? I18n.tr("party.rounds.parkour_race.height.level")
                : I18n.tr(
                        delta > 0
                                ? "party.rounds.parkour_race.height.up"
                                : "party.rounds.parkour_race.height.down",
                        (int) Math.ceil(Math.abs(delta)));
    }

    static String direction(float yaw, double dx, double dz) {
        if (Math.hypot(dx, dz) < .5) return "●";
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        int sector = Math.floorMod((int) Math.round((bearing - yaw) / 45), 8);
        return new String[] {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[sector];
    }

    void remove(UUID id) {
        Marker marker = markers.remove(id);
        if (marker != null) marker.remove();
    }

    void clear() {
        markers.values().forEach(Marker::remove);
        markers.clear();
    }
}
