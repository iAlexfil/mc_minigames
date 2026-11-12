package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Finals facade retains the native-check transport hook while sharing the packet adapter. */
final class FinalsTeammateGlow {
    private final PrivatePlayerGlow glow;

    FinalsTeammateGlow(JavaPlugin plugin) {
        glow = new PrivatePlayerGlow(plugin);
    }

    FinalsTeammateGlow(Function<Player, Object> handles, BiConsumer<Player, Object> sender) {
        glow = new PrivatePlayerGlow(handles, sender);
    }

    void update(World world, Collection<Player> players, Map<UUID, Integer> teams) {
        glow.update(world, players, teams);
    }

    void remove(UUID id) {
        glow.remove(id);
    }

    void clear() {
        glow.clear();
    }
}
