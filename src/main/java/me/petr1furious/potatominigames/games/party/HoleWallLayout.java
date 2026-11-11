package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import java.util.*;

/** Nested, fixed floor masks: shrinking only removes blocks. */
enum HoleWallLayout {
    PLUS,
    SNAKE,
    DONUT;

    static HoleWallLayout choose(Random random) {
        return values()[random.nextInt(values().length)];
    }

    Component description() {
        return I18n.tr("party.rounds.hole_wall.layout." + name().toLowerCase(Locale.ROOT));
    }

    int patternCount() {
        return this == PLUS ? 20 : 12;
    }

    boolean onFloor(int x, int z, int radius, int armRadius) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (this == PLUS && ax >= 5 && ax <= 6 && az >= 5 && az <= 6) return true;
        if (ax > radius || az > radius) return false;
        return switch (this) {
            case PLUS -> ax <= armRadius || az <= armRadius;
            case DONUT -> Math.max(ax, az) >= 3 && ax + az <= radius * 2 - 2;
            case SNAKE ->
                    (z >= -5 && z <= -3 && x <= 5)
                            || (az <= 1 && ax <= 5)
                            || (z >= 3 && z <= 5 && x >= -5)
                            || (x >= 3 && x <= 5 && z >= -5 && z <= 1)
                            || (x >= -5 && x <= -3 && z >= -1 && z <= 5);
        };
    }

    boolean supports(double x, double z, int radius, int armRadius) {
        for (double dx : new double[] {-.299, .299})
            for (double dz : new double[] {-.299, .299})
                if (!onFloor((int) Math.floor(x + dx), (int) Math.floor(z + dz), radius, armRadius))
                    return false;
        return true;
    }

    List<Integer> shuffledStages(Random random, boolean introductory) {
        if (this == PLUS) return HoleWallPattern.shuffledStages(random, introductory);
        var result = new ArrayList<Integer>();
        for (int stage = 1; stage <= patternCount(); stage++) result.add(stage);
        Collections.shuffle(result, random);
        if (introductory) {
            var easy = result.stream().filter(s -> s <= 5).toList();
            var middle = result.stream().filter(s -> s > 5 && s <= 10).toList();
            result.removeAll(easy);
            result.removeAll(middle);
            result.addAll(0, middle);
            result.addAll(0, easy);
        }
        return result;
    }

    HoleWallPattern pattern(int stage, int armRadius, Random random) {
        return this == PLUS
                ? HoleWallPattern.generate(stage, armRadius, random)
                : HoleWallMapPatterns.generate(this, stage, random);
    }

    boolean crossing(
            HoleWallPattern a,
            HoleWallPhysics.Direction directionA,
            HoleWallPattern b,
            HoleWallPhysics.Direction directionB) {
        if (directionA.dx * directionB.dx + directionA.dz * directionB.dz != 0)
            return HoleWallPattern.compatible(a, b);
        for (var ga : a.gates())
            for (var gb : b.gates()) {
                if (Math.max(ga.bottom(), gb.bottom()) + Math.min(ga.bodyHeight(), gb.bodyHeight())
                        > Math.min(ga.bottom() + ga.height(), gb.bottom() + gb.height()) + 1e-6)
                    continue;
                for (double u = ga.start() + .3; u <= ga.start() + ga.width() - .3 + 1e-6; u += .1)
                    for (double v = gb.start() + .3;
                            v <= gb.start() + gb.width() - .3 + 1e-6;
                            v += .1)
                        if (supports(directionA.dx == 0 ? u : v, directionA.dx == 0 ? v : u, 4, 1))
                            return true;
            }
        return false;
    }
}
