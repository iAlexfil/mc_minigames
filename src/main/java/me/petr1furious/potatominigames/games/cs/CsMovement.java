package me.petr1furious.potatominigames.games.cs;

/** Horizontal travel per server tick, including multiple movement packets within one tick. */
final class CsMovement {
    private long tick = Long.MIN_VALUE;
    private double distance, previousDistance;

    void move(long now, double dx, double dz) {
        if (now != tick) {
            previousDistance = now == tick + 1 ? distance : 0;
            distance = 0;
            tick = now;
        }
        distance += Math.hypot(dx, dz);
    }

    double speed(long now) {
        // The current tick may contain only part of the client's movement so far.
        if (now == tick) return Math.max(distance, previousDistance);
        if (now == tick + 1) return distance;
        return 0;
    }
}
