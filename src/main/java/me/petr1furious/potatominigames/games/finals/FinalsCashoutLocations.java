package me.petr1furious.potatominigames.games.finals;

import java.util.random.RandomGenerator;

/** Keeps two distinct cashout locations active and chooses replacements from inactive spots. */
final class FinalsCashoutLocations {
    static int[] initial(int count, RandomGenerator random) {
        if (count < 2)
            throw new IllegalArgumentException("At least two cashout locations required");
        if (count == 2) return new int[] {0, 1};
        int first = random.nextInt(count);
        int second = random.nextInt(count - 1);
        return new int[] {first, second >= first ? second + 1 : second};
    }

    static int nextIndex(int count, int completed, int other, RandomGenerator random) {
        if (count < 2
                || completed < 0
                || other < 0
                || completed >= count
                || other >= count
                || completed == other)
            throw new IllegalArgumentException("Two distinct active cashout locations required");
        if (count == 2) return completed;
        int choice = random.nextInt(count - 2);
        for (int index = 0; index < count; index++) {
            if (index == completed || index == other) continue;
            if (choice-- == 0) return index;
        }
        throw new AssertionError("Inactive cashout candidate missing");
    }

    private FinalsCashoutLocations() {}
}
