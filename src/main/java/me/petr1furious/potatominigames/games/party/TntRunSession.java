package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;

final class TntRunSession extends PartySurvivalSession {
    private TntRunArena arena;
    private final int duration, collapse;

    TntRunSession(SessionServices services, PartyConfig config) {
        this(services, config, PartyRoundContext.standalone(services));
    }

    TntRunSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "tnt_run", context);
        duration = Math.clamp(config.tntRunSeconds.get(), 30, 600);
        collapse = Math.clamp(config.tntRunCollapseTicks.get(), 3, 40);
    }

    @Override
    void buildArena() {
        arena = new TntRunArena(world);
        arena.build();
    }

    @Override
    Location spawn(int index, int count) {
        return arena.spawn(index, count);
    }

    @Override
    Location viewpoint() {
        return new Location(world, .5, TntRunArena.FLOORS[0] + 12, .5);
    }

    @Override
    double outHeight() {
        return TntRunArena.FLOORS[2] - 4;
    }

    @Override
    int durationSeconds() {
        return duration;
    }

    @Override
    Component instructions() {
        return I18n.tr("party.rounds.tnt_run.instructions");
    }

    @Override
    void playTick(long elapsed) {
        int delay = TntRunArena.delay(collapse, elapsed);
        for (var player : alivePlayers()) arena.step(player.getLocation(), elapsed, delay);
        arena.tick(elapsed);
    }

    @Override
    Component status() {
        return I18n.tr(
                "party.rounds.tnt_run.status",
                String.format(
                        java.util.Locale.ROOT,
                        "%.2f",
                        TntRunArena.delay(collapse, elapsed()) / 20.0));
    }
}
