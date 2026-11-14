package me.petr1furious.potatominigames.games.cs;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.key.Key;
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

    static Component ammoHud(CsGun gun, long tick) {
        return ammoHud(gun, tick, false);
    }

    static Component ammoHud(CsGun gun, long tick, boolean infiniteReserve) {
        Object reserve = infiniteReserve ? "∞" : gun.reserve;
        if (gun.reloadAt < 0) return tr("hud.weapon", gun.magazine, reserve);
        double seconds = Math.ceil(gun.reloadTicksRemaining(tick) / 2.0) / 10;
        return tr(
                "hud.weapon_reloading",
                gun.magazine,
                reserve,
                String.format(Locale.ROOT, "%.1f", seconds));
    }

    static Component killMessage(CsMatch.Actor victim, Component killerName, Component victimName) {
        var info = victim.killInfo;
        Component source =
                info == null
                        ? null
                        : info.weapon() != null
                                ? tr("weapon." + info.weapon().id)
                                : tr("kill.source." + info.cause().name().toLowerCase(Locale.ROOT));
        if (victim.killedBy == null || victim.killedBy.equals(victim.id))
            return info == null || info.cause() == CsMatch.KillCause.OTHER
                    ? tr("death", victimName)
                    : tr("death.cause", victimName, source);
        if (info == null) return tr("kill", killerName, victimName);
        if (info.weapon() != null)
            return info.headshot()
                    ? tr("kill.weapon.headshot", killerName, victimName, source, tr("kill.headshot"))
                    : tr("kill.weapon", killerName, victimName, source);
        return tr("kill.cause", killerName, victimName, source);
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
        ItemStack item =
                item(
                        gun.weapon.material,
                        "gun:" + gun.weapon.id,
                        tr("weapon." + gun.weapon.id),
                        gunLore(gun.weapon),
                        viewer);
        if (gun.weapon.requiresTriggerRelease()) {
            // Native use provides a real release event; repeating right-click packets alone
            // cannot distinguish holding the button from clicking again.
            item.setData(
                    DataComponentTypes.CONSUMABLE,
                    Consumable.consumable()
                            .consumeSeconds(3600)
                            .animation(ItemUseAnimation.NONE)
                            .sound(Key.key("minecraft:intentionally_empty"))
                            .hasConsumeParticles(false)
                            .build());
            item.setData(
                    DataComponentTypes.USE_EFFECTS,
                    UseEffects.useEffects()
                            .canSprint(true)
                            .speedMultiplier(1)
                            .interactVibrations(false)
                            .build());
        }
        return item;
    }

    static List<Component> gunLore(CsWeapon w) {
        List<Component> lore =
                new ArrayList<>(
                        List.of(
                                tr(w.requiresTriggerRelease() ? "controls.pistol" : "controls.gun"),
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

    /** Visual equipment only: empty native attributes keep protection in CsMatch. */
    static void syncArmour(Player p, CsMatch match) {
        var actor = match.actor(p.getUniqueId());
        Color color =
                Color.fromRGB(match.deathmatch ? 0x777777 : actor.team == 0 ? 0xB02E26 : 0x3C44AA);
        Component uniform =
                match.deathmatch
                        ? tr("dm.uniform")
                        : tr("item.uniform", tr(actor.team == 0 ? "team.red" : "team.blue"));
        p.getInventory().setBoots(cosmeticArmour(Material.LEATHER_BOOTS, uniform, color, p));
        p.getInventory().setLeggings(cosmeticArmour(Material.LEATHER_LEGGINGS, uniform, color, p));
        p.getInventory()
                .setChestplate(
                        actor.armour > 0
                                ? cosmeticArmour(Material.IRON_CHESTPLATE, tr("buy.vest"), color, p)
                                : cosmeticArmour(Material.LEATHER_CHESTPLATE, uniform, color, p));
        p.getInventory()
                .setHelmet(
                        actor.helmet
                                ? cosmeticArmour(Material.IRON_HELMET, tr("buy.helmet"), color, p)
                                : cosmeticArmour(Material.LEATHER_HELMET, uniform, color, p));
    }

    private static ItemStack cosmeticArmour(
            Material material, Component title, Color color, Player viewer) {
        ItemStack stack = item(material, "uniform", title, List.of(), viewer);
        stack.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather)
                leather.setColor(color);
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        return stack;
    }

    static void sync(Player p, CsMatch match, org.bukkit.map.MapView radar) {
        var a = match.actor(p.getUniqueId());
        p.setItemOnCursor(null);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        syncArmour(p, match);

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
        if (!match.deathmatch && match.side(a) == 0) {
            if (Objects.equals(match.carrier, a.id)) p.getInventory().setItem(7, bomb(p));
        } else if (!match.deathmatch)
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
                                tr(match.deathmatch ? "dm.radar" : "item.buy"),
                                List.of(tr(match.deathmatch ? "dm.radar_hint" : "controls.buy")),
                                p));
        ItemStack chart = p.getInventory().getItem(8);
        chart.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.MapMeta map) map.setMapView(radar);
        });
    }
}
