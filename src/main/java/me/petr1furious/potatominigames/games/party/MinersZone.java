package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A visible, non-solid storm boundary; vanilla borders prevent mining outside them on the client.
 */
final class MinersZone {
    static final double MIN_SIZE = 4;
    private static final float THICKNESS = .025f;

    record Bounds(double centerX, double centerZ, double size) {
        boolean outside(double x, double z) {
            double half = size / 2;
            return x < centerX - half
                    || x > centerX + half
                    || z < centerZ - half
                    || z > centerZ + half;
        }
    }

    private final Location center;
    private final double initialSize;
    private final long started, duration;
    private final List<BlockDisplay> walls = new ArrayList<>();
    private float renderedSize = Float.NaN;

    MinersZone(Location center, double initialSize, long started, long duration) {
        this.center = center.clone();
        this.center.setY(MinersArena.BASE);
        this.initialSize = initialSize;
        this.started = started;
        this.duration = duration;
    }

    static Bounds bounds(double x, double z, double initialSize, long elapsed, long duration) {
        double progress = Math.clamp(elapsed / (double) Math.max(1, duration), 0, 1);
        double size = Math.max(MIN_SIZE, initialSize);
        return new Bounds(x, z, size + (MIN_SIZE - size) * progress);
    }

    private Bounds bounds(long tick) {
        return bounds(center.getX(), center.getZ(), initialSize, tick - started, duration);
    }

    boolean outside(Location at, long tick) {
        return center.getWorld().equals(at.getWorld())
                && bounds(tick).outside(at.getX(), at.getZ());
    }

    /** Four display entities only: they have no collision and do not intercept block mining. */
    void update(long tick) {
        float size = (float) bounds(tick).size(), half = size / 2;
        if (size == renderedSize) return;
        for (int side = 0; side < 4; side++) {
            boolean alongX = side < 2;
            float edge = side % 2 == 0 ? -half : half;
            Transformation transform =
                    new Transformation(
                            new Vector3f(
                                    alongX ? -half : edge - THICKNESS / 2,
                                    0,
                                    alongX ? edge - THICKNESS / 2 : -half),
                            new Quaternionf(),
                            new Vector3f(
                                    alongX ? size : THICKNESS,
                                    MinersArena.SIDE,
                                    alongX ? THICKNESS : size),
                            new Quaternionf());
            if (walls.size() <= side) {
                walls.add(
                        center.getWorld()
                                .spawn(
                                        center,
                                        BlockDisplay.class,
                                        wall -> {
                                            wall.setPersistent(false);
                                            wall.setInvulnerable(true);
                                            wall.setGravity(false);
                                            wall.setBlock(
                                                    Material.LIGHT_BLUE_STAINED_GLASS
                                                            .createBlockData());
                                            wall.setBrightness(new Display.Brightness(15, 15));
                                            wall.setViewRange(2);
                                            wall.setDisplayWidth((float) initialSize + 2);
                                            wall.setDisplayHeight(MinersArena.SIDE);
                                            wall.setTransformation(transform);
                                        }));
            } else {
                // Use the same sampled boundary for rendering and damage, without interpolation
                // lag.
                walls.get(side).setTransformation(transform);
            }
        }
        renderedSize = size;
    }

    void clear() {
        for (BlockDisplay wall : walls) wall.remove();
        walls.clear();
        renderedSize = Float.NaN;
    }
}
