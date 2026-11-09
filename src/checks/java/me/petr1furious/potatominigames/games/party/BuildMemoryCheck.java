package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

public final class BuildMemoryCheck {
    public static void main(String[] args) {
        Set<Component> openingModels = new HashSet<>();
        for (int seed = 0; seed < 100; seed++)
            openingModels.add(BuildMemoryPattern.generate(1, new Random(seed)).title());
        check(openingModels.size() >= 8, "Opening model varies between matches");
        for (int stage = 1; stage <= 8; stage++)
            for (int seed = 0; seed < 50; seed++) {
                var p = BuildMemoryPattern.generate(stage, new Random(seed));
                check(
                        p.blocks().size() == 18 + 4 * (stage - 1),
                        "Faster progression: four extra pieces per stage");
                check(
                        p.blocks().keySet().stream().anyMatch(c -> c.y() >= 2),
                        "Every stage starts with a three-dimensional model");
                check(
                        !I18n.plain(p.title(), Locale.ENGLISH).isBlank()
                                && p.inventory().size() <= 12,
                        "Named model with coherent thematic materials");
                check(
                        p.inventory().values().stream().mapToInt(Integer::intValue).sum()
                                == p.blocks().size(),
                        "Exact kit");
                check(p.rotate(1).rotate(3).blocks().equals(p.blocks()), "Rotation round trip");
                for (var c : p.blocks().keySet()) {
                    check(
                            c.x() >= 0
                                    && c.x() < 5
                                    && c.z() >= 0
                                    && c.z() < 5
                                    && c.y() >= 0
                                    && c.y() < 4,
                            "Bounds");
                }
                Set<BuildMemoryPattern.Cell> reachable = new HashSet<>();
                p.blocks().keySet().stream().filter(c -> c.y() == 0).forEach(reachable::add);
                boolean changed;
                do {
                    changed = false;
                    for (var c : p.blocks().keySet())
                        if (!reachable.contains(c)
                                && reachable.stream()
                                        .anyMatch(v ->
                                                Math.abs(c.x() - v.x())
                                                                + Math.abs(c.y() - v.y())
                                                                + Math.abs(c.z() - v.z())
                                                        == 1)) {
                            reachable.add(c);
                            changed = true;
                        }
                } while (changed);
                check(
                        reachable.size() == p.blocks().size(),
                        "Can place every block from the floor without extra scaffolding");
                var actual = new HashMap<>(p.blocks());
                check(p.matches(actual), "Exact succeeds");
                var first = actual.keySet().iterator().next();
                actual.remove(first);
                check(!p.matches(actual), "One missing fails");
                actual.put(first, MemoryBlock.of(Material.BEDROCK));
                check(!p.matches(actual), "One wrong fails");
            }
        Set<Material> materials = new HashSet<>();
        for (int model = 0; model < BuildMemoryPattern.MODEL_COUNT; model++) {
            Set<Map<BuildMemoryPattern.Cell, MemoryBlock>> variants = new HashSet<>();
            for (int seed = 0; seed < 40; seed++) {
                var p = BuildMemoryPattern.generate(4, model, new Random(seed));
                variants.add(p.blocks());
                materials.addAll(p.inventory().keySet());
                for (var entry : p.blocks().entrySet()) {
                    var support = entry.getValue().support(entry.getKey());
                    check(
                            support == null || support.y() == -1 || p.blocks().containsKey(support),
                            "Every attachment has support");
                }
            }
            check(variants.size() >= 30, "Same model has many visual variants");
        }
        for (String suffix :
                List.of("_STAIRS", "_SLAB", "_SIGN", "_FENCE", "_CHAIN", "TORCH", "LAMP"))
            check(
                    materials.stream().anyMatch(m -> m.name().endsWith(suffix)),
                    "Includes decorative family " + suffix);
        for (int seed = 0; seed < 30; seed++) {
            var deck = BuildMemoryPattern.deck(new Random(seed));
            check(new HashSet<>(deck.subList(0, 8)).size() == 8, "No repeated model in a match");
        }
        var stair = MemoryBlock.of(Material.OAK_STAIRS, "facing=north", "half=top");
        check(
                stair.rotate(1).properties().get("facing").equals("east"),
                "Rotates facing with geometry");
        check(stair.rotate(4).equals(stair), "Stair state rotation round trip");
        var location = new BuildMemoryPattern.Cell(2, 0, 2);
        var reference = new BuildMemoryPattern(Component.text("Stair"), Map.of(location, stair));
        check(!reference.matches(Map.of(location, stair.rotate(1))), "Wrong facing fails");
        check(
                !reference.matches(
                        Map.of(location, MemoryBlock.of(Material.OAK_STAIRS, "half=bottom"))),
                "Wrong half fails");
        check(
                MemoryBlock.of(Material.OAK_WALL_SIGN).item() == Material.OAK_SIGN,
                "Wall sign refunds a placeable sign");
        check(
                MemoryBlock.of(Material.OAK_SLAB, "type=double").itemCount() == 2,
                "Double slab refunds two items");
        System.out.println("Build memory variants, difficulty, exact kits and orientations passed");
    }
}
