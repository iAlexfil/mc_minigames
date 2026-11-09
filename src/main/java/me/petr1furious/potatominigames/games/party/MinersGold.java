package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Each ore rolls one or two extra hearts; reaching the cap cannot generate repeatable healing. */
final class MinersGold {
    static final int MAX_BONUS_HEARTS = 4;
    private final Map<UUID, Integer> hearts = new HashMap<>();
    int bonus(UUID player) { return hearts.getOrDefault(player, 0); }
    static int roll(Random random) { return random.nextInt(100) < 20 ? 2 : 1; }
    int collect(UUID player, Random random) {
        int current = bonus(player);
        if (current >= MAX_BONUS_HEARTS) return 0;
        int added = Math.min(roll(random), MAX_BONUS_HEARTS - current);
        hearts.put(player, current + added);
        return added;
    }
    void clear() { hearts.clear(); }
}
