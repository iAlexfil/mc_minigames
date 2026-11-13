package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Passive Finals healing after the current life has avoided damage long enough. */
final class FinalsRegeneration implements Listener {
    private static final double HEALTH_PER_TICK = .2;
    private final Predicate<Player> active;
    private final LongSupplier clock;
    private final Map<UUID, Life> lives = new HashMap<>();
    private boolean started, closed;

    private static final class Life {
        long damagedAt, healedAt = Long.MIN_VALUE;
        Life(long tick) { damagedAt = tick; }
    }

    FinalsRegeneration(Predicate<Player> active, LongSupplier tick) {
        this.active = active;
        this.clock = tick;
    }

    void start(JavaPlugin plugin) {
        if (closed || started) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        started = true;
    }

    void reset(UUID player, long tick) {
        if (!closed) lives.put(player, new Life(tick));
    }

    void forget(UUID player) { lives.remove(player); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (closed || event.isCancelled() || event.getFinalDamage() <= 0
                || !(event.getEntity() instanceof Player player) || !active.test(player)) return;
        Life life = lives.get(player.getUniqueId());
        if (life != null) life.damagedAt = clock.getAsLong();
    }

    @SuppressWarnings("deprecation") // Effective MAX_HEALTH, including current attribute modifiers.
    void tick(Player player, FinalsClass type) {
        if (closed || !active.test(player) || !player.isOnline() || player.isDead()) return;
        Life life = lives.get(player.getUniqueId());
        if (life == null) return;
        long now = clock.getAsLong();
        int delay = switch (type) { case LIGHT -> 160; case MEDIUM -> 180; case HEAVY -> 200; };
        if (now < life.damagedAt || now - life.damagedAt < delay || now == life.healedAt) return;
        double health = player.getHealth(), maximum = player.getMaxHealth();
        if (!Double.isFinite(health) || !Double.isFinite(maximum) || health <= 0 || health >= maximum) return;
        player.setHealth(Math.min(maximum, health + HEALTH_PER_TICK));
        life.healedAt = now;
    }

    void close() {
        closed = true;
        lives.clear();
        if (started) HandlerList.unregisterAll(this);
    }
}
