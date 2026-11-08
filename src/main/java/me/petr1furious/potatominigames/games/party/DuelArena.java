package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Geometry and round reset shared by Party duel modes. Maps are protected by default. */
abstract class DuelArena {
    abstract void build();

    abstract void reset();

    abstract Location center();

    abstract Location spawn(boolean east);

    Location spawn(int index, int count) {
        return circleSpawn(center(), this instanceof SumoArena ? 6 : 20, index, count);
    }

    abstract Location spectator();

    abstract boolean contains(Location location);

    boolean canPlace(Block block) {
        return false;
    }

    boolean isPlaced(Block block) {
        return false;
    }

    void track(Block block) {
        throw new IllegalStateException("This arena does not allow building");
    }

    void broken(Block block) {}

    void tick(long elapsed, int roundSeconds) {}

    boolean isOut(Location location) {
        return false;
    }
    static Location circleSpawn(Location center, double radius, int index, int count) {
        if (count < 1 || count > 16 || index < 0 || index >= count)
            throw new IllegalArgumentException("Invalid group spawn");
        double angle = Math.PI * 2 * index / count;
        Location at = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        at.setDirection(center.toVector().subtract(at.toVector()));
        return at;
    }

}
