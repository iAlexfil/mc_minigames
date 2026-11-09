package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Locale;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Finite mobility finds; activation and cooldown are scoped to the miners round. */
enum MinersGadget {
    DASH(Material.FEATHER), LEAP(Material.RABBIT_FOOT);

    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "miners_gadget");
    final Material material;
    MinersGadget(Material material) { this.material = material; }

    static MinersGadget material(Material material) {
        for (MinersGadget gadget : values()) if (gadget.material == material) return gadget;
        return null;
    }

    ItemStack item(int amount) {
        ItemStack item = new ItemStack(material, amount);
        item.editMeta(meta -> {
            String prefix = "party.rounds.miners.gadget." + name().toLowerCase(Locale.ROOT);
            meta.displayName(I18n.tr(prefix + ".name").color(NamedTextColor.AQUA));
            meta.lore(List.of(I18n.tr(prefix + ".lore").color(NamedTextColor.GRAY)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, name());
        });
        return item;
    }

    static MinersGadget from(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            MinersGadget gadget = valueOf(value);
            return item.getType() == gadget.material ? gadget : null;
        } catch (IllegalArgumentException unknown) { return null; }
    }

    void activate(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        player.setVelocity(forward.multiply(this == DASH ? 1.2 : .25)
                .setY(this == DASH ? .25 : .8));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, .5, 0), 8, .2, .2, .2, .02);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .6f, 1.4f);
    }
}
