package me.petr1furious.potatominigames.games.party;
import java.util.*;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;
public final class ColorFloorCheck {
    public static void main(String[] args) {
        check(ColorFloorRound.choiceTicks(1)==120 && ColorFloorRound.choiceTicks(11)==40 && ColorFloorRound.choiceTicks(100)==40,"Bounded timings");
        for(int seed=0;seed<100;seed++) {
            var r=ColorFloorRound.generate(1,new Random(seed));
            var counts=new HashMap<org.bukkit.Material,Integer>();boolean[][] seen=new boolean[21][21];List<Integer> areas=new ArrayList<>();
            for(int x=0;x<21;x++)for(int z=0;z<21;z++){
                counts.merge(r.material(x,z),1,Integer::sum);double nearest=Double.POSITIVE_INFINITY;
                for(int tx=0;tx<21;tx++)for(int tz=0;tz<21;tz++)if(r.material(tx,tz)==r.target())nearest=Math.min(nearest,Math.hypot(tx-x,tz-z));
                check(nearest<=8,"Target remains reachable on the shortest countdown");
                if(r.material(x,z)==r.target())check(r.safe(x+.5,82,z+.5),"Jump above target safe");
                if(seen[x][z])continue;
                var queue=new ArrayDeque<int[]>();queue.add(new int[]{x,z});seen[x][z]=true;int area=0;
                while(!queue.isEmpty()){var c=queue.remove();area++;for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int nx=c[0]+d[0],nz=c[1]+d[1];
                    if(nx>=0&&nx<21&&nz>=0&&nz<21&&!seen[nx][nz]&&r.material(nx,nz)==r.material(x,z)){seen[nx][nz]=true;queue.add(new int[]{nx,nz});}}}
                areas.add(area);
            }
            check(counts.size()==6,"All announced colors exist");
            check(Collections.max(counts.values())>=3*Collections.min(counts.values()),"Some colors are meaningfully rarer");
            check(Collections.max(areas)>=20&&Collections.min(areas)<=6,"Large and small coherent patches");
            check(!r.safe(-1,81,0) && !r.safe(0,90,0),"Outside arena/height unsafe");
        }
        System.out.println("Color floor passed");
    }
}
