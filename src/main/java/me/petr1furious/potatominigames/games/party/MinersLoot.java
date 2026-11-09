package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Random reward rolls for automatic equipment upgrades and physical consumables. Models need no Bukkit registries. */
final class MinersLoot {
    enum Quality { COMMON, GOOD, RARE }
    record Drop(Material material, int amount, Quality quality, String enchant, int level, PvpPotion potion, boolean reveal) {
        ItemStack item() {
            MinersGadget gadget = MinersGadget.material(material);
            ItemStack item = gadget != null ? gadget.item(amount) : reveal ? MinersReveal.item() : potion != null ? potion.item(amount) : material == Material.TNT ? PvpBombs.item(amount, PvpBombs.MINERS_THROW_COOLDOWN) : new ItemStack(material, amount);
            // Rarity describes the roll, not the identity of a consumable stack.
            // Keep plain arrows/food/supplies vanilla and custom potion/TNT metadata canonical.
            if (material == Material.SHIELD) item.editMeta(meta -> meta.setUnbreakable(true));
            if (MinersEquipment.kind(material) != null) item.editMeta(meta -> {
                if (material.getMaxDurability() > 0) meta.setUnbreakable(true);
                if (enchant != null && level > 0) meta.addEnchant(resolve(enchant), level, true);
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(I18n.tr("party.rounds.miners.loot." + quality.name().toLowerCase(java.util.Locale.ROOT))
                        .color(quality == Quality.RARE ? NamedTextColor.LIGHT_PURPLE
                                : quality == Quality.GOOD ? NamedTextColor.AQUA : NamedTextColor.GRAY));
                meta.lore(lore);
            });
            return item;
        }
    }
    static Quality quality(Random random) { return quality(random, false); }
    static Quality quality(Random random, boolean lucky) {
        int roll = random.nextInt(100);
        return roll < (lucky ? 50 : 70) ? Quality.COMMON : roll < (lucky ? 90 : 95) ? Quality.GOOD : Quality.RARE;
    }
    static List<Drop> roll(MinersOre ore, Random random) {
        return roll(ore, random, false);
    }
    static List<Drop> roll(MinersOre ore, Random random, boolean lucky) {
        if (ore == MinersOre.GOLD || ore == MinersOre.PERKS) return List.of();
        Quality quality = quality(random, lucky);
        if (ore == MinersOre.EMERALD) {
            MinersOre[] pools = {MinersOre.COAL, MinersOre.IRON, MinersOre.DIAMOND, MinersOre.LAPIS, MinersOre.REDSTONE};
            if (quality == Quality.COMMON) quality = Quality.GOOD;
            // Totems use a separate 10% emerald roll, independent of the equipment quality roll.
            if (random.nextInt(100) < 10) return List.of(drop(Material.TOTEM_OF_UNDYING, 1, Quality.RARE, null, 0));
            if (random.nextInt(5) == 0) return List.of(drop(Material.GOLDEN_APPLE, 1, quality, null, 0));
            return roll(pools[random.nextInt(pools.length)], quality, random);
        }
        return roll(ore, quality, random);
    }
    private static List<Drop> roll(MinersOre ore, Quality q, Random random) {
        int grade = q.ordinal();
        return switch (ore) {
            case COAL -> {
                if (q == Quality.COMMON && random.nextBoolean()) yield List.of(drop(Material.COOKED_BEEF, 12, q, null, 0));
                Material pick = q == Quality.RARE || q == Quality.GOOD && random.nextBoolean() ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE;
                yield List.of(drop(pick, 1, q, "efficiency", grade + 3));
            }
            case IRON -> {
                if (q != Quality.COMMON && random.nextInt(5) == 0)
                    yield List.of(drop(Material.SHIELD, 1, q, null, 0));
                Material[][] armour = {
                    {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
                    {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
                    {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}
                };
                int level = q == Quality.COMMON ? (random.nextInt(4) == 0 ? 1 : 0) : q == Quality.GOOD ? 1 + random.nextInt(2) : 1;
                yield List.of(drop(armour[grade][random.nextInt(4)], 1, q, level > 0 ? "protection" : null, level));
            }
            case DIAMOND -> {
                if (q == Quality.RARE && random.nextInt(3) == 0)
                    yield List.of(drop(Material.MACE, 1, q, "density", 1 + random.nextInt(2)),
                            drop(Material.WIND_CHARGE, 4, q, null, 0));
                int weapon = random.nextInt(4);
                if (weapon < 2) {
                    Material material = weapon == 0 ? (q == Quality.COMMON ? Material.IRON_SWORD : Material.DIAMOND_SWORD)
                            : q == Quality.RARE ? Material.DIAMOND_AXE : q == Quality.GOOD ? Material.IRON_AXE : Material.STONE_AXE;
                    boolean fireBlade = weapon == 0 && q != Quality.COMMON && random.nextInt(4) == 0;
                    yield List.of(drop(material, 1, q, fireBlade ? "fire_aspect" : grade > 0 ? "sharpness" : null,
                            fireBlade ? 1 : grade));
                }
                Material material = weapon == 2 ? Material.BOW : Material.CROSSBOW;
                yield List.of(drop(material, 1, q, material == Material.BOW ? "power" : "quick_charge", grade),
                        drop(Material.ARROW, 32 + grade * 16, q, null, 0),
                        drop(Material.SPECTRAL_ARROW, 4 + grade * 2, q, null, 0));
            }
            case LAPIS -> {
                if (q == Quality.RARE) {
                    if (random.nextBoolean()) yield List.of(new Drop(Material.POTION, 1, q, null, 0, null, true));
                    PvpPotion[] rare = {PvpPotion.STRENGTH, PvpPotion.REGENERATION, PvpPotion.ABSORPTION};
                    yield List.of(potion(rare[random.nextInt(rare.length)], 1, q), potion(PvpPotion.HEALING, 1, q));
                }
                PvpPotion[] types = q == Quality.COMMON ? new PvpPotion[] {PvpPotion.SPEED, PvpPotion.SLOWNESS, PvpPotion.WEAKNESS, PvpPotion.NAUSEA}
                        : new PvpPotion[] {PvpPotion.HEALING, PvpPotion.POISON, PvpPotion.HARMING, PvpPotion.REGENERATION, PvpPotion.FIRE_RESISTANCE};
                yield List.of(potion(types[random.nextInt(types.length)], 1, q));
            }
            case REDSTONE -> {
                Material[] supplies = {Material.TNT, Material.ENDER_PEARL, Material.WIND_CHARGE, Material.COBWEB, Material.FEATHER, Material.RABBIT_FOOT};
                int choice = random.nextInt(supplies.length);
                int amount = switch (choice) {
                    case 0 -> 2 + grade;
                    case 1 -> 1 + grade;
                    case 2 -> 4 + grade * 2;
                    case 3 -> 1 + grade;
                    default -> q == Quality.RARE ? 2 : 1;
                };
                yield List.of(drop(supplies[choice], amount, q, null, 0));
            }
            default -> throw new IllegalArgumentException("Not a loot pool: " + ore);
        };
    }
    static List<Drop> sift(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> List.of(drop(Material.COOKED_BEEF, 4, Quality.COMMON, null, 0));
            case 1 -> List.of(drop(Material.ARROW, 8, Quality.COMMON, null, 0));
            case 2 -> List.of(drop(Material.SPECTRAL_ARROW, 2, Quality.COMMON, null, 0));
            case 3 -> List.of(drop(Material.TNT, 1, Quality.COMMON, null, 0));
            case 4 -> List.of(drop(Material.ENDER_PEARL, 1, Quality.COMMON, null, 0));
            case 5 -> List.of(drop(Material.WIND_CHARGE, 2, Quality.COMMON, null, 0));
            case 6 -> List.of(drop(Material.COBWEB, 1, Quality.COMMON, null, 0));
            case 7 -> List.of(drop(Material.FEATHER, 1, Quality.COMMON, null, 0));
            case 8 -> List.of(drop(Material.RABBIT_FOOT, 1, Quality.COMMON, null, 0));
            case 9 -> List.of(potion(PvpPotion.HEALING, 1, Quality.COMMON));
            case 10 -> List.of(potion(PvpPotion.SPEED, 1, Quality.COMMON));
            default -> List.of(potion(PvpPotion.WEAKNESS, 1, Quality.COMMON));
        };
    }
    private static Drop drop(Material material, int amount, Quality quality, String enchant, int level) {
        return new Drop(material, amount, quality, enchant, level, null, false);
    }
    private static Drop potion(PvpPotion potion, int amount, Quality quality) {
        return new Drop(Material.SPLASH_POTION, amount, quality, null, 0, potion, false);
    }
    private static Enchantment resolve(String enchant) {
        return switch (enchant) {
            case "efficiency" -> Enchantment.EFFICIENCY;
            case "protection" -> Enchantment.PROTECTION;
            case "sharpness" -> Enchantment.SHARPNESS;
            case "power" -> Enchantment.POWER;
            case "quick_charge" -> Enchantment.QUICK_CHARGE;
            case "density" -> Enchantment.DENSITY;
            case "fire_aspect" -> Enchantment.FIRE_ASPECT;
            default -> throw new IllegalArgumentException(enchant);
        };
    }
}
