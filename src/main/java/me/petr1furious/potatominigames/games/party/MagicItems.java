package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MagicItems {
    private static final NamespacedKey WAND = new NamespacedKey("potatominigames", "magic_wand");
    private static final NamespacedKey GUIDE = new NamespacedKey("potatominigames", "magic_guide");
    private static final NamespacedKey SPELL =
            new NamespacedKey("potatominigames", "magic_spell_choice");

    private static final class Book implements InventoryHolder {
        private final UUID owner;
        private final Inventory inventory;

        Book(Player player, MagicMana mana) {
            owner = player.getUniqueId();
            inventory =
                    Bukkit.createInventory(
                            this, 54, I18n.render(I18n.tr("party.magic.book.title"), player));
            for (var school : MagicSpell.School.values()) {
                ItemStack heading = new ItemStack(school.material);
                heading.editMeta(
                        meta -> meta.displayName(school.title().color(NamedTextColor.GOLD)));
                inventory.setItem(school.ordinal() * 9, I18n.item(heading, player));
                for (int i = 0; i < school.spells().size(); i++) {
                    MagicSpell spell = school.spells().get(i);
                    boolean selected = mana.selected(school) == spell;
                    ItemStack item = new ItemStack(school.material);
                    item.editMeta(meta -> {
                        meta.displayName(
                                spell.title()
                                        .color(
                                                selected
                                                        ? NamedTextColor.AQUA
                                                        : NamedTextColor.WHITE));
                        meta.lore(
                                List.of(
                                        I18n.tr(
                                                        "party.magic.book.cost",
                                                        spell.mana,
                                                        spell.cooldown / 20.0)
                                                .color(NamedTextColor.YELLOW),
                                        spell.description().color(NamedTextColor.GRAY),
                                        I18n.tr(
                                                        selected
                                                                ? "party.magic.book.selected"
                                                                : "party.magic.book.choose")
                                                .color(NamedTextColor.GREEN)));
                        meta.setEnchantmentGlintOverride(selected);
                        meta.getPersistentDataContainer()
                                .set(SPELL, PersistentDataType.STRING, spell.name());
                    });
                    inventory.setItem(school.ordinal() * 9 + i + 1, I18n.item(item, player));
                }
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static void openBook(Player player, MagicMana mana) {
        player.openInventory(new Book(player, mana).getInventory());
    }

    static MagicSpell bookChoice(Inventory inventory, int slot, Player player) {
        if (!(inventory.getHolder() instanceof Book book)
                || !book.owner.equals(player.getUniqueId())
                || slot < 0
                || slot >= inventory.getSize()) return null;
        ItemStack item = inventory.getItem(slot);
        if (item == null || !item.hasItemMeta()) return null;
        String choice =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(SPELL, PersistentDataType.STRING);
        if (choice == null) return null;
        try {
            return MagicSpell.valueOf(choice);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static ItemStack wand(MagicSpell.School school, MagicMana mana, Player player) {
        ItemStack item = new ItemStack(school.material);
        MagicSpell selected = mana.selected(school);
        item.editMeta(meta -> {
            meta.displayName(
                    I18n.tr("party.magic.item.wand", school.title()).color(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(I18n.tr("party.magic.item.controls").color(NamedTextColor.YELLOW));
            for (MagicSpell spell : school.spells())
                lore.add(
                        I18n.tr(
                                        spell == selected
                                                ? "party.magic.item.selected"
                                                : "party.magic.item.option",
                                        spell.title(),
                                        spell.mana,
                                        spell.cooldown / 20.0)
                                .color(
                                        spell == selected
                                                ? NamedTextColor.AQUA
                                                : NamedTextColor.GRAY));
            lore.add(selected.description().color(NamedTextColor.WHITE));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(WAND, PersistentDataType.STRING, school.name());
        });
        return I18n.item(item, player);
    }

    static MagicSpell.School school(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(WAND, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            var school = MagicSpell.School.valueOf(value);
            return school.material == item.getType() ? school : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static ItemStack guide(Player player) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(I18n.tr("party.magic.item.guide").color(NamedTextColor.LIGHT_PURPLE));
            meta.lore(List.of(I18n.tr("party.magic.item.guide_lore").color(NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(GUIDE, PersistentDataType.BYTE, (byte) 1);
        });
        return I18n.item(item, player);
    }

    static boolean guide(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(GUIDE, PersistentDataType.BYTE);
    }
}
