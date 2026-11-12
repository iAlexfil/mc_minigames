package me.petr1furious.potatominigames.games.cs;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;

/** Each viewer has a private scoreboard, so enemy names never become visible to opponents. */
final class CsNameTags {
    private CsNameTags() {}

    static void update(
            Scoreboard board, CsMatch match, Map<UUID, String> names, Integer viewerTeam) {
        Team allies = group(board, "cs_allied_names", Team.OptionStatus.ALWAYS);
        Team hidden = group(board, "cs_hidden_names", Team.OptionStatus.NEVER);
        if (viewerTeam != null)
            allies.color(viewerTeam == 0 ? NamedTextColor.RED : NamedTextColor.DARK_BLUE);
        for (var entry : names.entrySet()) {
            var actor = match.actor(entry.getKey());
            Team target =
                    viewerTeam != null && actor != null && actor.team == viewerTeam
                            ? allies
                            : hidden;
            if (!target.hasEntry(entry.getValue())) target.addEntry(entry.getValue());
        }
    }

    private static Team group(Scoreboard board, String name, Team.OptionStatus visibility) {
        Team team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, visibility);
        team.setAllowFriendlyFire(true);
        return team;
    }
}
