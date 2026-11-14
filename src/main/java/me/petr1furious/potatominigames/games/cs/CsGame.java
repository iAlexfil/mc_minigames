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
        this(new CsConfig(registry));
    }

    CsGame(CsConfig config) {
        this.config = config;
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
    public List<String> modes() {
        return List.of("default", "deathmatch", "wingman");
    }

    @Override
    public Material icon(String mode) {
        return switch (mode) {
            case "deathmatch" -> Material.DIAMOND_SWORD;
            case "wingman" -> Material.IRON_BOOTS;
            default -> icon();
        };
    }

    @Override
    public Component modeTitle(String mode) {
        return I18n.tr("cs.mode." + (modes().contains(mode) ? mode : "default"));
    }

    @Override
    public Component description(String mode) {
        return I18n.tr(
                switch (mode) {
                    case "deathmatch" -> "cs.dm.description";
                    case "wingman" -> "cs.wingman.description";
                    default -> "cs.description";
                });
    }

    @Override
    public Material icon() {
        return Material.IRON_HORSE_ARMOR;
    }

    @Override
    public int maxPlayers(String mode) {
        return mode.equals("deathmatch") ? 16 : 10;
    }

    @Override
    public boolean usesTeams() {
        return true;
    }

    @Override
    public List<TeamColor> teamColors(String mode) {
        return mode.equals("deathmatch") ? List.of() : List.of(TeamColor.RED, TeamColor.BLUE);
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        return new CsSession(
                services, config, this, mode.equals("deathmatch"), mode.equals("wingman"));
    }

    void started(CsSession session, Collection<UUID> ids) {
        ids.forEach(id -> playing.put(id, session));
    }

    void ended(CsSession session) {
        playing.values().removeIf(s -> s == session);
    }

    @Override
    public void extendCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (String action : List.of("buy", "score", "reload", "drop", "pause", "help"))
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
