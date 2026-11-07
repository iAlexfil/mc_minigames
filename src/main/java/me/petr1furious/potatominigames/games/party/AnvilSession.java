package me.petr1furious.potatominigames.games.party;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import me.petr1furious.potatominigames.core.game.SessionServices;
final class AnvilSession extends PartySurvivalSession {
    private final int waveSeconds, moveSeconds, waves, pvpWave;private AnvilArena arena;private AnvilRain rain;private AnvilRound round;private final SumoCombat combat=new SumoCombat();
    AnvilSession(SessionServices s,PartyConfig c,PartyRoundContext context){super(s,c,"anvils","Наковальни",context);waveSeconds=c.anvilWaveSeconds.get();moveSeconds=c.anvilMoveSeconds.get();waves=c.anvilWaves.get();pvpWave=c.anvilPvpWave.get();}
    void buildArena(){arena=new AnvilArena(world);arena.build();round=new AnvilRound(names.size(),waveSeconds*20,moveSeconds*20,waves,pvpWave);arena.relocateShelters(round.shelters(),random);rain=new AnvilRain(world,arena,random,this::eliminate);}
    Location spawn(int i,int n){return arena.spawn(i,n);}Location viewpoint(){return new Location(world,.5,91,.5,0,80);}
    double outHeight(){return 77;}int durationSeconds(){return waves*(waveSeconds+moveSeconds)+5;}boolean podiumAwards(){return true;}
    String instructions(){return "Прячься под полублоками или уклоняйся! Между волнами укрытия меняются. С "+pvpWave+"-й волны можно толкаться.";}
    void beginPlay(){round.start(elapsed());}
    void playTick(long elapsed){
        if(round.advance(elapsed)){
            rain.clear();
            if(round.done()){finish(standings.survivors());return;}
            if(!round.raining()){arena.relocateShelters(round.shelters(),random);announceTitle("Перебежка!","Укрытия переместились • "+moveSeconds+" с",2);}
            else announceTitle("Волна "+round.wave(),round.pvpEnabled()?"Можно толкаться!":"Укройся или уворачивайся",2);
        }
        if(round.raining())rain.tick(elapsed,round.wave(),alivePlayers());
    }
    String status(){return "Волна "+round.wave()+" • "+(round.raining()?"Ливень":"Перебежка")+" • "+round.remaining(elapsed())+" с";}
    @Override protected void handleDamage(EntityDamageEvent e){if(e instanceof EntityDamageByEntityEvent attack)handleAttack(attack);e.setCancelled(true);}
    @Override protected void handleAttack(EntityDamageByEntityEvent e){
        e.setCancelled(true);
        if(!playing()||!round.pvpEnabled()||!(e.getEntity() instanceof Player victim)||!(e.getDamager() instanceof Player attacker)
                ||!alive(victim)||!alive(attacker)||victim.equals(attacker)||!combat.acceptHit(victim.getUniqueId(),Bukkit.getCurrentTick()))return;
        victim.setVelocity(SumoCombat.knockback(victim.getLocation().toVector().subtract(attacker.getLocation().toVector()),victim.getVelocity(),attacker.isSprinting(),0));
    }
    void cleanup(){if(rain!=null)rain.clear();combat.reset();}
}
