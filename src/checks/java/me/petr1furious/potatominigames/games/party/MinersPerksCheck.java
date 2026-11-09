package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Regression scenarios for bounded perk mining, personal buff budgets and reward distributions. */
final class MinersPerksCheck {
    static void run() {
        geometry(); rewards(); budgets(); equipment();
    }
    private static void geometry() {
        var p = new MinersArena.Pos(-3, 72, 4);
        for (Vector look : List.of(new Vector(1, 0, 0), new Vector(0, -1, 0), new Vector(0, 0, -1))) {
            var plane = MinersMining.drill(p, look);
            check(plane.size() == 9 && new HashSet<>(plane).size() == 9 && plane.getFirst().equals(p), "Drill mines its centre first and eight unique neighbours");
            for (var b : plane) {
                check(look.getX() == 0 || b.x() == p.x(), "Horizontal drill stays in the target face plane");
                check(look.getY() == 0 || b.y() == p.y(), "Vertical drill stays in the target face plane");
                check(look.getZ() == 0 || b.z() == p.z(), "North/south drill stays in the target face plane");
            }
        }
        for (Vector look : List.of(new Vector(-1, 0, 0), new Vector(0, 1, 0), new Vector(1, 1, -1), new Vector(.3, -.8, .2))) {
            var line = MinersMining.tunnel(p, look);
            check(line.size() == 10 && new HashSet<>(line).size() == 10 && line.getFirst().equals(p), "Tunnel covers exactly ten distinct cells for axial and oblique aim");
            for (int i = 1; i < line.size(); i++) {
                var a = line.get(i - 1); var b = line.get(i);
                check(Math.abs(a.x() - b.x()) <= 1 && Math.abs(a.y() - b.y()) <= 1 && Math.abs(a.z() - b.z()) <= 1,
                        "Tunnel never skips a voxel to reach ore behind an obstacle");
            }
        }
        check(MinersMining.tunnel(p, new Vector()).isEmpty(), "Invalid aim cannot create a mining burst");
        var blast = MinersMining.blast(p);
        check(new HashSet<>(blast).size() == blast.size() && blast.contains(p), "Bomb mining has no duplicate reward positions");
        for (var b : blast) {
            int x = b.x() - p.x(), y = b.y() - p.y(), z = b.z() - p.z();
            check(x * x + y * y + z * z <= 9, "Bomb stays within its three-block mining radius");
        }
    }
    private static void rewards() {
        int[] lucky = new int[3];
        for (int i = 0; i < 100; i++) {
            int draw = i;
            lucky[MinersLoot.quality(new Random(0) { @Override public int nextInt(int n) { return draw; } }, true).ordinal()]++;
        }
        check(lucky[0] == 50 && lucky[1] == 40 && lucky[2] == 10, "Lucky Vein uses 50/40/10 without a second independent reward");
        for (int i = 0; i < MinersPerk.values().length; i++) {
            int draw = i;
            check(MinersPerk.random(new Random(0) { @Override public int nextInt(int n) {
                check(n == 7, "The agreed pool contains seven equally eligible perks"); return draw;
            } }) == MinersPerk.values()[i], "Every agreed perk can drop");
        }
        for (int i = 0; i < 12; i++) {
            int draw = i;
            for (var loot : MinersLoot.sift(new Random(0) { @Override public int nextInt(int n) { return draw; } }))
                check(MinersEquipment.kind(loot.material()) == null && loot.material() != Material.TOTEM_OF_UNDYING,
                        "Sifting produces finite consumables, never equipment, perk chains or extra totems");
        }
        int totems = 0;
        for (int i = 0; i < 100; i++) {
            int draw = i;
            Random random = new Random(0) {
                int calls;
                @Override public int nextInt(int n) { return switch (calls++) { case 0 -> 0; case 1 -> draw; default -> n == 5 ? 1 : 0; }; }
                @Override public boolean nextBoolean() { return false; }
            };
            if (MinersLoot.roll(MinersOre.EMERALD, random).stream().anyMatch(d -> d.material() == Material.TOTEM_OF_UNDYING)) totems++;
        }
        check(totems == 10, "Emerald totem probability remains ten percent despite other loot additions");
    }
    private static void budgets() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return a;
                    throw new AssertionError("Unexpected state-model player call: " + method.getName());
                });
        MinersPerks perks = new MinersPerks();
        check(perks.activate(player, MinersPerk.LUCK, 0, null, null), "Lucky Vein activates once");
        check(!perks.activate(player, MinersPerk.LUCK, 1, null, null), "An active buff cannot consume another copy");
        for (int i = 0; i < 3; i++) check(perks.lucky(a), "Three actual reward ores receive the bonus");
        check(!perks.lucky(a) && !perks.lucky(b), "Luck runs out and is personal");
        Random neverRoll = new Random(0) { @Override public int nextInt(int n) { throw new AssertionError("Inactive sifter must not roll"); } };
        check(!perks.sift(a, neverRoll), "Unbuffed stone has no bonus roll");
        int successes = 0;
        for (int i = 0; i < 100; i++) {
            if (i % 20 == 0) check(perks.activate(player, MinersPerk.SIFTER, i, null, null), "Sifter can reactivate after its twenty-block budget");
            int draw = i;
            if (perks.sift(a, new Random(0) { @Override public int nextInt(int n) { return draw; } })) successes++;
        }
        check(successes == 15 && !perks.sift(a, neverRoll), "Sifter has exactly fifteen-percent odds and twenty attempts per use");
        perks.activate(player, MinersPerk.LUCK, 100, null, null);
        perks.remove(a);
        check(!perks.lucky(a), "Leaving clears unused charges");
        perks.clear(); perks.clear();
    }
    private static void equipment() {
        var pick = MinersEquipment.Kind.PICKAXE;
        var helmet = MinersEquipment.Kind.HELMET;
        List<MinersEquipment.Piece> bag = new java.util.ArrayList<>(java.util.Collections.nCopies(41, null));
        bag.set(0, new MinersEquipment.Piece(pick, 999, true));
        bag.set(30, new MinersEquipment.Piece(pick, 20, false));
        check(MinersEquipment.plan(pick, 19, bag) == null, "A moved strong pickaxe prevents downgrades; a drill is not ranked as permanent gear");
        var upgrade = MinersEquipment.plan(pick, 21, bag);
        check(upgrade.slot() == 30 && upgrade.obsolete().equals(List.of(30)), "Upgrade follows moved gear without deleting the perk drill");
        java.util.Collections.fill(bag, new MinersEquipment.Piece(null, -1, false));
        bag.set(0, new MinersEquipment.Piece(pick, 999, true));
        upgrade = MinersEquipment.plan(pick, 20, bag);
        check(upgrade.slot() == 0 && upgrade.preserveDisplaced() && upgrade.obsolete().isEmpty(),
                "A full inventory preserves the drill as overflow when its preferred pickaxe slot is reused");
        bag.set(10, null);
        upgrade = MinersEquipment.plan(pick, 20, bag);
        check(upgrade.slot() == 10 && !upgrade.preserveDisplaced(), "Free storage is used before displacing a perk or consumable");
        bag.set(20, new MinersEquipment.Piece(helmet, 5, false));
        bag.set(39, new MinersEquipment.Piece(helmet, 3, false));
        upgrade = MinersEquipment.plan(helmet, 6, bag);
        check(upgrade.slot() == 39 && upgrade.obsolete().equals(List.of(20, 39)), "Better armor is worn automatically and obsolete copies are removed");
        check(MinersEquipment.plan(helmet, 5, bag) == null, "Equal armor does not delete a stronger stored piece");
        check(MinersEquipment.score(Material.IRON_PICKAXE, 5) > MinersEquipment.score(Material.DIAMOND_PICKAXE, 3),
                "Mining efficiency can outweigh the tool material");
        check(MinersEquipment.score(Material.DIAMOND_CHESTPLATE, 1) > MinersEquipment.score(Material.IRON_CHESTPLATE, 2),
                "Armor comparison accounts for material and protection together");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
