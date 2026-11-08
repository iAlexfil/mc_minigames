package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Continuous wall stream with independently timed platform changes. */
final class HoleWallFlow {
    static final class Wall {
        final int number;
        final HoleWallPattern pattern;
        final HoleWallPhysics.Direction direction;
        final long moveAt;
        double from = -16, depth = -16;

        Wall(
                int number,
                HoleWallPattern pattern,
                HoleWallPhysics.Direction direction,
                long moveAt) {
            this.number = number;
            this.pattern = pattern;
            this.direction = direction;
            this.moveAt = moveAt;
        }
    }

    record Frame(
            List<Wall> started,
            List<Wall> finished,
            int radius,
            int warningRadius,
            int armRadius,
            int warningArmRadius) {}

    private final long duration;
    private final Random random;
    private final List<Wall> walls = new ArrayList<>();
    private long nextSpawn;
    private int radius = 8, number;
    private HoleWallPhysics.Direction lastDirection;
    private final Deque<HoleWallPhysics.Direction> directions = new ArrayDeque<>();

    HoleWallFlow(long duration, Random random) {
        this.duration = duration;
        this.random = random;
    }

    List<Wall> active() {
        return List.copyOf(walls);
    }

    Frame tick(long tick) {
        List<Wall> started = new ArrayList<>(), finished = new ArrayList<>();
        double speed = HoleWallPhysics.speed(tick, duration);
        for (var it = walls.iterator(); it.hasNext(); ) {
            var wall = it.next();
            wall.from = wall.depth;
            if (tick >= wall.moveAt) wall.depth += speed;
            if (wall.depth > 16) {
                finished.add(wall);
                it.remove();
            }
        }
        int target = tick >= duration * 2 / 3 ? 4 : tick >= duration / 3 ? 6 : 8;
        int warning = tick >= duration * 2 / 3 - 60 ? 4 : tick >= duration / 3 - 60 ? 6 : 8;
        int armRadius = tick >= duration / 2 ? 1 : 2,
                warningArmRadius = tick >= duration / 2 - 60 ? 1 : 2;
        radius = target;
        if (tick < duration && tick >= nextSpawn && walls.size() < 4) {
            if (directions.isEmpty()) {
                var shuffled = new ArrayList<>(Arrays.asList(HoleWallPhysics.Direction.values()));
                Collections.shuffle(shuffled, random);
                if (shuffled.getFirst() == lastDirection) Collections.swap(shuffled, 0, 1);
                if (shuffled.get(0).dx * shuffled.get(1).dx
                                + shuffled.get(0).dz * shuffled.get(1).dz
                        == -1) Collections.swap(shuffled, 1, 2);
                directions.addAll(shuffled);
            }
            var direction = directions.getFirst();
            if (canApproach(direction, tick, speed)) {
                directions.removeFirst();
                lastDirection = direction;
                // A wall that might survive past halfway must already fit the narrower arms.
                long latestExit = tick + 40 + (long) Math.ceil(32 / speed) + 1;
                int gateRadius = latestExit >= duration / 2 ? 1 : 2;
                var wall =
                        new Wall(
                                ++number,
                                HoleWallPattern.generate(number, gateRadius, random),
                                direction,
                                tick + 40);
                walls.add(wall);
                started.add(wall);
                nextSpawn = tick + Math.round(80 - 40 * Math.clamp((double) tick / duration, 0, 1));
            }
        }
        return new Frame(
                List.copyOf(started),
                List.copyOf(finished),
                radius,
                warning,
                armRadius,
                warningArmRadius);
    }

    private boolean canApproach(HoleWallPhysics.Direction direction, long tick, double speed) {
        // Earliest arrival uses maximum speed; old-wall clearance uses its current minimum
        // speed. Two blocks of margin cover wall thickness and player footprint at the rim.
        // The first movement step happens on moveAt itself, so subtract that inclusive tick.
        double earliestEntry = 39 + Math.floor((16 - radius - 2) / .32);
        for (var wall : walls)
            if (wall.direction.dx * direction.dx + wall.direction.dz * direction.dz == -1) {
                double movement = Math.max(0, earliestEntry - Math.max(0, wall.moveAt - tick));
                if (wall.depth + movement * speed <= radius + 2) return false;
            }
        return true;
    }

    void clear() {
        walls.clear();
        directions.clear();
    }
}
