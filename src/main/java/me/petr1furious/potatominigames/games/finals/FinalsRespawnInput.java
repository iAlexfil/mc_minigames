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

/** Actual key input for respawning and loadout selection, including frozen spectators. */
final class FinalsRespawnInput implements Listener {
    private final Consumer<UUID> respawn;
    private final Consumer<UUID> loadout;
    private final Map<UUID, Player> tracked = new HashMap<>();
    private final Map<UUID, Boolean> jumping = new HashMap<>();
    private final Map<UUID, Boolean> sneaking = new HashMap<>();
    private boolean closed;

    FinalsRespawnInput(JavaPlugin plugin, Consumer<UUID> respawn) {
        this(plugin, respawn, id -> {});
    }

    FinalsRespawnInput(JavaPlugin plugin, Consumer<UUID> respawn, Consumer<UUID> loadout) {
        this.respawn = respawn;
        this.loadout = loadout;
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
        sneaking.put(id, input != null && input.isSneak());
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (closed || tracked.get(id) != event.getPlayer()) return;
        boolean next = event.getInput().isJump();
        boolean previous = Boolean.TRUE.equals(jumping.put(id, next));
        boolean sneak = event.getInput().isSneak();
        boolean wasSneaking = Boolean.TRUE.equals(sneaking.put(id, sneak));
        // Prefer opening the menu if both keys are pressed together.
        if (sneak && !wasSneaking) loadout.accept(id);
        else if (next && !previous) respawn.accept(id);
    }

    void remove(UUID id) {
        tracked.remove(id);
        jumping.remove(id);
        sneaking.remove(id);
    }

    void clear() {
        closed = true;
        tracked.clear();
        jumping.clear();
        sneaking.clear();
        HandlerList.unregisterAll(this);
    }
}
