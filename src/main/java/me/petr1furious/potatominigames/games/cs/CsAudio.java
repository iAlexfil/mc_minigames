package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;

/** Shared distance-bounded positional sound delivery for match participants and spectators. */
final class CsAudio {
    static boolean audible(Location source, Location listener, int radius) {
        if (source == null
                || listener == null
                || !Objects.equals(source.getWorld(), listener.getWorld())) return false;
        double dx = source.getX() - listener.getX();
        double dy = source.getY() - listener.getY();
        double dz = source.getZ() - listener.getZ();
        return dx * dx + dy * dy + dz * dz < (double) radius * radius;
    }

    static void play(
            Collection<Player> listeners, Location at, Sound sound, int radius, float pitch) {
        if (at == null) return;
        // Native variable-range sounds attenuate over 16 * volume blocks. Keep the actual
        // source position so the client can pan it as the listener turns or moves.
        float volume = radius / 16f;
        for (Player listener : listeners)
            if (audible(at, listener.getEyeLocation(), radius))
                listener.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private CsAudio() {}
}
