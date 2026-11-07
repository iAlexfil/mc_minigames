package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import java.util.function.Consumer;
final class AnvilRain {
    private static final class Drop {BlockDisplay display;double x,z,y=103,speed;int age;}
    private final World world;private final AnvilArena arena;private final Random random;
    private final Consumer<Player> hit;private final List<Drop> drops=new ArrayList<>();
    AnvilRain(World world,AnvilArena arena,Random random,Consumer<Player> hit){this.world=world;this.arena=arena;this.random=random;this.hit=hit;}
    // The anvil has a half-block-wide footprint. Return its bottom height at first contact.
    static double impact(double x,double z,double from,double to,BoundingBox box){
        if(x+.25<=box.getMinX()||x-.25>=box.getMaxX()||z+.25<=box.getMinZ()||z-.25>=box.getMaxZ()
                ||to>box.getMaxY()||from+.75<box.getMinY())return Double.NaN;
        return Math.min(from,box.getMaxY());
    }
    void tick(long tick,int wave,List<Player> targets){
        int interval=Math.max(1,5-(wave-1)/2);
        if(tick%interval==0)for(int i=0;i<2&&drops.size()<120;i++){
            Drop d=new Drop();d.x=random.nextInt(13)-6+.5;d.z=random.nextInt(13)-6+.5;
            d.display=world.spawn(new Location(world,d.x-.5,d.y,d.z-.5),BlockDisplay.class,e->{e.setBlock(Material.ANVIL.createBlockData());e.setTeleportDuration(1);e.setPersistent(false);});drops.add(d);
        }
        List<BoundingBox> solid=arena.collisionBoxes();
        for(var it=drops.iterator();it.hasNext();){Drop d=it.next();d.speed=Math.min(1.8,d.speed+.045);double next=d.y-d.speed;
            double solidY=Double.NEGATIVE_INFINITY;for(var box:solid){double y=impact(d.x,d.z,d.y,next,box);if(!Double.isNaN(y))solidY=Math.max(solidY,y);}
            Player victim=null;double playerY=Double.NEGATIVE_INFINITY;
            for(Player p:targets){if(p.getGameMode()!=GameMode.SURVIVAL)continue;double y=impact(d.x,d.z,d.y,next,p.getBoundingBox());if(!Double.isNaN(y)&&y>playerY){playerY=y;victim=p;}}
            if(victim!=null&&playerY>solidY){hit.accept(victim);d.display.remove();it.remove();}
            else if(solidY>Double.NEGATIVE_INFINITY||++d.age>160||next<79){d.display.remove();it.remove();}
            else{d.y=next;d.display.teleport(new Location(world,d.x-.5,d.y,d.z-.5));}
        }
    }
    int activeCount(){return drops.size();}
    void clear(){drops.forEach(d->d.display.remove());drops.clear();}
}
