package me.petr1furious.potatominigames.games.cs;

/** A physical weapon's ammunition follows it through drops; switching cancels reloads. */
final class CsGun {
    final CsWeapon weapon;
    int magazine, reserve, burst;
    double nextShot;
    long reloadAt = -1, lastShot = -1000, heldUntil = -1, deployUntil;
    private long scopeClickAt = -1000;
    boolean scoped;

    CsGun(CsWeapon weapon) {
        this.weapon = weapon;
        restock();
    }

    void restock() {
        magazine = weapon.magazine;
        reserve = weapon.reserve;
        reloadAt = -1;
        burst = 0;
        heldUntil = -1;
        scoped = false;
        nextShot = 0;
        deployUntil = 0;
        scopeClickAt = -1000;
    }

    void tick(long now) {
        if (reloadAt < 0 || now < reloadAt) return;
        if (weapon.shellReload()) {
            while (reloadAt >= 0 && now >= reloadAt) {
                magazine++;
                reserve--;
                if (magazine == weapon.magazine || reserve == 0) reloadAt = -1;
                else reloadAt += weapon.shellTicks();
            }
        } else {
            int n = Math.min(weapon.magazine - magazine, reserve);
            magazine += n;
            reserve -= n;
            reloadAt = -1;
        }
    }

    boolean fire(long now) {
        tick(now);
        if (weapon.shellReload() && reloadAt >= 0 && magazine > 0) reloadAt = -1;
        if (reloadAt >= 0 || now < deployUntil || now + 1e-6 < nextShot || magazine == 0)
            return false;
        if (now - lastShot > 8) {
            burst = 0;
            nextShot = now;
        }
        magazine--;
        burst++;
        lastShot = now;
        nextShot = Math.max(now - 1, nextShot) + weapon.cadence;
        return true;
    }

    boolean reload(long now) {
        tick(now);
        if (reloadAt >= 0 || magazine == weapon.magazine || reserve == 0) return false;
        reloadAt = now + (weapon.shellReload() ? 20 : weapon.reload);
        heldUntil = -1;
        scoped = false;
        return true;
    }

    boolean toggleScope(long now) {
        tick(now);
        if (!weapon.scope() || reloadAt >= 0 || now < deployUntil || now - scopeClickAt < 4)
            return false;
        scopeClickAt = now;
        scoped = !scoped;
        return true;
    }

    void switchAway() {
        reloadAt = -1;
        heldUntil = -1;
        scoped = false;
    }

    void deploy(long now) {
        switchAway();
        deployUntil = now + weapon.deployTicks();
    }
}
