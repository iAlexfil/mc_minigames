package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sequential checkpoints, attempt rollback and a continuously running race clock. */
final class ParkourRace {
    private static final class Runner {
        int checkpoint, distance;
        long finish = -1;
    }

    private final Map<UUID, Runner> runners = new LinkedHashMap<>();
    private final int stages;
    private boolean closed;

    ParkourRace(Collection<UUID> roster, int stages) {
        if (stages < 1
                || roster.isEmpty()
                || roster.size() > 12
                || Set.copyOf(roster).size() != roster.size())
            throw new IllegalArgumentException("Invalid race roster/course");
        this.stages = stages;
        for (UUID id : roster) runners.put(id, new Runner());
    }

    int checkpoint(UUID id) {
        Runner r = runners.get(id);
        return r == null ? -1 : r.checkpoint;
    }

    boolean finished(UUID id) {
        Runner r = runners.get(id);
        return r != null && r.finish >= 0;
    }

    long time(UUID id) {
        return finished(id) ? runners.get(id).finish : -1;
    }

    boolean reach(UUID id, int next, long elapsed) {
        Runner r = runners.get(id);
        if (closed
                || r == null
                || r.finish >= 0
                || next != r.checkpoint + 1
                || next > stages
                || elapsed < 0) return false;
        r.checkpoint = next;
        r.distance = 0;
        if (next == stages) r.finish = elapsed;
        return true;
    }

    void position(UUID id, double distance) {
        Runner r = runners.get(id);
        if (!closed && r != null && r.finish < 0 && Double.isFinite(distance))
            r.distance = (int) Math.max(0, Math.min(10000, distance * 100));
    }

    boolean reset(UUID id) {
        Runner r = runners.get(id);
        if (closed || r == null || r.finish >= 0) return false;
        r.distance = 0;
        return true;
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return PartyRoundResult.ordered(
                runners.keySet().stream().filter(eligible::contains).toList(),
                Comparator.<UUID, Boolean>comparing(id -> !finished(id))
                        .thenComparingLong(id -> finished(id) ? time(id) : Long.MAX_VALUE)
                        .thenComparing(Comparator.<UUID>comparingInt(this::checkpoint).reversed())
                        .thenComparing(
                                Comparator.<UUID>comparingInt(id -> runners.get(id).distance)
                                        .reversed()));
    }

    void close() {
        closed = true;
    }
}
