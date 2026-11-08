package me.petr1furious.potatominigames.games.party;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;

final class HoleWallDisplay {
    private final World world;private final HoleWallPhysics.Direction direction;
    private final List<HoleWallPattern.Cell> cells;private final List<BlockDisplay> displays=new ArrayList<>();
    HoleWallDisplay(World world,HoleWallPattern pattern,HoleWallPhysics.Direction direction,double depth){
        this.world=world;this.direction=direction;cells=pattern.cells();
        try{
            for(var cell:cells)displays.add(world.spawn(location(cell,depth),BlockDisplay.class,e->{
                e.setBlock(Material.SLIME_BLOCK.createBlockData());e.setTeleportDuration(1);e.setPersistent(false);
            }));
        }catch(RuntimeException failure){clear();throw failure;}
    }
    private Location location(HoleWallPattern.Cell cell,double depth){var box=HoleWallPhysics.box(cell,direction,depth);return new Location(world,box.getMinX(),box.getMinY(),box.getMinZ());}
    void move(double depth){for(int i=0;i<displays.size();i++)displays.get(i).teleport(location(cells.get(i),depth));}
    void clear(){for(var display:displays)display.remove();displays.clear();}
}
