package me.petr1furious.potatominigames.core.text;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

public final class TimeFormat {
    private TimeFormat() {
    }

    /** "MM:SS", or "HH:MM:SS" when an hour or longer. */
    public static String clock(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }

    /** "2m 30s" style, for chat messages. */
    public static Component words(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0 && secs > 0) {
            return I18n.tr("common.time.minutes", minutes).append(Component.space())
                    .append(I18n.tr("common.time.seconds", secs));
        }
        if (minutes > 0) {
            return I18n.tr("common.time.minutes", minutes);
        }
        return I18n.tr("common.time.seconds", secs);
    }
}
