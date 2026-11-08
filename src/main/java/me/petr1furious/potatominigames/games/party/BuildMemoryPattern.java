package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

record BuildMemoryPattern(Component title, Map<Cell, Material> blocks) {
    record Cell(int x, int y, int z) {}

    BuildMemoryPattern {
        blocks = Map.copyOf(blocks);
    }

    BuildMemoryPattern(Map<Cell, Material> blocks) {
        this(I18n.tr("party.rounds.build_memory.model.generic"), blocks);
    }

    static BuildMemoryPattern generate(int stage, Random random) {
        int level = Math.clamp(stage, 1, 8) + 2;
        Material wood = random.nextBoolean() ? Material.OAK_PLANKS : Material.SPRUCE_PLANKS;
        Material stone = random.nextBoolean() ? Material.STONE_BRICKS : Material.BRICKS;
        var b = new LinkedHashMap<Cell, Material>();
        Component title;
        switch (level) {
            case 3 -> {
                title = I18n.tr("party.rounds.build_memory.model.tree");
                for (int y = 0; y < 3; y++) put(b, wood, 2, y, 2);
                put(b, wood, 1, 0, 2);
                for (var c :
                        List.of(
                                new Cell(1, 2, 2),
                                new Cell(3, 2, 2),
                                new Cell(2, 2, 1),
                                new Cell(2, 2, 3),
                                new Cell(2, 3, 2))) b.put(c, Material.GREEN_WOOL);
            }
            case 4 -> {
                title = I18n.tr("party.rounds.build_memory.model.arch");
                for (int x : new int[] {1, 3}) for (int y = 0; y < 3; y++) put(b, stone, x, y, 2);
                for (int x = 1; x <= 3; x++) put(b, wood, x, 3, 2);
                put(b, stone, 0, 0, 2);
                put(b, stone, 4, 0, 2);
            }
            case 5 -> {
                title = I18n.tr("party.rounds.build_memory.model.well");
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++) if (x != 2 || z != 2) put(b, stone, x, 0, z);
                put(b, wood, 1, 1, 2);
                put(b, wood, 3, 1, 2);
                for (int x = 1; x <= 3; x++) put(b, wood, x, 2, 2);
            }
            case 6 -> {
                title = I18n.tr("party.rounds.build_memory.model.bridge");
                for (int x : new int[] {0, 4})
                    for (int z : new int[] {1, 3}) {
                        put(b, stone, x, 0, z);
                        put(b, wood, x, 1, z);
                    }
                for (int x = 0; x < 5; x++) put(b, wood, x, 1, 2);
                put(b, stone, 0, 2, 1);
                put(b, stone, 4, 2, 1);
            }
            case 7 -> {
                title = I18n.tr("party.rounds.build_memory.model.colonnade");
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) put(b, stone, x, 0, z);
                for (int x : new int[] {1, 3})
                    for (int z : new int[] {1, 3})
                        for (int y = 1; y <= 2; y++) put(b, Material.QUARTZ_BLOCK, x, y, z);
            }
            case 8 -> {
                title = I18n.tr("party.rounds.build_memory.model.fireplace");
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) put(b, stone, x, 0, z);
                for (int x = 1; x <= 3; x++) for (int y = 1; y <= 2; y++) put(b, stone, x, y, 3);
                put(b, Material.POLISHED_ANDESITE, 1, 1, 2);
                put(b, Material.POLISHED_ANDESITE, 3, 1, 2);
                put(b, Material.POLISHED_ANDESITE, 2, 2, 2);
                put(b, stone, 2, 3, 3);
            }
            case 9 -> {
                title = I18n.tr("party.rounds.build_memory.model.gate");
                for (int x : new int[] {0, 4}) {
                    for (int y = 0; y < 4; y++) put(b, stone, x, y, 2);
                    for (int z : new int[] {1, 3})
                        for (int y = 0; y < 2; y++) put(b, stone, x, y, z);
                    put(b, Material.POLISHED_ANDESITE, x, 2, 1);
                }
                for (int x = 1; x <= 3; x++) put(b, wood, x, 3, 2);
            }
            default -> {
                title = I18n.tr("party.rounds.build_memory.model.house");
                for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) put(b, stone, x, 0, z);
                for (int x = 1; x <= 3; x++) put(b, wood, x, 1, 3);
                for (int x : new int[] {1, 3}) for (int z = 1; z <= 2; z++) put(b, wood, x, 1, z);
                for (int x = 1; x <= 3; x++)
                    for (int z = 1; z <= 3; z++)
                        if (z != 1 || x == 2) put(b, Material.RED_TERRACOTTA, x, 2, z);
            }
        }
        return new BuildMemoryPattern(title, b).rotate(random.nextInt(4));
    }

    private static void put(Map<Cell, Material> blocks, Material material, int x, int y, int z) {
        blocks.put(new Cell(x, y, z), material);
    }

    Map<Material, Integer> inventory() {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        blocks.values().forEach(m -> result.merge(m, 1, Integer::sum));
        return Map.copyOf(result);
    }

    boolean matches(Map<Cell, Material> actual) {
        return blocks.equals(actual);
    }

    BuildMemoryPattern rotate(int turns) {
        Map<Cell, Material> rotated = new HashMap<>();
        blocks.forEach((c, m) -> {
            int x = c.x, z = c.z;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) {
                int next = 4 - z;
                z = x;
                x = next;
            }
            rotated.put(new Cell(x, c.y, z), m);
        });
        return new BuildMemoryPattern(title, rotated);
    }
}
