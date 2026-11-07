package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.util.BoundingBox;
final class AnvilArena {
    record Shelter(int x,int z){}
    private final World world;private List<Shelter> shelters=List.of();
    AnvilArena(World world){this.world=world;}
    static List<Shelter> layout(int count,Random random){
        List<Shelter> candidates=new ArrayList<>();
        for(int x=-6;x<=4;x+=2)for(int z=-6;z<=4;z+=2)candidates.add(new Shelter(x,z));
        Collections.shuffle(candidates,random);return List.copyOf(candidates.subList(0,Math.clamp(count,1,14)));
    }
    void build(){for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)world.getBlockAt(x,80,z).setType(Material.SMOOTH_STONE,false);
        // Open sky, low boundary only: players cannot leave the arena or reach slab tops.
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)if(Math.abs(x)==7||Math.abs(z)==7)for(int y=80;y<=84;y++)world.getBlockAt(x,y,z).setType(Material.BARRIER,false);
    }
    void relocateShelters(int count,Random random){
        for(Shelter s:shelters)paint(s,false);shelters=layout(count,random);for(Shelter s:shelters)paint(s,true);
    }
    private void paint(Shelter s,boolean show){for(int x=s.x;x<s.x+2;x++)for(int z=s.z;z<s.z+2;z++){
        var block=world.getBlockAt(x,83,z);
        if(!show)block.setType(Material.AIR,false);else{Slab data=(Slab)Material.SMOOTH_STONE_SLAB.createBlockData();data.setType(Slab.Type.BOTTOM);block.setBlockData(data,false);}
    }}
    List<BoundingBox> collisionBoxes(){List<BoundingBox> boxes=new ArrayList<>();for(Shelter s:shelters)boxes.add(new BoundingBox(s.x,83,s.z,s.x+2,83.5,s.z+2));boxes.add(new BoundingBox(-7,80,-7,8,81,8));return boxes;}
    Location spawn(int i,int n){double a=2*Math.PI*i/n;return new Location(world,.5+5*Math.cos(a),81,.5+5*Math.sin(a));}
}
