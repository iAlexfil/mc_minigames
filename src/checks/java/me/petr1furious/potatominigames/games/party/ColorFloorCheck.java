package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import java.util.*;

public final class ColorFloorCheck {
    public static void main(String[] args) {
        check(
                ColorFloorRound.COLORS.length == 32
                        && new HashSet<>(List.of(ColorFloorRound.COLORS)).size() == 32,
                "Thirty-two distinct floor materials");
        for (int i = 0; i < 32; i++)
            check(
                    ColorFloorRound.COLORS[i]
                            .name()
                            .endsWith(i < 16 ? "_CONCRETE" : "_GLAZED_TERRACOTTA"),
                    "First sixteen concrete colors, then sixteen glazed terracotta colors");
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
        Set<org.bukkit.Material> targets = new HashSet<>();
        Map<Locale, Set<String>> titles =
                Map.of(
                        Locale.ENGLISH,
                        new HashSet<>(),
                        Locale.forLanguageTag("ru"),
                        new HashSet<>());
        for (int seed = 0; seed < 200; seed++) {
            var r = ColorFloorRound.generate(12, new Random(seed * 7919L));
            targets.add(r.target());
            titles.forEach((locale, names) ->
                    names.add(me.petr1furious.potatominigames.i18n.I18n.plain(r.title(), locale)));
        }
        check(targets.size() == 32, "Every concrete and glazed variant can be the target");
        for (var names : titles.values())
            check(
                    names.size() == 32 && names.stream().noneMatch(n -> n.contains("party.rounds")),
                    "All thirty-two target names are distinct and localized");
        for (int stage : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 12})
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
                        counts.size()
                                == Map.of(
                                                1, 8, 2, 10, 3, 12, 4, 14, 5, 16, 6, 20, 7, 24, 8,
                                                28, 9, 32, 12, 32)
                                        .get(stage),
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
