package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;

enum MinersOre {
    COAL(Material.COAL_ORE, 80),
    IRON(Material.IRON_ORE, 64),
    DIAMOND(Material.DIAMOND_ORE, 24),
    GOLD(Material.GOLD_ORE, 16),
    LAPIS(Material.LAPIS_ORE, 24),
    REDSTONE(Material.REDSTONE_ORE, 32),
    EMERALD(Material.EMERALD_ORE, 8);
    final Material material;
    final int perCell;
    final Component description;
    MinersOre(Material material, int perCell) {
        this.material = material; this.perCell = perCell;
        this.description = I18n.tr("party.rounds.miners.ore." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
