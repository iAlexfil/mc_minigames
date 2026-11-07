package me.petr1furious.potatominigames.games.party;

import java.util.*;

public final class PartyMatchScore {
    private final Map<UUID,int[]> scores = new LinkedHashMap<>();
    private final Set<Integer> recorded = new HashSet<>();
    public PartyMatchScore(Collection<UUID> roster) { roster.forEach(id -> scores.put(id, new int[3])); }
    public boolean record(int roundIndex, PartyRoundResult result) {
        if (roundIndex < 0 || !scores.keySet().containsAll(result.awards().keySet())) throw new IllegalArgumentException("Invalid round");
        if (!recorded.add(roundIndex)) return false;
        result.awards().forEach((id, points) -> {
            int[] s = scores.get(id); s[0] += points;
            if (result.place(id) == 1) s[1]++;
            if (result.place(id) == 2) s[2]++;
        });
        return true;
    }
    public int points(UUID id) { return scores.containsKey(id) ? scores.get(id)[0] : 0; }
    public List<List<UUID>> standings() {
        return PartyRoundResult.ordered(scores.keySet(), (a,b) -> {
            for (int i=0;i<3;i++) { int c = Integer.compare(scores.get(b)[i], scores.get(a)[i]); if(c!=0)return c; }
            return 0;
        }).places();
    }
}
