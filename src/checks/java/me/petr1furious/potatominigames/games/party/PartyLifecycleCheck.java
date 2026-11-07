package me.petr1furious.potatominigames.games.party;
import java.util.*;
import me.petr1furious.potatominigames.core.game.EndReason;
public final class PartyLifecycleCheck {
    public static void main(String[] args) {
        int[] calls={0};
        PartyRoundContext a = new PartyRoundContext(null,List.of(),PartyRoundOptions.party(),(reason,result)->calls[0]++);
        PartyRoundContext b = new PartyRoundContext(null,List.of(),PartyRoundOptions.party(),(reason,result)->calls[0]++);
        PartyScoringCheck.check(!a.worldId("tnt").equals(b.worldId("tnt")), "Parallel worlds isolated");
        a.finish(EndReason.FINISHED,PartyRoundResult.empty()); a.finish(EndReason.ABORTED,PartyRoundResult.empty());
        PartyScoringCheck.check(calls[0]==1,"Result callback once, including late errors");
        PartyScoringCheck.check(a.ended() && !b.ended(),"Separate lifecycles");
        System.out.println("Party lifecycle passed");
    }
}
