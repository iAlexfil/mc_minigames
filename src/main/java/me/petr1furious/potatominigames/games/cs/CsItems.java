package me.petr1furious.potatominigames.games.cs;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

final class CsItems {
    static final NamespacedKey KEY = new NamespacedKey("potatominigames", "cs_item");

    static Component tr(String key, Object... args) {
        return I18n.tr("cs." + key, args);
    }

    static ItemStack item(
            Material material, String kind, Component title, List<Component> lore, Player viewer) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(title.color(NamedTextColor.GOLD));
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, kind);
        });
        return viewer == null ? item : I18n.item(item, viewer);
    }

    // Distinct payloads must never merge as vanilla dropped stacks.
    static ItemStack physical(ItemStack template) {
        ItemStack item = template.clone();
        item.editMeta(meta ->
                meta.getPersistentDataContainer()
                        .set(
                                new NamespacedKey("potatominigames", "cs_instance"),
                                PersistentDataType.STRING,
                                UUID.randomUUID().toString()));
        return item;
    }

    static ItemStack gun(CsGun gun, Player viewer) {
        return item(
                gun.weapon.material,
                "gun:" + gun.weapon.id,
                tr("weapon." + gun.weapon.id),
                gunLore(gun.weapon),
                viewer);
    }

    static List<Component> gunLore(CsWeapon w) {
        List<Component> lore =
                new ArrayList<>(
                        List.of(
                                tr("controls.gun"),
                                tr("item.ammo", w.magazine, w.reserve),
                                tr("category." + w.category.name().toLowerCase(Locale.ROOT)),
                                tr("item.ballistics", w.damage, Math.round(w.penetration * 100)),
                                tr(
                                        w.automatic() ? "item.auto" : "item.semi",
                                        Math.round(1200 / w.cadence))));
        if (w.pellets > 1) lore.add(tr("item.pellets", w.pellets));
        if (w.scope()) lore.add(tr("controls.scope"));
        if (w.silenced()) lore.add(tr("item.silenced"));
        if (w == CsWeapon.NEGEV) lore.add(tr("item.negev"));
        return lore;
    }

    static List<Component> shopLore(CsWeapon w) {
        List<Component> lore = new ArrayList<>();
        lore.add(tr("buy.price", w.price));
        lore.addAll(gunLore(w));
        return lore;
    }

    static ItemStack utility(CsMatch.Utility utility, Player viewer) {
        return item(
                material(utility),
                "utility:" + utility.name(),
                tr("utility." + utility.name().toLowerCase(Locale.ROOT)),
                List.of(tr("controls.throw")),
                viewer);
    }

    static Material material(CsMatch.Utility utility) {
        return switch (utility) {
            case HE -> Material.SLIME_BALL;
            case FLASH -> Material.FIREWORK_STAR;
            case SMOKE -> Material.GRAY_DYE;
            case FIRE -> Material.FIRE_CHARGE;
        };
    }

    static ItemStack bomb(Player p) {
        return item(Material.TNT, "bomb", tr("item.c4"), List.of(tr("controls.plant")), p);
    }

    static ItemStack kit(Player p) {
        return item(Material.SHEARS, "kit", tr("item.kit"), List.of(tr("controls.defuse", 5)), p);
    }

    static void sync(Player p, CsMatch match, org.bukkit.map.MapView radar) {
        var a = match.actor(p.getUniqueId());
        p.setItemOnCursor(null);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        // Cosmetic faction uniforms identify teammates without wall-visible nameplates.
        // Damage protection comes exclusively from the CS armor model.
        ItemStack uniform =
                item(
                        Material.LEATHER_CHESTPLATE,
                        "uniform",
                        tr("item.uniform", tr(match.side(a) == 0 ? "side.t" : "side.ct")),
                        List.of(),
                        p);
        uniform.editMeta(meta -> {
            ((org.bukkit.inventory.meta.LeatherArmorMeta) meta)
                    .setColor(
                            match.side(a) == 0
                                    ? Color.fromRGB(153, 105, 55)
                                    : Color.fromRGB(40, 70, 125));
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        p.getInventory().setChestplate(uniform);
        if (a.helmet) {
            ItemStack helmet = item(Material.IRON_HELMET, "helmet", tr("buy.helmet"), List.of(), p);
            helmet.editMeta(meta ->
                    meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of()));
            p.getInventory().setHelmet(helmet);
        }

        if (a.primary != null) p.getInventory().setItem(0, gun(a.primary, p));
        if (a.secondary != null) p.getInventory().setItem(1, gun(a.secondary, p));
        p.getInventory()
                .setItem(
                        2,
                        item(
                                Material.IRON_SWORD,
                                "knife",
                                tr("item.knife"),
                                List.of(tr("controls.knife")),
                                p));
        int slot = 3;
        for (var u : CsMatch.Utility.values()) {
            int n = a.utility.getOrDefault(u, 0);
            if (n > 0) {
                ItemStack item = utility(u, p);
                item.setAmount(n);
                p.getInventory().setItem(slot, item);
            }
            slot++;
        }
        if (match.side(a) == 0) {
            if (Objects.equals(match.carrier, a.id)) p.getInventory().setItem(7, bomb(p));
        } else
            p.getInventory()
                    .setItem(
                            7,
                            a.kit
                                    ? kit(p)
                                    : item(
                                            Material.TRIPWIRE_HOOK,
                                            "defuse",
                                            tr("item.defuse"),
                                            List.of(tr("controls.defuse", 10)),
                                            p));
        p.getInventory()
                .setItem(
                        8,
                        item(
                                Material.FILLED_MAP,
                                "buy",
                                tr("item.buy"),
                                List.of(tr("controls.buy")),
                                p));
        ItemStack chart = p.getInventory().getItem(8);
        chart.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.MapMeta map) map.setMapView(radar);
        });
    }
}
