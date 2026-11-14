package me.petr1furious.potatominigames.games.cs;

import java.util.*;

/** A-side clipping for the imported Dust II; no alternative rules or team-size limit. */
final class CsWingman {
    private CsWingman() {}

    static boolean contains(double x, double y, double z) {
        // Close the B route immediately north of all fifteen T spawn candidates.
        if (z < 19) return false;
        // Lower/dark tunnels, including their entrance from the T side of Mid.
        if (x >= 52 && x < 80 && z < 38) return false;
        if (x >= 80 && z < 58) {
            // Mid doors and B are closed at every height. Short is south of this line.
            if (z < 47) return false;
            // Short's approach is at 78, its upper deck at 82; CT-Mid below is at 75.
            // Half-block tolerance preserves the imported slabs and stairs.
            return y >= (x < 88 ? 77.5 : 81.5);
        }
        return true;
    }

    static Set<CsArena.Pos> borders() {
        Set<CsArena.Pos> result = new LinkedHashSet<>();
        // Membership can change only at these planes. Inspect their two sides rather
        // than allocating or filling the entire million-block forbidden volume.
        for (int x : new int[] {51, 52, 79, 80, 87, 88})
            for (int y = 73; y < 128; y++)
                for (int z = -34; z < 120; z++) addBorder(result, x, y, z);
        for (int z : new int[] {18, 19, 37, 38, 46, 47, 57, 58})
            for (int x = -25; x < 131; x++)
                for (int y = 73; y < 128; y++) addBorder(result, x, y, z);
        for (int y : new int[] {77, 78, 81, 82})
            for (int x = -25; x < 131; x++)
                for (int z = -34; z < 120; z++) addBorder(result, x, y, z);
        return Collections.unmodifiableSet(result);
    }

    private static void addBorder(Set<CsArena.Pos> result, int x, int y, int z) {
        if (contains(x + .5, y, z + .5)) return;
        CsArena.Pos p = new CsArena.Pos(x, y, z);
        if (contains(x - .5, y, z + .5)
                || contains(x + 1.5, y, z + .5)
                || contains(x + .5, y - 1, z + .5)
                || contains(x + .5, y + 1, z + .5)
                || contains(x + .5, y, z - .5)
                || contains(x + .5, y, z + 1.5)) result.add(p);
    }
}
