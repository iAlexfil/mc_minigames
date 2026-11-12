package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-owned disc playback, attached to the moving cashout body. */
final class FinalsCashoutMusic {
    private static final String[] TRACKS = {
        "minecraft:music_disc.pigstep", "minecraft:music_disc.lava_chicken"
    };
    private static final float VOLUME = .15f;
    private static final double RANGE_SQUARED = 16 * 16;
    private final Map<UUID, Player> listeners = new HashMap<>();
    private UUID sourceId;
    private String track;
    private int nextTrack;

    void update(Entity source, Collection<Player> audience) {
        if (source == null || !source.isValid()) {
            clear();
            return;
        }
        if (track == null) {
            // Both discs exceed even the maximum configured cashout duration (120 seconds).
            track = TRACKS[nextTrack];
            nextTrack = (nextTrack + 1) % TRACKS.length;
        }
        if (!source.getUniqueId().equals(sourceId)) {
            stopListeners();
            sourceId = source.getUniqueId();
        }
        Location origin = source.getLocation();
        // Entity sound packets are discarded if the client has not received the body yet.
        // Retry on later ticks, including after a reconnect or a recovered cashout body.
        Set<Player> tracking = source.getTrackedBy();
        Set<UUID> nearby = new HashSet<>();
        for (Player player : audience) {
            if (!player.isOnline()
                    || !tracking.contains(player)
                    || !origin.getWorld().equals(player.getWorld())
                    || player.getLocation().distanceSquared(origin) > RANGE_SQUARED) continue;
            UUID id = player.getUniqueId();
            nearby.add(id);
            Player previous = listeners.get(id);
            if (previous == player) continue;
            if (previous != null) stop(previous);
            player.playSound(source, track, SoundCategory.RECORDS, VOLUME, 1f);
            listeners.put(id, player);
        }
        var iterator = listeners.entrySet().iterator();
        while (iterator.hasNext()) {
            var listener = iterator.next();
            if (!nearby.contains(listener.getKey())) {
                stop(listener.getValue());
                iterator.remove();
            }
        }
    }

    void remove(Player player) {
        Player listener = listeners.remove(player.getUniqueId());
        if (listener != null) stop(listener);
    }

    void clear() {
        stopListeners();
        sourceId = null;
        track = null;
    }

    private void stopListeners() {
        for (Player listener : listeners.values()) stop(listener);
        listeners.clear();
    }

    private void stop(Player player) {
        // Stop only our current disc, leaving the steal siren and combat feedback intact.
        player.stopSound(track, SoundCategory.RECORDS);
    }
}
