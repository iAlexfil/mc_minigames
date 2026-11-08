package me.petr1furious.potatominigames.games.party;

import java.util.*;

/** Tick-driven stream; holds new arrivals while an announced platform shrink drains old walls. */
final class HoleWallFlow {
    static final class Wall {
        final int number;final HoleWallPattern pattern;final HoleWallPhysics.Direction direction;final long moveAt;
        double from=-16,depth=-16;
        Wall(int number,HoleWallPattern pattern,HoleWallPhysics.Direction direction,long moveAt){
            this.number=number;this.pattern=pattern;this.direction=direction;this.moveAt=moveAt;
        }
    }
    record Frame(List<Wall> started,List<Wall> finished,int radius,int warningRadius) {}
    private final long duration;private final Random random;private final List<Wall> walls=new ArrayList<>();
    private long nextSpawn;private int radius=8,number;private HoleWallPhysics.Direction lastDirection;
    HoleWallFlow(long duration,Random random){this.duration=duration;this.random=random;}
    List<Wall> active(){return List.copyOf(walls);}
    Frame tick(long tick){
        List<Wall> started=new ArrayList<>(),finished=new ArrayList<>();
        double speed=HoleWallPhysics.speed(tick,duration);
        for(var it=walls.iterator();it.hasNext();){var wall=it.next();wall.from=wall.depth;
            if(tick>=wall.moveAt)wall.depth+=speed;
            if(wall.depth>16){finished.add(wall);it.remove();}
        }
        int target=tick>=duration*2/3?4:tick>=duration/3?6:8;
        int warning=tick>=duration*2/3-60?4:tick>=duration/3-60?6:8;
        if(walls.isEmpty()&&target<radius)radius=target;
        if(tick<duration&&tick>=nextSpawn&&walls.size()<4&&warning>=radius){
            List<HoleWallPhysics.Direction> candidates=new ArrayList<>();
            for(var direction:HoleWallPhysics.Direction.values())
                if(walls.stream().noneMatch(w->w.direction.dx*direction.dx+w.direction.dz*direction.dz==-1))candidates.add(direction);
            if(candidates.size()>1)candidates.remove(lastDirection);
            var direction=candidates.get(random.nextInt(candidates.size()));lastDirection=direction;
            // All wall gates stay over the five-wide arms, including the center where walls cross.
            var wall=new Wall(++number,HoleWallPattern.generate(number,HoleWallArena.ARM_RADIUS,random),direction,tick+40);
            walls.add(wall);started.add(wall);
            nextSpawn=tick+Math.round(80-40*Math.clamp((double)tick/duration,0,1));
        }
        return new Frame(List.copyOf(started),List.copyOf(finished),radius,warning);
    }
    void clear(){walls.clear();}
}
