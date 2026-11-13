package me.petr1furious.potatominigames.games.cs;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weapon rows and the bottom equipment row never share inventory slots. */
final class CsShop {
    static final int SIZE = 54, WEAPON_ROWS = 4, GEAR_START = 45;

    record Group(int column, int width) {
        int slot(int index) {
            if (index < 0 || index >= width * WEAPON_ROWS)
                throw new IllegalArgumentException("Weapon exceeds category capacity");
            return 9 * (1 + index / width) + column + index % width;
        }
    }

    static Group group(CsWeapon.Category category) {
        return switch (category) {
            case PISTOL -> new Group(0, 2);
            case SMG -> new Group(2, 1);
            case RIFLE -> new Group(3, 2);
            case SNIPER -> new Group(5, 1);
            case SHOTGUN -> new Group(6, 2);
            case HEAVY -> new Group(8, 1);
        };
    }

    static List<CsWeapon> weapons(int side) {
        if (side < 0 || side > 1) throw new IllegalArgumentException("Invalid faction");
        return Arrays.stream(CsWeapon.values())
                .filter(w -> w.allowed(side))
                .sorted(
                        Comparator.comparing((CsWeapon w) -> w.category)
                                .thenComparingInt(w -> w.price)
                                .thenComparing(w -> w.id))
                .toList();
    }

    static List<CsWeapon> weapons(int side, CsWeapon.Category category) {
        List<CsWeapon> choices =
                weapons(side).stream().filter(w -> w.category == category).toList();
        if (choices.size() > group(category).width() * WEAPON_ROWS)
            throw new IllegalStateException("Weapon category exceeds shop capacity");
        return choices;
    }

    static Map<Integer, CsWeapon> slots(int side) {
        Map<Integer, CsWeapon> slots = new LinkedHashMap<>();
        for (var category : CsWeapon.Category.values()) {
            var choices = weapons(side, category);
            for (int i = 0; i < choices.size(); i++)
                if (slots.put(group(category).slot(i), choices.get(i)) != null)
                    throw new IllegalStateException("Weapon categories overlap");
        }
        return slots;
    }

    static Map<Integer, CsWeapon> deathmatchSlots() {
        Map<Integer, CsWeapon> slots = new LinkedHashMap<>();
        for (var category : CsWeapon.Category.values()) {
            var weapons =
                    Arrays.stream(CsWeapon.values())
                            .filter(w -> w.category == category)
                            .sorted(
                                    Comparator.comparingInt((CsWeapon w) -> w.price)
                                            .thenComparing(w -> w.id))
                            .toList();
            if (weapons.size() > 8) throw new IllegalStateException("DM weapon row overflow");
            for (int i = 0; i < weapons.size(); i++)
                slots.put(category.ordinal() * 9 + i + 1, weapons.get(i));
        }
        return slots;
    }

    private CsShop() {}
}
