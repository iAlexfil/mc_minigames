package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.List;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Random finds upgrade their own equipment category; consumables keep their physical loot flow. */
final class MinersEquipment {
    enum Kind {
        PICKAXE(0), SWORD(1), AXE(3), BOW(4), CROSSBOW(5), MACE(6),
        BOOTS(36), LEGGINGS(37), CHESTPLATE(38), HELMET(39);

        final int slot;
        Kind(int slot) { this.slot = slot; }
        boolean armor() { return slot >= 36; }
    }

    static Kind kind(Material material) {
        if (material == Material.MACE) return Kind.MACE;
        String name = material.name();
        if (name.endsWith("_PICKAXE")) return Kind.PICKAXE;
        if (name.endsWith("_SWORD")) return Kind.SWORD;
        if (name.endsWith("_AXE")) return Kind.AXE;
        if (name.endsWith("_BOOTS")) return Kind.BOOTS;
        if (name.endsWith("_LEGGINGS")) return Kind.LEGGINGS;
        if (name.endsWith("_CHESTPLATE")) return Kind.CHESTPLATE;
        if (name.endsWith("_HELMET")) return Kind.HELMET;
        return material == Material.BOW ? Kind.BOW : material == Material.CROSSBOW ? Kind.CROSSBOW : null;
    }

    /** Category ranking weighs material and relevant enchantments; model evaluation needs no registries. */
    static double score(Material material, int enchantment) {
        Kind kind = kind(material);
        if (kind == null) return -1;
        String name = material.name();
        int tier = name.startsWith("NETHERITE_") ? 4 : name.startsWith("DIAMOND_") ? 3
                : name.startsWith("IRON_") ? 2 : name.startsWith("STONE_") || name.startsWith("CHAINMAIL_") ? 1 : 0;
        double sharpness = enchantment > 0 ? .5 * enchantment + .5 : 0;
        return switch (kind) {
            case PICKAXE -> (tier == 4 ? 9 : tier == 3 ? 8 : tier == 2 ? 6 : tier == 1 ? 4 : 2)
                    + (enchantment > 0 ? enchantment * enchantment + 1 : 0) + tier * .001;
            case SWORD -> 4 + tier + sharpness;
            case AXE -> (tier >= 1 ? tier == 4 ? 10 : 9 : 7) * (tier >= 3 ? 1 : tier == 2 ? .9 : .8)
                    + sharpness;
            case BOW, CROSSBOW -> enchantment;
            case MACE -> enchantment;
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> {
                int index = kind == Kind.HELMET ? 0 : kind == Kind.CHESTPLATE ? 1 : kind == Kind.LEGGINGS ? 2 : 3;
                int[] armor = tier >= 3 ? new int[] {3, 8, 6, 3} : tier == 2 ? new int[] {2, 6, 5, 2}
                        : tier == 1 ? new int[] {2, 5, 4, 1} : new int[] {1, 3, 2, 1};
                yield armor[index] + enchantment * .75 + (tier >= 3 ? .5 : 0) + tier * .001;
            }
        };
    }

    private static double score(ItemStack item) {
        Kind kind = kind(item.getType());
        if (kind == null) return -1;
        Enchantment enchantment = switch (kind) {
            case PICKAXE -> Enchantment.EFFICIENCY;
            case SWORD, AXE -> Enchantment.SHARPNESS;
            case BOW -> Enchantment.POWER;
            case CROSSBOW -> Enchantment.QUICK_CHARGE;
            case MACE -> Enchantment.DENSITY;
            default -> Enchantment.PROTECTION;
        };
        return score(item.getType(), item.getEnchantmentLevel(enchantment))
                + (kind == Kind.SWORD ? .75 * item.getEnchantmentLevel(Enchantment.FIRE_ASPECT) : 0);
    }

    record Piece(Kind kind, double score, boolean perk) {}
    record Upgrade(int slot, List<Integer> obsolete, boolean preserveDisplaced) {}

    /** Plan replacement before mutating the inventory, including a full inventory containing a drill. */
    static Upgrade plan(Kind kind, double candidateScore, List<Piece> inventory) {
        int previousSlot = -1;
        double previousScore = -1;
        List<Integer> obsolete = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            Piece piece = inventory.get(slot);
            if (piece == null || piece.perk() || piece.kind() != kind) continue;
            obsolete.add(slot);
            if (piece.score() > previousScore) { previousSlot = slot; previousScore = piece.score(); }
        }
        if (candidateScore <= previousScore) return null;
        int slot = kind.armor() ? kind.slot : previousSlot >= 0 ? previousSlot : kind.slot;
        if (!kind.armor() && previousSlot < 0 && inventory.get(slot) != null) {
            for (int i = 0; i < Math.min(36, inventory.size()); i++)
                if (inventory.get(i) == null) { slot = i; break; }
        }
        return new Upgrade(slot, List.copyOf(obsolete), inventory.get(slot) != null && !obsolete.contains(slot));
    }

    /** True also consumes inferior rolls: they cannot be farmed into piles of spare equipment. */
    static boolean apply(Player player, ItemStack candidate, MinersDrops drops) {
        Kind kind = kind(candidate.getType());
        if (kind == null) return false;
        PlayerInventory inventory = player.getInventory();
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) { pieces.add(null); continue; }
            boolean perk = MinersPerk.from(item) != null;
            Kind category = kind(item.getType());
            pieces.add(new Piece(category, !perk && category != null ? score(item) : -1, perk));
        }
        Upgrade upgrade = plan(kind, score(candidate), pieces);
        if (upgrade == null) return true;
        int slot = upgrade.slot();
        ItemStack displaced = inventory.getItem(slot);
        for (int index : upgrade.obsolete()) inventory.setItem(index, null);
        inventory.setItem(slot, drops.forViewer(candidate, player));
        if (upgrade.preserveDisplaced())
            for (ItemStack overflow : inventory.addItem(displaced).values()) {
                Item dropped = player.getWorld().dropItem(player.getLocation(), drops.unrendered(overflow));
                drops.track(dropped);
            }
        player.sendActionBar(I18n.tr("party.rounds.miners.upgrade", Component.translatable(candidate.getType().translationKey()))
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .4f, 1.4f);
        return true;
    }
}
