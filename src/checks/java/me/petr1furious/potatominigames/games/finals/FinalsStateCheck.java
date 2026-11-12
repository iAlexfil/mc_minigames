package me.petr1furious.potatominigames.games.finals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-free checks for the cashbox, cashout and participant lifecycle. */
public final class FinalsStateCheck {
    private static final UUID RED = new UUID(0, 1);
    private static final UUID RED_MATE = new UUID(0, 2);
    private static final UUID BLUE = new UUID(0, 3);
    private static final UUID UNKNOWN = new UUID(0, 4);
    private static final Map<UUID, Integer> ROSTER = Map.of(RED, 0, RED_MATE, 0, BLUE, 1);
    private static int checks;

    public static void main(String[] args) {
        thrownCashoutLifecycle();
        thrownCashoutEligibilityAndDeadlines();
        vaultAndCashboxLifecycle();
        cashoutScoresOnceAndResets();
        targetVictory();
        stealMustCompleteWithoutCancellation();
        payoutWinsSimultaneousSteal();
        lifecycleAndRoster();
        timeoutAndEligibility();
        skippedTicksRespectTimeout();
        invalidInputsAndImmutableViews();
        System.out.println("FinalsStateCheck: " + checks + " checks passed");
    }

    private static void thrownCashoutLifecycle() {
        FinalsState state = new FinalsState(ROSTER, 0);
        check(!depositThrown(state, RED, 0, 0), "a thrown deposit cannot bypass a closed vault");
        state.activateVault(RED, 0);
        check(!depositThrown(state, RED, 0, 99), "a thrown deposit cannot bypass vault opening");
        state.pickUp(RED, 100);
        check(!depositThrown(state, BLUE, 0, 100), "a thrown deposit cannot consume another player's carried cashbox");
        state.drop(RED, 100);
        check(!depositThrown(state, RED, -1, 100) && !depositThrown(state, RED, 2, 100),
                "thrown cashboxes reject invalid cashout indices");
        check(!depositThrown(state, UNKNOWN, 0, 100) && !depositThrown(state, null, 0, 100),
                "only a known thrower can own a thrown cashout");
        check(state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE && state.ownerTeam() == -1,
                "rejected throws leave the loose cashbox and ownership intact");
        check(depositThrown(state, RED, 1, 101), "a loose thrown cashbox deposits atomically on arrival");
        check(state.phase() == FinalsState.Phase.CASHOUT && state.carrier() == null
                        && state.ownerTeam() == 0 && state.station() == 1,
                "thrown deposit credits the thrower's team and station without an intermediate carrier");
        check(state.phaseDeadline() == 901 && state.score(0) == 0 && state.stealDeadlines().isEmpty(),
                "arrival starts a complete cashout duration and awards no immediate cash");
        check(!depositThrown(state, BLUE, 0, 102) && !state.deposit(RED, 0, 102),
                "duplicate collision and normal deposit cannot replace an ongoing thrown cashout");
        check(state.ownerTeam() == 0 && state.station() == 1 && state.phaseDeadline() == 901,
                "duplicate arrivals preserve ownership, destination and original payout deadline");
        check(state.beginSteal(BLUE, 200) && state.completeSteal(BLUE, 300),
                "a thrown cashout uses normal enemy steal rules");
        check(state.ownerTeam() == 1 && state.phaseDeadline() == 901,
                "stealing a thrown cashout never resets its arrival deadline");
        state.tick(900);
        check(state.score(1) == 0, "a thrown cashout never pays early");
        state.tick(901);
        check(state.score(1) == 10_000 && state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "a thrown cashout pays its final owner once and resets the objective");
        state.tick(902);
        check(state.score(1) == 10_000, "a completed thrown cashout cannot pay twice");

        FinalsState simultaneous = launched(RED, FinalsState.Rules.defaults());
        check(depositThrown(simultaneous, RED, 0, 100), "fixture deposits a thrown cashbox");
        simultaneous.beginSteal(BLUE, 800);
        check(!simultaneous.completeSteal(BLUE, 900) && simultaneous.score(0) == 10_000,
                "payout still wins an exact simultaneous steal after a thrown deposit");
    }

    private static void thrownCashoutEligibilityAndDeadlines() {
        FinalsState disconnected = launched(BLUE, FinalsState.Rules.defaults());
        disconnected.disconnect(BLUE, 100);
        check(!disconnected.isActive(BLUE) && depositThrown(disconnected, BLUE, 1, 101),
                "an already launched cashbox retains credit after temporary disconnection");
        check(disconnected.ownerTeam() == 1 && disconnected.carrier() == null,
                "disconnected throwers are credited without reconnecting or picking up the box");
        FinalsState dead = launched(RED, FinalsState.Rules.defaults());
        dead.death(RED, 100);
        check(depositThrown(dead, RED, 0, 101), "thrower death never cancels an already launched cashbox");
        FinalsState withdrawn = launched(RED, FinalsState.Rules.defaults());
        withdrawn.withdraw(RED, 100);
        check(!depositThrown(withdrawn, RED, 0, 101)
                        && withdrawn.phase() == FinalsState.Phase.CASHBOX_AVAILABLE,
                "permanently withdrawn throwers cannot start a cashout");

        FinalsState beforeDeadline = launched(RED, rules(150));
        check(depositThrown(beforeDeadline, RED, 0, 149), "arrival immediately before match timeout is valid");
        beforeDeadline.tick(150);
        check(beforeDeadline.isFinished() && beforeDeadline.score(0) == 0,
                "a late thrown deposit cannot extend the match to reach its payout");
        FinalsState exactDeadline = launched(RED, rules(150));
        check(!depositThrown(exactDeadline, RED, 0, 150) && exactDeadline.isFinished(),
                "match timeout is processed before a simultaneous thrown arrival");
        check(!depositThrown(exactDeadline, RED, 1, 151), "finished matches reject later thrown collisions");
        throwsType(IllegalArgumentException.class, () -> depositThrown(exactDeadline, RED, 0, 149),
                "thrown arrival timestamps obey the existing monotonic clock");
    }

    private static FinalsState launched(UUID thrower, FinalsState.Rules rules) {
        FinalsState state = new FinalsState(ROSTER, 0, rules);
        check(state.activateVault(thrower, 0) && state.pickUp(thrower, 100) && state.drop(thrower, 100),
                "fixture launches the actual carrier's opened cashbox");
        return state;
    }

    private static boolean depositThrown(FinalsState state, UUID thrower, int station, long now) {
        try {
            return (Boolean) FinalsState.class.getMethod("depositThrown", UUID.class, int.class, long.class)
                    .invoke(state, thrower, station, now);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("FinalsState must atomically accept a thrown cashbox at a cashout", missing);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw new AssertionError("Unexpected thrown-deposit failure", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Thrown deposits must be available to the session", failure);
        }
    }

    private static void vaultAndCashboxLifecycle() {
        FinalsState state = new FinalsState(ROSTER, 1_000);
        check(!state.pickUp(RED, 1_000), "closed vault has no cashbox");
        check(state.activateVault(RED, 1_000), "active participant opens vault");
        check(!state.activateVault(BLUE, 1_020), "opening cannot restart");
        state.tick(1_099);
        check(state.phase() == FinalsState.Phase.VAULT_OPENING, "vault waits full five seconds");
        check(!state.pickUp(RED, 1_099), "cashbox cannot be picked early");
        check(state.pickUp(RED, 1_100), "opening deadline makes cashbox available");
        check(!state.pickUp(BLUE, 1_100), "only one carrier");
        check(!state.drop(BLUE, 1_100), "other players cannot drop carrier's box");
        check(!state.deposit(BLUE, 0, 1_100), "other players cannot deposit carrier's box");
        check(state.drop(RED, 1_101), "carrier drops box");
        check(
                state.carrier() == null && state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE,
                "dropped cashbox remains available");
        check(!state.drop(RED, 1_101), "duplicate drop rejected");
        check(state.pickUp(BLUE, 1_101), "opponent can recover dropped box");
        check(state.deposit(BLUE, 1, 1_102), "carrier deposits at second station");
        check(
                state.ownerTeam() == 1 && state.station() == 1 && state.carrier() == null,
                "deposit records team and station and consumes carried box");
        check(state.phaseDeadline() == 1_902, "cashout lasts full forty seconds");
        check(
                !state.deposit(BLUE, 0, 1_102) && !state.pickUp(RED, 1_102),
                "deposited box cannot be duplicated");
    }

    private static void cashoutScoresOnceAndResets() {
        FinalsState state = deposited(0, RED, 0);
        state.tick(899);
        check(state.score(0) == 0, "cashout does not pay early");
        state.tick(900);
        check(state.score(0) == 10_000 && state.score(1) == 0, "cashout pays its owning team");
        check(
                state.phase() == FinalsState.Phase.VAULT_CLOSED && state.phaseDeadline() == -1,
                "payout resets vault");
        check(state.ownerTeam() == -1 && state.station() == -1, "payout clears old cashout");
        state.tick(900);
        state.tick(901);
        check(state.score(0) == 10_000, "same cashout pays only once");
    }

    private static void targetVictory() {
        FinalsState state = deposited(0, RED, 0);
        state.tick(900);
        check(state.activateVault(BLUE, 900), "next vault can activate");
        check(state.pickUp(RED_MATE, 1_000), "next cashbox opens normally");
        check(state.deposit(RED_MATE, 1, 1_000), "teammate deposits next cashbox");
        state.tick(1_800);
        check(state.isFinished() && !state.isDraw(), "target score finishes match");
        check(
                state.score(0) == 20_000 && state.winningTeams().equals(Set.of(0)),
                "first team to target wins");
        check(state.winners().equals(Set.of(RED, RED_MATE)), "whole winning team receives victory");
        check(
                !state.activateVault(BLUE, 1_801) && !state.pickUp(BLUE, 1_801),
                "finished match rejects gameplay");
        state.tick(9_000);
        check(
                state.score(0) == 20_000 && state.winningTeams().equals(Set.of(0)),
                "finished result cannot pay again or change at timeout");

        FinalsState offlineCashout =
                new FinalsState(
                        ROSTER, 0, new FinalsState.Rules(100, 800, 100, 7_200, 10_000, 10_000));
        offlineCashout.activateVault(RED, 0);
        offlineCashout.pickUp(RED, 100);
        offlineCashout.deposit(RED, 0, 100);
        offlineCashout.disconnect(RED, 899);
        offlineCashout.disconnect(RED_MATE, 899);
        offlineCashout.tick(900);
        check(
                offlineCashout.isFinished()
                        && offlineCashout.winningTeams().equals(Set.of(0))
                        && offlineCashout.winners().isEmpty(),
                "target cashout wins for a temporarily offline team without awarding offline"
                        + " members");
    }

    private static void stealMustCompleteWithoutCancellation() {
        FinalsState state = deposited(0, RED, 0);
        check(!state.beginSteal(RED_MATE, 100), "own team cannot steal");
        check(state.beginSteal(BLUE, 100), "enemy starts steal");
        check(!state.beginSteal(BLUE, 120), "repeated interaction does not restart channel");
        check(state.stealDeadlines().get(BLUE) == 200, "channel keeps original deadline");
        check(!state.completeSteal(BLUE, 199), "steal cannot complete early");
        check(state.cancelSteal(BLUE), "distance, sneak or damage interruption cancels channel");
        check(!state.completeSteal(BLUE, 200), "cancelled steal cannot complete");
        check(state.beginSteal(BLUE, 200), "cancelled channel can restart");
        state.tick(300);
        check(state.ownerTeam() == 0, "time alone does not validate steal channel");
        check(state.completeSteal(BLUE, 300), "validated uninterrupted channel steals");
        check(
                state.ownerTeam() == 1 && state.phaseDeadline() == 900,
                "steal changes ownership without extending cashout");
        check(!state.completeSteal(BLUE, 300), "channel cannot complete twice");
        check(
                state.beginSteal(RED, 300) && state.beginSteal(RED_MATE, 300),
                "two opponents may channel concurrently");
        check(state.completeSteal(RED, 400), "first completed opponent retakes ownership");
        check(
                !state.completeSteal(RED_MATE, 400) && state.stealDeadlines().isEmpty(),
                "ownership change invalidates all stale channels");
        state.tick(900);
        check(state.score(0) == 10_000, "final owner gets payout");
    }

    private static void payoutWinsSimultaneousSteal() {
        FinalsState state = deposited(0, RED, 0);
        check(state.beginSteal(BLUE, 800), "steal starts one channel duration before payout");
        check(!state.completeSteal(BLUE, 900), "payout wins simultaneous steal boundary");
        check(
                state.score(0) == 10_000 && state.score(1) == 0,
                "simultaneous steal cannot divert payout");
        check(state.stealDeadlines().isEmpty(), "payout clears channels");
    }

    private static void lifecycleAndRoster() {
        FinalsState state = new FinalsState(ROSTER, 0);
        state.activateVault(RED, 0);
        state.pickUp(RED, 100);
        check(state.death(RED, 100) && state.carrier() == null, "death drops carried box");
        check(state.pickUp(BLUE, 100), "death leaves one recoverable cashbox");
        check(state.disconnect(BLUE, 100), "disconnect deactivates participant");
        check(
                state.phase() == FinalsState.Phase.CASHBOX_AVAILABLE && state.carrier() == null,
                "disconnect drops carried box");
        check(!state.pickUp(BLUE, 100), "disconnected player cannot interact");
        check(
                state.reconnect(BLUE, 100) && state.teamOf(BLUE) == 1,
                "reconnect restores fixed original team");
        check(
                state.pickUp(BLUE, 100) && state.deposit(BLUE, 0, 100),
                "reconnected player can deposit");
        state.beginSteal(RED, 100);
        state.death(RED, 150);
        check(!state.completeSteal(RED, 200), "death interrupts steal");
        state.beginSteal(RED, 200);
        state.disconnect(RED, 250);
        check(!state.completeSteal(RED, 300), "disconnect interrupts steal");
        check(
                state.withdraw(RED_MATE, 300) && state.isWithdrawn(RED_MATE),
                "explicit withdrawal is permanent");
        check(
                !state.reconnect(RED_MATE, 300) && !state.beginSteal(RED_MATE, 300),
                "withdrawn player cannot rejoin or interact");
        check(state.teamOf(RED_MATE) == 0, "withdrawal retains fixed team assignment");
    }

    private static void timeoutAndEligibility() {
        FinalsState tie = new FinalsState(ROSTER, 0, rules(1_000));
        tie.tick(1_000);
        check(tie.isDraw() && tie.winners().isEmpty(), "equal timeout scores draw");

        FinalsState leading = new FinalsState(ROSTER, 0, rules(1_000));
        leading.activateVault(RED, 0);
        leading.pickUp(RED, 100);
        leading.deposit(RED, 0, 100);
        leading.tick(900);
        leading.withdraw(RED_MATE, 950);
        leading.tick(1_000);
        check(
                leading.winningTeams().equals(Set.of(0)) && leading.winners().equals(Set.of(RED)),
                "timeout leader wins and withdrawn teammate receives no victory");

        FinalsState departed = new FinalsState(ROSTER, 0, rules(1_000));
        departed.activateVault(RED, 0);
        departed.pickUp(RED, 100);
        departed.deposit(RED, 0, 100);
        departed.tick(900);
        departed.withdraw(RED, 950);
        departed.disconnect(RED_MATE, 950);
        departed.tick(1_000);
        check(
                departed.winningTeams().equals(Set.of(0)) && departed.winners().isEmpty(),
                "disconnected score leader keeps its standing but receives no player award");

        FinalsState disconnectAtDeadline = new FinalsState(ROSTER, 0, rules(1_000));
        disconnectAtDeadline.activateVault(RED, 0);
        disconnectAtDeadline.pickUp(RED, 100);
        disconnectAtDeadline.deposit(RED, 0, 100);
        disconnectAtDeadline.tick(900);
        disconnectAtDeadline.disconnect(RED, 999);
        disconnectAtDeadline.disconnect(RED_MATE, 999);
        disconnectAtDeadline.tick(1_000);
        check(
                disconnectAtDeadline.winningTeams().equals(Set.of(0))
                        && disconnectAtDeadline.score(1) == 0,
                "last-second disconnect cannot make the lower-scoring connected team win");

        FinalsState withdrawnTeam = new FinalsState(ROSTER, 0, rules(1_000));
        withdrawnTeam.activateVault(RED, 0);
        withdrawnTeam.pickUp(RED, 100);
        withdrawnTeam.deposit(RED, 0, 100);
        withdrawnTeam.tick(900);
        withdrawnTeam.withdraw(RED, 950);
        withdrawnTeam.withdraw(RED_MATE, 950);
        withdrawnTeam.tick(1_000);
        check(
                withdrawnTeam.winningTeams().equals(Set.of(1)),
                "team whose entire roster withdrew remains ineligible");

        FinalsState empty = new FinalsState(ROSTER, 0, rules(1_000));
        ROSTER.keySet().forEach(id -> empty.withdraw(id, 0));
        empty.tick(1_000);
        check(empty.isDraw() && empty.winners().isEmpty(), "no eligible players produces draw");
    }

    private static void skippedTicksRespectTimeout() {
        FinalsState lateCashout = new FinalsState(ROSTER, 0, rules(899));
        lateCashout.activateVault(RED, 0);
        lateCashout.pickUp(RED, 100);
        lateCashout.deposit(RED, 0, 100);
        lateCashout.tick(2_000);
        check(
                lateCashout.isDraw() && lateCashout.score(0) == 0,
                "skipped ticks never pay cashout scheduled after match timeout");

        FinalsState atTimeout = new FinalsState(ROSTER, 0, rules(900));
        atTimeout.activateVault(RED, 0);
        atTimeout.pickUp(RED, 100);
        atTimeout.deposit(RED, 0, 100);
        atTimeout.tick(2_000);
        check(
                atTimeout.score(0) == 10_000 && atTimeout.winningTeams().equals(Set.of(0)),
                "cashout exactly at timeout pays before timeout standings");

        FinalsState custom = new FinalsState(ROSTER, 10, new FinalsState.Rules(2, 3, 1, 20, 7, 7));
        custom.activateVault(BLUE, 10);
        custom.pickUp(BLUE, 12);
        custom.deposit(BLUE, 1, 12);
        custom.tick(15);
        check(
                custom.score(1) == 7 && custom.isFinished(),
                "custom timings, value and target apply");
    }

    private static void invalidInputsAndImmutableViews() {
        FinalsState state = new FinalsState(ROSTER, 0);
        check(
                !state.activateVault(UNKNOWN, 0) && !state.reconnect(UNKNOWN, 0),
                "unknown player rejected");
        check(
                !state.activateVault(null, 0) && state.teamOf(UNKNOWN) == -1,
                "null and unknown roster lookups safe");
        state.activateVault(RED, 0);
        state.pickUp(RED, 100);
        check(
                !state.deposit(RED, -1, 100) && !state.deposit(RED, 2, 100),
                "invalid station rejected");
        check(state.carrier().equals(RED), "invalid deposit preserves carried box");
        throwsType(
                IllegalArgumentException.class,
                () -> state.score(2),
                "invalid team query rejected");
        throwsType(IllegalArgumentException.class, () -> state.tick(99), "time cannot rewind");
        throwsType(
                IllegalArgumentException.class,
                () -> new FinalsState(Map.of(RED, 2), 0),
                "invalid roster team rejected");
        throwsType(
                IllegalArgumentException.class,
                () -> new FinalsState(Map.of(), 0),
                "empty roster rejected");
        throwsType(
                IllegalArgumentException.class,
                () -> new FinalsState.Rules(0, 1, 1, 1, 1, 1),
                "nonpositive timing rejected");
        throwsType(
                IllegalArgumentException.class,
                () -> new FinalsState.Rules(1, 1, 1, 1, 1, 0),
                "nonpositive target rejected");
        throwsType(
                UnsupportedOperationException.class,
                () -> state.scores().put(0, 100_000),
                "score snapshot is immutable");
        throwsType(
                UnsupportedOperationException.class,
                () -> state.stealDeadlines().put(BLUE, 0L),
                "channel snapshot is immutable");
        Map<UUID, Integer> original = new LinkedHashMap<>(ROSTER);
        FinalsState copied = new FinalsState(original, 0);
        original.put(RED, 1);
        check(copied.teamOf(RED) == 0, "constructor copies fixed roster");
    }

    private static FinalsState deposited(long start, UUID player, int station) {
        FinalsState state = new FinalsState(ROSTER, start);
        check(state.activateVault(player, start), "fixture activates vault");
        check(state.pickUp(player, start + 100), "fixture picks up box");
        check(state.deposit(player, station, start + 100), "fixture deposits box");
        return state;
    }

    private static FinalsState.Rules rules(long matchTicks) {
        return new FinalsState.Rules(100, 800, 100, matchTicks, 10_000, 20_000);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void throwsType(
            Class<? extends Throwable> expected, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable failure) {
            check(expected.isInstance(failure), message + ": wrong exception " + failure);
            return;
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }
}
