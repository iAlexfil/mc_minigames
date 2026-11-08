package me.petr1furious.potatominigames.games.party;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.command.CommandFailure;

/** Owns the full party. Child completion never restores players or ends the outer queue. */
final class PartyGamesSession extends PartyRoundForwarder {
    private enum Phase { ACTIVE, TRANSITION, BREAK, DONE }
    private final SessionServices services;private final PartyConfig config;
    private final Map<UUID,String> names=new LinkedHashMap<>();
    private PartyGamesState state;private PartyRoundOptions options;private PartyRoundContext context;
    private PartyRoundSession child;private World hub;private Phase phase=Phase.BREAK;
    private BukkitTask ticker,transition;private int index=-1,roundCount,intermission;private long tick,deadline,aloneSince=-1,childSeconds;
    private boolean internalTeleport,complete,awarded;
    private List<Component> summary=List.of();
    PartyGamesSession(SessionServices services,PartyConfig config){this.services=services;this.config=config;}
    private static Component text(String s){return Component.text(s,NamedTextColor.GOLD);}
    @Override protected GameSession delegate(){return phase==Phase.ACTIVE&&context!=null&&!context.ended()?child:null;}
    @Override public void onStart() throws CommandFailure {
        try {
            services.participants().all().forEach(p->names.put(p.id(),p.name()));
            List<String> pool=Arrays.stream(config.partyModes.get().split(",",-1)).map(String::trim).toList();
            state=new PartyGamesState(new ArrayList<>(names.keySet()),pool,new Random());
            roundCount=Math.min(config.partyRounds.get(),pool.size());intermission=config.partyIntermission.get();
            options=new PartyRoundOptions(true,intermission,config.partyArmsRaceSeconds.get(),config.partyMinersPreparation.get(),config.partyMinersCombat.get(),config.partyMinersFinale.get());
            hub=services.worlds().createVoid("party_hub_"+UUID.randomUUID().toString().replace("-",""));
            services.arena().applyStandardGameRules(hub);hub.setGameRule(GameRules.PVP,false);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)hub.getBlockAt(x,80,z).setType(Material.SMOOTH_QUARTZ,false);
            for(Player p:services.session().audiencePlayers())park(p);
            startNext();
            ticker=Bukkit.getScheduler().runTaskTimer(services.plugin(),()->{try{tick();}catch(RuntimeException e){fail(e);}},1,1);
        } catch(RuntimeException e){onEnd(EndReason.ABORTED);throw new CommandFailure("Не удалось подготовить Party Games: "+e.getMessage());}
    }
    private void park(Player p){
        internalTeleport=true;
        try{
            if(p.isDead())p.spigot().respawn();if(p.getGameMode()==GameMode.SPECTATOR)p.setSpectatorTarget(null);
            p.closeInventory();p.setItemOnCursor(null);services.playerState().resetForRound(p,GameMode.SPECTATOR);
            p.teleport(new Location(hub,.5,82,.5));p.setVelocity(new org.bukkit.util.Vector());
        } finally{internalTeleport=false;}
    }
    private void startNext() {
        if(phase==Phase.DONE)return;
        index++;state.beginRound(index);childSeconds=0;int generation=index;
        var roster=services.participants().all().stream().filter(p->state.nextRoster().contains(p.id())&&p.player()!=null).toList();
        if(roster.isEmpty()){services.session().endGame(EndReason.ALL_PLAYERS_LEFT);return;}
        context=new PartyRoundContext(services,roster,options.forRound(index),(reason,result)->roundEnded(generation,reason,result));
        child=PartyRoundFactory.create(state.schedule().get(index),services,config,context);
        phase=Phase.ACTIVE;
        services.messages().broadcast(text("Party Games • "+(index+1)+"/"+roundCount+" • "+PartyRoundFactory.title(state.schedule().get(index))));
        try{child.onStart();}catch(CommandFailure|RuntimeException e){fail(e);return;}
        // Child startup moves players in its roster. Everyone else watches the new arena.
        for(Player p:services.session().audiencePlayers())if(!state.nextRoster().contains(p.getUniqueId()))watch(p);
    }
    private void roundEnded(int generation,EndReason reason,PartyRoundResult result){
        if(phase!=Phase.ACTIVE||generation!=index)return;
        if(reason!=EndReason.FINISHED){phase=Phase.TRANSITION;transition=Bukkit.getScheduler().runTask(services.plugin(),()->services.session().endGame(reason));return;}
        if(!state.acceptResult(generation,result))return;
        phase=Phase.TRANSITION;
        PartySounds.play(services.session().audiencePlayers(),Sound.ENTITY_PLAYER_LEVELUP,.6f,1.2f);
        services.messages().broadcast(text("Результаты раунда:"));
        for(var group:result.places())for(UUID id:group)services.messages().broadcast(text(result.place(id)+". "+names.get(id)+" • +"+result.awards().get(id)));
        // Never reset players or unload a world while inside its death/damage callback.
        transition=Bukkit.getScheduler().runTask(services.plugin(),()->{
            transition=null;
            try{
                child.onEnd(EndReason.FINISHED);child=null;
                for(Player p:services.session().audiencePlayers())park(p);
                context.disposeWorlds();context=null;
                if(index+1>=roundCount){complete=true;services.session().endGame(EndReason.FINISHED);return;}
                phase=Phase.BREAK;deadline=tick+intermission*20L;aloneSince=-1;hud();
                PartySounds.countdown(services.session().audiencePlayers(),intermission*20L);
            }catch(RuntimeException e){fail(e);}
        });
    }
    private void tick(){
        if(phase==Phase.DONE)return;tick++;
        if(phase==Phase.BREAK){
            PartySounds.countdown(services.session().audiencePlayers(),deadline-tick);
            if(!services.isTest()&&state.nextRoster().size()<2){if(aloneSince<0)aloneSince=tick;if(tick-aloneSince>=300){services.messages().broadcast(text("Матч завершён досрочно: недостаточно игроков. Очки не сохранены."));services.session().endGame(EndReason.ABORTED);return;}}
            else {aloneSince=-1;if(tick>=deadline)startNext();}
        }
    }
    private void fail(Exception e){services.plugin().getLogger().log(java.util.logging.Level.SEVERE,"Party Games failed",e);services.messages().broadcast(text("Party Games остановлен из-за ошибки. Очки не сохранены."));services.session().endGame(EndReason.ABORTED);}
    @Override public void onSecondTick(long ignored){try{GameSession active=delegate();if(active!=null)active.onSecondTick(++childSeconds);hud();}catch(RuntimeException e){fail(e);}}
    private void hud(){
        if(state==null||phase!=Phase.BREAK)return;
        List<Component> lines=new ArrayList<>();lines.add(text("Раунд "+(index+1)+"/"+roundCount));
        lines.add(Component.text(phase==Phase.ACTIVE?PartyRoundFactory.title(state.schedule().get(index)):aloneSince>=0?"Ждём игроков: "+Math.max(0,(300-(tick-aloneSince)+19)/20)+" с":"Следующая игра: "+Math.max(0,(deadline-tick+19)/20)+" с",NamedTextColor.AQUA));
        for(var group:state.score().standings())for(UUID id:group){if(lines.size()>=14)break;lines.add(Component.text(names.get(id)+": "+state.score().points(id),NamedTextColor.WHITE));}
        for(Player p:services.session().audiencePlayers())services.sidebar().update(p,text("Party Games"),lines);
    }
    @Override public void onEnd(EndReason reason){
        if(phase==Phase.DONE)return;phase=Phase.DONE;
        if(ticker!=null)endStep(ticker::cancel);if(transition!=null)endStep(transition::cancel);
        PartyRoundSession endingChild=child;child=null;
        if(endingChild!=null)endStep(()->endingChild.onEnd(EndReason.ABORTED));
        if(state!=null)endStep(()->{List<Component> lines=new ArrayList<>();lines.add(text(complete?"Итоги Party Games":"Промежуточный счёт • очки не сохранены"));int place=1;
            for(var group:state.score().standings()){for(UUID id:group)lines.add(text(place+". "+names.get(id)+" • "+state.score().points(id)));place+=group.size();}summary=List.copyOf(lines);
            if(complete&&reason==EndReason.FINISHED&&!awarded){awarded=true;for(UUID id:names.keySet())services.award("party_games",id,state.score().points(id));}
        });
        if(context!=null){endStep(context::hideBars);context=null;}
        // ActiveSession restores everyone before destroying its worlds. In particular, a world
        // whose transition-time unload failed remains owned there for this final retry.
        // Never unload here: an exception must not prevent the core from scheduling restoration.
    }
    private void endStep(Runnable step){
        try{step.run();}catch(RuntimeException failure){
            java.util.logging.Logger.getLogger(PartyGamesSession.class.getName())
                    .log(java.util.logging.Level.SEVERE,"Party Games teardown step failed; continuing core cleanup",failure);
        }
    }
    @Override public List<Component> summary(){return summary;}
    @Override public Component globalResult(){return complete?text("Party Games завершён! Победители: "+String.join(", ",state.score().standings().getFirst().stream().map(names::get).toList())):text("Party Games остановлен");}
    @Override public boolean isInGame(Player p){return delegate()!=null&&child.isInGame(p);}
    @Override public boolean mayFollowPlayers(Player p){return delegate()!=null&&child.mayFollowPlayers(p);}
    @Override public Location spectatorLocation(Player p){return delegate()!=null?child.spectatorLocation(p):hub==null?null:new Location(hub,.5,83,.5);}
    @Override public void onParticipantQuit(Player p){state.disconnect(p.getUniqueId());if(child!=null)child.onParticipantQuit(p);}
    @Override public void onParticipantLeave(Player p){state.withdraw(p.getUniqueId());if(child!=null)child.onParticipantLeave(p);if(context!=null)context.services().bossBars().hideFrom(p);}
    @Override public void onParticipantRejoin(Player p){state.reconnect(p.getUniqueId());if(delegate()!=null)child.onParticipantRejoin(p);watch(p);}
    @Override public void onParticipantJoin(Player p){watch(p);}
    private void watch(Player p){if(delegate()==null){park(p);return;}internalTeleport=true;try{if(p.isDead())p.spigot().respawn();services.playerState().resetForRound(p,GameMode.SPECTATOR);Location at=child.spectatorLocation(p);if(at!=null)p.teleport(at);}finally{internalTeleport=false;}}
    @Override public void onPlayerTeleport(PlayerTeleportEvent e){if(!internalTeleport&&phase!=Phase.DONE)super.onPlayerTeleport(e);}
    @Override public void onPlayerRespawn(PlayerRespawnEvent e){if(delegate()!=null&&child.isInGame(e.getPlayer()))super.onPlayerRespawn(e);else if(hub!=null)e.setRespawnLocation(new Location(hub,.5,82,.5));}
    @Override public void onPlayerPostRespawn(PlayerPostRespawnEvent e){if(delegate()!=null)super.onPlayerPostRespawn(e);else if(hub!=null)park(e.getPlayer());}
}
