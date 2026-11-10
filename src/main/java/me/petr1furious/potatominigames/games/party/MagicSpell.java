package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum MagicSpell {
    FIREBOLT(School.FIRE, 16, 36),
    FIREBALL(School.FIRE, 32, 100),
    METEOR(School.FIRE, 50, 240),
    FLAME_CONE(School.FIRE, 26, 80),
    CINDER_RAIN(School.FIRE, 38, 180),
    PHOENIX_STEP(School.FIRE, 24, 120),
    COMBUSTION(School.FIRE, 30, 140),
    ICE_LANCE(School.ICE, 14, 40),
    FROST_NOVA(School.ICE, 30, 120),
    ICE_WALL(School.ICE, 25, 140),
    BLIZZARD(School.ICE, 36, 180),
    FROZEN_ORB(School.ICE, 28, 100),
    ICE_ARMOR(School.ICE, 26, 200),
    SHATTER(School.ICE, 28, 120),
    GALE(School.STORM, 18, 70),
    WIND_STEP(School.STORM, 20, 100),
    THUNDER(School.STORM, 38, 120),
    CHAIN_LIGHTNING(School.STORM, 36, 160),
    UPDRAFT(School.STORM, 28, 180),
    DISPEL(School.STORM, 30, 180),
    SKYHOOK(School.STORM, 22, 120),
    STONE_SHOT(School.EARTH, 14, 40),
    BRIDGE(School.EARTH, 25, 120),
    QUAKE(School.EARTH, 40, 160),
    STONE_DOME(School.EARTH, 34, 240),
    FAULT_LINE(School.EARTH, 30, 140),
    ERODE(School.EARTH, 24, 140),
    PILLAR(School.EARTH, 22, 120),
    MEND(School.LIFE, 24, 140),
    AEGIS(School.LIFE, 28, 160),
    SANCTUARY(School.LIFE, 40, 360),
    PURIFY(School.LIFE, 18, 120),
    SECOND_WIND(School.LIFE, 55, 600),
    LIFE_TETHER(School.LIFE, 24, 120),
    MANA_CHANNEL(School.LIFE, 8, 360),
    RIFT(School.VOID, 20, 60),
    BLINK(School.VOID, 26, 120),
    GRAVITY_WELL(School.VOID, 45, 240),
    SILENCE(School.VOID, 28, 180),
    SWAP(School.VOID, 42, 280),
    REWIND(School.VOID, 30, 240),
    DARK_VEIL(School.VOID, 32, 200);

    enum School {
        FIRE(Material.BLAZE_ROD, 0xFF763B),
        ICE(Material.PRISMARINE_SHARD, 0x89E7FF),
        STORM(Material.BREEZE_ROD, 0xE8F5FF),
        EARTH(Material.STICK, 0xC7A174),
        LIFE(Material.AMETHYST_SHARD, 0x76F5AF),
        VOID(Material.ECHO_SHARD, 0xBC7BFF);
        final Material material;
        final int color;

        School(Material material, int color) {
            this.material = material;
            this.color = color;
        }

        List<MagicSpell> spells() {
            return Arrays.stream(MagicSpell.values()).filter(s -> s.school == this).toList();
        }

        Component title() {
            return I18n.tr("party.magic.school." + name().toLowerCase(Locale.ROOT));
        }
    }

    final School school;
    final int mana, cooldown;

    MagicSpell(School school, int mana, int cooldown) {
        this.school = school;
        this.mana = mana;
        this.cooldown = cooldown;
    }

    String key() {
        return "party.magic.spell." + name().toLowerCase(Locale.ROOT);
    }

    Component title() {
        return I18n.tr(key() + ".name");
    }

    Component description() {
        return I18n.tr(key() + ".description");
    }

    boolean aimed() {
        return switch (this) {
            case METEOR,
                    THUNDER,
                    GRAVITY_WELL,
                    QUAKE,
                    ICE_WALL,
                    CINDER_RAIN,
                    BLIZZARD,
                    UPDRAFT,
                    ERODE ->
                    true;
            default -> false;
        };
    }

    int range() {
        return switch (this) {
            case ICE_WALL -> 12;
            case GRAVITY_WELL, CINDER_RAIN, BLIZZARD, UPDRAFT, ERODE -> 48;
            default -> 80;
        };
    }

    double radius() {
        return switch (this) {
            case METEOR -> 4.5;
            case QUAKE, GRAVITY_WELL, DARK_VEIL -> 5;
            case BLIZZARD, CINDER_RAIN, ERODE -> 4;
            case FROZEN_ORB -> 3.5;
            case ICE_WALL -> 1;
            default -> 3;
        };
    }

    int warningTicks() {
        return this == METEOR ? 24 : this == CINDER_RAIN ? 20 : this == THUNDER ? 16 : 14;
    }
}
