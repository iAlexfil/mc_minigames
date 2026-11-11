package me.petr1furious.potatominigames.games.cs;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/** Independent category and command root; never a Party Games round. */
public final class CsGame implements Minigame {
    private final CsConfig config;
    private final Map<UUID, CsSession> playing = new HashMap<>();

    public CsGame(ConfigRegistry registry) {
        config = new CsConfig(registry);
    }

    @Override
    public String id() {
        return "cs";
    }

    @Override
    public Component displayName() {
        return I18n.tr("cs.name").color(NamedTextColor.GOLD);
    }

    @Override
    public Component modeTitle(String mode) {
        return I18n.tr("cs.mode.default");
    }

    @Override
    public Component description(String mode) {
        return I18n.tr("cs.description");
    }

    @Override
    public Material icon() {
        return Material.IRON_HORSE_ARMOR;
    }

    @Override
    public int maxPlayers(String mode) {
        return 10;
    }

    @Override
    public boolean usesTeams() {
        return true;
    }

    @Override
    public List<TeamColor> teamColors(String mode) {
        return List.of(TeamColor.RED, TeamColor.BLUE);
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        return new CsSession(services, config, this);
    }

    void started(CsSession session, Collection<UUID> ids) {
        ids.forEach(id -> playing.put(id, session));
    }

    void ended(CsSession session) {
        playing.values().removeIf(s -> s == session);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (String action : List.of("buy", "reload", "drop", "pause", "help"))
            root.then(
                    Commands.literal(action)
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player p)) {
                                    ctx.getSource()
                                            .getSender()
                                            .sendMessage(I18n.tr("cs.error.player"));
                                    return 0;
                                }
                                CsSession session = playing.get(p.getUniqueId());
                                if (session == null) {
                                    p.sendMessage(I18n.tr("cs.error.not_playing"));
                                    return 0;
                                }
                                session.command(p, action);
                                return 1;
                            }));
    }
}
