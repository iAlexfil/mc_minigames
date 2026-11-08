package me.petr1furious.potatominigames.games.party;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import me.petr1furious.potatominigames.core.game.SessionServices;

final class HoleWallSession extends PartySurvivalSession {
    private final int seconds;
    private HoleWallArena arena;private HoleWallDisplay display;private HoleWallPattern pattern;
    private HoleWallPhysics.Direction direction;
    private final Map<UUID,BoundingBox> previous=new HashMap<>();
    private final Map<UUID,Long> nextHitSound=new HashMap<>();
    private long moveAt,nextWall;private double depth;private int wallNumber,warnedRadius=10;
    HoleWallSession(SessionServices services,PartyConfig config,PartyRoundContext context){
        super(services,config,"hole_in_the_wall","Дырка в стене",context);seconds=config.holeWallSeconds.get();
    }
    void buildArena(){arena=new HoleWallArena(world);arena.build();world.setTime(6000);}
    Location spawn(int i,int n){return arena.spawn(i,n);}
    Location viewpoint(){return new Location(world,.5,96,.5,0,70);}
    double outHeight(){return 76;}
    int durationSeconds(){return seconds;}
    boolean podiumAwards(){return true;}
    String instructions(){return "Проходи в отверстия и перепрыгивай препятствия! Стены толкают к краю. Не упади: платформа сужается, стены ускоряются.";}
    void beginPlay(){nextWall=elapsed();}
    void playTick(long elapsed){
        long duration=seconds*20L;
        int target=elapsed>=duration*2/3?6:elapsed>=duration/3?8:10;
        int warning=elapsed>=duration*2/3-60?6:elapsed>=duration/3-60?8:10;
        if(warning<warnedRadius){
            warnedRadius=warning;arena.warn(warning);
            announceTitle("Край исчезнет!","Красная полоса обрушится после прохода стены",2);
            PartySounds.play(alivePlayers(),Sound.BLOCK_NOTE_BLOCK_BELL,.7f,.6f);
        }
        if(display==null){
            if(elapsed<nextWall)return;
            if(target<arena.radius()){arena.shrink(target);PartySounds.play(alivePlayers(),Sound.BLOCK_GLASS_BREAK,.6f,.7f);}
            wallNumber++;pattern=HoleWallPattern.generate(wallNumber,arena.radius(),random);
            direction=HoleWallPhysics.Direction.values()[random.nextInt(4)];depth=-16;
            display=new HoleWallDisplay(world,pattern,direction,depth);moveAt=elapsed+40;
            previous.clear();nextHitSound.clear();
            announceTitle(direction.title,"Стена "+wallNumber+" • найди проход",2);
            PartySounds.play(alivePlayers(),Sound.BLOCK_NOTE_BLOCK_PLING,.7f,1.2f);
        }
        var players=alivePlayers();
        if(elapsed<moveAt){
            for(Player p:players)previous.put(p.getUniqueId(),p.getBoundingBox());
            PartySounds.countdown(players,moveAt-elapsed);return;
        }
        double speed=HoleWallPhysics.speed(elapsed,duration),nextDepth=depth+speed;
        display.move(nextDepth);
        for(Player player:players){
            var body=player.getBoundingBox();var before=previous.getOrDefault(player.getUniqueId(),body);
            if(HoleWallPhysics.collides(pattern.cells(),direction,depth,nextDepth,before,body)){
                push(player,nextDepth,speed);
                if(elapsed>=nextHitSound.getOrDefault(player.getUniqueId(),0L)){
                    player.playSound(player.getLocation(),Sound.ENTITY_SLIME_JUMP,.7f,.7f);nextHitSound.put(player.getUniqueId(),elapsed+10);
                }
            }
            previous.put(player.getUniqueId(),player.getBoundingBox());
        }
        depth=nextDepth;
        if(depth>16){display.clear();display=null;previous.clear();nextWall=elapsed+20;}
    }
    private void push(Player player,double position,double speed){
        Location location=player.getLocation();BoundingBox body=player.getBoundingBox();Vector velocity=player.getVelocity();
        BoundingBox before=previous.getOrDefault(player.getUniqueId(),body);
        double projectedCenter=direction.dx*before.getCenterX()+direction.dz*before.getCenterZ();
        // Preserve the side of approach: catching the rear must never warp through a solid cell.
        int side=projectedCenter>=depth+(direction.dx+direction.dz)*.5?1:-1;
        int nx=direction.dx*side,nz=direction.dz*side;
        double x=.5+direction.dx*position+nx*(.5+body.getWidthX()/2+.02);
        double z=.5+direction.dz*position+nz*(.5+body.getWidthZ()/2+.02);
        if(nx>0)location.setX(Math.max(location.getX(),x));
        if(nx<0)location.setX(Math.min(location.getX(),x));
        if(nz>0)location.setZ(Math.max(location.getZ(),z));
        if(nz<0)location.setZ(Math.min(location.getZ(),z));
        teleport(player,location);
        if(nx!=0)velocity.setX(nx*(speed+.08));
        else velocity.setZ(nz*(speed+.08));
        player.setVelocity(velocity);
    }
    String status(){return "Стена "+wallNumber+" • "+(direction==null?"Готовься":direction.title)+" • осталось "+Math.max(0,seconds-elapsed()/20)+" с";}
    void playerOut(Player player){previous.remove(player.getUniqueId());nextHitSound.remove(player.getUniqueId());}
    void cleanup(){if(display!=null){display.clear();display=null;}previous.clear();nextHitSound.clear();}
}
