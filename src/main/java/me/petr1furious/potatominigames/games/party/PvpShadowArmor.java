package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

/** Hide armour to other clients without removing its real items or protection. */
final class PvpShadowArmor implements Listener {
    static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final Map<EquipmentSlot, ItemStack> HIDDEN;
    static {
        Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        // sendEquipmentChange explicitly supports null to display an empty slot.
        for (EquipmentSlot slot : ARMOR) empty.put(slot, null);
        HIDDEN = Collections.unmodifiableMap(empty);
    }
    private static final class Cloak {
        final Player player;
        final Set<Player> viewers = new HashSet<>();
        boolean dirty;
        Cloak(Player player) { this.player = player; }
    }
    private final Map<UUID, Cloak> cloaks = new HashMap<>();
    private final Predicate<Player> fighting;
    private boolean registered;

    PvpShadowArmor(Predicate<Player> fighting) { this.fighting = fighting; }

    void start(Plugin plugin) {
        if (registered) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
    }
    void watch(Player player) {
        remove(player.getUniqueId());
        cloaks.put(player.getUniqueId(), new Cloak(player));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void equipmentChanged(EntityEquipmentChangedEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getEquipmentChanges().keySet().stream().anyMatch(ARMOR::contains)) changed(player);
    }
    void changed(Player player) {
        Cloak cloak = cloaks.get(player.getUniqueId());
        if (cloak != null) cloak.dirty = true;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tracked(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) changed(player);
    }
    // Run after vanilla's equipment broadcasts, so an armour swap cannot reveal the cloak.
    @EventHandler(priority = EventPriority.MONITOR)
    public void tick(ServerTickEndEvent event) {
        for (Cloak cloak : List.copyOf(cloaks.values())) {
            Player player = cloak.player;
            if (!player.isOnline() || !fighting.test(player)) remove(player.getUniqueId());
            else update(player, player.hasPotionEffect(PotionEffectType.INVISIBILITY));
        }
    }
    void update(Player player, boolean invisible) {
        Cloak cloak = cloaks.get(player.getUniqueId());
        if (cloak == null) return;
        if (!invisible) { restore(cloak); return; }
        Set<Player> tracking = player.getTrackedBy();
        cloak.viewers.retainAll(tracking);
        for (Player viewer : tracking) {
            if (!maySend(player, viewer)) continue;
            if (cloak.dirty || !cloak.viewers.contains(viewer)) viewer.sendEquipmentChange(player, HIDDEN);
            cloak.viewers.add(viewer);
        }
        cloak.dirty = false;
    }
    private static boolean maySend(Player player, Player viewer) {
        return !player.getUniqueId().equals(viewer.getUniqueId()) && viewer.isOnline()
                && player.getWorld().equals(viewer.getWorld());
    }
    private static void restore(Cloak cloak) {
        Player player = cloak.player;
        if (player.isOnline() && !cloak.viewers.isEmpty()) {
            Map<EquipmentSlot, ItemStack> actual = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ARMOR) actual.put(slot, player.getInventory().getItem(slot));
            Set<Player> tracking = player.getTrackedBy();
            for (Player viewer : cloak.viewers) if (tracking.contains(viewer) && maySend(player, viewer))
                viewer.sendEquipmentChange(player, actual);
        }
        cloak.viewers.clear();
        cloak.dirty = false;
    }
    void remove(UUID id) {
        Cloak cloak = cloaks.remove(id);
        if (cloak != null) restore(cloak);
    }
    void clear() {
        for (UUID id : List.copyOf(cloaks.keySet())) remove(id);
    }
    void close() {
        clear();
        if (registered) { HandlerList.unregisterAll(this); registered = false; }
    }
}
