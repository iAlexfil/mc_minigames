package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.Material;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class BuildMemoryCheck {
    public static void main(String[] args) {
        for(int stage=1;stage<=8;stage++) for(int seed=0;seed<50;seed++) {
            var p=BuildMemoryPattern.generate(stage,new Random(seed));
            check(p.blocks().size()==5+2*(stage-1),"Exact increasing block count");
            check(p.inventory().values().stream().mapToInt(Integer::intValue).sum()==p.blocks().size(),"Exact kit");
            check(p.rotate(1).rotate(3).blocks().equals(p.blocks()),"Rotation round trip");
            for(var c:p.blocks().keySet()) {check(c.x()>=0&&c.x()<5&&c.z()>=0&&c.z()<5&&c.y()>=0&&c.y()<4,"Bounds");
                if(c.y()>0)check(p.blocks().containsKey(new BuildMemoryPattern.Cell(c.x(),c.y()-1,c.z())),"No scaffolding required");}
            var actual=new HashMap<>(p.blocks());check(p.matches(actual),"Exact succeeds");
            var first=actual.keySet().iterator().next();actual.remove(first);check(!p.matches(actual),"One missing fails");
            actual.put(first,Material.BEDROCK);check(!p.matches(actual),"One wrong fails");
        }
        System.out.println("Build memory passed");
    }
}
