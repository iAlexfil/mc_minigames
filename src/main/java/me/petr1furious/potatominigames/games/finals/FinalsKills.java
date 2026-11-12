package me.petr1furious.potatominigames.games.finals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Match-local enemy death credit; reconnects and new lives never erase the killer's total. */
final class FinalsKills {
    private final Map<UUID, Integer> counts = new HashMap<>();
    private final Set<UUID> dead = new HashSet<>();

    boolean death(UUID victim, UUID killer, Map<UUID, Integer> teams) {
        if (victim == null || !teams.containsKey(victim) || !dead.add(victim)) return false;
        if (killer == null || killer.equals(victim) || !teams.containsKey(killer)
                || teams.get(victim).equals(teams.get(killer))) return false;
        counts.merge(killer, 1, (current, increment) ->
                current == Integer.MAX_VALUE ? current : current + increment);
        return true;
    }

    void spawn(UUID player) {
        dead.remove(player);
    }

    Map<UUID, Integer> snapshot() {
        return Map.copyOf(counts);
    }
}
