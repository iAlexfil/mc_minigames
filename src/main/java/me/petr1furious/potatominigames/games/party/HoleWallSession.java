package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

final class HoleWallSession extends PartySurvivalSession {
    private final int seconds;
    private HoleWallArena arena;
    private HoleWallLayout layout = HoleWallLayout.PLUS;
    private HoleWallFlow flow;
    private HoleWallBlocks blocks;
    private final Map<UUID, BoundingBox> previous = new HashMap<>();
    private final Map<UUID, Long> nextHitSound = new HashMap<>();
    private final Map<UUID, Player> hiddenPlayers = new HashMap<>();
    private boolean visibilityConfigured;
    private int wallNumber, warnedRadius = 8, warnedArmRadius = 2;

    HoleWallSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "hole_in_the_wall", context);
        seconds = config.holeWallSeconds.get();
    }

    void buildArena() {
        layout = HoleWallLayout.choose(random);
        arena = new HoleWallArena(world, layout);
        arena.build();
        flow = new HoleWallFlow(seconds * 20L, random, layout);
        blocks = new HoleWallBlocks(world);
        world.setTime(6000);
        visibilityConfigured = true;
        services.sidebar().setHiddenNonCollidingPlayers(names.values());
    }

    @Override
    void preparePlayer(Player player) {
        // The base session has already cleared equipment and effects for this round.
        hiddenPlayers.put(player.getUniqueId(), player);
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        PotionEffect.INFINITE_DURATION,
                        0,
                        false,
                        false,
                        false));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
    }

    private void restoreAppearance(Player player) {
        if (hiddenPlayers.remove(player.getUniqueId()) == null) return;
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.getInventory().setBoots(null);
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
        return I18n.tr("party.rounds.hole_wall.instructions", layout.description());
    }

    void playTick(long elapsed) {
        var frame = flow.tick(elapsed);
        boolean newWarning =
                frame.warningRadius() < warnedRadius || frame.warningArmRadius() < warnedArmRadius;
        boolean reshaping =
                frame.radius() < arena.radius() || frame.armRadius() < arena.armRadius();
        if (newWarning) {
            boolean narrowing =
                    layout == HoleWallLayout.PLUS && frame.warningArmRadius() < warnedArmRadius;
            warnedRadius = frame.warningRadius();
            warnedArmRadius = frame.warningArmRadius();
            announceTitle(
                    I18n.tr(
                            narrowing
                                    ? "party.rounds.hole_wall.narrow"
                                    : "party.rounds.hole_wall.edge"),
                    I18n.tr(
                            narrowing
                                    ? "party.rounds.hole_wall.narrow_warning"
                                    : "party.rounds.hole_wall.edge_warning"),
                    2);
            PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, .6f);
        }
        if (reshaping) {
            arena.shrink(frame.radius(), frame.armRadius());
            PartySounds.play(alivePlayers(), Sound.BLOCK_GLASS_BREAK, .6f, .7f);
        }
        if (newWarning || reshaping) arena.warn(frame.warningRadius(), frame.warningArmRadius());
        for (var wall : frame.started()) {
            wallNumber = wall.number;
            Component announcement =
                    wall.direction
                            .title
                            .append(Component.text(" • "))
                            .append(
                                    I18n.tr(
                                            "party.rounds.hole_wall.wall",
                                            wall.pattern.title(),
                                            wallNumber));
            for (Player viewer : services.session().audiencePlayers())
                viewer.sendActionBar(announcement);
            PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .6f, 1.2f);
        }
        var walls = flow.active();
        var players = alivePlayers();
        boolean changed = !frame.started().isEmpty() || !frame.finished().isEmpty();
        for (var wall : walls) {
            if (wall.from != wall.depth) {
                changed = true;
                world.playSound(
                        new Location(
                                world,
                                .5 + wall.direction.dx * wall.depth,
                                83,
                                .5 + wall.direction.dz * wall.depth),
                        Sound.BLOCK_PISTON_EXTEND,
                        .35f,
                        .8f);
            } else if (elapsed < wall.moveAt) PartySounds.countdown(players, wall.moveAt - elapsed);
        }
        if (changed) blocks.render(walls);
        for (Player player : players) {
            carry(player, walls);
            if (resolveContacts(player, walls, elapsed)
                    && elapsed >= nextHitSound.getOrDefault(player.getUniqueId(), 0L)) {
                player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, .7f, .9f);
                nextHitSound.put(player.getUniqueId(), elapsed + 10);
            }
            // All walls see the same previous tick, not the position after another wall's push.
            previous.put(player.getUniqueId(), player.getBoundingBox());
        }
    }

    void carry(Player player, List<HoleWallFlow.Wall> walls) {
        if (player.getVelocity().getY() > .1) return; // Jumping off remains under player control.
        var visible = HoleWallBlocks.visibleCells(walls, true);
        for (var wall : walls) {
            if (wall.from == wall.depth
                    || !HoleWallPhysics.supports(
                            visible.get(wall), wall.direction, wall.from, player.getBoundingBox()))
                continue;
            Vector velocity = player.getVelocity();
            teleport(
                    player,
                    player.getLocation()
                            .add(
                                    wall.direction.dx * (wall.depth - wall.from),
                                    0,
                                    wall.direction.dz * (wall.depth - wall.from)));
            player.setVelocity(velocity);
            break;
        }
    }

    boolean resolveContacts(Player player, List<HoleWallFlow.Wall> walls, long elapsed) {
        BoundingBox before = previous.getOrDefault(player.getUniqueId(), player.getBoundingBox());
        var visible = HoleWallBlocks.visibleCells(walls, false);
        boolean hit = false;
        // A perpendicular push may leave an earlier hole. Resolve those new overlaps without
        // replaying the original swept motion on later passes.
        for (int pass = 0; pass <= walls.size(); pass++) {
            boolean moved = false;
            for (var wall : walls) {
                if (elapsed < wall.moveAt) continue;
                BoundingBox body = player.getBoundingBox();
                if (HoleWallPhysics.collides(
                        visible.get(wall),
                        wall.direction,
                        pass == 0 ? wall.from : wall.depth,
                        wall.depth,
                        pass == 0 ? before : body,
                        body)) {
                    Location old = player.getLocation();
                    push(player, wall, before);
                    hit = true;
                    moved |=
                            old.toVector().distanceSquared(player.getLocation().toVector()) > 1e-10;
                }
            }
            if (!moved) break;
        }
        return hit;
    }

    private void push(Player player, HoleWallFlow.Wall wall, BoundingBox before) {
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
        if (nx != 0) velocity.setX(nx * 1.1);
        else velocity.setZ(nz * 1.1);
        player.setVelocity(velocity);
    }

    Component status() {
        return I18n.tr(
                "party.rounds.hole_wall.status",
                wallNumber,
                flow.active().size(),
                Math.max(0, seconds - elapsed() / 20));
    }

    void playerOut(Player player) {
        restoreAppearance(player);
        previous.remove(player.getUniqueId());
        nextHitSound.remove(player.getUniqueId());
    }

    void cleanup() {
        for (Player player : List.copyOf(hiddenPlayers.values())) restoreAppearance(player);
        if (visibilityConfigured) {
            services.sidebar().setHiddenNonCollidingPlayers(List.of());
            visibilityConfigured = false;
        }
        if (blocks != null) blocks.clear();
        if (flow != null) flow.clear();
        previous.clear();
        nextHitSound.clear();
    }
}
