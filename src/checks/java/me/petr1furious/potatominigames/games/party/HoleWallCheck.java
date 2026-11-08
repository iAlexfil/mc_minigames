package me.petr1furious.potatominigames.games.party;

import java.util.*;
import java.lang.reflect.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

public final class HoleWallCheck {
    public static void main(String[] args) throws Exception {
        openings(); collisions(); floor(); displayLifecycle(); pushingAndRouting();
        check(PartyGamesState.MODES.contains("hole_in_the_wall"),"New mode eligible for party selection");
        var game=new PartyGame((PartyConfig)null);
        check(game.modes().contains("hole_in_the_wall")&&game.maxPlayers("hole_in_the_wall")==12,"New mode in menu and commands");
        check(game.modeTitle("hole_in_the_wall").equals("Дырка в стене"),"Readable mode name");
        System.out.println("Hole in the Wall openings, continuous collision, platform and registration passed");
    }
    private static void openings(){
        Set<List<HoleWallPattern.Cell>> patterns=new HashSet<>();boolean jumps=false;
        for(int radius:new int[]{10,8,6})for(int stage=1;stage<=18;stage++)for(int seed=0;seed<8;seed++){
            var pattern=HoleWallPattern.generate(stage,radius,new Random(seed));patterns.add(pattern.cells());
            check(!pattern.gates().isEmpty(),"Every wall has a passage");
            check(new HashSet<>(pattern.cells()).size()==pattern.cells().size(),"No duplicate display cells");
            for(var gate:pattern.gates()){
                check(gate.start()>=-radius&&gate.start()+gate.width()<=radius+1,"Opening stays above current floor");
                check(gate.bottom()==0||gate.bottom()==1,"Jump never exceeds one block");jumps|=gate.bottom()==1;
                for(var dir:HoleWallPhysics.Direction.values()){
                    double u=gate.start()+.5, y=81+gate.bottom()+.01;
                    BoundingBox body=dir.dx==0?new BoundingBox(u-.3,y,.2,u+.3,y+1.8,.8):new BoundingBox(.2,y,u-.3,.8,y+1.8,u+.3);
                    check(!HoleWallPhysics.collides(pattern.cells(),dir,-1,1,body,body),"Actual player fits while wall passes in every direction");
                }
            }
        }
        check(jumps&&patterns.size()>50,"Varied walk and jump patterns");
    }
    private static void collisions(){
        var cells=List.of(new HoleWallPattern.Cell(0,0),new HoleWallPattern.Cell(0,1));
        for(var dir:HoleWallPhysics.Direction.values()){
            var body=new BoundingBox(.2,81,.2,.8,82.8,.8);
            check(HoleWallPhysics.collides(cells,dir,-2,2,body,body),"Fast wall cannot tunnel through player");
            check(!HoleWallPhysics.collides(cells,dir,-4,-3,body,body),"Distant wall does not hit");
            var before=body.clone().shift(-dir.dx*2,0,-dir.dz*2);
            var after=body.clone().shift(dir.dx*2,0,dir.dz*2);
            check(HoleWallPhysics.collides(cells,dir,0,0,before,after),"Player cannot move through a stationary solid cell between ticks");
            var jumped=body.clone().shift(0,2.1,0);
            check(!HoleWallPhysics.collides(cells,dir,-2,2,jumped,jumped),"Clearance above an obstacle works");
        }
        check(HoleWallPhysics.speed(0,2400)<HoleWallPhysics.speed(2400,2400),"Walls accelerate");
    }
    private static void floor(){
        var fixture=new PartyArenaCheck.WorldMap();var arena=new HoleWallArena(fixture.world);arena.build();
        check(fixture.blocks.values().stream().filter(m->m!=Material.AIR).count()==441,"Initial 21 by 21 platform");
        arena.warn(8);
        check(fixture.world.getBlockAt(10,80,0).getType()==Material.RED_CONCRETE,"Doomed rim visibly warns before removal");
        check(fixture.world.getBlockAt(8,80,0).getType()!=Material.RED_CONCRETE,"Safe floor stays recognizable");
        arena.shrink(8);arena.shrink(6);
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)
            check((fixture.world.getBlockAt(x,80,z).getType()!=Material.AIR)==(Math.abs(x)<=6&&Math.abs(z)<=6),"Shrink leaves a continuous 13 by 13 floor");
        for(int i=0;i<12;i++){var p=arena.spawn(i,12);check(fixture.world.getBlockAt(p.getBlockX(),80,p.getBlockZ()).getType()!=Material.AIR,"Spawn remains over floor");}
    }
    private static void pushingAndRouting() throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        PartyConfig config=(PartyConfig)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(PartyConfig.class);
        Field duration=PartyConfig.class.getDeclaredField("holeWallSeconds");duration.setAccessible(true);
        duration.set(config,new PropertyBag("fixture").define("duration",PropertyCodec.INT,120));
        var context=new PartyRoundContext(null,List.of(),PartyRoundOptions.party(),(reason,result)->{});
        var round=PartyRoundFactory.create("hole_in_the_wall",null,config,context);
        check(round instanceof HoleWallSession,"Embedded factory selects the new session");
        check(new PartyGame(config).createSession(null,"hole_in_the_wall") instanceof HoleWallSession,"Standalone factory selects the same game");
        Field direction=HoleWallSession.class.getDeclaredField("direction");direction.setAccessible(true);
        Method push=HoleWallSession.class.getDeclaredMethod("push",Player.class,double.class,double.class);push.setAccessible(true);
        Field historyField=HoleWallSession.class.getDeclaredField("previous");historyField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,BoundingBox> history=(Map<UUID,BoundingBox>)historyField.get(round);
        UUID playerId=new UUID(0,1);
        for(var dir:HoleWallPhysics.Direction.values()){
            history.clear();
            direction.set(round,dir);Location[] position={new Location(null,.5,81,.5)};Vector[] velocity={new Vector(0,.3,0)};
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->playerId;
                case "getLocation"->position[0].clone();case "getVelocity"->velocity[0].clone();
                case "getBoundingBox"->body(position[0]);
                case "teleport"->{position[0]=((Location)a[0]).clone();yield true;}
                case "setFallDistance"->null;case "setVelocity"->{velocity[0]=((Vector)a[0]).clone();yield null;}
                default->throw new AssertionError("Unexpected player method: "+m.getName());
            });
            push.invoke(round,player,0,.2);
            check(velocity[0].getX()*dir.dx+velocity[0].getZ()*dir.dz>0,"Collision pushes along wall travel");
            check(velocity[0].getY()==.3&&position[0].getY()==81,"Pushing preserves jump velocity and height");
            check(!HoleWallPhysics.collides(List.of(new HoleWallPattern.Cell(0,0)),dir,0,0,body(position[0]),body(position[0])),"Player is resolved outside solid wall");
            var behind=new Location(null,.5-dir.dx*.85,81,.5-dir.dz*.85);
            history.put(playerId,body(behind));position[0]=new Location(null,.5-dir.dx*.57,81,.5-dir.dz*.57);
            check(HoleWallPhysics.collides(List.of(new HoleWallPattern.Cell(0,0)),dir,0,.2,body(behind),body(position[0])),"Sprinting into rear face is a real collision");
            push.invoke(round,player,.2,.2);
            check(position[0].getX()*dir.dx+position[0].getZ()*dir.dz<.2+(dir.dx+dir.dz)*.5,"Rear contact cannot teleport a player through a solid wall");
            check(!HoleWallPhysics.collides(List.of(new HoleWallPattern.Cell(0,0)),dir,.2,.2,body(position[0]),body(position[0])),"Rear contact resolves outside trailing face");
        }
    }
    private static BoundingBox body(Location p){return new BoundingBox(p.getX()-.3,p.getY(),p.getZ()-.3,p.getX()+.3,p.getY()+1.8,p.getZ()+.3);}
    private static void displayLifecycle(){
        var pattern=HoleWallPattern.generate(3,6,new Random(4));
        for(var dir:HoleWallPhysics.Direction.values()){
            var fixture=new DisplayWorld(-1);var wall=new HoleWallDisplay(fixture.world,pattern,dir,-16);wall.move(2.5);
            check(fixture.positions.size()==pattern.cells().size(),"Exactly one display per solid cell");
            for(int i=0;i<fixture.positions.size();i++){
                var b=HoleWallPhysics.box(pattern.cells().get(i),dir,2.5);var p=fixture.positions.get(i);
                check(p.getX()==b.getMinX()&&p.getY()==b.getMinY()&&p.getZ()==b.getMinZ(),"Visible wall matches collision geometry");
            }
            wall.clear();wall.clear();check(fixture.removed.size()==fixture.positions.size(),"Clearing wall removes every display exactly once");
        }
        var fixture=new DisplayWorld(4);
        try{new HoleWallDisplay(fixture.world,pattern,HoleWallPhysics.Direction.NORTH,-16);throw new AssertionError("Expected spawn failure");}
        catch(IllegalStateException expected){check(fixture.removed.size()==4,"Partial spawn failure cleans existing displays");}
    }
    private static final class DisplayWorld {
        final List<Location> positions=new ArrayList<>();final Set<Integer> removed=new HashSet<>();int failAfter;
        DisplayWorld(int failAfter){this.failAfter=failAfter;}
        final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->{
            if(!m.getName().equals("spawn"))throw new AssertionError("Unexpected world method: "+m.getName());
            if(positions.size()==failAfter)throw new IllegalStateException("Simulated spawn failure");
            int id=positions.size();positions.add(((Location)a[0]).clone());
            // Display appearance needs a server; this fixture tests movement and ownership.
            return Proxy.newProxyInstance(BlockDisplay.class.getClassLoader(),new Class[]{BlockDisplay.class},(display,method,args)->switch(method.getName()){
                case "teleport"->{positions.set(id,((Location)args[0]).clone());yield true;}
                case "remove"->{check(removed.add(id),"Display removed once");yield null;}
                default->throw new AssertionError("Unexpected display method: "+method.getName());
            });
        });
    }
}
