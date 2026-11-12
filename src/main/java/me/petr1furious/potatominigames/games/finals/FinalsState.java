package me.petr1furious.potatominigames.games.finals;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-independent rules for one vault and one cashbox shared by two teams.
 *
 * <p>All timestamps are absolute, monotonically increasing game ticks. The session owns locations,
 * respawns and channel conditions; it must cancel interrupted steals and explicitly complete a
 * valid channel. Every timed action processes due payouts before accepting the action.
 */
public final class FinalsState {
    public enum Phase {
        VAULT_CLOSED,
        VAULT_OPENING,
        CASHBOX_AVAILABLE,
        CARRIED,
        CASHOUT,
        FINISHED
    }

    public record Rules(
            long vaultOpenTicks,
            long cashoutTicks,
            long stealTicks,
            long matchTicks,
            int cashoutValue,
            int targetScore) {
        public Rules {
            if (vaultOpenTicks <= 0
                    || cashoutTicks <= 0
                    || stealTicks <= 0
                    || matchTicks <= 0
                    || cashoutValue <= 0
                    || targetScore <= 0) {
                throw new IllegalArgumentException(
                        "Timings, cashout value and target must be positive");
            }
        }

        public static Rules defaults() {
            return new Rules(100, 1_800, 100, 7_200, 10_000, 20_000);
        }
    }

    private final Map<UUID, Integer> roster;
    private final Set<UUID> connected;
    private final Set<UUID> withdrawn = new HashSet<>();
    private final Map<UUID, Long> stealDeadlines = new LinkedHashMap<>();
    private final int[] scores = new int[2];
    private final Rules rules;
    private final long matchDeadline;
    private long lastTick;
    private Phase phase = Phase.VAULT_CLOSED;
    private UUID carrier;
    private int ownerTeam = -1;
    private int station = -1;
    private long phaseDeadline = -1;
    private Set<Integer> winningTeams = Set.of();

    public FinalsState(Map<UUID, Integer> roster, long startTick) {
        this(roster, startTick, Rules.defaults());
    }

    public FinalsState(Map<UUID, Integer> roster, long startTick, Rules rules) {
        if (roster == null || roster.isEmpty() || startTick < 0 || rules == null) {
            throw new IllegalArgumentException(
                    "Roster, rules and a nonnegative start tick required");
        }
        Map<UUID, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : roster.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || !validTeam(entry.getValue())) {
                throw new IllegalArgumentException("Roster requires UUIDs assigned to team 0 or 1");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        this.roster = Collections.unmodifiableMap(copy);
        this.connected = new LinkedHashSet<>(copy.keySet());
        this.rules = rules;
        this.lastTick = startTick;
        this.matchDeadline = deadline(startTick, rules.matchTicks());
    }

    /** Applies scheduled transitions in order, even when the caller skips ticks. */
    public void tick(long now) {
        if (now < lastTick) throw new IllegalArgumentException("Game time cannot move backwards");
        lastTick = now;
        if (isFinished()) return;

        if (phaseDeadline >= 0 && phaseDeadline <= now && phaseDeadline <= matchDeadline) {
            if (phase == Phase.VAULT_OPENING) {
                phase = Phase.CASHBOX_AVAILABLE;
                phaseDeadline = -1;
            } else if (phase == Phase.CASHOUT) {
                int paidTeam = ownerTeam;
                scores[paidTeam] =
                        (int)
                                Math.min(
                                        Integer.MAX_VALUE,
                                        (long) scores[paidTeam] + rules.cashoutValue());
                resetObjective();
                if (scores[paidTeam] >= rules.targetScore() && hasRemainingTeam(paidTeam)) {
                    finish(Set.of(paidTeam));
                }
            }
        }
        if (!isFinished() && now >= matchDeadline) finishTimeout();
    }

    public boolean activateVault(UUID player, long now) {
        tick(now);
        if (!isActive(player) || phase != Phase.VAULT_CLOSED) return false;
        phase = Phase.VAULT_OPENING;
        phaseDeadline = deadline(now, rules.vaultOpenTicks());
        return true;
    }

    public boolean pickUp(UUID player, long now) {
        tick(now);
        if (!isActive(player) || phase != Phase.CASHBOX_AVAILABLE) return false;
        carrier = player;
        phase = Phase.CARRIED;
        return true;
    }

    public boolean drop(UUID player, long now) {
        tick(now);
        if (!isActive(player) || phase != Phase.CARRIED || !player.equals(carrier)) return false;
        dropCarriedBox(player);
        return true;
    }

    public boolean deposit(UUID player, int station, long now) {
        tick(now);
        if (!isActive(player)
                || phase != Phase.CARRIED
                || !player.equals(carrier)
                || !validTeam(station)) return false;
        beginCashout(player, station, now);
        return true;
    }

    /** The session verifies the actual thrown item's owner and collision with this station. */
    public boolean depositThrown(UUID thrower, int station, long now) {
        tick(now);
        if (phase != Phase.CASHBOX_AVAILABLE
                || !validTeam(station)
                || !roster.containsKey(thrower)
                || withdrawn.contains(thrower)) return false;
        // An in-flight item retains ownership after death or temporary disconnection.
        beginCashout(thrower, station, now);
        return true;
    }

    private void beginCashout(UUID player, int station, long now) {
        carrier = null;
        ownerTeam = teamOf(player);
        this.station = station;
        phase = Phase.CASHOUT;
        phaseDeadline = deadline(now, rules.cashoutTicks());
    }

    public boolean beginSteal(UUID player, long now) {
        tick(now);
        if (!isActive(player)
                || phase != Phase.CASHOUT
                || teamOf(player) == ownerTeam
                || stealDeadlines.containsKey(player)) return false;
        stealDeadlines.put(player, deadline(now, rules.stealTicks()));
        return true;
    }

    public boolean cancelSteal(UUID player) {
        return stealDeadlines.remove(player) != null;
    }

    /** The session must verify proximity, sneaking and uninterrupted channel conditions first. */
    public boolean completeSteal(UUID player, long now) {
        tick(now);
        Long channelDeadline = stealDeadlines.get(player);
        if (!isActive(player)
                || phase != Phase.CASHOUT
                || teamOf(player) == ownerTeam
                || channelDeadline == null
                || now < channelDeadline) return false;
        ownerTeam = teamOf(player);
        stealDeadlines.clear();
        return true;
    }

    /** Drops objective items and cancels channels; the session owns respawn eligibility. */
    public boolean death(UUID player, long now) {
        tick(now);
        if (!isActive(player) || isFinished()) return false;
        dropCarriedBox(player);
        cancelSteal(player);
        return true;
    }

    public boolean disconnect(UUID player, long now) {
        tick(now);
        if (!connected.remove(player)) return false;
        dropCarriedBox(player);
        cancelSteal(player);
        return true;
    }

    public boolean reconnect(UUID player, long now) {
        tick(now);
        if (!roster.containsKey(player) || withdrawn.contains(player) || isFinished()) return false;
        return connected.add(player);
    }

    /** Permanently withdraws a participant while retaining their immutable team assignment. */
    public boolean withdraw(UUID player, long now) {
        tick(now);
        if (!roster.containsKey(player) || !withdrawn.add(player)) return false;
        connected.remove(player);
        dropCarriedBox(player);
        cancelSteal(player);
        return true;
    }

    public Phase phase() {
        return phase;
    }

    public UUID carrier() {
        return carrier;
    }

    public int ownerTeam() {
        return ownerTeam;
    }

    public int station() {
        return station;
    }

    /** Returns -1 when the current phase has no deadline. */
    public long phaseDeadline() {
        return phaseDeadline;
    }

    public long matchDeadline() {
        return matchDeadline;
    }

    public int score(int team) {
        if (!validTeam(team)) throw new IllegalArgumentException("Team must be 0 or 1");
        return scores[team];
    }

    public Map<Integer, Integer> scores() {
        return Map.of(0, scores[0], 1, scores[1]);
    }

    public int teamOf(UUID player) {
        return roster.getOrDefault(player, -1);
    }

    public boolean isActive(UUID player) {
        return connected.contains(player) && !withdrawn.contains(player);
    }

    public boolean isWithdrawn(UUID player) {
        return withdrawn.contains(player);
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public boolean isDraw() {
        return isFinished() && winningTeams.isEmpty();
    }

    /** Empty before completion and for a draw. */
    public Set<Integer> winningTeams() {
        return winningTeams;
    }

    public Set<UUID> winners() {
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID player : connected) {
            if (isActive(player) && winningTeams.contains(teamOf(player))) result.add(player);
        }
        return Collections.unmodifiableSet(result);
    }

    public Map<UUID, Long> stealDeadlines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(stealDeadlines));
    }

    private void dropCarriedBox(UUID player) {
        if (player != null && player.equals(carrier)) {
            carrier = null;
            phase = Phase.CASHBOX_AVAILABLE;
        }
    }

    private void resetObjective() {
        carrier = null;
        ownerTeam = -1;
        station = -1;
        phaseDeadline = -1;
        stealDeadlines.clear();
        phase = Phase.VAULT_CLOSED;
    }

    private void finishTimeout() {
        // Temporary disconnection changes award recipients, never the cash standings.
        boolean red = hasRemainingTeam(0);
        boolean blue = hasRemainingTeam(1);
        if (red && (!blue || scores[0] > scores[1])) finish(Set.of(0));
        else if (blue && (!red || scores[1] > scores[0])) finish(Set.of(1));
        else finish(Set.of());
    }

    private void finish(Set<Integer> teams) {
        resetObjective();
        winningTeams = Set.copyOf(teams);
        phase = Phase.FINISHED;
    }

    private boolean hasRemainingTeam(int team) {
        return roster.entrySet().stream()
                .anyMatch(entry -> entry.getValue() == team && !withdrawn.contains(entry.getKey()));
    }

    private static boolean validTeam(int team) {
        return team == 0 || team == 1;
    }

    private static long deadline(long now, long duration) {
        return duration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + duration;
    }
}
