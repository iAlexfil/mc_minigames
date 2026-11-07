package me.petr1furious.potatominigames.games.party;
import java.util.Collection;
import org.bukkit.entity.Player;
final class PartyTargeting {
    static Player select(Player caster,Collection<Player> candidates,double range){
        Player selected=null;double best=.75,bestDistance=Double.MAX_VALUE;
        var eye=caster.getEyeLocation();var facing=eye.getDirection();
        for(Player p:candidates){if(p.equals(caster)||!p.getWorld().equals(caster.getWorld())||!caster.hasLineOfSight(p))continue;
            var delta=p.getEyeLocation().toVector().subtract(eye.toVector());double distance=delta.lengthSquared();if(distance>range*range||distance<1.e-6)continue;
            double alignment=delta.normalize().dot(facing);
            if(alignment>best||alignment==best&&distance<bestDistance){best=alignment;bestDistance=distance;selected=p;}
        }
        return selected;
    }
}
