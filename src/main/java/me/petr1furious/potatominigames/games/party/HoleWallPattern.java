package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

/** Authored silhouettes with explicit alternative routes over the shrinking plus. */
record HoleWallPattern(List<Cell> cells, List<Gate> gates, Component title) {
    static final int HALF_WIDTH = 13, HEIGHT = 5;
    static final int[] KINDS = {
        0, 12, 1, 2, 3, 4, 5, 18, 6, 19, 13, 7, 15, 8, 16, 9, 17, 10, 14, 11
    };
    private static final Set<Integer> INTRO = Set.of(0, 1, 2, 3, 4, 6, 7, 10, 11, 18);
    private static final String[] NAMES = {
        "arch",
        "double_gate",
        "barrier",
        "steps",
        "columns",
        "battlements",
        "zigzag",
        "aqueduct",
        "canopy",
        "keyhole",
        "terraces",
        "fence_windows",
        "duck_tunnel",
        "fence_slit",
        "duck_fence",
        "iron_slit",
        "chain_curtain",
        "end_lattice",
        "chess_columns",
        "reading_tunnel"
    };

    record Cell(int u, int y, HoleWallBlock block) {
        Cell(int u, int y) {
            this(u, y, HoleWallBlock.STONE);
        }
    }

    record Gate(double start, double width, double bottom, double height) {
        double bodyHeight() {
            return Math.min(1.8, height);
        }
    }

    HoleWallPattern {
        cells = List.copyOf(cells);
        gates = List.copyOf(gates);
    }

    static List<Integer> shuffledStages(Random random, boolean introductory) {
        var stages = new ArrayList<Integer>();
        for (int stage = 1; stage <= KINDS.length; stage++) stages.add(stage);
        Collections.shuffle(stages, random);
        if (introductory) {
            var first = stages.stream().filter(s -> INTRO.contains(KINDS[s - 1])).limit(5).toList();
            stages.removeAll(first);
            var next =
                    stages.stream()
                            .filter(s -> KINDS[s - 1] < 13 || KINDS[s - 1] > 17)
                            .limit(5)
                            .toList();
            stages.removeAll(next);
            stages.addAll(0, next);
            stages.addAll(0, first);
        }
        return stages;
    }

    static HoleWallPattern generate(int stage, int radius, Random random) {
        int kind = KINDS[Math.floorMod(stage - 1, KINDS.length)];
        boolean mirror = random.nextBoolean();
        var gates = routes(kind, radius);
        var full =
                new HoleWallBlock(
                        random.nextBoolean() ? Material.STONE_BRICKS : Material.DARK_PRISMARINE,
                        HoleWallBlock.Shape.FULL);
        var cells = new ArrayList<Cell>();
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++)
            for (int y = 0; y < HEIGHT; y++) {
                HoleWallBlock block = silhouette(kind, u, y) ? full : null;
                // Each silhouette has a readable sill/ceiling around its actual routes, including
                // half-height headroom. The wide alternatives are playable even on three-wide arms.
                for (var gate : gates)
                    if (u >= gate.start() && u + 1 <= gate.start() + gate.width()) {
                        double bottom = gate.bottom(), top = bottom + gate.height();
                        if (y >= bottom && y + 1 <= top) block = null;
                        else if (y < bottom) block = full;
                        else if (y < top && y + 1 > top)
                            block =
                                    new HoleWallBlock(
                                            Material.SMOOTH_STONE_SLAB,
                                            HoleWallBlock.Shape.UPPER_SLAB);
                        else if (y == top) block = full;
                    }
                if (kind >= 13 && kind <= 15 && (u == -1 || u == 0)) {
                    // Two disconnected posts leave 0.75/0.875 blocks; a separate full-width route
                    // rewards moving farther instead of making precision the only solution.
                    if (y < (kind == 14 ? 1 : 3))
                        block =
                                new HoleWallBlock(
                                        kind == 15 ? Material.IRON_BARS : Material.OAK_FENCE,
                                        kind == 15
                                                ? HoleWallBlock.Shape.BARS
                                                : HoleWallBlock.Shape.FENCE_POST);
                    else if (kind == 14 && y == 1)
                        block =
                                new HoleWallBlock(
                                        Material.SMOOTH_STONE_SLAB, HoleWallBlock.Shape.UPPER_SLAB);
                    else block = full;
                } else if (block != null && kind == 16 && y < 4) {
                    // Keep any crouching lintel solid; hanging chains themselves form many thin
                    // gaps.
                    boolean lintel = false;
                    for (var g : gates)
                        if (g.height() == 1.5
                                && u >= g.start()
                                && u + 1 <= g.start() + g.width()
                                && y == 1) lintel = true;
                    if (!lintel)
                        block = new HoleWallBlock(Material.IRON_CHAIN, HoleWallBlock.Shape.CHAIN);
                } else if (block != null && kind == 17 && y != 0 && y != 3) {
                    block = new HoleWallBlock(Material.END_ROD, HoleWallBlock.Shape.ROD);
                } else if (block != null && kind == 11 && Math.abs(u) > radius && y < 3) {
                    block = new HoleWallBlock(Material.OAK_FENCE, HoleWallBlock.Shape.FENCE_POST);
                } else if (block != null && (kind == 3 || kind == 10) && Math.abs(u) > radius) {
                    block =
                            new HoleWallBlock(
                                    Material.STONE_BRICK_STAIRS, HoleWallBlock.Shape.STAIRS);
                } else if (block != null && kind == 2 && y == 0) {
                    block =
                            new HoleWallBlock(
                                    Material.STONE_BRICK_SLAB, HoleWallBlock.Shape.LOWER_SLAB);
                }
                if (block != null) cells.add(new Cell(mirror ? -u : u, y, block));
            }
        if (mirror)
            gates =
                    gates.stream()
                            .map(g ->
                                    new Gate(
                                            1 - g.start() - g.width(),
                                            g.width(),
                                            g.bottom(),
                                            g.height()))
                            .toList();
        return new HoleWallPattern(
                cells, gates, I18n.tr("party.rounds.hole_wall.pattern." + NAMES[kind]));
    }

    private static List<Gate> routes(int kind, int radius) {
        Gate left = new Gate(-radius, radius, 0, 3), right = new Gate(1, radius, 0, 3);
        Gate center = new Gate(0, 1, 0, 3), jumpCenter = new Gate(0, 1, 1, 3);
        Gate jumpRight = new Gate(1, radius, 1, 3), duckLeft = new Gate(-radius, radius, 0, 1.5);
        Gate duckCenter = new Gate(0, 1, 0, 1.5);
        return switch (kind) {
            case 0, 4, 11 -> List.of(left, right);
            case 1, 18 -> List.of(center, jumpRight);
            case 2, 17 -> List.of(jumpCenter, left);
            case 3, 6, 10 -> List.of(left, jumpRight);
            case 5, 9 -> List.of(jumpCenter, duckLeft);
            case 7 -> List.of(center, new Gate(-radius, radius, 1, 3), jumpRight);
            case 8, 12 -> List.of(duckCenter, right);
            case 13 -> List.of(new Gate(-.375, .75, 0, 3), right);
            case 14 -> List.of(new Gate(-.375, .75, 0, 1.5), jumpRight);
            case 15 -> List.of(new Gate(-.4375, .875, 0, 3), new Gate(1, radius, 0, 1.5));
            case 16 -> List.of(left, new Gate(1, radius, 0, 1.5));
            default -> List.of(duckLeft, jumpRight);
        };
    }

    private static boolean silhouette(int kind, int u, int y) {
        int a = Math.abs(u), tooth = Math.floorMod(u, 4), wave = Math.floorMod(u, 6);
        return switch (kind) {
            case 0 -> y == 3 || (tooth == 0 && y < 4); // A row of open arches.
            case 1 -> y >= 2 || (a % 5 == 2 && y < 2); // Central door flanked by raised windows.
            case 2 -> y == 0 || (a % 5 == 0 && y <= 2); // Low hurdle with broad escape route.
            case 3 -> y <= Math.floorMod(u + HALF_WIDTH, 4); // Ascending stair skyline.
            case 4 -> y == 0 || (tooth == 0 && y < 4); // Comb, open above the teeth.
            case 5 -> y <= (tooth < 2 ? 2 : 0); // Alternating tall and short battlements.
            case 6 -> y == wave % 4 || y == (wave + 1) % 4 || y == 0; // Diagonal ribbons.
            case 7 -> y == 3 || (a % 4 == 0 && y < 3); // Aqueduct: center or raised side windows.
            case 8 -> y == 1 || y == 2 || (a % 6 == 0 && y == 0); // Low canopy, tall escape door.
            case 9 -> !(y >= 1 && y <= 3 && (tooth == 1 || (y == 2 && tooth == 2))); // Keyholes.
            case 10 -> y <= Math.floorMod(u + 2, 3); // Three-height terraces.
            case 11 -> y == 3 || a % 3 == 0; // Fence arcade, generous floor-level windows.
            case 12 -> y == 1 || y == 2 || a % 4 == 0; // Low beam with upright bypass.
            case 13 -> y == 3 || a % 3 == 0; // Fence wicket plus wide side arch.
            case 14 -> y <= 2 || a % 3 == 0; // Duck or jump, neither route forced.
            case 15 -> y == 0 || y == 3 || a % 4 < 2; // Iron window or low side tunnel.
            case 16 -> true; // A curtain of physically thin chains, not a solid rectangular frame.
            case 17 -> y == 0 || y == 3 || a % 2 == 0; // End-rod lattice.
            case 18 ->
                    !(y == 2 && tooth != 0
                            || y == 1 && tooth == 2
                            || y == 3 && tooth == 2); // Cross windows.
            default -> y == 0 || a % 4 == 0 || y == 4 - a % 4; // Repeating M-shaped skyline.
        };
    }

    static boolean compatible(HoleWallPattern a, HoleWallPattern b) {
        for (Gate first : a.gates())
            for (Gate second : b.gates())
                if (Math.max(first.bottom(), second.bottom())
                                + Math.min(first.bodyHeight(), second.bodyHeight())
                        <= Math.min(
                                        first.bottom() + first.height(),
                                        second.bottom() + second.height())
                                + 1e-6) return true;
        return false;
    }
}
