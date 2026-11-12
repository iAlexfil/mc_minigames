package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Uses actual jump-key input, including frozen spectators, rather than movement events. */
final class FinalsRespawnInput implements Listener {
    private final Consumer<UUID> respawn;
    private final Map<UUID, Player> tracked = new HashMap<>();
    private final Map<UUID, Boolean> jumping = new HashMap<>();
    private boolean closed;

    FinalsRespawnInput(JavaPlugin plugin, Consumer<UUID> respawn) {
        this.respawn = respawn;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void track(Player player) {
        if (closed) return;
        UUID id = player.getUniqueId();
        // Spawning from this callback can re-track before Paper publishes the new input.
        if (tracked.get(id) == player) return;
        tracked.put(id, player);
        Input input = player.getCurrentInput();
        jumping.put(id, input != null && input.isJump());
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (closed || tracked.get(id) != event.getPlayer()) return;
        boolean next = event.getInput().isJump();
        boolean previous = Boolean.TRUE.equals(jumping.put(id, next));
        if (next && !previous) respawn.accept(id);
    }

    void remove(UUID id) {
        tracked.remove(id);
        jumping.remove(id);
    }

    void clear() {
        closed = true;
        tracked.clear();
        jumping.clear();
        HandlerList.unregisterAll(this);
    }
}
