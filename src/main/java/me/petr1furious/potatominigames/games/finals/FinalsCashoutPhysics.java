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
    private static final double HALF = .5;
    private static final double HEIGHT = 1;
    private static final double MAX_RISE = 4;
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

        Body(Location origin, Location position, BlockDisplay display, Interaction interaction) {
            this.origin = origin.clone();
            this.position = position.clone();
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
        replace(slot, null);
    }

    private void replace(int slot, Location recoveryOrigin) {
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
        Location configuredOrigin = recoveryOrigin == null ? origin.clone() : recoveryOrigin.clone();
        Fall initial = collisionStep(origin, 0);
        if (initial != null && initial.y() > origin.getY()) origin.setY(initial.y());
        if (initial == null || !fits(origin) || overlapsOther(origin, slot)) {
            origin = recoveryLocation(configuredOrigin, slot);
            if (origin == null)
                throw new IllegalStateException("No collision-free cashout spawn is available");
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
                                        entity.setInteractionWidth(1f);
                                        entity.setInteractionHeight(1f);
                                        entity.setResponsive(true);
                                    });
            bodies[slot] = new Body(configuredOrigin, origin, display, interaction);
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
                replace(slot, body.origin);
                body = bodies[slot];
            }
            Location point = body.position;
            Fall next = collisionStep(point, body.velocity);
            body.velocity = next == null ? 0 : next.velocity();
            Location destination = point.clone();
            if (next != null) destination.setY(next.y());
            if (next == null || !fits(destination)) {
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
        double clearY = y;
        // A saved anchor can be inside a slab or fence, and terrain can be placed
        // into a body after spawning. Resolve full-body penetration before falling.
        for (int pass = 0; pass < terrain.size(); pass++) {
            double raised = clearY;
            for (BoundingBox box : terrain) {
                if (horizontalOverlap(x, z, box)
                        && box.getMaxY() > clearY + EPSILON
                        && box.getMinY() < clearY + HEIGHT - EPSILON)
                    raised = Math.max(raised, box.getMaxY());
            }
            if (raised == clearY) break;
            clearY = raised;
        }
        if (clearY > y + EPSILON) return new Fall(clearY, 0);
        double destination = y + speed;
        boolean landed = false;
        for (BoundingBox box : terrain) {
            if (!horizontalOverlap(x, z, box)) continue;
            double top = box.getMaxY();
            if (top <= y + EPSILON && top >= destination - EPSILON) {
                destination = Math.max(destination, Math.min(y, top));
                landed = true;
            }
        }
        return new Fall(destination, landed ? 0 : speed);
    }

    private static boolean horizontalOverlap(double x, double z, BoundingBox box) {
        return box.getMaxX() > x - HALF + EPSILON
                && box.getMinX() < x + HALF - EPSILON
                && box.getMaxZ() > z - HALF + EPSILON
                && box.getMinZ() < z + HALF - EPSILON;
    }

    private static Fall collisionStep(Location point, double velocity) {
        double speed = Math.max(-MAX_SPEED, Math.min(0, velocity) - .08);
        List<BoundingBox> terrain = collisionBoxes(point, speed);
        if (terrain == null) return null;
        Fall next = fall(point.getX(), point.getY(), point.getZ(), velocity, terrain);
        if (next.y() > point.getY() + MAX_RISE) return null;
        Location destination = point.clone();
        destination.setY(next.y());
        List<BoundingBox> clearance = collisionBoxes(destination, 0);
        if (clearance == null) return null;
        for (BoundingBox box : clearance) {
            if (horizontalOverlap(point.getX(), point.getZ(), box)
                    && box.getMaxY() > next.y() + EPSILON
                    && box.getMinY() < next.y() + HEIGHT - EPSILON) return null;
        }
        return next;
    }

    private static List<BoundingBox> collisionBoxes(Location point, double velocity) {
        World world = point.getWorld();
        List<BoundingBox> result = new ArrayList<>();
        int low = Math.max(world.getMinHeight(), (int) Math.floor(point.getY() + velocity) - 1);
        int high = Math.min(world.getMaxHeight() - 1,
                (int) Math.floor(point.getY() + HEIGHT + MAX_RISE));
        for (int x = (int) Math.floor(point.getX() - HALF);
                x <= Math.floor(point.getX() + HALF);
                x++)
            for (int z = (int) Math.floor(point.getZ() - HALF);
                    z <= Math.floor(point.getZ() + HALF);
                    z++) {
                int chunkX = x >> 4, chunkZ = z >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)
                        && !world.getChunkAt(chunkX, chunkZ).load()) return null;
                for (int y = low; y <= high; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    // Bukkit voxel boxes use local block coordinates.
                    for (BoundingBox box : block.getCollisionShape().getBoundingBoxes())
                        result.add(box.clone().shift(x, y, z));
                }
            }
        return result;
    }

    private Location recover(Body body, int slot) {
        Location recovered = recoveryLocation(body.origin, slot);
        if (recovered != null) return recovered;
        // Never teleport to an unchecked, unsupported or obstructed anchor. If all
        // protected supports are gone, retain the last published position and retry.
        return body.position.clone();
    }

    private Location recoveryLocation(Location configuredOrigin, int slot) {
        List<Location> candidates = new ArrayList<>();
        candidates.add(configuredOrigin);
        candidates.addAll(arena.cashoutLocations());
        candidates.add(arena.spawn(slot, 0));
        for (Location candidate : candidates) {
            if (!arena.contains(candidate)) continue;
            Fall supported = collisionStep(candidate, 0);
            if (supported == null || supported.velocity() != 0) continue;
            Location corrected = candidate.clone();
            corrected.setY(supported.y());
            if (fits(corrected) && !overlapsOther(corrected, slot)) return corrected;
        }
        return null;
    }

    private boolean fits(Location point) {
        return arena.contains(point)
                && arena.contains(point.clone().add(-HALF + EPSILON, 0, -HALF + EPSILON))
                && arena.contains(point.clone().add(HALF - EPSILON, HEIGHT - EPSILON, HALF - EPSILON));
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
