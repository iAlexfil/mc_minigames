package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import net.kyori.adventure.text.Component;

import java.util.*;

/** Counts distinct authored geometry, and checks that the live stream uses the correct tier. */
final class HoleWallCatalogCheck {
    static void run() {
        Set<List<HoleWallPattern.Cell>> islands = new HashSet<>();
        for (int seed = 0; seed < 100; seed++)
            islands.add(HoleWallPattern.islands(new Random(seed * 7919L)).cells());
        check(islands.size() == 3, "Three separate hard corner-island challenges");
        System.out.println(
                "Hole in the Wall catalog: PLUS HARD corner islands = " + islands.size());
        for (var layout : HoleWallLayout.values()) {
            Set<List<Map<String, HoleWallBlock.Shape>>> artwork = new HashSet<>();
            Map<Component, HoleWallDifficulty> tiers = new HashMap<>();
            for (int stage = 1; stage <= layout.patternCount(); stage++) {
                List<Map<String, HoleWallBlock.Shape>> shape = new ArrayList<>();
                for (int radius : new int[] {1, 2}) {
                    var pattern = layout.pattern(stage, radius, new Random(0));
                    if (stage > (layout == HoleWallLayout.PLUS ? 20 : 12))
                        check(
                                pattern.cells().stream().anyMatch(c -> c.y() >= 2),
                                "A new silhouette must retain its upper artwork after grounding");
                    Map<String, HoleWallBlock.Shape> cells = new HashMap<>();
                    for (var cell : pattern.cells())
                        cells.put(cell.u() + ":" + cell.y(), cell.block().shape());
                    shape.add(cells);
                    tiers.put(pattern.title(), layout.difficulty(stage));
                    if (layout.difficulty(stage) == HoleWallDifficulty.EASY)
                        check(
                                pattern.gates().stream()
                                        .allMatch(g -> g.width() >= 1 && g.height() >= 1.8),
                                "Easy walls need no crouching or precision");
                    if (layout.difficulty(stage) == HoleWallDifficulty.NORMAL)
                        check(
                                pattern.gates().stream().allMatch(g -> g.width() >= 1),
                                "Medium walls do not introduce precision slits");
                }
                check(artwork.add(shape), "Duplicate geometry in " + layout + " stage " + stage);
            }
            check(tiers.size() == 48, "Every authored wall has its own title on " + layout);
            for (var tier : HoleWallDifficulty.values()) {
                var stages = layout.shuffledStages(new Random(0), tier);
                int expected = tier == HoleWallDifficulty.HARD ? 12 : 18;
                check(
                        stages.size() == expected && new HashSet<>(stages).size() == expected,
                        "Expected distinct " + tier + " patterns on " + layout);
                check(
                        stages.stream().allMatch(s -> layout.difficulty(s) == tier),
                        "Tier deck is isolated");
                System.out.println(
                        "Hole in the Wall catalog: " + layout + " " + tier + " = " + stages.size());
            }
            var flow = new HoleWallFlow(3600, new Random(24), layout);
            Set<HoleWallDifficulty> seen = EnumSet.noneOf(HoleWallDifficulty.class);
            for (int tick = 0; tick < 3600; tick++)
                for (var wall : flow.tick(tick).started()) {
                    var tier = HoleWallDifficulty.at(tick, 3600);
                    if (wall.pattern.requiresIslands())
                        check(tier == HoleWallDifficulty.HARD, "Corner jumps are late challenges");
                    else
                        check(
                                tiers.get(wall.pattern.title()) == tier,
                                "Live walls obey difficulty progression");
                    seen.add(tier);
                }
            check(seen.size() == 3, "Every difficulty appears in a complete game");
        }
    }
}
