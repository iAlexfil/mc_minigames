package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import java.util.*;

final class PartyGamesState {
    static final List<String> MODES =
            List.of(
                    "pvp",
                    "sumo",
                    "arms_race",
                    "tnt_run",
                    "mob_count",
                    "rhino",
                    "miners",
                    "color_floor",
                    "anvils",
                    "build_memory",
                    "tool_race",
                    "hole_in_the_wall");
    private final List<String> schedule;
    private final Set<UUID> roster,
            connected,
            withdrawn = new HashSet<>(),
            disqualified = new HashSet<>();
    private final PartyMatchScore score;
    private int generation = -1;
    private boolean closed = true;

    PartyGamesState(List<UUID> roster, List<String> modes, Random random) {
        if (roster.isEmpty() || roster.size() > 12 || new HashSet<>(roster).size() != roster.size())
            throw I18n.exception("party.rounds.party_games.error.roster");
        if (modes.isEmpty()
                || !MODES.containsAll(modes)
                || new HashSet<>(modes).size() != modes.size())
            throw I18n.exception("party.rounds.party_games.error.modes");
        List<String> shuffled = new ArrayList<>(modes);
        Collections.shuffle(shuffled, random);
        schedule = List.copyOf(shuffled);
        this.roster = new LinkedHashSet<>(roster);
        connected = new LinkedHashSet<>(roster);
        score = new PartyMatchScore(roster);
    }

    List<String> schedule() {
        return schedule;
    }

    PartyMatchScore score() {
        return score;
    }

    void beginRound(int generation) {
        if (!closed || generation != this.generation + 1 || generation >= schedule.size())
            throw new IllegalStateException("Invalid round transition");
        this.generation = generation;
        closed = false;
        disqualified.clear();
        for (UUID id : roster) if (!connected.contains(id)) disqualified.add(id);
    }

    boolean acceptResult(int generation, PartyRoundResult result) {
        if (closed || this.generation != generation) return false;
        Set<UUID> eligible = new LinkedHashSet<>(nextRoster());
        eligible.removeAll(disqualified);
        closed = true;
        return score.record(generation, result.eligible(eligible));
    }

    void disconnect(UUID id) {
        connected.remove(id);
        if (!closed) disqualified.add(id);
    }

    void reconnect(UUID id) {
        if (roster.contains(id) && !withdrawn.contains(id)) connected.add(id);
    }

    void withdraw(UUID id) {
        withdrawn.add(id);
        disconnect(id);
    }

    Set<UUID> nextRoster() {
        Set<UUID> result = new LinkedHashSet<>(connected);
        result.removeAll(withdrawn);
        return Collections.unmodifiableSet(result);
    }
}
