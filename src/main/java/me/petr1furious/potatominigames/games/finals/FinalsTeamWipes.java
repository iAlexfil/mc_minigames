package me.petr1furious.potatominigames.games.finals;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Three actual deaths form one immutable respawn cohort; reconnect delays are not deaths. */
final class FinalsTeamWipes {
    record Wave(int team, Set<UUID> members, long deadline) {
        Wave { members = Set.copyOf(members); }
    }

    private final Set<UUID> dead = new HashSet<>();
    private final Map<Integer, Wave> waves = new LinkedHashMap<>();

    Wave death(UUID player, long now, Map<UUID, Integer> roster, Set<UUID> withdrawn) {
        Integer team = roster.get(player);
        if (team == null || withdrawn.contains(player) || !dead.add(player) || waves.containsKey(team))
            return null;
        Set<UUID> members = new HashSet<>();
        roster.forEach((id, assigned) -> {
            if (assigned.equals(team) && !withdrawn.contains(id)) members.add(id);
        });
        if (members.size() != 3 || !dead.containsAll(members)) return null;
        Wave wave = new Wave(team, members, now > Long.MAX_VALUE - 320 ? Long.MAX_VALUE : now + 320);
        waves.put(team, wave);
        return wave;
    }

    boolean dead(UUID player) { return dead.contains(player); }

    Wave wave(UUID player) {
        return waves.values().stream().filter(wave -> wave.members().contains(player)).findFirst().orElse(null);
    }

    boolean locked(UUID player, long now) {
        Wave wave = wave(player);
        return wave != null && now < wave.deadline();
    }

    List<Wave> due(long now) {
        return waves.values().stream().filter(wave -> now >= wave.deadline()).toList();
    }

    void release(Wave wave) {
        if (waves.remove(wave.team(), wave)) dead.removeAll(wave.members());
    }

    void spawn(UUID player) { dead.remove(player); }

    void withdraw(UUID player) { dead.remove(player); }

    void clear() { dead.clear(); waves.clear(); }
}
