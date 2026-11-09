package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.*;

final class ColorFloorSession extends PartySurvivalSession {
    private final int stages;
    private ColorFloorArena arena;
    private ColorFloorRound round;
    private int stage = 1;
    private long deadline;
    private boolean revealing;

    ColorFloorSession(SessionServices s, PartyConfig c, PartyRoundContext context) {
        super(s, c, "color_floor", context);
        stages = c.colorFloorStages.get();
    }

    void buildArena() {
        arena = new ColorFloorArena(world);
        round = ColorFloorRound.generate(stage, random);
        arena.show(round, false);
    }

    Location spawn(int i, int n) {
        return arena.spawn(i, n);
    }

    Location viewpoint() {
        return new Location(world, .5, 94, .5, 0, 80);
    }

    double outHeight() {
        return 76;
    }

    int durationSeconds() {
        return stages * 8 + 10;
    }

    boolean podiumAwards() {
        return true;
    }

    Component instructions() {
        return I18n.tr("party.rounds.color_floor.instructions");
    }

    void beginPlay() {
        startStage();
    }

    private void startStage() {
        revealing = false;
        round = ColorFloorRound.generate(stage, random);
        arena.show(round, false);
        deadline = elapsed() + ColorFloorRound.choiceTicks(stage);
        announceTitle(round.title(), I18n.tr("party.rounds.color_floor.find", stage), 2);
        PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, 1.1f);
    }

    void playTick(long elapsed) {
        if (elapsed < deadline) {
            if (!revealing) PartySounds.countdown(alivePlayers(), deadline - elapsed);
            return;
        }
        if (!revealing) {
            // A missed color removes support, not the player. The common height check eliminates
            // only after an actual fall, allowing a last-second jump onto a remaining tile.
            arena.show(round, true);
            revealing = true;
            deadline = elapsed + 40;
            PartySounds.play(alivePlayers(), Sound.BLOCK_GLASS_BREAK, .6f, .8f);
        } else if (stage >= stages) finish(standings.survivors());
        else {
            stage++;
            startStage();
        }
    }

    Component status() {
        return I18n.tr(
                "party.rounds.color_floor.status",
                stage,
                round.title(),
                Math.max(0, (deadline - elapsed() + 19) / 20));
    }
}
