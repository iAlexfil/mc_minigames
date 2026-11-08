package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import java.util.*;

record HoleWallPattern(List<Cell> cells, List<Gate> gates, Component title) {
    static final int HALF_WIDTH = 13, HEIGHT = 5;

    record Cell(int u, int y) {}

    record Gate(int start, int width, int bottom, int height) {
        boolean contains(int u, int y) {
            return u >= start && u < start + width && y >= bottom && y < bottom + height;
        }
    }

    HoleWallPattern {
        cells = List.copyOf(cells);
        gates = List.copyOf(gates);
    }

    static HoleWallPattern generate(int stage, int radius, Random random) {
        List<Gate> gates = new ArrayList<>();
        int kind = Math.floorMod(stage - 1, 10);
        int sideWidth = Math.min(2, radius);
        Component title;
        switch (kind) {
            case 0 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.arch");
                gates.add(sideGate(3, radius, random));
            }
            case 1 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.double_gate");
                gates.add(new Gate(-radius, sideWidth, 0, 3));
                gates.add(new Gate(radius - sideWidth + 1, sideWidth, 0, 3));
            }
            case 2 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.barrier");
                gates.add(gate(3, 1, 3, radius, random));
            }
            case 3 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.steps");
                gates.add(new Gate(-radius, sideWidth, 0, 3));
                gates.add(new Gate(radius - sideWidth + 1, sideWidth, 1, 3));
            }
            case 4 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.columns");
                gates.add(sideGate(1, radius, random));
            }
            case 5 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.battlements");
                gates.add(gate(2, 1, 3, radius, random));
            }
            case 6 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.zigzag");
                gates.add(sideGate(2, radius, random));
            }
            case 7 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.aqueduct");
                gates.add(new Gate(-radius, sideWidth, 0, 3));
                gates.add(new Gate(radius - sideWidth + 1, sideWidth, 0, 3));
            }
            case 8 -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.canopy");
                gates.add(sideGate(2, radius, random));
            }
            default -> {
                title = I18n.tr("party.rounds.hole_wall.pattern.keyhole");
                gates.add(sideGate(1, radius, random));
            }
        }
        List<Cell> cells = new ArrayList<>();
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++)
            for (int y = 0; y < HEIGHT; y++) {
                boolean hole = false;
                for (Gate gate : gates)
                    if (gate.contains(u, y)) {
                        hole = true;
                        break;
                    }
                // Silhouettes vary as well as passages. Every advertised gate overrides the
                // outline.
                boolean solid =
                        switch (kind) {
                            case 0 -> y < 4 || Math.abs(u) <= 4;
                            case 2 -> y == 0 || (Math.abs(u) >= 5 && Math.abs(u) % 4 == 1 && y < 4);
                            case 3 -> y <= Math.floorMod(u + HALF_WIDTH, 5);
                            case 4 -> y < 4 && (Math.floorMod(u, 3) != 0 || y == 0);
                            case 5 -> y <= (Math.floorMod(u, 2) == 0 ? 3 : 1);
                            case 6 -> y < 4 && !(y >= 2 && Math.floorMod(u + y, 5) <= 1);
                            case 7 -> y == 3 || Math.floorMod(u, 4) == 0;
                            case 8 ->
                                    y == 2
                                            || y == 3
                                            || (Math.abs(u) >= 4 && Math.floorMod(u, 4) == 0);
                            case 9 -> !(y >= 2 && y <= 3 && Math.abs(u) <= 2);
                            default -> true;
                        };
                // Silhouette cutouts must not create an idle-safe center. Explicit jump windows
                // may open its upper cell, but a bottom hurdle always remains under those windows.
                if (!hole && (solid || (u == 0 && y < 2))) cells.add(new Cell(u, y));
            }
        return new HoleWallPattern(cells, gates, title);
    }

    private static Gate sideGate(int width, int radius, Random random) {
        width = Math.min(width, radius);
        int offset = random.nextInt(radius - width + 1);
        return new Gate(random.nextBoolean() ? -radius + offset : 1 + offset, width, 0, 3);
    }

    private static Gate gate(int width, int bottom, int height, int radius, Random random) {
        // Keep landing margins where possible, but a three-wide arm also accepts a full-width gate.
        width = Math.min(width, 2 * radius + 1);
        int padding = 2 * radius + 1 - width >= 2 ? 1 : 0;
        int min = -radius + padding, max = radius + 1 - width - padding;
        return new Gate(min + random.nextInt(max - min + 1), width, bottom, height);
    }
}
