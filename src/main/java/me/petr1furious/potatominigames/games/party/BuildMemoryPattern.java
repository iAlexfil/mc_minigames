package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

record BuildMemoryPattern(Component title, Map<Cell, MemoryBlock> blocks) {
    static final int MODEL_COUNT = 14;

    record Cell(int x, int y, int z) {}

    BuildMemoryPattern {
        blocks = Map.copyOf(blocks);
    }

    static List<Integer> deck(Random random) {
        List<Integer> models = new ArrayList<>();
        for (int i = 0; i < MODEL_COUNT; i++) models.add(i);
        Collections.shuffle(models, random);
        return List.copyOf(models);
    }

    static BuildMemoryPattern generate(int stage, Random random) {
        return generate(stage, random.nextInt(MODEL_COUNT), random);
    }

    static BuildMemoryPattern generate(int stage, int model, Random random) {
        int level = Math.clamp(stage, 1, 8);
        String wood = List.of("OAK", "SPRUCE", "BIRCH", "CHERRY").get(random.nextInt(4));
        Material planks = Material.valueOf(wood + "_PLANKS"),
                fence = Material.valueOf(wood + "_FENCE"),
                sign = Material.valueOf(wood + "_SIGN"),
                stairs = Material.valueOf(wood + "_STAIRS"),
                slab = Material.valueOf(wood + "_SLAB");
        Material stone = random.nextBoolean() ? Material.STONE_BRICKS : Material.BRICKS;
        Material accent =
                List.of(
                                Material.RED_TERRACOTTA,
                                Material.CYAN_TERRACOTTA,
                                Material.YELLOW_TERRACOTTA,
                                Material.GREEN_TERRACOTTA)
                        .get(random.nextInt(4));
        Material light = random.nextBoolean() ? Material.LANTERN : Material.SOUL_LANTERN;
        Builder b = new Builder();
        Component title;
        switch (model) {
            case 0 -> {
                title = I18n.tr("party.rounds.build_memory.model.bench");
                for (int x = 1; x <= 3; x++) {
                    b.put(stone, x, 0, 2);
                    b.put(stairs, x, 1, 2, "facing=south");
                }
                b.put(fence, 0, 0, 2);
                b.put(fence, 4, 0, 2);
                b.put(fence, 4, 1, 2);
                b.put(fence, 4, 2, 2);
                b.put(light, 4, 3, 2);
            }
            case 1 -> {
                title = I18n.tr("party.rounds.build_memory.model.market_stall");
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(accent, x, 2, 2);
                }
                for (int x : new int[] {1, 3}) {
                    b.put(fence, x, 1, 2);
                    b.put(slab, x, 2, 1);
                }
                b.put(sign, 2, 1, 2, "rotation=0");
                b.put(light, random.nextBoolean() ? 1 : 3, 0, 1);
            }
            case 2 -> {
                title = I18n.tr("party.rounds.build_memory.model.chain_well");
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++) if (x != 2 || z != 2) b.put(stone, x, 0, z);
                for (int x : new int[] {1, 3}) b.put(fence, x, 1, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                b.put(Material.IRON_CHAIN, 2, 1, 2);
            }
            case 3 -> {
                title = I18n.tr("party.rounds.build_memory.model.lantern_arch");
                for (int x : new int[] {1, 3}) for (int y = 0; y <= 2; y++) b.put(stone, x, y, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 3, 2);
                b.put(Material.IRON_CHAIN, 2, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
                b.put(stairs, 1, 0, 1, "facing=south");
                b.put(stairs, 3, 0, 1, "facing=south");
            }
            case 4 -> {
                title = I18n.tr("party.rounds.build_memory.model.railed_bridge");
                for (int x = 1; x <= 3; x++) {
                    b.put(planks, x, 0, 2);
                    b.put(planks, x, 0, 1);
                    b.put(planks, x, 0, 3);
                    b.put(fence, x, 1, 1);
                    b.put(fence, x, 1, 3);
                }
                b.put(stairs, 0, 0, 2, "facing=east");
                b.put(stairs, 4, 0, 2, "facing=west");
                b.put(light, 1, 2, random.nextBoolean() ? 1 : 3);
            }
            case 5 -> {
                title = I18n.tr("party.rounds.build_memory.model.mantel");
                for (int x = 1; x <= 3; x++) for (int y = 0; y <= 1; y++) b.put(stone, x, y, 3);
                b.put(stone, 1, 0, 2);
                b.put(stone, 3, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 3);
                b.put(Material.SEA_LANTERN, 2, 0, 2);
                b.put(stairs, 1, 1, 2, "facing=west");
                b.put(stairs, 3, 1, 2, "facing=east");
            }
            case 6 -> {
                title = I18n.tr("party.rounds.build_memory.model.signal_tower");
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) b.put(stone, x, 0, z);
                b.put(planks, 2, 1, 2);
                b.put(Material.REDSTONE_BLOCK, 2, 2, 2);
                b.put(Material.REDSTONE_LAMP, 2, 3, 2);
                b.put(stairs, 2, 0, 0, "facing=south");
            }
            case 7 -> {
                title = I18n.tr("party.rounds.build_memory.model.reading_corner");
                for (int x = 1; x <= 3; x++) {
                    b.put(Material.BOOKSHELF, x, 0, 3);
                    b.put(Material.BOOKSHELF, x, 1, 3);
                    b.put(slab, x, 2, 3);
                }
                b.put(stairs, 1, 0, 1, "facing=west");
                b.put(fence, 2, 0, 1);
                b.put(planks, 2, 1, 1);
                b.put(light, 2, 2, 1);
            }
            case 8 -> {
                title = I18n.tr("party.rounds.build_memory.model.cart");
                for (int x : new int[] {1, 3}) for (int z : new int[] {1, 3}) b.put(stone, x, 0, z);
                b.put(stone, 1, 0, 2);
                b.put(stone, 3, 0, 2);
                for (int x = 1; x <= 3; x++) b.put(planks, x, 1, 2);
                b.put(accent, 1, 2, 2);
                b.put(accent, 2, 2, 2);
                b.put(slab, 3, 2, 2);
                b.put(fence, 2, 1, 1);
                b.put(fence, 2, 1, 0);
                b.put(planks, 2, 0, 1);
                b.put(planks, 2, 0, 0);
            }
            case 9 -> {
                title = I18n.tr("party.rounds.build_memory.model.signpost");
                for (int y = 0; y <= 2; y++) b.put(planks, 2, y, 2);
                for (int x = 0; x <= 4; x++) b.put(planks, x, 2, 2);
                for (int x : new int[] {0, 4}) {
                    b.put(sign, x, 3, 2, "rotation=0");
                    b.put(stone, x, 0, 2);
                    b.put(Material.TORCH, x, 1, 2);
                }
                b.put(stairs, 2, 0, 1, "facing=south");
            }
            case 10 -> {
                title = I18n.tr("party.rounds.build_memory.model.mine_entrance");
                for (int x : new int[] {1, 3}) for (int y = 0; y <= 2; y++) b.put(planks, x, y, 2);
                b.put(planks, 2, 2, 2);
                b.put(light, 2, 1, 2, "hanging=true");
                for (int x = 1; x <= 3; x++) b.put(stone, x, 0, 3);
                b.put(Material.TORCH, 1, 1, 3);
                b.put(Material.IRON_CHAIN, 3, 1, 3);
            }
            case 11 -> {
                title = I18n.tr("party.rounds.build_memory.model.gazebo");
                for (int x : new int[] {1, 3})
                    for (int z : new int[] {1, 3}) {
                        b.put(stone, x, 0, z);
                        b.put(fence, x, 1, z);
                        b.put(slab, x, 2, z);
                    }
                for (int x = 1; x <= 3; x++) b.put(slab, x, 2, 2);
                b.put(light, 2, 0, 2);
            }
            case 12 -> {
                title = I18n.tr("party.rounds.build_memory.model.monument_steps");
                for (int x = 1; x <= 3; x++) {
                    b.put(stairs, x, 0, 1, "facing=south");
                    b.put(stone, x, 0, 2);
                    b.put(slab, x, 1, 2);
                }
                b.put(stone, 2, 0, 3);
                b.put(planks, 2, 1, 3);
                b.put(accent, 2, 2, 3);
                b.put(light, 2, 3, 3);
            }
            case 13 -> {
                title = I18n.tr("party.rounds.build_memory.model.workshop");
                for (int x = 1; x <= 3; x++) b.put(planks, x, 0, 2);
                b.put(Material.CRAFTING_TABLE, 1, 0, 2);
                b.put(stairs, 1, 0, 1, "facing=north");
                for (int x : new int[] {1, 3}) {
                    b.put(planks, x, 0, 3);
                    b.put(fence, x, 1, 3);
                    b.put(planks, x, 2, 3);
                }
                b.put(planks, 2, 2, 3);
                b.put(light, 2, 1, 3, "hanging=true");
                b.put(sign, 3, 1, 2, "rotation=0");
                b.put(stone, 0, 0, 2);
                b.put(Material.TORCH, 0, 1, 2);
            }
            default -> throw new IllegalArgumentException("Unknown model: " + model);
        }
        // The opening uses only the recognizable model, without random filler around it.
        if (level <= 3) return new BuildMemoryPattern(title, b.blocks).rotate(random.nextInt(4));
        // From stage four, add four pieces per stage: paving, corner posts and lamps.
        int target = 18 + 4 * (level - 3);
        List<Cell> paving = new ArrayList<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) paving.add(new Cell(x, 0, z));
        Collections.shuffle(paving, random);
        for (Cell c : paving) {
            if (b.blocks.size() >= target) break;
            b.blocks.putIfAbsent(c, MemoryBlock.of(random.nextBoolean() ? stone : accent));
        }
        List<Cell> posts =
                new ArrayList<>(
                        List.of(
                                new Cell(0, 0, 0),
                                new Cell(4, 0, 0),
                                new Cell(0, 0, 4),
                                new Cell(4, 0, 4),
                                new Cell(0, 0, 1),
                                new Cell(4, 0, 1),
                                new Cell(0, 0, 3),
                                new Cell(4, 0, 3)));
        Collections.shuffle(posts, random);
        for (Cell c : posts)
            for (int y = 1; y <= 3 && b.blocks.size() < target; y++)
                b.blocks.putIfAbsent(new Cell(c.x, y, c.z), MemoryBlock.of(y == 3 ? light : fence));
        return new BuildMemoryPattern(title, b.blocks).rotate(random.nextInt(4));
    }

    Map<Material, Integer> inventory() {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        blocks.values().forEach(b -> result.merge(b.item(), b.itemCount(), Integer::sum));
        return Map.copyOf(result);
    }

    boolean matches(Map<Cell, MemoryBlock> actual) {
        return blocks.equals(actual);
    }

    BuildMemoryPattern rotate(int turns) {
        Map<Cell, MemoryBlock> rotated = new HashMap<>();
        blocks.forEach((c, block) -> {
            int x = c.x, z = c.z;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) {
                int next = 4 - z;
                z = x;
                x = next;
            }
            rotated.put(new Cell(x, c.y, z), block.rotate(turns));
        });
        return new BuildMemoryPattern(title, rotated);
    }

    private static final class Builder {
        final Map<Cell, MemoryBlock> blocks = new LinkedHashMap<>();

        void put(Material m, int x, int y, int z, String... properties) {
            blocks.put(new Cell(x, y, z), MemoryBlock.of(m, properties));
        }
    }
}
