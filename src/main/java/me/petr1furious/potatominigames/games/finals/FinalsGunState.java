package me.petr1furious.potatominigames.games.finals;

/** One life's magazine and deadlines, independent of Bukkit and wall-clock time. */
final class FinalsGunState {
    private final int capacity;
    private final int cadence;
    private final int reloadDuration;
    private int ammo;
    private long nextShot;
    private long reloadAt = -1;
    private long heldUntil = -1;
    private boolean closed;

    FinalsGunState(int capacity, int cadence, int reloadDuration) {
        if (capacity < 1 || cadence < 1 || reloadDuration < 1)
            throw new IllegalArgumentException("Invalid gun timings");
        this.capacity = capacity;
        this.cadence = cadence;
        this.reloadDuration = reloadDuration;
        ammo = capacity;
    }

    int ammo() {
        return ammo;
    }

    boolean reloading() {
        return reloadAt >= 0;
    }

    long reloadRemaining(long now) {
        return Math.max(0, reloadAt - now);
    }

    void advance(long now) {
        if (!closed && reloading() && now >= reloadAt) {
            ammo = capacity;
            reloadAt = -1;
        }
    }

    boolean fire(long now) {
        advance(now);
        if (closed || reloading() || ammo == 0 || now < nextShot) return false;
        ammo--;
        nextShot = now + cadence;
        return true;
    }

    boolean reload(long now) {
        advance(now);
        if (closed || reloading() || ammo == capacity) return false;
        reloadAt = now + reloadDuration;
        stopHolding();
        return true;
    }

    void cancelReload() {
        reloadAt = -1;
        stopHolding();
    }

    void hold(long now, int ticks) {
        if (!closed) heldUntil = now + Math.max(1, ticks);
    }

    boolean held(long now) {
        return !closed && now < heldUntil;
    }

    void stopHolding() {
        heldUntil = -1;
    }

    void close() {
        closed = true;
        cancelReload();
    }
}
