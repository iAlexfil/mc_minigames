package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.Material;
record BuildMemoryPattern(Map<Cell,Material> blocks) {
    record Cell(int x,int y,int z){}
    private static final Material[] PALETTE={Material.RED_CONCRETE,Material.BLUE_CONCRETE,Material.YELLOW_CONCRETE,Material.LIME_CONCRETE,Material.WHITE_CONCRETE};
    BuildMemoryPattern {blocks=Map.copyOf(blocks);}
    static BuildMemoryPattern generate(int stage,Random random){
        int count=5+2*(Math.clamp(stage,1,8)-1);
        List<Cell> perimeter=new ArrayList<>();
        for(int x=0;x<5;x++)perimeter.add(new Cell(x,0,0));
        for(int z=1;z<5;z++)perimeter.add(new Cell(4,0,z));
        for(int x=3;x>=0;x--)perimeter.add(new Cell(x,0,4));
        for(int z=3;z>0;z--)perimeter.add(new Cell(0,0,z));
        Collections.rotate(perimeter,random.nextInt(16));
        Map<Cell,Material> blocks=new LinkedHashMap<>();int base=stage<=2?count:Math.min(8,count);
        for(int i=0;i<base;i++)blocks.put(perimeter.get(i),PALETTE[random.nextInt(PALETTE.length)]);
        while(blocks.size()<count){Cell c=perimeter.get(random.nextInt(base));int y=0;
            while(blocks.containsKey(new Cell(c.x,y,c.z)))y++;
            if(y<4)blocks.put(new Cell(c.x,y,c.z),PALETTE[random.nextInt(PALETTE.length)]);
        }
        return new BuildMemoryPattern(blocks);
    }
    Map<Material,Integer> inventory(){Map<Material,Integer> result=new EnumMap<>(Material.class);blocks.values().forEach(m->result.merge(m,1,Integer::sum));return Map.copyOf(result);}
    boolean matches(Map<Cell,Material> actual){return blocks.equals(actual);}
    BuildMemoryPattern rotate(int turns){Map<Cell,Material> rotated=new HashMap<>();blocks.forEach((c,m)->{int x=c.x,z=c.z;for(int i=0;i<Math.floorMod(turns,4);i++){int next=4-z;z=x;x=next;}rotated.put(new Cell(x,c.y,z),m);});return new BuildMemoryPattern(rotated);}
}
