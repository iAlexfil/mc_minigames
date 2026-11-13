package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

import java.util.*;

/** Authored repeating ribbons and eyelets, aligned with the surviving side lanes. */
final class HoleWallMapPatterns {
    static HoleWallPattern generate(HoleWallLayout layout, int stage, Random random) {
        int kind = Math.floorMod(stage - 1, 12);
        var walkLeft = new HoleWallPattern.Gate(-4, 2, 0, 3);
        var walkRight = new HoleWallPattern.Gate(3, 2, 0, 3);
        var jumpLeft = new HoleWallPattern.Gate(-4, 2, 1, 3);
        var jumpRight = new HoleWallPattern.Gate(3, 2, 1, 3);
        var duckLeft = new HoleWallPattern.Gate(-4, 2, 0, 1.5);
        var duckRight = new HoleWallPattern.Gate(3, 2, 0, 1.5);
        List<HoleWallPattern.Gate> gates =
                new ArrayList<>(
                        switch (kind) {
                            case 0 -> List.of(walkLeft, walkRight);
                            case 1, 3 -> List.of(walkLeft, jumpRight);
                            case 2 -> List.of(jumpLeft, walkRight);
                            case 4 -> List.of(jumpLeft, jumpRight);
                            case 5 -> List.of(duckLeft, walkRight);
                            case 6, 9 -> List.of(jumpLeft, duckRight);
                            case 7 -> List.of(walkLeft, duckRight);
                            case 8 -> List.of(duckLeft, jumpRight);
                            case 10 ->
                                    List.of(new HoleWallPattern.Gate(-3.375, .75, 0, 3), jumpRight);
                            default ->
                                    List.of(
                                            new HoleWallPattern.Gate(-3.375, .75, 0, 1.5),
                                            walkRight);
                        });
        if (layout == HoleWallLayout.SNAKE && (kind == 2 || kind == 5 || kind == 8 || kind == 11))
            gates.add(
                    new HoleWallPattern.Gate(
                            0, 1, kind == 2 ? 1 : 0, kind == 5 || kind == 11 ? 1.5 : 3));
        var full =
                new HoleWallBlock(
                        layout == HoleWallLayout.SNAKE
                                ? Material.PRISMARINE_BRICKS
                                : Material.PURPUR_BLOCK,
                        HoleWallBlock.Shape.FULL);
        var cells = new ArrayList<HoleWallPattern.Cell>();
        for (int u = -HoleWallPattern.HALF_WIDTH; u <= HoleWallPattern.HALF_WIDTH; u++)
            for (int y = 0; y < 5; y++) {
                HoleWallBlock block = outline(layout, kind, u, y) ? full : null;
                for (var gate : gates)
                    if (u >= gate.start() && u + 1 <= gate.start() + gate.width()) {
                        double bottom = gate.bottom(), top = bottom + gate.height();
                        if (y >= bottom && y + 1 <= top) block = null;
                        else if (y < bottom) block = full;
                        else if (y < top && y + 1 > top) block = full.upperSlab();
                    }
                if (kind >= 10 && (u == -4 || u == -3)) {
                    if (y < (kind == 11 ? 1 : 3))
                        block =
                                new HoleWallBlock(
                                        Material.OAK_FENCE, HoleWallBlock.Shape.FENCE_POST);
                    else if (kind == 11 && y == 1) block = full.upperSlab();
                    else block = full;
                }
                if (block != null) cells.add(new HoleWallPattern.Cell(u, y, block));
            }
        cells = new ArrayList<>(HoleWallPattern.frameLowPassages(cells, gates, full));
        return new HoleWallPattern(
                cells,
                gates,
                I18n.tr(
                        "party.rounds.hole_wall."
                                + layout.name().toLowerCase(Locale.ROOT)
                                + "."
                                + kind));
    }

    // Rows read top to bottom. Every seven-column tile puts its two-column opening at
    // columns 2/3: repeating it aligns those openings with u=-4/-3 and u=3/4. Consequently
    // the actual routes belong to the artwork, rather than cutting across unrelated shapes.
    // The physical route pass above supplies jump sills, crouching slabs and precision posts.
    private static final String[][] SNAKE_RIBBONS = {
        { // Deep U ribbons: tall prongs over a continuous low band between the openings.
            ".#..#..", ".#..#..", ".#..#..", "##..###", "##..###"
        },
        { // Repeated rising steps.
            "....##.", "....###", ".#..###", "##..###", "##..###"
        },
        { // Repeated falling steps.
            "##.....", "###....", "##..#..", "##..##.", "##..###"
        },
        { // Broad U-shaped stair ribbons.
            ".#..#..", "##..##.", "##..###", "##..###", "##..###"
        },
        { // Lower U ribbons leave an especially clear skyline above ducking passages.
            ".......", ".#..#..", "##..##.", "##..###", "##..###"
        },
        { // A stepped ribbon falls on the left and climbs again on the right.
            "##...##", "###..##", ".#..###", "##..##.", "##..###"
        }
    };

    private static final String[][] DONUT_EYELETS = {
        { // Rounded, four-cornered ring windows.
            ".####..", "##..##.", "#....##", "##..##.", ".####.."
        },
        { // Narrow upright windows with simple paired columns.
            ".####..", "##..##.", "##..##.", "##..##.", ".####.."
        },
        { // Large eyelets above a shared solid sill.
            ".####..", "##..##.", "#....#.", "##..##.", "#######"
        },
        { // Two clean rows of eyelets separated by one horizontal band.
            ".####..", "##..##.", "######.", "##..##.", ".####.."
        },
        { // A lower arcade for crouching routes.
            ".......", ".####..", "##..##.", "##..##.", ".####.."
        },
        { // Broad ring frames make the two precision posts easy to read.
            "#######", "##..###", "#....##", "##..###", "#######"
        }
    };

    private static boolean outline(HoleWallLayout layout, int kind, int u, int y) {
        int motif =
                switch (kind) {
                    case 0 -> 0;
                    case 1, 8 -> 1;
                    case 2, 6 -> 2;
                    case 3, 10 -> 3;
                    case 4, 7 -> 4;
                    case 5 -> 0;
                    default -> 5;
                };
        String[] rows = (layout == HoleWallLayout.SNAKE ? SNAKE_RIBBONS : DONUT_EYELETS)[motif];
        return rows[HoleWallPattern.HEIGHT - 1 - y].charAt(Math.floorMod(u + 6, 7)) == '#';
    }
}
