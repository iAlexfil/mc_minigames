package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Positional vanilla equivalents of C4 cues, calibrated to this imported Dust II. */
final class CsBombAudio {
    // A--B and A--CT Spawn landmark distances fit roughly 30 CS units per block.
    // See assets/maps/lesraisins-dust2/AUDIO-Dust2.md for sources and calibration.
    enum Cue {
        PLANT_START(36),
        PLANT_KEYS(36),
        PLANTED(136),
        DEFUSE_START(66),
        DEFUSED(66),
        BEEP(44);

        final int radius;

        Cue(int radius) {
            this.radius = radius;
        }
    }

    static boolean audible(Location source, Location listener, Cue cue) {
        return CsAudio.audible(source, listener, cue.radius);
    }

    static void play(Collection<Player> listeners, Location at, Cue cue, float pitch) {
        if (at == null) return;
        Sound sound =
                switch (cue) {
                    case PLANT_START -> Sound.BLOCK_STONE_BUTTON_CLICK_ON;
                    case PLANT_KEYS -> Sound.BLOCK_NOTE_BLOCK_HAT;
                    case PLANTED, BEEP -> Sound.BLOCK_NOTE_BLOCK_PLING;
                    case DEFUSE_START -> Sound.BLOCK_GRINDSTONE_USE;
                    case DEFUSED -> Sound.BLOCK_BEACON_DEACTIVATE;
                };
        CsAudio.play(listeners, at, sound, cue.radius, pitch);
    }

    private CsBombAudio() {}
}
