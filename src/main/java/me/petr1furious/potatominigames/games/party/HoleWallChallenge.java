package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Physical movement requirements, including gaps outside the advertised gates. */
final class HoleWallChallenge {
    static boolean required(HoleWallDifficulty difficulty, int kind, boolean plus) {
        return difficulty == HoleWallDifficulty.HARD
                || difficulty == HoleWallDifficulty.NORMAL
                        && !(plus ? Set.of(8, 22, 42) : Set.of(5, 22, 42)).contains(kind);
    }

    static List<HoleWallPattern.Gate> gates(
            List<HoleWallPattern.Gate> gates, int kind, boolean required) {
        if (!required) return gates;
        return gates.stream()
                .map(g -> {
                    if (g.bottom() != 0 || g.height() < 1.8) return g;
                    return g.width() < 1 || kind % 2 == 0
                            ? new HoleWallPattern.Gate(g.start(), g.width(), 0, 1.5)
                            : new HoleWallPattern.Gate(
                                    g.start(), g.width(), 1, Math.min(3, g.height()));
                })
                .toList();
    }

    static List<HoleWallPattern.Cell> frame(
            List<HoleWallPattern.Cell> cells,
            List<HoleWallPattern.Gate> gates,
            HoleWallBlock full,
            boolean required) {
        if (!required) return cells;
        Map<String, HoleWallPattern.Cell> result = new LinkedHashMap<>();
        for (var c : cells) result.put(c.u() + ":" + c.y(), c);
        for (int u = -HoleWallPattern.HALF_WIDTH; u <= HoleWallPattern.HALF_WIDTH; u++) {
            boolean low = false, openFloor = false;
            for (var g : gates)
                if (g.bottom() == 0
                        && g.height() == 1.5
                        && u < g.start() + g.width()
                        && u + 1 > g.start()) {
                    low = true;
                    openFloor |= u >= g.start() && u + 1 <= g.start() + g.width();
                }
            if (!low) result.put(u + ":0", new HoleWallPattern.Cell(u, 0, full));
            else {
                if (openFloor) result.remove(u + ":0");
                // Fractional fence/bars gaps keep their posts, but receive a real low lintel.
                result.put(u + ":1", new HoleWallPattern.Cell(u, 1, full.upperSlab()));
            }
        }
        return HoleWallPattern.frameLowPassages(List.copyOf(result.values()), gates, full);
    }
}
