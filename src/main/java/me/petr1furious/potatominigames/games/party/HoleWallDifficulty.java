package me.petr1furious.potatominigames.games.party;

/** Authored challenge tiers, unlocked in successive thirds of a round. */
enum HoleWallDifficulty {
    EASY,
    NORMAL,
    HARD;

    static HoleWallDifficulty at(long tick, long duration) {
        return tick >= duration * 2 / 3 ? HARD : tick >= duration / 3 ? NORMAL : EASY;
    }
}
