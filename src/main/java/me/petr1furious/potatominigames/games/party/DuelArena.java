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
        double angle = 2 * Math.PI * index / count;
        double radius = this instanceof SumoArena ? 6 : 19;
        Location at = center().clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
        at.setDirection(center().toVector().subtract(at.toVector()));
        return at;
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
}
