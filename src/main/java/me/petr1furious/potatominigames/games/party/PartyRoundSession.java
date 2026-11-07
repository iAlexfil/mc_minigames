package me.petr1furious.potatominigames.games.party;
import me.petr1furious.potatominigames.core.game.GameSession;
interface PartyRoundSession extends GameSession {
    PartyRoundResult roundResult();
    default boolean ready() { return true; }
}
