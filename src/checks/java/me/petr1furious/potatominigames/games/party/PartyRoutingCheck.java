package me.petr1furious.potatominigames.games.party;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import me.petr1furious.potatominigames.core.game.*;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class PartyRoutingCheck {
    public static void main(String[] args){
        Route route=new Route();int[] calls={0};GameSession first=session(calls),second=session(calls);
        Player p=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->new UUID(0,1);case "getName"->"Test";default->throw new AssertionError(m.getName());});
        route.active=first;route.onPlayerMove(new PlayerMoveEvent(p,new Location(null,0,81,0),new Location(null,1,81,0)));check(calls[0]==1,"Routes to current round");
        route.active=null;var between=new PlayerMoveEvent(p,new Location(null,0,81,0),new Location(null,1,81,0));route.onPlayerMove(between);check(between.isCancelled()&&calls[0]==1,"Protects intermission without invoking old round");
        route.active=second;route.onPlayerMove(new PlayerMoveEvent(p,new Location(null,0,81,0),new Location(null,1,81,0)));check(calls[0]==2,"New round receives events");
        // Every GameSession event callback must have an explicit forwarding boundary.
        for(Method method:GameSession.class.getDeclaredMethods())if(method.getParameterCount()==1&&method.getParameterTypes()[0].getSimpleName().endsWith("Event")){
            try{PartyRoundForwarder.class.getDeclaredMethod(method.getName(),method.getParameterTypes());}catch(NoSuchMethodException e){throw new AssertionError("Unrouted event: "+method.getName());}
        }
        System.out.println("Party event routing passed");
    }
    static GameSession session(int[] calls){return new GameSession(){public void onStart(){}public void onPlayerMove(PlayerMoveEvent e){calls[0]++;}};}
    static final class Route extends PartyRoundForwarder {GameSession active;protected GameSession delegate(){return active;}public void onStart(){}}
}
