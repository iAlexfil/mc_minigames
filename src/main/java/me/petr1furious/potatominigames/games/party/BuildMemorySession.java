package me.petr1furious.potatominigames.games.party;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import me.petr1furious.potatominigames.core.game.SessionServices;
final class BuildMemorySession extends PartySurvivalSession {
    private enum Phase{SHOW,BUILD,REVIEW}
    private final int stages;private BuildMemoryArena arena;private Phase phase=Phase.SHOW;private int stage=1;private long deadline;
    private final Map<UUID,BuildMemoryPattern> patterns=new LinkedHashMap<>();
    BuildMemorySession(SessionServices s,PartyConfig c,PartyRoundContext context){super(s,c,"build_memory","Повтори постройку",context);stages=c.buildMemoryStages.get();}
    void buildArena(){arena=new BuildMemoryArena(world,names.keySet());arena.build();}
    Location spawn(int i,int n){return arena.spawn(new ArrayList<>(names.keySet()).get(i));}
    Location viewpoint(){return new Location(world,.5,100,.5,0,75);}
    double outHeight(){return 76;}int durationSeconds(){return 500;}boolean podiumAwards(){return true;}
    String instructions(){return "Запомни постройку на своей платформе и повтори её. Блоков ровно достаточно. Любая ошибка в конце — выбывание!";}
    void beginPlay(){showPattern();}
    private void showPattern(){phase=Phase.SHOW;arena.screens(false);patterns.clear();BuildMemoryPattern shared=BuildMemoryPattern.generate(stage,random);
        for(Player p:alivePlayers()){var pattern=shared.rotate(random.nextInt(4));patterns.put(p.getUniqueId(),pattern);teleport(p,arena.spawn(p.getUniqueId()));clearKit(p);arena.show(p.getUniqueId(),pattern);}
        deadline=elapsed()+Math.max(5,13-stage)*20L;announceTitle("Запоминай!","Этап "+stage+" • ориентир — золотой блок",2);
    }
    void playTick(long elapsed){if(elapsed<deadline)return;
        switch(phase){
            case SHOW->{phase=Phase.BUILD;arena.screens(true);for(Player p:alivePlayers()){arena.clear(p.getUniqueId());clearKit(p);patterns.get(p.getUniqueId()).inventory().forEach((m,n)->p.getInventory().addItem(new ItemStack(m,n)));}deadline=elapsed+Math.max(19,43-3*stage)*20L;announceTitle("Построй!","Воспроизведи образец точно",2);}
            case BUILD->{phase=Phase.REVIEW;standings.holdResults(true);
                List<Player> failed=new ArrayList<>();for(Player p:alivePlayers())if(!patterns.get(p.getUniqueId()).matches(arena.capture(p.getUniqueId())))failed.add(p);
                for(Player p:failed)eliminate(p);
                for(Player p:alivePlayers())clearKit(p);
                patterns.forEach((id,pattern)->arena.show(id,pattern));arena.screens(false);
                deadline=elapsed+60;announceTitle("Проверка",failed.isEmpty()?"Все верно!":"Ошиблись: "+failed.size(),2);
            }
            case REVIEW->{standings.holdResults(false);if(stage>=stages)finish(standings.survivors());else{stage++;showPattern();}}
        }
    }
    private void clearKit(Player p){p.closeInventory();p.setItemOnCursor(null);p.getInventory().clear();}
    private boolean building(Player p){return playing()&&phase==Phase.BUILD&&alive(p);}
    @Override protected void handleBlockPlace(BlockPlaceEvent e){
        if(!building(e.getPlayer())||arena.localCell(e.getPlayer().getUniqueId(),e.getBlock())==null||!e.getBlockReplacedState().getType().isAir()
                ||!patterns.get(e.getPlayer().getUniqueId()).inventory().containsKey(e.getBlock().getType()))e.setCancelled(true);
    }
    @Override protected void handleBlockBreak(BlockBreakEvent e){
        e.setCancelled(true);Player p=e.getPlayer();
        if(!building(p)||arena.localCell(p.getUniqueId(),e.getBlock())==null||e.getBlock().getType().isAir())return;
        Material material=e.getBlock().getType();e.getBlock().setType(Material.AIR,false);p.getInventory().addItem(new ItemStack(material));e.setDropItems(false);e.setExpToDrop(0);
    }
    @Override public void onPlayerInteract(PlayerInteractEvent e){if(!building(e.getPlayer())||!e.getAction().isRightClick())e.setCancelled(true);}
    @Override protected void handleInventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!building(p)||e.getSlotType()==InventoryType.SlotType.CRAFTING||e.getSlotType()==InventoryType.SlotType.RESULT
                ||e.getAction().name().contains("DROP")||e.getAction()==InventoryAction.CLONE_STACK)e.setCancelled(true);
    }
    String status(){return "Этап "+stage+" • "+switch(phase){case SHOW->"Запоминай";case BUILD->"Строй";case REVIEW->"Проверка";}+" • "+Math.max(0,(deadline-elapsed()+19)/20)+" с";}
}
