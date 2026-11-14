package me.petr1furious.potatominigames.games.cs;

/** A physical weapon's ammunition follows it through drops; switching cancels reloads. */
final class CsGun {
    final CsWeapon weapon;
    int magazine, reserve, burst;
    double nextShot;
    long reloadAt = -1, lastShot = -1000, heldUntil = -1, deployUntil;
    private long scopeClickAt = -1000;
    private boolean triggerHeld;
    private double accuracyHeat, shotHeat;
    private long accuracyAt = -1;
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
        accuracyHeat = shotHeat = 0;
        accuracyAt = -1;
        releaseTrigger();
    }

    boolean pressTrigger() {
        if (triggerHeld) return false;
        triggerHeld = true;
        return true;
    }

    void releaseTrigger() {
        triggerHeld = false;
    }

    boolean triggerHeld() {
        return triggerHeld;
    }

    void tick(long now) {
        if (accuracyAt >= 0 && now > accuracyAt)
            accuracyHeat =
                    Math.max(
                            0,
                            accuracyHeat
                                    - (double) (now - accuracyAt) / weapon.accuracyRecoveryTicks());
        accuracyAt = now;
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
        // The current bullet uses the penalty left by previous shots. A successful
        // shot adds heat for the next one; blocked clicks and dry fire add nothing.
        shotHeat = accuracyHeat;
        accuracyHeat = Math.min(6, accuracyHeat + 1);
        magazine--;
        burst++;
        lastShot = now;
        nextShot = Math.max(now - 1, nextShot) + weapon.cadence;
        return true;
    }

    double shotSpread() {
        double penalty = shotHeat * weapon.firingSpreadStep();
        if (scoped && weapon.scope())
            penalty *= weapon.category == CsWeapon.Category.SNIPER ? .35 : .5;
        return weapon.shotSpread(scoped, burst) + penalty;
    }

    boolean reload(long now) {
        tick(now);
        if (reloadAt >= 0 || magazine == weapon.magazine || reserve == 0) return false;
        reloadAt = now + (weapon.shellReload() ? 20 : weapon.reload);
        heldUntil = -1;
        scoped = false;
        return true;
    }

    /** Time until the entire reload finishes, including every remaining tube-fed shell. */
    long reloadTicksRemaining(long now) {
        if (reloadAt < 0) return 0;
        long finish = reloadAt;
        if (weapon.shellReload()) {
            int shells = Math.min(weapon.magazine - magazine, reserve);
            finish += Math.max(0, shells - 1) * (long) weapon.shellTicks();
        }
        return Math.max(0, finish - now);
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
        releaseTrigger();
        reloadAt = -1;
        heldUntil = -1;
        scoped = false;
    }

    void deploy(long now) {
        switchAway();
        deployUntil = now + weapon.deployTicks();
    }
}
