package me.petr1furious.potatominigames.games.finals;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Cancels only the native impulse produced synchronously by a gun damage call. */
final class FinalsBulletKnockback implements Listener, AutoCloseable {
    private record Hit(UUID target, UUID source) {}

    private Hit hit;
    private boolean listening;

    void start(JavaPlugin plugin) {
        if (listening) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    void damage(Player target, Player source, boolean bullet, Runnable damage) {
        Hit previous = hit;
        // Nested melee, gadget and environmental damage must not inherit a bullet's scope.
        hit = bullet ? new Hit(target.getUniqueId(), source.getUniqueId()) : null;
        try {
            damage.run();
        } finally {
            hit = previous;
        }
    }

    private boolean matches(Entity target, Entity source) {
        return hit != null
                && source != null
                && hit.target().equals(target.getUniqueId())
                && hit.source().equals(source.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void legacyKnockback(EntityKnockbackEvent event) {
        if (event.getCause() == EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK
                && event instanceof EntityKnockbackByEntityEvent push
                && matches(event.getEntity(), push.getSourceEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void knockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (event.getCause()
                        == io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK
                && event instanceof EntityPushedByEntityAttackEvent push
                && matches(event.getEntity(), push.getPushedBy())) event.setCancelled(true);
    }

    @Override
    public void close() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        hit = null;
    }
}
