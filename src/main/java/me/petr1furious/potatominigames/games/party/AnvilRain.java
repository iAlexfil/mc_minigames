package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.function.Consumer;

final class AnvilRain {
    private static final class Drop {
        BlockDisplay display;
        double x, z, y = 103, speed;
        int age;
    }

    private final World world;
    private final AnvilArena arena;
    private final Random random;
    private final Consumer<Player> hit;
    private final List<Drop> drops = new ArrayList<>();
    private final List<Integer> cells = new ArrayList<>();
    private final int[] sparsePhases = new int[AnvilArena.WIDTH * AnvilArena.WIDTH];
    private int cellIndex, sweep;
    private long nextImpactSound;

    static int spawnInterval(int wave) {
        return 1;
    }

    static int batchSize(int wave) {
        return 2 * (24 + 2 * Math.clamp(wave, 1, 6));
    }

    AnvilRain(World world, AnvilArena arena, Random random, Consumer<Player> hit) {
        this.world = world;
        this.arena = arena;
        this.random = random;
        this.hit = hit;
    }

    // The anvil has a half-block-wide footprint. Return its bottom height at first contact.
    static double impact(double x, double z, double from, double to, BoundingBox box) {
        if (x + .25 <= box.getMinX()
                || x - .25 >= box.getMaxX()
                || z + .25 <= box.getMinZ()
                || z - .25 >= box.getMaxZ()
                || to > box.getMaxY()
                || from + .75 < box.getMinY()) return Double.NaN;
        return Math.min(from, box.getMaxY());
    }

    void tick(long tick, int wave, List<Player> targets) {
        var bodies = new LinkedHashMap<Player, BoundingBox>();
        for (Player p : targets)
            if (p.getGameMode() == GameMode.SURVIVAL && p.getWorld().equals(world))
                bodies.put(p, p.getBoundingBox());
        int interval = spawnInterval(wave);
        if (tick % interval == 0)
            for (int i = 0; i < batchSize(wave) && drops.size() < 2560; i++) {
                int width = AnvilArena.WIDTH;
                // Dense cells get every sweep; distant cells rotate through one of four groups.
                if (cellIndex == cells.size()) {
                    boolean first = cells.isEmpty();
                    if (first) for (int cell = 0; cell < width * width; cell++) cells.add(cell);
                    else sweep = (sweep + 1) % 4;
                    Collections.shuffle(cells, random);
                    if (first)
                        for (int index = 0; index < cells.size(); index++)
                            sparsePhases[cells.get(index)] = index % 4;
                    cellIndex = 0;
                }
                int cell = cells.get(cellIndex++);
                double x = cell % width + AnvilArena.MIN + .5;
                double z = cell / width + AnvilArena.MIN + .5;
                if (sparsePhases[cell] != sweep && !nearPlayer(x, z, bodies.values())) continue;
                Drop d = new Drop();
                d.x = x;
                d.z = z;
                d.display =
                        world.spawn(
                                new Location(world, d.x - .5, d.y, d.z - .5),
                                BlockDisplay.class,
                                e -> {
                                    e.setBlock(Material.ANVIL.createBlockData());
                                    e.setTeleportDuration(1);
                                    e.setPersistent(false);
                                });
                drops.add(d);
            }
        List<BoundingBox> solid = arena.collisionBoxes();
        if (tick % 20 == 0) PartySounds.play(targets, Sound.ENTITY_WIND_CHARGE_THROW, .25f, .6f);
        for (var it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.speed = Math.min(1.8, d.speed + .045);
            double next = d.y - d.speed;
            double solidY = Double.NEGATIVE_INFINITY;
            for (var box : solid) {
                double y = impact(d.x, d.z, d.y, next, box);
                if (!Double.isNaN(y)) solidY = Math.max(solidY, y);
            }
            Player victim = null;
            double playerY = Double.NEGATIVE_INFINITY;
            for (var entry : bodies.entrySet()) {
                Player p = entry.getKey();
                if (p.getGameMode() != GameMode.SURVIVAL) continue;
                double y = impact(d.x, d.z, d.y, next, entry.getValue());
                if (!Double.isNaN(y) && y > playerY) {
                    playerY = y;
                    victim = p;
                }
            }
            if (victim != null && playerY > solidY) {
                hit.accept(victim);
                d.display.remove();
                it.remove();
            } else if (solidY > Double.NEGATIVE_INFINITY || ++d.age > 160 || next < 79) {
                if (solidY > Double.NEGATIVE_INFINITY && tick >= nextImpactSound) {
                    world.playSound(
                            new Location(world, d.x, solidY, d.z),
                            Sound.BLOCK_ANVIL_LAND,
                            .5f,
                            .8f + random.nextFloat() * .4f);
                    nextImpactSound = tick + 6;
                }
                d.display.remove();
                it.remove();
            } else {
                d.y = next;
                d.display.teleport(new Location(world, d.x - .5, d.y, d.z - .5));
            }
        }
    }

    int activeCount() {
        return drops.size();
    }

    private static boolean nearPlayer(double x, double z, Collection<BoundingBox> bodies) {
        for (BoundingBox body : bodies) {
            double dx = x - body.getCenterX(), dz = z - body.getCenterZ();
            if (dx * dx + dz * dz <= 64) return true;
        }
        return false;
    }

    void clear() {
        drops.forEach(d -> d.display.remove());
        drops.clear();
        cells.clear();
        cellIndex = 0;
        sweep = 0;
    }
}
