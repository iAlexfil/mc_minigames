import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.petr1furious.potatominigames.core.hud.SidebarService;
import me.petr1furious.potatominigames.core.team.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Real scoreboard options: hiding Finals names must not change another game's scoreboard. */
public final class FinalsNameTagsSmoke {
    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        SidebarService finals = new SidebarService(List::of);
        Class<?> viewType = Class.forName(SidebarService.class.getName() + "$View");
        Constructor<?> constructor = viewType.getDeclaredConstructor(Component.class, Locale.class);
        constructor.setAccessible(true);
        Object view = constructor.newInstance(Component.text("Finals"), Locale.ENGLISH);
        Field scoreboardField = viewType.getDeclaredField("scoreboard");
        scoreboardField.setAccessible(true);
        Scoreboard scoreboard = (Scoreboard) scoreboardField.get(view);
        Field viewsField = SidebarService.class.getDeclaredField("views");
        viewsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> views = (Map<UUID, Object>) viewsField.get(finals);
        views.put(UUID.randomUUID(), view);
        Team red = scoreboard.registerNewTeam("color_" + TeamColor.RED.id());
        Team blue = scoreboard.registerNewTeam("color_" + TeamColor.BLUE.id());
        red.addEntry("FinalsRed");
        blue.addEntry("FinalsBlue");
        Scoreboard other = Bukkit.getScoreboardManager().getNewScoreboard();
        Team otherTeam = other.registerNewTeam("color_" + TeamColor.RED.id());
        otherTeam.addEntry("AnotherGame");
        Team.Option option = Team.Option.NAME_TAG_VISIBILITY;
        int checks = 0;
        try {
            finals.setNameTagsVisible(false);
            require(red.getOption(option) == Team.OptionStatus.NEVER, "Red overhead names hidden"); checks++;
            require(blue.getOption(option) == Team.OptionStatus.NEVER, "Blue overhead names hidden"); checks++;
            require(red.hasEntry("FinalsRed") && blue.hasEntry("FinalsBlue"), "Team membership retained"); checks++;
            require(otherTeam.getOption(option) == Team.OptionStatus.ALWAYS, "Other sessions unaffected"); checks++;
            finals.setNameTagsVisible(true);
            require(red.getOption(option) == Team.OptionStatus.ALWAYS
                    && blue.getOption(option) == Team.OptionStatus.ALWAYS, "Names can be restored"); checks++;
            finals.setNameTagsVisible(false);
            finals.clearAll();
            Field visibleField = SidebarService.class.getDeclaredField("nameTagsVisible");
            visibleField.setAccessible(true);
            require(visibleField.getBoolean(finals), "Cleanup resets the visibility policy"); checks++;
            require(otherTeam.getOption(option) == Team.OptionStatus.ALWAYS, "Cleanup leaves other boards alone"); checks++;
            smoke.getLogger().info("FINALS_NAME_TAGS_SMOKE_PASS: " + checks + " assertions");
            return checks;
        } finally {
            finals.clearAll();
            red.unregister();
            blue.unregister();
            otherTeam.unregister();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
