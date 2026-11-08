package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

final class HoleWallSession extends PartySurvivalSession {
    private final int seconds;
    private HoleWallArena arena;
    private HoleWallFlow flow;
    private final Map<HoleWallFlow.Wall, HoleWallDisplay> displays = new LinkedHashMap<>();
    private final Map<UUID, BoundingBox> previous = new HashMap<>();
    private final Map<UUID, Long> nextHitSound = new HashMap<>();
    private int wallNumber, warnedRadius = 8, warnedArmRadius = 2;

    HoleWallSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "hole_in_the_wall", context);
        seconds = config.holeWallSeconds.get();
    }

    void buildArena() {
        arena = new HoleWallArena(world);
        arena.build();
        flow = new HoleWallFlow(seconds * 20L, random);
        world.setTime(6000);
    }

    Location spawn(int i, int n) {
        return arena.spawn(i, n);
    }

    Location viewpoint() {
        return new Location(world, .5, 96, .5, 0, 70);
    }

    double outHeight() {
        return 76;
    }

    int durationSeconds() {
        return seconds;
    }

    boolean podiumAwards() {
        return true;
    }

    Component instructions() {
        return I18n.tr("party.rounds.hole_wall.instructions");
    }

    void playTick(long elapsed) {
        var frame = flow.tick(elapsed);
        for (var wall : frame.finished()) {
            var display = displays.remove(wall);
            if (display != null) display.clear();
        }
        boolean newWarning =
                frame.warningRadius() < warnedRadius || frame.warningArmRadius() < warnedArmRadius;
        boolean reshaping =
                frame.radius() < arena.radius() || frame.armRadius() < arena.armRadius();
        if (newWarning) {
            boolean narrowing = frame.warningArmRadius() < warnedArmRadius;
            warnedRadius = frame.warningRadius();
            warnedArmRadius = frame.warningArmRadius();
            announceTitle(
                    I18n.tr(narrowing ? "party.rounds.hole_wall.narrow" : "party.rounds.hole_wall.edge"),
                    I18n.tr(narrowing ? "party.rounds.hole_wall.narrow_warning" : "party.rounds.hole_wall.edge_warning"),
                    2);
            PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, .6f);
        }
        if (reshaping) {
            arena.shrink(frame.radius(), frame.armRadius());
            PartySounds.play(alivePlayers(), Sound.BLOCK_GLASS_BREAK, .6f, .7f);
        }
        if (newWarning || reshaping) arena.warn(frame.warningRadius(), frame.warningArmRadius());
        for (var wall : frame.started()) {
            displays.put(
                    wall, new HoleWallDisplay(world, wall.pattern, wall.direction, wall.depth));
            wallNumber = wall.number;
            if (!newWarning)
                announceTitle(
                        wall.direction.title, I18n.tr("party.rounds.hole_wall.wall", wall.pattern.title(), wallNumber), 1);
            PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .6f, 1.2f);
        }
        var walls = flow.active();
        var players = alivePlayers();
        for (var wall : walls) {
            if (elapsed >= wall.moveAt) displays.get(wall).move(wall.depth);
            else PartySounds.countdown(players, wall.moveAt - elapsed);
        }
        for (Player player : players) {
            if (resolveContacts(player, walls, elapsed)
                    && elapsed >= nextHitSound.getOrDefault(player.getUniqueId(), 0L)) {
                player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, .7f, .7f);
                nextHitSound.put(player.getUniqueId(), elapsed + 10);
            }
            // All walls see the same previous tick, not the position after another wall's push.
            previous.put(player.getUniqueId(), player.getBoundingBox());
        }
    }

    boolean resolveContacts(Player player, List<HoleWallFlow.Wall> walls, long elapsed) {
        BoundingBox before = previous.getOrDefault(player.getUniqueId(), player.getBoundingBox());
        double speed = HoleWallPhysics.speed(elapsed, seconds * 20L);
        boolean hit = false;
        // A perpendicular push may leave an earlier hole. Resolve those new overlaps without
        // replaying the original swept motion on later passes.
        for (int pass = 0; pass <= walls.size(); pass++) {
            boolean moved = false;
            for (var wall : walls) {
                if (elapsed < wall.moveAt) continue;
                BoundingBox body = player.getBoundingBox();
                if (HoleWallPhysics.collides(
                        wall.pattern.cells(),
                        wall.direction,
                        pass == 0 ? wall.from : wall.depth,
                        wall.depth,
                        pass == 0 ? before : body,
                        body)) {
                    Location old = player.getLocation();
                    push(player, wall, before, speed);
                    hit = true;
                    moved |=
                            old.toVector().distanceSquared(player.getLocation().toVector()) > 1e-10;
                }
            }
            if (!moved) break;
        }
        return hit;
    }

    private void push(Player player, HoleWallFlow.Wall wall, BoundingBox before, double speed) {
        Location location = player.getLocation();
        BoundingBox body = player.getBoundingBox();
        Vector velocity = player.getVelocity();
        var direction = wall.direction;
        double projectedCenter =
                direction.dx * before.getCenterX() + direction.dz * before.getCenterZ();
        int side = projectedCenter >= wall.from + (direction.dx + direction.dz) * .5 ? 1 : -1;
        int nx = direction.dx * side, nz = direction.dz * side;
        double x = .5 + direction.dx * wall.depth + nx * (.5 + body.getWidthX() / 2 + .02);
        double z = .5 + direction.dz * wall.depth + nz * (.5 + body.getWidthZ() / 2 + .02);
        if (nx > 0) location.setX(Math.max(location.getX(), x));
        if (nx < 0) location.setX(Math.min(location.getX(), x));
        if (nz > 0) location.setZ(Math.max(location.getZ(), z));
        if (nz < 0) location.setZ(Math.min(location.getZ(), z));
        teleport(player, location);
        if (nx != 0) velocity.setX(nx * (speed + .08));
        else velocity.setZ(nz * (speed + .08));
        player.setVelocity(velocity);
    }

    Component status() {
        return I18n.tr("party.rounds.hole_wall.status", wallNumber, displays.size(),
                Math.max(0, seconds - elapsed() / 20));
    }

    void playerOut(Player player) {
        previous.remove(player.getUniqueId());
        nextHitSound.remove(player.getUniqueId());
    }

    void cleanup() {
        displays.values().forEach(HoleWallDisplay::clear);
        displays.clear();
        if (flow != null) flow.clear();
        previous.clear();
        nextHitSound.clear();
    }
}
