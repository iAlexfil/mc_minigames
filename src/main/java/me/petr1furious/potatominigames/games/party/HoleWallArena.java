package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;

final class HoleWallArena {
    private final World world;private int radius=10;
    HoleWallArena(World world){this.world=world;}
    int radius(){return radius;}
    void build(){paint();}
    void warn(int nextRadius){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(Math.max(Math.abs(x),Math.abs(z))>nextRadius)world.getBlockAt(x,80,z).setType(Material.RED_CONCRETE,false);
    }
    void shrink(int nextRadius){
        if(nextRadius<6||nextRadius>=radius)return;
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(Math.max(Math.abs(x),Math.abs(z))>nextRadius)world.getBlockAt(x,80,z).setType(Material.AIR,false);
        radius=nextRadius;paint();
    }
    private void paint(){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            Material material=Material.SMOOTH_QUARTZ;
            if(z==-radius)material=Material.BLUE_CONCRETE;
            else if(x==radius)material=Material.YELLOW_CONCRETE;
            else if(z==radius)material=Material.RED_CONCRETE;
            else if(x==-radius)material=Material.LIME_CONCRETE;
            world.getBlockAt(x,80,z).setType(material,false);
        }
    }
    Location spawn(int index,int count){double angle=2*Math.PI*index/count;return new Location(world,.5+4*Math.cos(angle),81,.5+4*Math.sin(angle));}
}
