package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Forced opening kits must survive shuffled player order without changing standard BO3 rules. */
final class PvpDuelKitsCheck {
    static void run() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), other = new UUID(0, 3);
        PvpClass[] all = PvpClass.values();
        Random fixed = new Random(0) {
            @Override public int nextInt(int bound) { throw new AssertionError("Fixed kits must not be rerolled"); }
        };
        check(PvpDuelKits.fromOptions(Map.of()) == null, "Normal PvP keeps its usual random opening");
        check(PvpDuelKits.classList().size() == all.length, "Every class has a numbered command entry");
        for (int first = 1; first <= all.length; first++) for (int second = 1; second <= all.length; second++) {
            PvpDuelKits setup = new PvpDuelKits(first, second, a, b);
            check(PvpDuelKits.fromOptions(setup.options()).equals(setup), "Command options round-trip including player identity");
            check(setup.matches(List.of(b, a)) && !setup.matches(List.of(a, b, other)), "Exactly the intended pair is accepted");
            check(setup.opening(new DuelTournament.Pair(a, b), fixed).equals(List.of(all[first - 1], all[second - 1])), "Numbers assign the expected classes");
            check(setup.opening(new DuelTournament.Pair(b, a), fixed).equals(List.of(all[second - 1], all[first - 1])), "Tournament shuffling never swaps A/B assignments");
        }
        for (int index = 0; index < all.length; index++) {
            int choice = index;
            Random random = new Random(0) {
                @Override public int nextInt(int bound) { check(bound == all.length, "Random means the whole class pool"); return choice; }
            };
            check(new PvpDuelKits(-1, 1, a, b).opening(new DuelTournament.Pair(a, b), random).getFirst() == all[index], "Random A can draw every class");
            check(new PvpDuelKits(1, -1, a, b).opening(new DuelTournament.Pair(a, b), random).getLast() == all[index], "Random B can draw every class");
            check(new PvpDuelKits(-1, -1, a, b).opening(new DuelTournament.Pair(b, a), random).equals(List.of(all[index], all[index])), "Both random slots resolve independently and may match");
        }
        for (int invalid : new int[] {-2, 0, all.length + 1}) {
            expectFailure(() -> new PvpDuelKits(invalid, 1, a, b));
            expectFailure(() -> new PvpDuelKits(1, invalid, a, b));
        }
        expectFailure(() -> new PvpDuelKits(1, 1, a, a));
        expectFailure(() -> new PvpDuelKits(1, 1, a, null));
        PvpDuelKits setup = new PvpDuelKits(1, all.length, a, b);
        expectFailure(() -> setup.opening(new DuelTournament.Pair(a, other), fixed));
        Map<String, String> partial = new HashMap<>(setup.options());
        partial.remove("pvp.classA");
        expectFailure(() -> PvpDuelKits.fromOptions(partial));
        Map<String, String> malformed = new HashMap<>(setup.options());
        malformed.put("pvp.playerA", "not-a-uuid");
        expectFailure(() -> PvpDuelKits.fromOptions(malformed));
        DuelTournament.Match match = new DuelTournament.Match(new DuelTournament.Pair(a, b));
        match.winRound(a); check(!match.isComplete(), "The initial kits do not turn BO3 into one-life FFA");
        match.winRound(b); check(!match.isComplete() && match.roundsWon() == 2, "A tied BO3 still needs its deciding round");
        match.winRound(a); check(match.isComplete() && match.winner().equals(a), "Standard BO3 still ends at two wins");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid duel configuration accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
