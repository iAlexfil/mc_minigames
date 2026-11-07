package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.block.BlockBreakEvent;
import me.petr1furious.potatominigames.core.game.SessionServices;
final class ToolRaceSession extends PartySurvivalSession {
    private final int duration;private ToolRaceArena arena;private ToolRaceTrack track;
    ToolRaceSession(SessionServices s,PartyConfig c,PartyRoundContext context){super(s,c,"tool_race","Гонка инструментов",context);duration=c.toolRaceSeconds.get();}
    void buildArena(){track=new ToolRaceTrack(names.keySet(),ToolRaceTrack.generate(random));arena=new ToolRaceArena(world,names.keySet());arena.build(track);}
    Location spawn(int i,int n){return arena.spawn(new ArrayList<>(names.keySet()).get(i));}Location viewpoint(){return new Location(world,.5,88,.5,0,80);}
    double outHeight(){return 25;}int durationSeconds(){return duration;}boolean podiumAwards(){return true;}boolean survivalAutoFinish(){return false;}
    String instructions(){return "Прокопай 50 блоков вниз! 1 — железный топор, 2 — лопата, 3 — кирка. Выбирай инструмент под блок.";}
    void beginPlay(){for(var p:alivePlayers()){p.getInventory().setItem(0,PvpClass.item(Material.IRON_AXE));p.getInventory().setItem(1,PvpClass.item(Material.IRON_SHOVEL));p.getInventory().setItem(2,PvpClass.item(Material.IRON_PICKAXE));}}
    @Override protected void handleBlockBreak(BlockBreakEvent e){var p=e.getPlayer();UUID id=p.getUniqueId();int depth=arena.depth(id,e.getBlock());
        if(!playing()||!alive(p)||!track.mine(id,depth,elapsed())){e.setCancelled(true);return;}
        e.setDropItems(false);e.setExpToDrop(0);
        if(track.finished(id)){p.sendMessage(text("Финиш! Время: "+String.format(java.util.Locale.ROOT,"%.2f",elapsed()/20.0)+" с"));spectate(p);}
    }
    void playTick(long elapsed){if(context.eligible().stream().allMatch(track::finished))finish(List.of());}
    @Override public PartyRoundResult roundResult(){return track==null?PartyRoundResult.empty():track.result(context.eligible());}
    String status(){return "50 блоков • финишировали: "+names.keySet().stream().filter(track::finished).count()+" • "+Math.max(0,duration-elapsed()/20)+" с";}
    @Override public boolean isInGame(org.bukkit.entity.Player p){return alive(p)&&!track.finished(p.getUniqueId());}
    void cleanup(){if(track!=null)track.close();}
}
