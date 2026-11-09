package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import java.util.*;

public final class ColorFloorCheck {
    public static void main(String[] args) {
        check(
                ColorFloorRound.choiceTicks(1) == 120
                        && ColorFloorRound.choiceTicks(3) == 88
                        && ColorFloorRound.choiceTicks(6) == 40
                        && ColorFloorRound.choiceTicks(100) == 40,
                "Bounded timings");
        var world = new PartyArenaCheck.WorldMap();
        var arena = new ColorFloorArena(world.world);
        var finalRound = ColorFloorRound.generate(12, new Random(91));
        arena.show(finalRound, false);
        arena.show(finalRound, true);
        for (int x = 0; x < 21; x++)
            for (int z = 0; z < 21; z++)
                check(
                        world.block(x - 10, 80, z - 10).getType()
                                == (finalRound.material(x, z) == finalRound.target()
                                        ? finalRound.target()
                                        : org.bukkit.Material.AIR),
                        "Only the target-colored tiles remain after the floor collapses");
        for (int stage : new int[] {1, 3, 6, 9, 12})
            for (int seed = 0; seed < 20; seed++) {
                var r = ColorFloorRound.generate(stage, new Random(seed));
                var counts = new HashMap<org.bukkit.Material, Integer>();
                boolean[][] seen = new boolean[21][21];
                List<Integer> areas = new ArrayList<>();
                for (int x = 0; x < 21; x++)
                    for (int z = 0; z < 21; z++) {
                        counts.merge(r.material(x, z), 1, Integer::sum);
                        double nearest = Double.POSITIVE_INFINITY;
                        for (int tx = 0; tx < 21; tx++)
                            for (int tz = 0; tz < 21; tz++)
                                if (r.material(tx, tz) == r.target())
                                    nearest = Math.min(nearest, Math.hypot(tx - x, tz - z));
                        check(nearest <= 8, "Target remains reachable on the shortest countdown");
                        if (r.material(x, z) == r.target())
                            check(r.safe(x + .5, 82, z + .5), "Jump above target safe");
                        if (seen[x][z]) continue;
                        var queue = new ArrayDeque<int[]>();
                        queue.add(new int[] {x, z});
                        seen[x][z] = true;
                        int area = 0;
                        while (!queue.isEmpty()) {
                            var c = queue.remove();
                            area++;
                            for (var d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                int nx = c[0] + d[0], nz = c[1] + d[1];
                                if (nx >= 0
                                        && nx < 21
                                        && nz >= 0
                                        && nz < 21
                                        && !seen[nx][nz]
                                        && r.material(nx, nz) == r.material(x, z)) {
                                    seen[nx][nz] = true;
                                    queue.add(new int[] {nx, nz});
                                }
                            }
                        }
                        areas.add(area);
                    }
                check(
                        counts.size() == Map.of(1, 6, 3, 8, 6, 10, 9, 12, 12, 12).get(stage),
                        "More actual colors appear as the game progresses");
                check(
                        Collections.max(counts.values()) >= 3 * Collections.min(counts.values()),
                        "Some colors are meaningfully rarer");
                check(
                        Collections.max(areas) >= 20 && Collections.min(areas) <= 6,
                        "Large and small coherent patches");
                check(!r.safe(-1, 81, 0) && !r.safe(0, 90, 0), "Outside arena/height unsafe");
            }
        System.out.println("Color floor passed");
    }
}
