package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;

final class HoleWallArena {
    static final int ARM_RADIUS=2;
    private final World world;private int radius=8;
    HoleWallArena(World world){this.world=world;}
    int radius(){return radius;}
    void build(){paint();}
    void warn(int nextRadius){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(onFloor(x,z,radius)&&Math.max(Math.abs(x),Math.abs(z))>nextRadius)world.getBlockAt(x,80,z).setType(Material.RED_CONCRETE,false);
    }
    void shrink(int nextRadius){
        if(nextRadius<4||nextRadius>=radius)return;
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(onFloor(x,z,radius)&&Math.max(Math.abs(x),Math.abs(z))>nextRadius)world.getBlockAt(x,80,z).setType(Material.AIR,false);
        radius=nextRadius;paint();
    }
    private void paint(){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(!onFloor(x,z,radius))continue;
            Material material=Material.SMOOTH_QUARTZ;
            if(z==-radius)material=Material.BLUE_CONCRETE;
            else if(x==radius)material=Material.YELLOW_CONCRETE;
            else if(z==radius)material=Material.MAGENTA_CONCRETE;
            else if(x==-radius)material=Material.LIME_CONCRETE;
            world.getBlockAt(x,80,z).setType(material,false);
        }
    }
    static boolean onFloor(int x,int z,int radius){return Math.abs(x)<=radius&&Math.abs(z)<=radius&&(Math.abs(x)<=ARM_RADIUS||Math.abs(z)<=ARM_RADIUS);}
    Location spawn(int index,int count){
        int lane=index/4-1,side=index%4;double distance=Math.min(4,radius-1);
        return switch(side){
            case 0->new Location(world,.5+lane,81,.5-distance);
            case 1->new Location(world,.5+distance,81,.5+lane);
            case 2->new Location(world,.5+lane,81,.5+distance);
            default->new Location(world,.5-distance,81,.5+lane);
        };
    }
}
