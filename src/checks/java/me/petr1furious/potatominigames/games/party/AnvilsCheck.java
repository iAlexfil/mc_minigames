package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.util.BoundingBox;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class AnvilsCheck {
    public static void main(String[] args) {
        AnvilRound r=new AnvilRound(12); r.start(0);
        check(!r.raining() && r.shelters()==14,"Move before first rain");
        r.advance(79); check(!r.raining(),"Full four seconds");
        r.advance(80); check(r.raining(),"Rain begins");
        r.advance(320); check(!r.raining() && r.wave()==2 && r.shelters()==13,"Next wave relocates after rain");
        r.advance(400);r.advance(640);r.advance(720); check(!r.pvpEnabled(),"No PvP through third rain");
        r.advance(960);check(r.pvpEnabled(),"PvP from fourth wave");
        var a=AnvilArena.layout(8,new Random(1));var b=AnvilArena.layout(7,new Random(2));
        check(!a.containsAll(b),"New shelter layout is not just removed old shelter");
        BoundingBox slab=new BoundingBox(0,83,0,1,83.5,1), body=new BoundingBox(.2,81,.2,.8,82.8,.8);
        check(AnvilRain.impact(.5,.5,90,80,slab)>AnvilRain.impact(.5,.5,90,80,body),"Slab intercepts before player");
        check(Double.isNaN(AnvilRain.impact(2,.5,90,80,slab)),"No imaginary radius shelter");
        check(AnvilRain.impact(.5,.5,84,81,body)>0,"Swept collision cannot tunnel through body");
        System.out.println("Anvil waves passed");
    }
}
