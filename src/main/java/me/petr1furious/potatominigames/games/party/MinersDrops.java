package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Item;

/** Bound abandoned loot without scanning the world or removing inventory items. */
final class MinersDrops {
    static final int LIMIT = 256;
    private final Map<UUID, Item> items = new LinkedHashMap<>();
    private final Map<ItemStack, ItemStack> templates = new LinkedHashMap<>();

    ItemStack forViewer(ItemStack source, Player viewer) {
        ItemStack identity = identity(source);
        ItemStack template = templates.getOrDefault(identity, identity);
        ItemStack rendered = I18n.item(template.clone(), viewer);
        templates.put(identity, template);
        templates.put(identity(rendered), template);
        rendered.setAmount(source.getAmount());
        return rendered;
    }

    ItemStack unrendered(ItemStack source) {
        ItemStack template = templates.getOrDefault(identity(source), source);
        ItemStack result = template.clone();
        result.setAmount(source.getAmount());
        return result;
    }

    private static ItemStack identity(ItemStack item) {
        ItemStack identity = item.clone();
        identity.setAmount(1);
        return identity;
    }
    void track(Item item) {
        prune();
        items.put(item.getUniqueId(), item);
        while (items.size() > LIMIT) {
            var oldest = items.entrySet().iterator();
            Item removed = oldest.next().getValue(); oldest.remove();
            if (removed.isValid()) removed.remove();
        }
    }
    void prune() { items.values().removeIf(item -> !item.isValid()); }
    void clear() { for (Item item : items.values()) if (item.isValid()) item.remove(); items.clear(); templates.clear(); }
}
