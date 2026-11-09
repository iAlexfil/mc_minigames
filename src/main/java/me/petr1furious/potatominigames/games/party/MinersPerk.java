package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Canonical, finite perk items. Names never serve as item identity. */
enum MinersPerk {
    DRILL(Material.DIAMOND_PICKAXE, 3),
    TUNNEL(Material.END_ROD, 1),
    VISION(Material.QUARTZ, 1),
    LUCK(Material.EMERALD, 1),
    SIFTER(Material.FLINT, 1),
    BOMB(Material.RESPAWN_ANCHOR, 1),
    AUDIT(Material.BOOK, 1);

    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "miners_perk");
    private static final NamespacedKey CHARGES = new NamespacedKey("potatominigames", "miners_perk_charges");
    final Material material;
    final int charges;
    MinersPerk(Material material, int charges) { this.material = material; this.charges = charges; }

    static MinersPerk random(Random random) { return values()[random.nextInt(values().length)]; }
    String key() { return "party.rounds.miners.perk." + name().toLowerCase(Locale.ROOT); }
    ItemStack item(int remaining) {
        if (remaining < 1 || remaining > charges) throw new IllegalArgumentException("Invalid perk charges");
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setMaxStackSize(this == DRILL ? 1 : 16);
            meta.setEnchantmentGlintOverride(true);
            meta.displayName(I18n.tr(key() + ".name").color(NamedTextColor.LIGHT_PURPLE));
            meta.lore(List.of(I18n.tr(key() + ".lore").color(NamedTextColor.GRAY),
                    I18n.tr("party.rounds.miners.perk.charges", remaining).color(NamedTextColor.GOLD)));
            if (this == DRILL) {
                meta.setUnbreakable(true);
                meta.addEnchant(Enchantment.EFFICIENCY, 4, false);
            }
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, name());
            meta.getPersistentDataContainer().set(CHARGES, PersistentDataType.INTEGER, remaining);
        });
        return item;
    }
    static MinersPerk from(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        String name = data.get(KEY, PersistentDataType.STRING);
        Integer charges = data.get(CHARGES, PersistentDataType.INTEGER);
        if (name == null || charges == null) return null;
        try {
            MinersPerk perk = valueOf(name);
            return item.getType() == perk.material && charges > 0 && charges <= perk.charges ? perk : null;
        } catch (IllegalArgumentException unknown) { return null; }
    }
    static int charges(ItemStack item) {
        return from(item) == null ? 0 : item.getItemMeta().getPersistentDataContainer().get(CHARGES, PersistentDataType.INTEGER);
    }
}
