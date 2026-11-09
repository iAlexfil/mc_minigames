package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;

/** Bounded integer geometry, shared by perk mining and server-free checks. */
final class MinersMining {
    static List<MinersArena.Pos> drill(MinersArena.Pos center, Vector look) {
        List<MinersArena.Pos> result = new ArrayList<>();
        result.add(center);
        double x = Math.abs(look.getX()), y = Math.abs(look.getY()), z = Math.abs(look.getZ());
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            result.add(x >= y && x >= z ? offset(center, 0, a, b)
                    : y >= z ? offset(center, a, 0, b) : offset(center, a, b, 0));
        }
        return List.copyOf(result);
    }
    static List<MinersArena.Pos> tunnel(MinersArena.Pos start, Vector direction) {
        double[] d = {direction.getX(), direction.getY(), direction.getZ()};
        if (!Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 1e-8) return List.of();
        int[] p = {start.x(), start.y(), start.z()};
        int[] step = new int[3]; double[] next = new double[3], delta = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            step[axis] = d[axis] > 0 ? 1 : d[axis] < 0 ? -1 : 0;
            delta[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(d[axis]);
            next[axis] = delta[axis] / 2;
        }
        List<MinersArena.Pos> result = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            result.add(new MinersArena.Pos(p[0], p[1], p[2]));
            double nearest = Math.min(next[0], Math.min(next[1], next[2]));
            for (int axis = 0; axis < 3; axis++) if (next[axis] <= nearest + 1e-9) {
                p[axis] += step[axis]; next[axis] += delta[axis];
            }
        }
        return List.copyOf(result);
    }
    static List<MinersArena.Pos> blast(MinersArena.Pos center) {
        List<MinersArena.Pos> result = new ArrayList<>();
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++)
            if (x * x + y * y + z * z <= 9) result.add(offset(center, x, y, z));
        return List.copyOf(result);
    }
    private static MinersArena.Pos offset(MinersArena.Pos p, int x, int y, int z) {
        return new MinersArena.Pos(p.x() + x, p.y() + y, p.z() + z);
    }
}
