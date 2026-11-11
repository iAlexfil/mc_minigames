package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Material;

/** Initial CS:GO selection; prices, finite magazines and armour penetration are in CS units. */
enum CsWeapon {
    GLOCK("glock", false, 0, 200, 28, .47, 20, 120, 3, 46, 1.0, .014, 1, Material.IRON_NUGGET),
    USP("usp", false, 1, 200, 35, .505, 12, 24, 3.4, 44, 1.0, .009, 1, Material.FLINT),
    DEAGLE("deagle", false, -1, 700, 53, .932, 7, 35, 4.5, 44, .92, .012, 1, Material.GOLD_INGOT),
    AK47("ak47", true, 0, 2700, 36, .775, 30, 90, 2, 50, .86, .007, 1, Material.IRON_HORSE_ARMOR),
    M4A4(
            "m4a4",
            true,
            1,
            3100,
            33,
            .70,
            30,
            90,
            1.8,
            62,
            .90,
            .006,
            1,
            Material.GOLDEN_HORSE_ARMOR),
    AWP("awp", true, -1, 4750, 115, .975, 10, 30, 29, 74, .80, .10, 1, Material.SPYGLASS),
    MP9(
            "mp9",
            true,
            1,
            1250,
            26,
            .60,
            30,
            120,
            1.4,
            42,
            .96,
            .014,
            1,
            Material.LEATHER_HORSE_ARMOR),
    MAC10("mac10", true, 0, 1050, 29, .575, 30, 100, 1.5, 52, .96, .018, 1, Material.COPPER_INGOT),
    NOVA("nova", true, -1, 1050, 26, .50, 8, 32, 18, 64, .88, .075, 9, Material.BLAZE_ROD);

    final String id;
    final boolean primary;
    final int side, price, magazine, reserve, reload, pellets;
    final double damage, penetration, cadence, mobility, spread;
    final Material material;

    CsWeapon(
            String id,
            boolean primary,
            int side,
            int price,
            double damage,
            double penetration,
            int magazine,
            int reserve,
            double cadence,
            int reload,
            double mobility,
            double spread,
            int pellets,
            Material material) {
        this.id = id;
        this.primary = primary;
        this.side = side;
        this.price = price;
        this.damage = damage;
        this.penetration = penetration;
        this.magazine = magazine;
        this.reserve = reserve;
        this.cadence = cadence;
        this.reload = reload;
        this.mobility = mobility;
        this.spread = spread;
        this.pellets = pellets;
        this.material = material;
    }

    boolean automatic() {
        return this == AK47 || this == M4A4 || this == MP9 || this == MAC10;
    }

    int killReward() {
        return this == AWP ? 100 : this == NOVA ? 900 : this == MP9 || this == MAC10 ? 600 : 300;
    }

    boolean allowed(int side) {
        return this.side < 0 || this.side == side;
    }

    static CsWeapon find(String id) {
        for (var w : values()) if (w.id.equals(id)) return w;
        return null;
    }
}
