package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/** Enemy death credit belongs to one victim life and persists for the complete match. */
public final class FinalsKillsCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsKills");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Finals must retain enemy kill counts for each match", missing);
        }
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object kills = constructor.newInstance();
        Method death = method(type, "death", UUID.class, UUID.class, Map.class);
        Method spawn = method(type, "spawn", UUID.class);
        Method snapshot = method(type, "snapshot");
        UUID red = UUID.randomUUID(), redMate = UUID.randomUUID(), blue = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Map<UUID, Integer> roster = Map.of(red, 0, redMate, 0, blue, 1);

        require((Boolean) death.invoke(kills, blue, red, roster), "An enemy death credits its killer");
        require(count(kills, snapshot, red) == 1, "The credited killer has one kill");
        require(!(Boolean) death.invoke(kills, blue, red, roster), "Duplicate death notifications cannot count twice");
        require(count(kills, snapshot, red) == 1, "Repeated death credit leaves the count unchanged");
        Map<UUID, Integer> first = view(kills, snapshot);
        spawn.invoke(kills, blue);
        require((Boolean) death.invoke(kills, blue, red, roster), "The next life can produce another credited kill");
        require(count(kills, snapshot, red) == 2, "Respawning the victim preserves and increments match totals");
        spawn.invoke(kills, red);
        require(count(kills, snapshot, red) == 2, "Respawning the killer never clears their kills");
        require(first.get(red) == 1, "Published kill snapshots do not change after later deaths");
        try {
            first.put(red, 100);
            throw new AssertionError("Kill snapshots must be read-only");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }

        require(!(Boolean) death.invoke(kills, red, red, roster), "Suicide never credits a kill");
        spawn.invoke(kills, red);
        require(!(Boolean) death.invoke(kills, red, redMate, roster), "Friendly deaths never credit a kill");
        spawn.invoke(kills, red);
        require(!(Boolean) death.invoke(kills, red, outsider, roster), "Noncontestants cannot receive kill credit");
        spawn.invoke(kills, red);
        require(!(Boolean) death.invoke(kills, red, null, roster), "Unattributed deaths do not invent a killer");
        require(!(Boolean) death.invoke(kills, red, blue, roster),
                "An uncredited death cannot later be credited by a duplicate notification");
        require(!(Boolean) death.invoke(kills, outsider, red, roster), "Noncontestant deaths do not count");
        require(count(kills, snapshot, red) == 2 && count(kills, snapshot, redMate) == 0
                        && count(kills, snapshot, blue) == 0,
                "Rejected deaths leave every existing count intact");

        spawn.invoke(kills, red);
        require((Boolean) death.invoke(kills, red, blue, roster), "Enemy credit works symmetrically for blue");
        require(count(kills, snapshot, blue) == 1 && count(kills, snapshot, red) == 2,
                "Each contestant keeps their independent total");
        Object nextMatch = constructor.newInstance();
        require(view(nextMatch, snapshot).isEmpty(), "A new match begins with no carried-over kills");
        System.out.println("FinalsKillsCheck: " + checks + " checks passed");
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Integer> view(Object kills, Method snapshot) throws Exception {
        return (Map<UUID, Integer>) snapshot.invoke(kills);
    }

    private static int count(Object kills, Method snapshot, UUID id) throws Exception {
        return view(kills, snapshot).getOrDefault(id, 0);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
