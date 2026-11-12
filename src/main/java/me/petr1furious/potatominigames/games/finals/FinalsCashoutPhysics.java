package me.petr1furious.potatominigames.games.finals;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Two session-owned cashout bodies. Terrain collision never places or drops a block. */
final class FinalsCashoutPhysics {
    private static final double HALF = .49;
    private static final double MAX_SPEED = 1.5;
    private static final double EPSILON = 1e-7;
    private final FinalsMapArena arena;
    private final Body[] bodies = new Body[2];

    private static final class Body {
        final Location origin;
        final BlockDisplay display;
        final Interaction interaction;
        Location position;
        double velocity;
        boolean recoveryAnchor;

        Body(Location origin, BlockDisplay display, Interaction interaction) {
            this.origin = origin.clone();
            this.position = origin.clone();
            this.display = display;
            this.interaction = interaction;
        }

        void remove() {
            display.remove();
            interaction.remove();
        }
    }

    record Fall(double y, double velocity) {}

    FinalsCashoutPhysics(FinalsMapArena arena) {
        this.arena = arena;
        try {
            replace(0);
            replace(1);
        } catch (RuntimeException error) {
            clear();
            throw error;
        }
    }

    void replace(int slot) {
        if (bodies[slot] != null) {
            bodies[slot].remove();
            bodies[slot] = null;
        }
        Location origin = arena.station(slot).clone();
        if (overlapsOther(origin, slot)) {
            for (Location candidate : arena.cashoutLocations()) {
                if (!overlapsOther(candidate, slot)) {
                    origin = candidate.clone();
                    break;
                }
            }
        }
        BlockDisplay display =
                arena.world()
                        .spawn(
                                displayPosition(origin),
                                BlockDisplay.class,
                                entity -> {
                                    entity.setPersistent(false);
                                    entity.setGravity(false);
                                    entity.setInvulnerable(true);
                                    entity.setBlock(Material.GOLD_BLOCK.createBlockData());
                                    entity.setBrightness(new Display.Brightness(15, 15));
                                    entity.setViewRange(4f);
                                    entity.setTeleportDuration(1);
                                });
        try {
            Interaction interaction =
                    arena.world()
                            .spawn(
                                    origin,
                                    Interaction.class,
                                    entity -> {
                                        entity.setPersistent(false);
                                        entity.setGravity(false);
                                        entity.setInvulnerable(true);
                                        entity.setInteractionWidth(.98f);
                                        entity.setInteractionHeight(1f);
                                        entity.setResponsive(true);
                                    });
            bodies[slot] = new Body(origin, display, interaction);
            arena.moveStation(slot, origin);
        } catch (RuntimeException error) {
            display.remove();
            throw error;
        }
    }

    void tick() {
        for (int slot = 0; slot < bodies.length; slot++) {
            Body body = bodies[slot];
            if (body == null) continue;
            if (!body.display.isValid() || !body.interaction.isValid()) {
                // Preserve the live position if an entity was removed or its chunk unloaded.
                replace(slot);
                body = bodies[slot];
            }
            if (body.recoveryAnchor) continue;
            Location point = body.position;
            double velocity = Math.max(-MAX_SPEED, body.velocity - .08);
            Fall next =
                    fall(
                            point.getX(),
                            point.getY(),
                            point.getZ(),
                            body.velocity,
                            collisionBoxes(point, velocity));
            body.velocity = next.velocity();
            Location destination = point.clone();
            destination.setY(next.y());
            if (!arena.contains(destination)) {
                destination = recover(body, slot);
                body.velocity = 0;
            }
            if (!point.equals(destination)) {
                body.position = destination;
                body.display.teleport(displayPosition(destination));
                body.interaction.teleport(destination);
                arena.moveStation(slot, destination);
            }
        }
    }

    /** Sweep the complete footprint to the first terrain surface, including slabs and stairs. */
    static Fall fall(
            double x, double y, double z, double velocity, Collection<BoundingBox> terrain) {
        double speed = Math.max(-MAX_SPEED, Math.min(0, velocity) - .08);
        double destination = y + speed;
        boolean landed = false;
        for (BoundingBox box : terrain) {
            if (box.getMaxX() <= x - HALF
                    || box.getMinX() >= x + HALF
                    || box.getMaxZ() <= z - HALF
                    || box.getMinZ() >= z + HALF) continue;
            double top = box.getMaxY();
            if (top <= y + EPSILON && top >= destination - EPSILON) {
                destination = Math.max(destination, Math.min(y, top));
                landed = true;
            }
        }
        return new Fall(destination, landed ? 0 : speed);
    }

    private static List<BoundingBox> collisionBoxes(Location point, double velocity) {
        World world = point.getWorld();
        List<BoundingBox> result = new ArrayList<>();
        int low = Math.max(world.getMinHeight(), (int) Math.floor(point.getY() + velocity) - 1);
        int high = Math.min(world.getMaxHeight() - 1, (int) Math.floor(point.getY()));
        for (int x = (int) Math.floor(point.getX() - HALF);
                x <= Math.floor(point.getX() + HALF);
                x++)
            for (int z = (int) Math.floor(point.getZ() - HALF);
                    z <= Math.floor(point.getZ() + HALF);
                    z++)
                for (int y = low; y <= high; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    // Bukkit voxel boxes use local block coordinates.
                    for (BoundingBox box : block.getCollisionShape().getBoundingBoxes())
                        result.add(box.clone().shift(x, y, z));
                }
        return result;
    }

    private Location recover(Body body, int slot) {
        List<Location> candidates = new ArrayList<>();
        candidates.add(body.origin);
        candidates.addAll(arena.cashoutLocations());
        candidates.add(arena.spawn(slot, 0));
        for (Location candidate : candidates) {
            if (!arena.contains(candidate) || overlapsOther(candidate, slot)) continue;
            Fall supported =
                    fall(
                            candidate.getX(),
                            candidate.getY(),
                            candidate.getZ(),
                            0,
                            collisionBoxes(candidate, -.08));
            if (supported.velocity() == 0) return candidate.clone();
        }
        // A map may have no surviving terrain below any candidate. Keep the original
        // clickable objective reachable rather than repeatedly losing it to the void.
        body.recoveryAnchor = true;
        return body.origin.clone();
    }

    private boolean overlapsOther(Location point, int slot) {
        Body other = bodies[1 - slot];
        return other != null
                && Math.abs(point.getX() - other.position.getX()) < 1
                && Math.abs(point.getZ() - other.position.getZ()) < 1
                && Math.abs(point.getY() - other.position.getY()) < 1;
    }

    int station(Entity entity) {
        for (int slot = 0; slot < bodies.length; slot++) {
            Body body = bodies[slot];
            if (body != null
                    && (body.interaction.getUniqueId().equals(entity.getUniqueId())
                            || body.display.getUniqueId().equals(entity.getUniqueId())))
                return slot;
        }
        return -1;
    }

    int aimedStation(Player player, double reach) {
        Location eye = player.getEyeLocation();
        if (!arena.world().equals(eye.getWorld())) return -1;
        Vector direction = eye.getDirection();
        RayTraceResult wall =
                arena.world().rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        double limit = wall == null ? reach : wall.getHitPosition().distance(eye.toVector());
        int found = -1;
        for (int slot = 0; slot < bodies.length; slot++) {
            Body body = bodies[slot];
            if (body == null) continue;
            Location point = body.position;
            RayTraceResult hit =
                    new BoundingBox(
                                    point.getX() - HALF,
                                    point.getY(),
                                    point.getZ() - HALF,
                                    point.getX() + HALF,
                                    point.getY() + 1,
                                    point.getZ() + HALF)
                            .rayTrace(eye.toVector(), direction, limit + EPSILON);
            if (hit != null) {
                double distance = hit.getHitPosition().distance(eye.toVector());
                if (distance <= limit + EPSILON) {
                    limit = distance;
                    found = slot;
                }
            }
        }
        return found;
    }

    private static Location displayPosition(Location point) {
        return point.clone().subtract(.5, 0, .5);
    }

    void clear() {
        for (int slot = 0; slot < bodies.length; slot++) {
            if (bodies[slot] != null) bodies[slot].remove();
            bodies[slot] = null;
        }
    }
}
