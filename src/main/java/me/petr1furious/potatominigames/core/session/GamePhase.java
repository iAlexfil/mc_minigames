package me.petr1furious.potatominigames.core.session;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

public enum GamePhase {
    IDLE,
    RUNNING,
    /** The game is over and results are showing; players return after the grace period. */
    ENDING;

    public Component displayName() {
        return switch (this) {
            case IDLE -> I18n.tr("core.phase.idle");
            case RUNNING -> I18n.tr("core.phase.running");
            case ENDING -> I18n.tr("core.phase.ending");
        };
    }

    public boolean active() {
        return this != IDLE;
    }
}
