package me.petr1furious.potatominigames.games.party;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Private nametag masks and persistent native outlines for the finale. */
final class MinersVisibility {
    private record Mask(Team team, Map<String, String> previous) {}
    private record Outline(Player player, boolean previous) {}

    private final String teamName = "miners_" + UUID.randomUUID().toString().substring(0, 8);
    private final Map<Scoreboard, Mask> masks = new HashMap<>();
    private final Map<UUID, Outline> outlines = new LinkedHashMap<>();

    void reveal(Collection<Player> players) {
        for (Player player : players) {
            outlines.computeIfAbsent(player.getUniqueId(), ignored -> new Outline(player, player.isGlowing()));
            // Entity metadata is independent of sneaking and cannot be cleared by milk.
            player.setGlowing(true);
        }
    }

    void stopReveal(Player player) {
        Outline outline = outlines.remove(player.getUniqueId());
        if (outline != null) outline.player().setGlowing(outline.previous());
    }

    void hideNames(Collection<Player> viewers, Collection<String> names) {
        for (Player viewer : viewers) {
            Scoreboard board = viewer.getScoreboard();
            Mask mask = masks.computeIfAbsent(board, ignored -> {
                Team team = board.registerNewTeam(teamName);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                team.setAllowFriendlyFire(true);
                team.setCanSeeFriendlyInvisibles(false);
                return new Mask(team, new LinkedHashMap<>());
            });
            for (String name : names) {
                Team current = board.getEntryTeam(name);
                if (!mask.previous().containsKey(name))
                    mask.previous().put(name, current == null ? null : current.getName());
                if (current == null || !current.getName().equals(teamName)) mask.team().addEntry(name);
            }
        }
    }

    void clear() {
        for (Scoreboard board : List.copyOf(masks.keySet())) release(board);
        for (Outline outline : List.copyOf(outlines.values())) stopReveal(outline.player());
    }

    void release(Scoreboard board) {
        Mask mask = masks.remove(board);
        if (mask == null || board.getTeam(teamName) == null) return;
        for (var entry : mask.previous().entrySet()) {
            Team current = board.getEntryTeam(entry.getKey());
            if (current == null || !current.getName().equals(teamName)) continue;
            mask.team().removeEntry(entry.getKey());
            Team previous = entry.getValue() == null ? null : board.getTeam(entry.getValue());
            if (previous != null) previous.addEntry(entry.getKey());
        }
        mask.team().unregister();
    }
}
