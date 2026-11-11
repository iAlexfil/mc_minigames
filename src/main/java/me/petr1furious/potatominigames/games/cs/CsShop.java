package me.petr1furious.potatominigames.games.cs;

import java.util.Arrays;
import java.util.List;

/** Weapon rows and the bottom equipment row never share inventory slots. */
final class CsShop {
    static final int SIZE = 45, GEAR_START = 36;

    static List<CsWeapon> weapons(int side) {
        if (side < 0 || side > 1) throw new IllegalArgumentException("Invalid faction");
        List<CsWeapon> choices =
                Arrays.stream(CsWeapon.values()).filter(w -> w.allowed(side)).toList();
        if (choices.size() > GEAR_START)
            throw new IllegalStateException("Weapon choices exceed shop capacity");
        return choices;
    }

    private CsShop() {}
}
