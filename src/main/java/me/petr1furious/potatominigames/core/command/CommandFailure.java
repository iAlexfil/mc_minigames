package me.petr1furious.potatominigames.core.command;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** A user-facing command error; the command framework renders it in red. */
public class CommandFailure extends Exception {
    private final Component text;

    public CommandFailure(String message) {
        super(message);
        this.text = Component.text(message, NamedTextColor.RED);
    }

    public CommandFailure(Component text) {
        super(I18n.plain(text, java.util.Locale.ENGLISH));
        this.text = text.colorIfAbsent(NamedTextColor.RED);
    }

    public Component text() {
        return text;
    }
}
