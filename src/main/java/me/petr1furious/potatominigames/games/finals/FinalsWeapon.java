package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Official weapon identities with deliberately Minecraft-sized combat values (health points). */
enum FinalsWeapon {
    M11("m11", FinalsClass.LIGHT, "IRON_HOE", 1.15, 24, 40, 2, 36, 1, .034, 1.35, true),
    XP54("xp54", FinalsClass.LIGHT, "GOLDEN_HOE", 1.75, 34, 30, 3, 42, 1, .018, 1.4, true),
    SR84("sr84", FinalsClass.LIGHT, "NETHERITE_HOE", 10, 80, 6, 24, 64, 1, .001, 1.8, false),
    SWORD("sword", FinalsClass.LIGHT, "IRON_SWORD", 4.6, 3.1, 0, 8, 0, 1, 0, 1, false),
    DAGGER("dagger", FinalsClass.LIGHT, "GOLDEN_SWORD", 4, 2.8, 0, 10, 0, 1, 0, 1, false),

    AKM("akm", FinalsClass.MEDIUM, "STONE_HOE", 2.1, 46, 36, 3, 48, 1, .014, 1.4, true),
    FCAR("fcar", FinalsClass.MEDIUM, "DIAMOND_HOE", 2.65, 52, 20, 4, 42, 1, .008, 1.4, true),
    MODEL_1887("model1887", FinalsClass.MEDIUM, "CROSSBOW", 1.35, 23, 6, 18, 55, 8, .075, 1, false),
    RIOT_SHIELD("riotshield", FinalsClass.MEDIUM, "SHIELD", 4.3, 2.9, 0, 12, 0, 1, 0, 1, false),
    DUAL_BLADES(
            "dualblades", FinalsClass.MEDIUM, "DIAMOND_SWORD", 3.8, 3.1, 0, 7, 0, 1, 0, 1, false),

    M60("m60", FinalsClass.HEAVY, "IRON_SHOVEL", 1.8, 44, 70, 3, 76, 1, .024, 1.35, true),
    LEWIS_GUN(
            "lewisgun",
            FinalsClass.HEAVY,
            "NETHERITE_SHOVEL",
            2.25,
            50,
            47,
            4,
            66,
            1,
            .016,
            1.35,
            true),
    SA1216("sa1216", FinalsClass.HEAVY, "GOLDEN_SHOVEL", 1.05, 19, 16, 8, 60, 7, .09, 1, true),
    SLEDGEHAMMER("sledgehammer", FinalsClass.HEAVY, "MACE", 9, 3.3, 0, 24, 0, 1, 0, 1, false),
    SPEAR("spear", FinalsClass.HEAVY, "TRIDENT", 5.8, 4.3, 0, 14, 0, 1, 0, 1, false);

    private final String id;
    private final FinalsClass playerClass;
    // Resolve Material only when a live inventory is built; pure roster checks need no server
    // registry.
    private final String material;
    private final double damage;
    private final double range;
    private final int magazine;
    private final int cadenceTicks;
    private final int reloadTicks;
    private final int pellets;
    private final double spread;
    private final double headshot;
    private final boolean automatic;

    FinalsWeapon(
            String id,
            FinalsClass playerClass,
            String material,
            double damage,
            double range,
            int magazine,
            int cadenceTicks,
            int reloadTicks,
            int pellets,
            double spread,
            double headshot,
            boolean automatic) {
        this.id = id;
        this.playerClass = playerClass;
        this.material = material;
        this.damage = damage;
        this.range = range;
        this.magazine = magazine;
        this.cadenceTicks = cadenceTicks;
        this.reloadTicks = reloadTicks;
        this.pellets = pellets;
        this.spread = spread;
        this.headshot = headshot;
        this.automatic = automatic;
    }

    String id() {
        return id;
    }

    FinalsClass playerClass() {
        return playerClass;
    }

    Material material() {
        return Material.valueOf(material);
    }

    double damage() {
        return damage;
    }

    double range() {
        return range;
    }

    int magazine() {
        return magazine;
    }

    int cadenceTicks() {
        return cadenceTicks;
    }

    int reloadTicks() {
        return reloadTicks;
    }

    int pellets() {
        return pellets;
    }

    double spread() {
        return spread;
    }

    double headshot() {
        return headshot;
    }

    boolean automatic() {
        return automatic;
    }

    boolean isGun() {
        return magazine > 0;
    }

    Component display() {
        return I18n.tr("finals.weapon." + id);
    }

    Component display(Player viewer) {
        return I18n.render(display(), viewer);
    }

    double damageAt(double distance, boolean head) {
        double falloff =
                isGun() ? Math.clamp(1 - Math.max(0, distance / range - .45) * .8, .56, 1) : 1;
        return damage * falloff * (head ? headshot : 1);
    }

    static List<FinalsWeapon> byClass(FinalsClass type) {
        return Arrays.stream(values()).filter(weapon -> weapon.playerClass == type).toList();
    }

    static FinalsWeapon parse(String input) {
        if (input == null) return null;
        String id =
                input.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("-", "")
                        .replace("_", "")
                        .replace(" ", "");
        return Arrays.stream(values())
                .filter(weapon -> weapon.id.equals(id))
                .findFirst()
                .orElse(null);
    }
}
