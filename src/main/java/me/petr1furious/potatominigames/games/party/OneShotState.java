package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-free ammunition, one-hit wards and kill-only standings, isolated by respawn life. */
final class OneShotState {
    static final long REFILL_TICKS = 200;
    static final int MAX_ARROWS = 64;

    private static final class Runner {
        int arrows, kills, deaths, life;
        boolean alive, shield;
        long refillAt = -1;
    }

    private final Map<UUID, Runner> players = new LinkedHashMap<>();
    private boolean closed;

    OneShotState(Collection<UUID> roster) {
        if (roster.isEmpty()
                || roster.size() > 16
                || roster.stream().distinct().count() != roster.size())
            throw new IllegalArgumentException(
                    "Distinct roster of one to sixteen players required");
        roster.forEach(id -> players.put(id, new Runner()));
    }

    boolean alive(UUID id) {
        Runner p = players.get(id);
        return p != null && p.alive;
    }

    int arrows(UUID id) {
        return players.get(id).arrows;
    }

    int kills(UUID id) {
        return players.get(id).kills;
    }

    int deaths(UUID id) {
        return players.get(id).deaths;
    }

    int life(UUID id) {
        return players.get(id).life;
    }

    boolean shield(UUID id) {
        return players.get(id).shield;
    }

    long refillIn(UUID id, long tick) {
        Runner p = players.get(id);
        return p.refillAt < 0 || !p.alive ? 0 : Math.max(0, p.refillAt - tick);
    }

    void spawn(UUID id) {
        if (closed) return;
        Runner p = players.get(id);
        p.alive = true;
        p.arrows = 1;
        p.shield = false;
        p.refillAt = -1;
        p.life++;
    }

    boolean fire(UUID id, long tick) {
        if (closed || !alive(id) || arrows(id) == 0) return false;
        Runner p = players.get(id);
        if (--p.arrows == 0) p.refillAt = tick + REFILL_TICKS;
        return true;
    }

    boolean addArrow(UUID id) {
        if (closed || !alive(id) || arrows(id) >= MAX_ARROWS) return false;
        Runner p = players.get(id);
        p.arrows++;
        p.refillAt = -1;
        return true;
    }

    boolean refill(UUID id, long tick) {
        if (closed || !alive(id)) return false;
        Runner p = players.get(id);
        if (p.arrows != 0 || p.refillAt < 0 || tick < p.refillAt) return false;
        return addArrow(id);
    }

    boolean giveShield(UUID id) {
        if (closed || !alive(id) || shield(id)) return false;
        players.get(id).shield = true;
        return true;
    }

    boolean block(UUID id) {
        if (closed || !alive(id) || !shield(id)) return false;
        players.get(id).shield = false;
        return true;
    }

    /** A duplicate death, suicide or absent/dead killer cannot award a kill or ammunition. */
    boolean death(UUID victim, UUID killer) {
        if (closed || !alive(victim)) return false;
        Runner dead = players.get(victim);
        dead.alive = false;
        dead.deaths++;
        dead.shield = false;
        dead.refillAt = -1;
        if (victim.equals(killer) || !alive(killer)) return false;
        players.get(killer).kills++;
        addArrow(killer);
        return true;
    }

    void disconnect(UUID id) {
        Runner p = players.get(id);
        if (p != null) {
            p.alive = false;
            p.shield = false;
            p.refillAt = -1;
        }
    }

    boolean validLife(UUID id, int life) {
        return !closed && alive(id) && players.get(id).life == life;
    }

    PartyRoundResult result(Collection<UUID> eligible) {
        return PartyRoundResult.ordered(
                players.keySet().stream().filter(eligible::contains).toList(),
                Comparator.<UUID>comparingInt(this::kills).reversed());
    }

    List<UUID> leaderboard(Collection<UUID> eligible) {
        return result(eligible).places().stream().flatMap(Collection::stream).toList();
    }

    void close() {
        closed = true;
    }
}
