package me.petr1furious.potatominigames.games.party;
import java.util.*;
import java.lang.reflect.Proxy;
import org.bukkit.*;
import org.bukkit.entity.Player;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class PartyFfaCheck {
    public static void main(String[] args){
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->switch(m.getName()){case "equals"->o==a[0];case "hashCode"->1;default->throw new AssertionError(m.getName());});
        for(DuelArena arena:List.of(new SumoArena(world),new PvpArena(world)))for(int count:List.of(3,12)){
            Set<String> unique=new HashSet<>();for(int i=0;i<count;i++){Location s=arena.spawn(i,count);unique.add(s.getX()+":"+s.getZ());check(s.getY()==81,"Safe spawn floor");}
            check(unique.size()==count,"All fighters have distinct spawn");
        }
        Player caster=player(world,0,0,true), near=player(world,0,3,true), side=player(world,3,1,true), hidden=player(world,0,2,false);
        check(PartyTargeting.select(caster,List.of(side,near),6)==near,"Aim beats collection order");
        check(PartyTargeting.select(caster,List.of(side),2)==null,"Range enforced");
        check(PartyTargeting.select(caster,List.of(hidden),6)==null,"Occluded target rejected");
        System.out.println("Party FFA spawns and targeting passed");
    }
    static Player player(World world,double x,double z,boolean visible){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
        case "getLocation","getEyeLocation"->new Location(world,x,81,z,0,0);
        case "getWorld"->world;case "getGameMode"->visible?GameMode.SURVIVAL:GameMode.SPECTATOR;
        case "equals"->o==a[0];case "hasLineOfSight"->((Player)a[0]).getGameMode()==GameMode.SURVIVAL;
        default->throw new AssertionError(m.getName());});}
}
