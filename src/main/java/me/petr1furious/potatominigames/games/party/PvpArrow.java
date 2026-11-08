package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import java.util.List;
import java.util.function.IntPredicate;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Finite ammunition, ordered by general usefulness before the renewable regular stack. */
enum PvpArrow {
    SLOWNESS(6, 80, 0x709AB8),
    POISON(4, 80, 0x69A543),
    WEAKNESS(4, 100, 0x797482),
    SPECTRAL(6, 0, 0xEBC75E);

    static final int FIRST_SLOT = 9;
    static final int NORMAL_SLOT = 13;
    final Component title;
    final int amount;
    final int durationTicks;
    private final int color;

    PvpArrow(int amount, int durationTicks, int color) {
        this.title = I18n.tr("party.duels.arrow." + name().toLowerCase(java.util.Locale.ROOT));
        this.amount = amount; this.durationTicks = durationTicks; this.color = color;
    }
    int slot() { return FIRST_SLOT + ordinal(); }
    Material material() { return this == SPECTRAL ? Material.SPECTRAL_ARROW : Material.TIPPED_ARROW; }
    private PotionEffectType effectType() {
        return switch (this) {
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case POISON -> PotionEffectType.POISON;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case SPECTRAL -> throw new IllegalStateException("Spectral arrows use native Glowing");
        };
    }
    ItemStack item() {
        ItemStack arrows = new ItemStack(material(), amount);
        if (this != SPECTRAL) {
            arrows.editMeta(PotionMeta.class, meta -> {
                meta.setColor(Color.fromRGB(color));
                meta.addCustomEffect(new PotionEffect(effectType(), durationTicks, 0), true);
            });
        }
        arrows.editMeta(meta -> {
            meta.displayName(I18n.tr("party.duels.arrow.numbered", ordinal() + 1, title).color(this == SPECTRAL ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            meta.lore(List.of(I18n.tr("party.duels.arrow.finite").color(NamedTextColor.GRAY),
                    I18n.tr("party.duels.arrow.select").color(NamedTextColor.GRAY)));
        });
        // Apply after ItemMeta edits so the final item keeps these already-short effect durations.
        if (this != SPECTRAL) arrows.setData(DataComponentTypes.POTION_DURATION_SCALE, 1.0f);
        return arrows;
    }
    /** Never recreate ordinary arrows ahead of surviving special ammunition. */
    static int normalRefillSlot(IntPredicate empty) {
        for (int slot = NORMAL_SLOT; slot < 36; slot++) if (empty.test(slot)) return slot;
        return -1;
    }
}
