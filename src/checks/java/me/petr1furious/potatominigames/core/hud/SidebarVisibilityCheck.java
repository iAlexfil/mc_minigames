package me.petr1furious.potatominigames.core.hud;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Proxy;
import java.util.*;

/** Exercises the same team setup used for both existing and newly created viewer boards. */
public final class SidebarVisibilityCheck {
    public static void run() throws Exception {
        var service = new SidebarService(List::of);
        Map<String, Team> teams = new HashMap<>();
        Set<String> entries = new HashSet<>();
        Map<Team.Option, Team.OptionStatus> options = new EnumMap<>(Team.Option.class);
        boolean[] seeInvisible = {true};
        Team team =
                (Team)
                        Proxy.newProxyInstance(
                                Team.class.getClassLoader(),
                                new Class<?>[] {Team.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "setOption" -> {
                                                options.put(
                                                        (Team.Option) args[0],
                                                        (Team.OptionStatus) args[1]);
                                                yield null;
                                            }
                                            case "setCanSeeFriendlyInvisibles" -> {
                                                seeInvisible[0] = (boolean) args[0];
                                                yield null;
                                            }
                                            case "addEntry" -> entries.add((String) args[0]);
                                            case "getEntries" -> Set.copyOf(entries);
                                            case "unregister" -> {
                                                teams.clear();
                                                entries.clear();
                                                yield null;
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        });
        Scoreboard board =
                (Scoreboard)
                        Proxy.newProxyInstance(
                                Scoreboard.class.getClassLoader(),
                                new Class<?>[] {Scoreboard.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getTeam" -> teams.get((String) args[0]);
                                            case "registerNewTeam" -> {
                                                teams.put((String) args[0], team);
                                                yield team;
                                            }
                                            default -> throw new AssertionError(method.getName());
                                        });
        var apply = SidebarService.class.getDeclaredMethod("applyNameColors", Scoreboard.class);
        apply.setAccessible(true);
        service.setHiddenNonCollidingPlayers(List.of("Alice", "Bob"));
        apply.invoke(service, board);
        require(entries.equals(Set.of("Alice", "Bob")), "Every participant is covered");
        require(
                options.get(Team.Option.COLLISION_RULE) == Team.OptionStatus.NEVER,
                "Client and server collisions disabled");
        require(
                options.get(Team.Option.NAME_TAG_VISIBILITY) == Team.OptionStatus.NEVER,
                "Names do not obscure walls");
        require(!seeInvisible[0], "Teammates cannot see invisible bodies");
        apply.invoke(service, board);
        require(entries.size() == 2 && !seeInvisible[0], "Sidebar refresh preserves presentation");
        service.setHiddenNonCollidingPlayers(List.of());
        apply.invoke(service, board);
        require(
                teams.isEmpty() && entries.isEmpty(),
                "Round cleanup removes collision and visibility rules");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
