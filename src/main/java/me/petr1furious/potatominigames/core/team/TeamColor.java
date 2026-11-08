package me.petr1furious.potatominigames.core.team;

import me.petr1furious.potatominigames.i18n.I18n;
import java.util.Locale;

import org.bukkit.Material;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum TeamColor {
    WHITE(NamedTextColor.WHITE, BossBar.Color.WHITE),
    ORANGE(NamedTextColor.GOLD, BossBar.Color.YELLOW),
    MAGENTA(NamedTextColor.LIGHT_PURPLE, BossBar.Color.PURPLE),
    LIGHT_BLUE(NamedTextColor.BLUE, BossBar.Color.BLUE),
    YELLOW(NamedTextColor.YELLOW, BossBar.Color.YELLOW),
    LIME(NamedTextColor.GREEN, BossBar.Color.GREEN),
    PINK(NamedTextColor.LIGHT_PURPLE, BossBar.Color.PINK),
    GRAY(NamedTextColor.DARK_GRAY, BossBar.Color.WHITE),
    LIGHT_GRAY(NamedTextColor.GRAY, BossBar.Color.WHITE),
    CYAN(NamedTextColor.DARK_AQUA, BossBar.Color.BLUE),
    PURPLE(NamedTextColor.DARK_PURPLE, BossBar.Color.PURPLE),
    BLUE(NamedTextColor.DARK_BLUE, BossBar.Color.BLUE),
    BROWN(NamedTextColor.GOLD, BossBar.Color.YELLOW),
    GREEN(NamedTextColor.DARK_GREEN, BossBar.Color.GREEN),
    RED(NamedTextColor.RED, BossBar.Color.RED),
    BLACK(NamedTextColor.BLACK, BossBar.Color.WHITE);

    private final NamedTextColor textColor;
    private final BossBar.Color bossBarColor;
    private final Material woolMaterial;

    TeamColor(NamedTextColor textColor, BossBar.Color bossBarColor) {
        this.textColor = textColor;
        this.bossBarColor = bossBarColor;
        this.woolMaterial = Material.valueOf(name() + "_WOOL");
    }


    public NamedTextColor textColor() {
        return textColor;
    }

    public BossBar.Color bossBarColor() {
        return bossBarColor;
    }

    public Material woolMaterial() {
        return woolMaterial;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayNameComponent() {
        return I18n.tr("core.team.color." + id()).color(textColor);
    }

    /** Accepts "light_blue", "light-blue", "Light Blue"; null if unknown. */
    public static TeamColor fromId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TeamColor color : values()) {
            if (color.id().equals(normalized)) {
                return color;
            }
        }
        return null;
    }
}
