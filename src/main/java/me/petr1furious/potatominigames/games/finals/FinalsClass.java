package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import java.util.Locale;

/** Minecraft health and movement adaptation of the three contestant builds. */
enum FinalsClass {
    LIGHT("light", 15, .24f, .8),
    MEDIUM("medium", 25, .20f, 1),
    HEAVY("heavy", 35, .17f, 1.25);

    private final String id;
    private final double health;
    private final float walkSpeed;
    private final double scale;

    FinalsClass(String id, double health, float walkSpeed, double scale) {
        this.id = id;
        this.health = health;
        this.walkSpeed = walkSpeed;
        this.scale = scale;
    }

    String id() {
        return id;
    }

    double health() {
        return health;
    }

    float walkSpeed() {
        return walkSpeed;
    }

    double scale() {
        return scale;
    }

    Component display() {
        return I18n.tr("finals.class." + id);
    }

    Component display(Player viewer) {
        return I18n.render(display(), viewer);
    }

    static FinalsClass parse(String input) {
        if (input == null) return null;
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "s", "small", "light" -> LIGHT;
            case "m", "medium" -> MEDIUM;
            case "l", "large", "heavy" -> HEAVY;
            default -> null;
        };
    }
}
