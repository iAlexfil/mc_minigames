package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.Material;

import java.util.*;

/** Separate silhouettes for the S track and ring, with exits above their surviving side lanes. */
final class HoleWallMapPatterns {
    static HoleWallPattern generate(HoleWallLayout layout, int stage, Random random) {
        int kind = Math.floorMod(stage - 1, 12);
        boolean mirror = random.nextBoolean();
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
                if (block != null) cells.add(new HoleWallPattern.Cell(mirror ? -u : u, y, block));
            }
        cells =
                new ArrayList<>(
                        HoleWallPattern.frameLowPassages(
                                cells,
                                mirror
                                        ? gates.stream()
                                                .map(g ->
                                                        new HoleWallPattern.Gate(
                                                                1 - g.start() - g.width(),
                                                                g.width(),
                                                                g.bottom(),
                                                                g.height()))
                                                .toList()
                                        : gates,
                                full));
        if (mirror)
            gates =
                    gates.stream()
                            .map(g ->
                                    new HoleWallPattern.Gate(
                                            1 - g.start() - g.width(),
                                            g.width(),
                                            g.bottom(),
                                            g.height()))
                            .toList();
        return new HoleWallPattern(
                cells,
                gates,
                I18n.tr(
                        "party.rounds.hole_wall."
                                + layout.name().toLowerCase(Locale.ROOT)
                                + "."
                                + kind));
    }

    private static boolean outline(HoleWallLayout layout, int kind, int u, int y) {
        int a = Math.abs(u), tooth = a % 4;
        if (layout == HoleWallLayout.SNAKE)
            return switch (kind) {
                case 0 -> y <= 1 || tooth == 0; // Symmetric comb along the three track bands.
                case 1 -> y <= a % 4; // Rising chevrons, grounded at every column.
                case 2 -> y == 0 || y == 4 || tooth == 0; // Wide three-lane bridge frames.
                case 3 -> y <= 3 - Math.min(3, a % 6); // Mirrored stair descents.
                case 4 -> y <= (tooth < 2 ? 2 : 0); // Alternating hurdle heights.
                case 5 -> y <= 2 || tooth == 0; // Low beam and tall exit.
                case 6 -> y == 0 || y == 4 || a % 3 == 0; // Ladder frames.
                case 7 -> y <= 1 || a % 5 <= 1; // Broad low ribbons with grounded piers.
                case 8 -> y <= 4 - Math.min(4, a % 5); // Symmetric peaked skyline.
                case 9 -> y == 0 || y == 2 || y == 4 || a % 4 == 0; // Three horizontal slots.
                case 10 -> y <= 2 || a % 3 == 0;
                default -> y == 0 || y == 4 || a % 4 == 0;
            };
        return switch (kind) {
            case 0 -> y == 0 || y == 4 || a % 6 == 0; // Paired large ring windows.
            case 1 -> y <= 1 || a % 4 == 0; // Inner teeth and outer windows.
            case 2 -> y <= Math.min(4, a % 6); // Bowl silhouette.
            case 3 -> y <= 4 - Math.min(4, a % 6); // Crown silhouette.
            case 4 -> y == 0 || y == 1 || a % 5 <= 1; // Two raised side windows.
            case 5 -> y <= 2 || a % 6 == 0; // Inner low lintels.
            case 6 -> y == 0 || y == 3 || a % 4 <= 1; // Arch pairs over both ring branches.
            case 7 -> y <= 1 || a % 3 == 0; // Radial comb.
            case 8 -> y == 0 || y == 4 || a % 3 == 0; // Roman numeral-like frames.
            case 9 -> y == 0 || y == 2 || y == 4 || a % 6 <= 1; // Layered eyelets.
            case 10 -> y <= 3 || a % 5 == 0;
            default -> y == 0 || y == 3 || a % 5 == 0;
        };
    }
}
