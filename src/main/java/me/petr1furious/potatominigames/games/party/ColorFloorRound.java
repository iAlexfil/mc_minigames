package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.*;

final class ColorFloorRound {
    static final Material[] COLORS = {
        Material.RED_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.LIME_CONCRETE,
        Material.ORANGE_CONCRETE,
        Material.PURPLE_CONCRETE
    };
    static final String[] TITLES = {
        "red", "blue", "yellow", "lime", "orange", "purple"
    };
    private final Material[][] cells = new Material[21][21];
    private final int target;

    private ColorFloorRound(int target) {
        this.target = target;
    }

    static ColorFloorRound generate(int stage, Random random) {
        var r = new ColorFloorRound(random.nextInt(COLORS.length));
        var palette = new ArrayList<>(List.of(COLORS));
        Collections.shuffle(palette, random);
        // Unequal rectangles merge into larger patches; two colors appear only in small islands.
        for (int z = 0; z < 21; ) {
            int height = 2 + random.nextInt(4);
            for (int x = 0; x < 21; ) {
                int width = 2 + random.nextInt(5), draw = random.nextInt(11);
                r.paint(
                        x,
                        z,
                        width,
                        height,
                        palette.get(draw < 5 ? 0 : draw < 8 ? 1 : draw < 10 ? 2 : 3));
                x += width;
            }
            z += height;
        }
        for (int i = 0; i < 4; i++)
            r.paint(i % 2 == 0 ? 1 : 17, i < 2 ? 1 : 17, 3, 3, palette.get(i));
        r.paint(7, 7, 6, 6, palette.getFirst());
        for (int i = 0; i < 4; i++)
            r.paint(
                    i % 2 == 0 ? 3 : 16,
                    i < 2 ? 3 : 16,
                    1 + random.nextInt(2),
                    1 + random.nextInt(2),
                    palette.get(i == 0 || i == 3 ? 4 : 5));
        // Repair only unreachable gaps instead of placing a target in every 3x3 square.
        // Eight blocks leaves reaction time even at the two-second lower limit.
        for (int attempts = 0; attempts < 441; attempts++) {
            int farX = -1, farZ = -1;
            double farthest = 64;
            for (int x = 0; x < 21; x++)
                for (int z = 0; z < 21; z++) {
                    double nearest = Double.POSITIVE_INFINITY;
                    for (int tx = 0; tx < 21; tx++)
                        for (int tz = 0; tz < 21; tz++)
                            if (r.cells[tx][tz] == r.target())
                                nearest =
                                        Math.min(
                                                nearest, (tx - x) * (tx - x) + (tz - z) * (tz - z));
                    if (nearest > farthest) {
                        farthest = nearest;
                        farX = x;
                        farZ = z;
                    }
                }
            if (farX < 0) break;
            int px = -1, pz = -1;
            double distance = Double.POSITIVE_INFINITY;
            for (int x = 0; x < 21; x++)
                for (int z = 0; z < 21; z++)
                    if (!palette.subList(4, 6).contains(r.cells[x][z])) {
                        double d = (x - farX) * (x - farX) + (z - farZ) * (z - farZ);
                        if (d < distance) {
                            distance = d;
                            px = x;
                            pz = z;
                        }
                    }
            for (int x = px; x <= Math.min(20, px + 1); x++)
                for (int z = pz; z <= Math.min(20, pz + 1); z++)
                    if (!palette.subList(4, 6).contains(r.cells[x][z])) r.cells[x][z] = r.target();
        }
        return r;
    }

    private void paint(int x, int z, int width, int height, Material material) {
        for (int dx = x; dx < Math.min(21, x + width); dx++)
            for (int dz = z; dz < Math.min(21, z + height); dz++) cells[dx][dz] = material;
    }

    static int choiceTicks(int stage) {
        return (int) Math.max(40, 120 - 8L * (Math.max(1, stage) - 1));
    }

    Material target() {
        return COLORS[target];
    }

    Component title() {
        return I18n.tr("party.rounds.color_floor.color." + TITLES[target]);
    }

    Material material(int x, int z) {
        return cells[x][z];
    }

    boolean safe(double x, double y, double z) {
        return x >= 0
                && x < 21
                && z >= 0
                && z < 21
                && y >= 81
                && y <= 83
                && cells[(int) x][(int) z] == target();
    }
}
