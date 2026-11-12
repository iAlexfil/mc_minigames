package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Actual death cohorts, independent from reconnect timers and online transport. */
public final class FinalsTeamWipesCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsTeamWipes"); }
        catch (ClassNotFoundException missing) {
            throw new AssertionError("Three dead Finals teammates need a shared sixteen-second respawn wave", missing);
        }
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object wipes = constructor.newInstance();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), enemy = UUID.randomUUID();
        Map<UUID, Integer> roster = new HashMap<>(Map.of(a, 0, b, 0, c, 0, enemy, 1));
        Set<UUID> withdrawn = new HashSet<>();
        require(call(wipes, "death", a, 10L, roster, withdrawn) == null, "First death retains ordinary respawn");
        require(call(wipes, "death", b, 20L, roster, withdrawn) == null, "Second death retains ordinary respawn");
        Object wave = call(wipes, "death", c, 30L, roster, withdrawn);
        require(wave != null && (Long) call(wave, "deadline") == 350L,
                "Third death creates one wave sixteen seconds after the last death");
        require(call(wave, "members").equals(Set.of(a, b, c)), "Only the wiped three-person team joins the wave");
        require(call(wipes, "death", c, 40L, roster, withdrawn) == null
                        && (Long) call(call(wipes, "wave", a), "deadline") == 350L,
                "Duplicate deaths cannot extend an already pending wipe");
        require((Boolean) call(wipes, "locked", b, 349L), "Revive and spawn remain locked on the final waiting tick");
        require(!(Boolean) call(wipes, "locked", b, 350L), "All three become eligible at the exact deadline");
        require(((List<?>) call(wipes, "due", 349L)).isEmpty()
                        && ((List<?>) call(wipes, "due", 350L)).equals(List.of(wave)),
                "Due-wave lookup observes the same exact deadline for the entire cohort");
        call(wipes, "release", wave);
        require(call(wipes, "wave", a) == null && !(Boolean) call(wipes, "dead", a),
                "Releasing a wave clears old deaths, including an offline member's life");
        call(wipes, "death", a, 300L, roster, withdrawn);
        call(wipes, "spawn", a);
        call(wipes, "death", b, 301L, roster, withdrawn);
        require(call(wipes, "death", c, 302L, roster, withdrawn) == null,
                "An individual respawn or pre-wipe revive prevents a stale three-death wipe");
        wave = call(wipes, "death", a, 303L, roster, withdrawn);
        require(wave != null && (Long) call(wave, "deadline") == 623L,
                "A later full-team death starts an independent new wipe");
        withdrawn.add(c);
        call(wipes, "withdraw", c);
        require((Boolean) call(wipes, "locked", a, 400L),
                "Permanent departure cannot cancel the remaining members' existing wipe penalty");
        call(wipes, "release", wave);
        call(wipes, "death", a, 600L, roster, withdrawn);
        require(call(wipes, "death", b, 601L, roster, withdrawn) == null,
                "A team reduced to two by permanent departure cannot trigger another three-person wipe");
        call(wipes, "clear");
        withdrawn.clear();
        UUID fourth = UUID.randomUUID();
        roster.put(fourth, 0);
        for (UUID id : Set.of(a, b, c, fourth))
            require(call(wipes, "death", id, 700L, roster, withdrawn) == null,
                    "Teams of four do not receive the three-person wipe rule");
        call(wipes, "clear");
        roster.remove(fourth);
        call(wipes, "death", a, Long.MAX_VALUE - 20, roster, withdrawn);
        call(wipes, "death", b, Long.MAX_VALUE - 10, roster, withdrawn);
        wave = call(wipes, "death", c, Long.MAX_VALUE - 1, roster, withdrawn);
        require((Long) call(wave, "deadline") == Long.MAX_VALUE, "Wipe deadlines saturate rather than overflowing");
        call(wipes, "clear");
        require(((List<?>) call(wipes, "due", Long.MAX_VALUE)).isEmpty(), "Match cleanup forgets all pending cohorts");
        System.out.println("FinalsTeamWipesCheck: " + checks + " checks passed");
    }

    private static Object call(Object receiver, String name, Object... args) throws Exception {
        Method method = java.util.Arrays.stream(receiver.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == args.length)
                .findFirst().orElseThrow(() -> new AssertionError("Missing wipe API: " + name));
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
