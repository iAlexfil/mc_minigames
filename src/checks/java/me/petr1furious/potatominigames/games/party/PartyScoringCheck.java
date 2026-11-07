package me.petr1furious.potatominigames.games.party;

import java.util.*;

public final class PartyScoringCheck {
    public static void main(String[] args) {
        UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3), d = new UUID(0,4);
        PartyRoundResult tied = new PartyRoundResult(List.of(List.of(a), List.of(b,c), List.of(d)));
        check(tied.awards().equals(Map.of(a,3,b,2,c,2,d,0)), "1,2,2,4 prizes");
        check(new PartyRoundResult(List.of(List.of(a,b),List.of(c))).awards().equals(Map.of(a,3,b,3,c,1)), "1,1,3 prizes");
        check(tied.eligible(Set.of(b,c,d)).place(d)==3, "Remove ineligible before ranking");
        try { new PartyRoundResult(List.of(List.of(a),List.of(a))); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalArgumentException expected) { }
        PartyMatchScore score = new PartyMatchScore(List.of(a,b,c,d));
        check(score.record(0,tied) && !score.record(0,tied), "Round recorded once");
        check(score.points(a)==3 && score.points(d)==0, "No duplicate awards");
        score.record(1,new PartyRoundResult(List.of(List.of(b,c),List.of(a))));
        check(score.standings().getFirst().equals(List.of(b,c)), "Exact match ties preserved");
        SurvivalStandings survival = new SurvivalStandings(List.of(a,b,c,d));
        survival.eliminate(d,5); survival.eliminate(b,10); survival.eliminate(c,10);
        check(survival.result(List.of(a,b,c,d),20).places().equals(tied.places()), "Survival ties use tick");
        survival.eliminate(a,10);
        check(survival.result(List.of(a,b,c,d),20).places().getFirst().size()==3, "Simultaneous final deaths tie");
        System.out.println("Party scoring passed");
    }
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
