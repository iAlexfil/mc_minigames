package me.petr1furious.potatominigames.games.party;

import java.util.List;
import org.bukkit.util.BoundingBox;

final class HoleWallPhysics {
    enum Direction {
        NORTH(0,1,"Север"), EAST(-1,0,"Восток"), SOUTH(0,-1,"Юг"), WEST(1,0,"Запад");
        final int dx,dz;final String title;
        Direction(int dx,int dz,String title){this.dx=dx;this.dz=dz;this.title=title;}
    }
    static double speed(long elapsed,long duration){return .14+.18*Math.clamp((double)elapsed/Math.max(1,duration),0,1);}
    static BoundingBox box(HoleWallPattern.Cell cell,Direction direction,double depth){
        double x=direction.dx==0?cell.u():direction.dx*depth;
        double z=direction.dz==0?cell.u():direction.dz*depth;
        return new BoundingBox(x,81+cell.y(),z,x+1,82+cell.y(),z+1);
    }
    static boolean collides(List<HoleWallPattern.Cell> cells,Direction direction,double from,double to,BoundingBox previous,BoundingBox current){
        double vx=current.getMinX()-previous.getMinX()-direction.dx*(to-from);
        double vy=current.getMinY()-previous.getMinY();
        double vz=current.getMinZ()-previous.getMinZ()-direction.dz*(to-from);
        for(var cell:cells)if(swept(previous,box(cell,direction,from),vx,vy,vz))return true;
        return false;
    }
    /** Relative swept AABB: both actor and wall motion count, including motion through a cell. */
    private static boolean swept(BoundingBox body,BoundingBox solid,double vx,double vy,double vz){
        double entry=0,exit=1;
        double[] min={body.getMinX(),body.getMinY(),body.getMinZ()},max={body.getMaxX(),body.getMaxY(),body.getMaxZ()};
        double[] low={solid.getMinX(),solid.getMinY(),solid.getMinZ()},high={solid.getMaxX(),solid.getMaxY(),solid.getMaxZ()},v={vx,vy,vz};
        for(int axis=0;axis<3;axis++){
            if(Math.abs(v[axis])<1e-9){if(max[axis]<=low[axis]||min[axis]>=high[axis])return false;}
            else{
                double a=(low[axis]-max[axis])/v[axis],b=(high[axis]-min[axis])/v[axis];
                entry=Math.max(entry,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
                if(entry>=exit)return false;
            }
        }
        return entry<exit;
    }
}
