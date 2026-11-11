package me.petr1furious.potatominigames.games.finals;

import java.util.random.RandomGenerator;

/** Uniform choice among every candidate except the previous vault, when alternatives exist. */
final class FinalsVaultLocations {
    static int nextIndex(int count, int previous, RandomGenerator random) {
        if (count < 1 || previous < -1 || previous >= count)
            throw new IllegalArgumentException(
                    "At least one vault and a valid previous index required");
        if (count == 1) return 0;
        if (previous == -1) return random.nextInt(count);
        int choice = random.nextInt(count - 1);
        return choice >= previous ? choice + 1 : choice;
    }

    private FinalsVaultLocations() {}
}
