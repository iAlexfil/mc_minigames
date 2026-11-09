package me.petr1furious.potatominigames.games.party;

import java.util.List;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/** Finite drinkable detection loot, separate from PvP class potions. */
final class MinersReveal {
    static final int DURATION_TICKS = 100;
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "miners_reveal");

    static ItemStack item() {
        ItemStack item = new ItemStack(Material.POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(Color.fromRGB(0xFFD45A));
            meta.setEnchantmentGlintOverride(true);
            meta.displayName(I18n.tr("party.rounds.miners.reveal.name").color(NamedTextColor.GOLD));
            meta.lore(List.of(I18n.tr("party.rounds.miners.reveal.lore", DURATION_TICKS / 20)
                    .color(NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    static boolean is(ItemStack item) {
        return item != null && item.getType() == Material.POTION && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
