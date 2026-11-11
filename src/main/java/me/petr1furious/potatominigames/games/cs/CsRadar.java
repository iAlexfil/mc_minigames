package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

import javax.imageio.ImageIO;

/** Per-viewer vanilla map radar: team information and only legitimately spotted enemies. */
final class CsRadar extends MapRenderer {
    private final CsSession session;
    private final BufferedImage image;
    final MapView view;

    private record Spotted(Location at, long until) {}

    private final Map<Integer, Map<UUID, Spotted>> spotted =
            Map.of(0, new HashMap<>(), 1, new HashMap<>());
    private long updated = -1;

    CsRadar(CsSession session) {
        super(true);
        this.session = session;
        try (var in = CsRadar.class.getResourceAsStream("/maps/cs/radar.png")) {
            if (in == null) throw new IOException("Missing radar");
            image = ImageIO.read(in);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read map radar", error);
        }
        view = Bukkit.createMap(session.world());
        for (var renderer : List.copyOf(view.getRenderers())) view.removeRenderer(renderer);
        view.addRenderer(this);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player viewer) {
        var actor = session.actor(viewer);
        if (actor == null) return;
        if (updated < 0 || session.now() - updated >= 5) {
            updated = session.now();
            for (Player observer : session.players())
                if (session.active(observer))
                    for (Player target : session.players())
                        if (session.active(target)
                                && session.actor(observer).team != session.actor(target).team
                                && session.visible(observer, target))
                            spotted.get(session.actor(observer).team)
                                    .put(
                                            target.getUniqueId(),
                                            new Spotted(
                                                    target.getLocation().clone(),
                                                    session.now() + 30));
        }
        canvas.drawImage(0, 0, image);
        canvas.drawText(x(104) - 2, z(89) - 3, MinecraftFont.Font, "A");
        canvas.drawText(x(114) - 2, z(7) - 3, MinecraftFont.Font, "B");
        for (Player p : session.players())
            if (session.active(p)) {
                boolean ally = session.actor(p).team == actor.team;
                Spotted seen = spotted.get(actor.team).get(p.getUniqueId());
                if (ally || seen != null && seen.until > session.now())
                    dot(
                            canvas,
                            ally ? p.getLocation() : seen.at,
                            p.getUniqueId().equals(viewer.getUniqueId())
                                    ? Color.GREEN
                                    : ally ? Color.CYAN : Color.RED);
            }
        Location objective = session.objective(viewer);
        if (objective != null) dot(canvas, objective, Color.YELLOW);
    }

    private static int x(double at) {
        return (int) Math.clamp((at + 25) / 156 * 127, 0, 127);
    }

    private static int z(double at) {
        return (int) Math.clamp((at + 34) / 154 * 127, 0, 127);
    }

    private static void dot(MapCanvas canvas, Location at, Color color) {
        int x = x(at.getX()), z = z(at.getZ());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (x + dx >= 0 && x + dx < 128 && z + dz >= 0 && z + dz < 128)
                    canvas.setPixelColor(x + dx, z + dz, color);
    }

    void clear() {
        for (var entries : spotted.values()) entries.clear();
        view.removeRenderer(this);
    }
}
