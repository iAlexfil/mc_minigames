package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Immutable competition ranking; each group occupies as many positions as it has players. */
public record PartyRoundResult(List<List<UUID>> places) {
    public PartyRoundResult {
        Set<UUID> seen = new HashSet<>();
        List<List<UUID>> copy = new ArrayList<>();
        for (List<UUID> group : places) {
            if (group.isEmpty()) throw new IllegalArgumentException("Empty place");
            for (UUID id : group) if (id == null || !seen.add(id)) throw new IllegalArgumentException("Duplicate/null participant");
            copy.add(List.copyOf(group));
        }
        places = List.copyOf(copy);
    }
    public static PartyRoundResult empty() { return new PartyRoundResult(List.of()); }
    public int place(UUID id) {
        int place = 1;
        for (var group : places) { if (group.contains(id)) return place; place += group.size(); }
        return 0;
    }
    public Map<UUID,Integer> awards() {
        Map<UUID,Integer> awards = new LinkedHashMap<>();
        int place = 1;
        for (var group : places) {
            int points = Math.max(0, 4 - place);
            group.forEach(id -> awards.put(id, points));
            place += group.size();
        }
        return Collections.unmodifiableMap(awards);
    }
    public PartyRoundResult eligible(Set<UUID> eligible) {
        return new PartyRoundResult(places.stream().map(g -> g.stream().filter(eligible::contains).toList())
                .filter(g -> !g.isEmpty()).toList());
    }
    static PartyRoundResult ordered(Collection<UUID> roster, Comparator<UUID> order) {
        List<UUID> sorted = roster.stream().sorted(order).toList();
        List<List<UUID>> groups = new ArrayList<>();
        for (UUID id : sorted) {
            if (groups.isEmpty() || order.compare(groups.getLast().getFirst(), id) != 0) groups.add(new ArrayList<>());
            groups.getLast().add(id);
        }
        return new PartyRoundResult(groups);
    }
}
