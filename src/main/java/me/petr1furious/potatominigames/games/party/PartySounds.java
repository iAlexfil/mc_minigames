package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Short local cues; callers invoke countdown on their tick boundary, never every frame. */
final class PartySounds {
    private PartySounds(){}
    static void play(Collection<Player> players,Sound sound,float volume,float pitch){
        for(Player p:players)p.playSound(p.getLocation(),sound,volume,pitch);
    }
    static void countdown(Collection<Player> players,long ticksLeft){
        if(ticksLeft>0&&ticksLeft<=100&&ticksLeft%20==0)
            play(players,Sound.BLOCK_NOTE_BLOCK_HAT,.6f,ticksLeft<=20?1.8f:1.2f);
    }
}
