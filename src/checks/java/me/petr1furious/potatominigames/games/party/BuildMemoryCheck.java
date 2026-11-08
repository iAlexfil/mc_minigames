package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.Material;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class BuildMemoryCheck {
    public static void main(String[] args) {
        for(int stage=1;stage<=8;stage++) for(int seed=0;seed<50;seed++) {
            var p=BuildMemoryPattern.generate(stage,new Random(seed));
            check(p.blocks().size()==9+2*(stage-1),"Substantial starting model and increasing exact block count");
            check(p.blocks().keySet().stream().anyMatch(c->c.y()>=2),"Every stage starts with a three-dimensional model");
            check(!p.title().isBlank()&&p.inventory().size()<=3,"Named model with coherent thematic materials");
            check(p.inventory().values().stream().mapToInt(Integer::intValue).sum()==p.blocks().size(),"Exact kit");
            check(p.rotate(1).rotate(3).blocks().equals(p.blocks()),"Rotation round trip");
            for(var c:p.blocks().keySet()) {check(c.x()>=0&&c.x()<5&&c.z()>=0&&c.z()<5&&c.y()>=0&&c.y()<4,"Bounds");
            }
            Set<BuildMemoryPattern.Cell> reachable=new HashSet<>();p.blocks().keySet().stream().filter(c->c.y()==0).forEach(reachable::add);
            boolean changed;do{changed=false;for(var c:p.blocks().keySet())if(!reachable.contains(c)&&reachable.stream().anyMatch(v->Math.abs(c.x()-v.x())+Math.abs(c.y()-v.y())+Math.abs(c.z()-v.z())==1)){reachable.add(c);changed=true;}}while(changed);
            check(reachable.size()==p.blocks().size(),"Can place every block from the floor without extra scaffolding");
            var actual=new HashMap<>(p.blocks());check(p.matches(actual),"Exact succeeds");
            var first=actual.keySet().iterator().next();actual.remove(first);check(!p.matches(actual),"One missing fails");
            actual.put(first,Material.BEDROCK);check(!p.matches(actual),"One wrong fails");
        }
        System.out.println("Build memory passed");
    }
}
