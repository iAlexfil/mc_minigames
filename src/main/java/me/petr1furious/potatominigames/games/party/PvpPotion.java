package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Tagged, stackable class potions. Beneficial splashes affect their caster; debuffs affect enemies. */
enum PvpPotion {
    HEALING(false, 1, 0xF94B4B),
    HARMING(true, 1, 0x652B85),
    POISON(true, 100, 0x59B542),
    SLOWNESS(true, 80, 0x718AA8),
    WEAKNESS(true, 120, 0x626262),
    NAUSEA(true, 160, 0x695370),
    SPEED(false, 120, 0x7CAFC6),
    INVISIBILITY(false, 400, 0x444C64),
    STRENGTH(false, 100, 0x9D3838),
    REGENERATION(false, 160, 0xCD5CAB),
    ABSORPTION(false, 160, 0xE8C94B),
    FIRE_RESISTANCE(false, 200, 0xE99436);

    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "pvp_potion");
    final Component title;
    final boolean harmful;
    final int durationTicks;
    private final int color;

    PvpPotion(boolean harmful, int durationTicks, int color) {
        this.title = I18n.tr("party.duels.potion." + name().toLowerCase(java.util.Locale.ROOT));
        this.harmful = harmful; this.durationTicks = durationTicks; this.color = color;
    }
    private PotionEffectType effectType() {
        return switch (this) {
            case HEALING -> PotionEffectType.INSTANT_HEALTH;
            case HARMING -> PotionEffectType.INSTANT_DAMAGE;
            case POISON -> PotionEffectType.POISON;
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case NAUSEA -> PotionEffectType.NAUSEA;
            case SPEED -> PotionEffectType.SPEED;
            case INVISIBILITY -> PotionEffectType.INVISIBILITY;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case REGENERATION -> PotionEffectType.REGENERATION;
            case ABSORPTION -> PotionEffectType.ABSORPTION;
            case FIRE_RESISTANCE -> PotionEffectType.FIRE_RESISTANCE;
        };
    }
    boolean affects(boolean caster, boolean activeFighter) { return activeFighter && (harmful ? !caster : caster); }
    ItemStack item(int amount) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, amount);
        potion.editMeta(PotionMeta.class, meta -> {
            meta.setMaxStackSize(8);
            meta.setColor(Color.fromRGB(color));
            meta.addCustomEffect(new PotionEffect(effectType(), durationTicks, 0, false, this != INVISIBILITY, true), true);
            meta.displayName(title.color(harmful ? NamedTextColor.RED : NamedTextColor.GREEN));
            meta.lore(List.of(I18n.tr(harmful ? "party.duels.potion.harmful_lore"
                    : "party.duels.potion.beneficial_lore").color(NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, name());
        });
        return potion;
    }
    static PvpPotion from(ItemStack item) {
        if (item == null || item.getType() != Material.SPLASH_POTION) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try { return valueOf(name); } catch (IllegalArgumentException unknown) { return null; }
    }
    int replenish(Player player, int amount, int cap) {
        int owned = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (from(item) == this) owned += item.getAmount();
        }
        int added = refillAmount(owned, amount, cap);
        if (added == 0) return 0;
        int leftover = player.getInventory().addItem(I18n.item(item(added), player)).values().stream().mapToInt(ItemStack::getAmount).sum();
        return added - leftover;
    }
    static int refillAmount(int owned, int requested, int cap) { return Math.max(0, Math.min(requested, cap - owned)); }
}
