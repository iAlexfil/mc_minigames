package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
final class BuildMemoryArena {
    private final World world;private final Map<UUID,Location> centers=new LinkedHashMap<>();
    BuildMemoryArena(World world,Collection<UUID> roster){this.world=world;int i=0,n=roster.size();double radius=Math.max(16,n*3.2);
        for(UUID id:roster){double angle=2*Math.PI*i++/n;centers.put(id,new Location(world,Math.round(radius*Math.cos(angle)),81,Math.round(radius*Math.sin(angle))));}}
    void build(){for(Location c:centers.values()){
        for(int x=-4;x<=8;x++)for(int z=-4;z<=8;z++)world.getBlockAt(c.getBlockX()+x,80,c.getBlockZ()+z).setType(x==0&&z==-1?Material.GOLD_BLOCK:Material.SMOOTH_QUARTZ,false);
        for(int x=-4;x<=8;x++)for(int z=-4;z<=8;z++)if(x==-4||x==8||z==-4||z==8)for(int y=81;y<=88;y++)world.getBlockAt(c.getBlockX()+x,y,c.getBlockZ()+z).setType(Material.BARRIER,false);
    }}
    Location spawn(UUID id){return centers.get(id).clone().add(2.5,0,-2.5);}
    BuildMemoryPattern.Cell localCell(UUID owner,Block block){Location c=centers.get(owner);if(c==null||!block.getWorld().equals(world))return null;
        int x=block.getX()-c.getBlockX(),y=block.getY()-81,z=block.getZ()-c.getBlockZ();return x>=0&&x<5&&y>=0&&y<4&&z>=0&&z<5?new BuildMemoryPattern.Cell(x,y,z):null;}
    void show(UUID id,BuildMemoryPattern p){clear(id);Location c=centers.get(id);p.blocks().forEach((cell,m)->world.getBlockAt(c.getBlockX()+cell.x(),81+cell.y(),c.getBlockZ()+cell.z()).setType(m,false));}
    void clear(UUID id){Location c=centers.get(id);for(int x=0;x<5;x++)for(int y=81;y<85;y++)for(int z=0;z<5;z++)world.getBlockAt(c.getBlockX()+x,y,c.getBlockZ()+z).setType(Material.AIR,false);}
    Map<BuildMemoryPattern.Cell,Material> capture(UUID id){Map<BuildMemoryPattern.Cell,Material> result=new HashMap<>();Location c=centers.get(id);
        for(int x=0;x<5;x++)for(int y=0;y<4;y++)for(int z=0;z<5;z++){Material m=world.getBlockAt(c.getBlockX()+x,81+y,c.getBlockZ()+z).getType();if(!m.isAir())result.put(new BuildMemoryPattern.Cell(x,y,z),m);}return result;}
    void screens(boolean show){for(Location c:centers.values())for(int x=-4;x<=8;x++)for(int z=-4;z<=8;z++)if(x==-4||x==8||z==-4||z==8)for(int y=81;y<=88;y++)world.getBlockAt(c.getBlockX()+x,y,c.getBlockZ()+z).setType(show?Material.BLACK_CONCRETE:Material.BARRIER,false);}
}
