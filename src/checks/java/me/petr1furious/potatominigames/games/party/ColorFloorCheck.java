package me.petr1furious.potatominigames.games.party;
import java.util.*;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class ColorFloorCheck {
    public static void main(String[] args) {
        check(ColorFloorRound.choiceTicks(1)==120 && ColorFloorRound.choiceTicks(11)==40 && ColorFloorRound.choiceTicks(100)==40,"Bounded timings");
        for(int seed=0;seed<100;seed++) {
            var r=ColorFloorRound.generate(1,new Random(seed));
            for(int bx=0;bx<21;bx+=3) for(int bz=0;bz<21;bz+=3) {
                boolean safe=false;
                for(int x=bx;x<bx+3;x++) for(int z=bz;z<bz+3;z++) if(r.material(x,z)==r.target()) {
                    safe=true; check(r.safe(x+.5,82,z+.5),"Jump above target safe");
                }
                check(safe,"Every neighborhood has target");
            }
            check(!r.safe(-1,81,0) && !r.safe(0,90,0),"Outside arena/height unsafe");
        }
        System.out.println("Color floor passed");
    }
}
