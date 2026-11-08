package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import java.util.*;

public final class ToolRaceCheck {
    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        var blocks = ToolRaceTrack.generate(new Random(4));
        check(blocks.size() == 50, "Exactly fifty blocks");
        var counts = new HashMap<Object, Integer>();
        blocks.forEach(m -> counts.merge(m, 1, Integer::sum));
        check(
                Collections.max(counts.values()) - Collections.min(counts.values()) <= 1,
                "Balanced categories");
        var track = new ToolRaceTrack(List.of(a, b, c), blocks);
        check(track.result(Set.of(a, b, c)).place(b) == 1, "Initially tied for first");
        check(
                track.mine(a, 0, 1) && !track.mine(a, 0, 2) && !track.mine(a, 2, 2),
                "Sequential own column");
        check(
                track.result(Set.of(a, b, c)).place(a) == 1
                        && track.result(Set.of(a, b, c)).place(b) == 2,
                "Live place follows progress");
        check(track.result(Set.of(b, c)).place(b) == 1, "Withdrawn players do not occupy places");
        for (int depth = 1; depth < 50; depth++)
            check(track.mine(a, depth, depth + 1), "Valid next block");
        check(track.finished(a) && !track.mine(a, 50, 100), "Finish at fifty");
        check(
                track.result(Set.of(a, b, c)).places().equals(List.of(List.of(a), List.of(b, c))),
                "Finish beats progress, ties preserved");
        track.close();
        check(!track.mine(b, 0, 101), "No mining after timeout");
        System.out.println("Tool race passed");
    }
}
