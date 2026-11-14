package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Material;

/**
 * CS2 purchase prices and CS-inspired weapon roles, with firing/accuracy values adapted to
 * Minecraft ticks.
 */
enum CsWeapon {
    GLOCK(
            "glock",
            Category.PISTOL,
            0,
            200,
            28,
            .47,
            20,
            120,
            3,
            46,
            240,
            .014,
            1,
            Material.IRON_NUGGET),
    USP("usp", Category.PISTOL, 1, 200, 35, .505, 12, 24, 3.4, 44, 240, .009, 1, Material.FLINT),
    P250(
            "p250",
            Category.PISTOL,
            -1,
            300,
            38,
            .64,
            13,
            26,
            3,
            44,
            240,
            .015,
            1,
            Material.IRON_INGOT),
    DUAL_BERETTAS(
            "dual_berettas",
            Category.PISTOL,
            -1,
            300,
            38,
            .575,
            30,
            120,
            2.4,
            76,
            240,
            .020,
            1,
            Material.GOLD_NUGGET),
    FIVE_SEVEN(
            "five_seven",
            Category.PISTOL,
            1,
            500,
            32,
            .911,
            20,
            100,
            3,
            44,
            240,
            .015,
            1,
            Material.PRISMARINE_SHARD),
    TEC9(
            "tec9",
            Category.PISTOL,
            0,
            500,
            33,
            .906,
            18,
            90,
            2.4,
            50,
            240,
            .018,
            1,
            Material.AMETHYST_SHARD),
    CZ75("cz75", Category.PISTOL, -1, 500, 31, .776, 12, 12, 2, 54, 240, .020, 1, Material.QUARTZ),
    DEAGLE(
            "deagle",
            Category.PISTOL,
            -1,
            700,
            53,
            .932,
            7,
            35,
            4.5,
            44,
            230,
            .012,
            1,
            Material.GOLD_INGOT),

    MAC10(
            "mac10",
            Category.SMG,
            0,
            1050,
            29,
            .575,
            30,
            100,
            1.5,
            52,
            240,
            .018,
            1,
            Material.COPPER_INGOT),
    MP9(
            "mp9",
            Category.SMG,
            1,
            1250,
            26,
            .60,
            30,
            120,
            1.4,
            42,
            240,
            .014,
            1,
            Material.LEATHER_HORSE_ARMOR),
    UMP45(
            "ump45",
            Category.SMG,
            -1,
            1200,
            35,
            .65,
            25,
            100,
            1.8,
            70,
            230,
            .014,
            1,
            Material.NETHERITE_SCRAP),
    MP7("mp7", Category.SMG, -1, 1400, 29, .625, 30, 120, 1.6, 62, 220, .012, 1, Material.IRON_HOE),
    P90(
            "p90",
            Category.SMG,
            -1,
            2350,
            26,
            .69,
            50,
            100,
            1.33,
            66,
            230,
            .017,
            1,
            Material.GOLDEN_HOE),

    GALIL(
            "galil",
            Category.RIFLE,
            0,
            1800,
            30,
            .775,
            35,
            90,
            1.8,
            60,
            215,
            .010,
            1,
            Material.STONE_HOE),
    FAMAS(
            "famas",
            Category.RIFLE,
            1,
            1950,
            30,
            .70,
            25,
            90,
            1.8,
            66,
            220,
            .009,
            1,
            Material.DIAMOND_HOE),
    AK47(
            "ak47",
            Category.RIFLE,
            0,
            2700,
            36,
            .775,
            30,
            90,
            2,
            50,
            215,
            .007,
            1,
            Material.IRON_HORSE_ARMOR),
    M4A1S(
            "m4a1s",
            Category.RIFLE,
            1,
            2900,
            38,
            .70,
            20,
            80,
            2,
            62,
            225,
            .004,
            1,
            Material.DIAMOND_HORSE_ARMOR),
    M4A4(
            "m4a4",
            Category.RIFLE,
            1,
            2900,
            33,
            .70,
            30,
            90,
            1.8,
            62,
            225,
            .006,
            1,
            Material.GOLDEN_HORSE_ARMOR),
    AUG("aug", Category.RIFLE, 1, 3300, 28, .90, 30, 90, 2, 76, 220, .009, 1, Material.IRON_AXE),
    SG553(
            "sg553",
            Category.RIFLE,
            0,
            3000,
            30,
            1.0,
            30,
            90,
            2.2,
            56,
            210,
            .010,
            1,
            Material.DIAMOND_AXE),

    SSG08(
            "ssg08",
            Category.SNIPER,
            -1,
            1700,
            88,
            .85,
            10,
            90,
            25,
            74,
            230,
            .060,
            1,
            Material.DIAMOND_SHOVEL),
    AWP(
            "awp",
            Category.SNIPER,
            -1,
            4750,
            115,
            .975,
            10,
            30,
            29,
            74,
            200,
            .10,
            1,
            Material.NETHERITE_HOE),

    NOVA(
            "nova",
            Category.SHOTGUN,
            -1,
            1050,
            26,
            .50,
            8,
            32,
            18,
            64,
            220,
            .075,
            9,
            Material.BLAZE_ROD),
    XM1014(
            "xm1014",
            Category.SHOTGUN,
            -1,
            2000,
            20,
            .80,
            7,
            32,
            5,
            80,
            215,
            .065,
            6,
            Material.BREEZE_ROD),
    MAG7("mag7", Category.SHOTGUN, 1, 1300, 30, .75, 5, 32, 17, 48, 225, .070, 8, Material.STICK),
    SAWED_OFF(
            "sawed_off",
            Category.SHOTGUN,
            0,
            1100,
            32,
            .75,
            7,
            32,
            24,
            88,
            210,
            .095,
            8,
            Material.BONE),

    NEGEV(
            "negev",
            Category.HEAVY,
            -1,
            1700,
            35,
            .71,
            150,
            300,
            1.5,
            114,
            150,
            .055,
            1,
            Material.NETHERITE_INGOT);

    enum Category {
        PISTOL,
        SMG,
        RIFLE,
        SNIPER,
        SHOTGUN,
        HEAVY
    }

    final String id;
    final Category category;
    final boolean primary;
    final int side, price, magazine, reserve, reload, pellets, maxSpeed;
    final double damage, penetration, cadence, mobility, spread;
    final Material material;

    CsWeapon(
            String id,
            Category category,
            int side,
            int price,
            double damage,
            double penetration,
            int magazine,
            int reserve,
            double cadence,
            int reload,
            int maxSpeed,
            double spread,
            int pellets,
            Material material) {
        this.id = id;
        this.category = category;
        this.primary = category != Category.PISTOL;
        this.side = side;
        this.price = price;
        this.damage = damage;
        this.penetration = penetration;
        this.magazine = magazine;
        this.reserve = reserve;
        this.cadence = cadence;
        this.reload = reload;
        this.maxSpeed = maxSpeed;
        this.mobility = maxSpeed / 250.0;
        this.spread = spread;
        this.pellets = pellets;
        this.material = material;
    }

    boolean automatic() {
        return category == Category.RIFLE
                || category == Category.SMG
                || category == Category.HEAVY
                || this == CZ75
                || this == XM1014;
    }

    boolean scope() {
        return category == Category.SNIPER || this == AUG || this == SG553;
    }

    int scopedSpeed() {
        return switch (this) {
            case AWP -> 100;
            case AUG, SG553 -> 150;
            default -> maxSpeed;
        };
    }

    double scopedMobility() {
        return (double) scopedSpeed() / maxSpeed;
    }

    double attackMobility() {
        return this == NEGEV ? .5 : 1;
    }

    boolean requiresTriggerRelease() {
        return category == Category.PISTOL && !automatic();
    }

    boolean silenced() {
        return this == USP || this == M4A1S;
    }

    int soundRadius() {
        if (silenced()) return 36;
        return switch (category) {
            case PISTOL -> 128;
            case SMG, SHOTGUN -> 144;
            case RIFLE, HEAVY -> 176;
            case SNIPER -> 192;
        };
    }

    boolean shellReload() {
        return this == NOVA || this == XM1014 || this == SAWED_OFF;
    }

    int shellTicks() {
        return this == XM1014 ? 10 : 11;
    }

    int deployTicks() {
        return this == CZ75 ? 35 : category == Category.SNIPER || this == NEGEV ? 25 : 12;
    }

    double range() {
        // Dust II spans over 200 blocks diagonally. Damage falloff, not a short cutoff,
        // differentiates weapon roles at range.
        return 256;
    }

    double rangeFalloff() {
        return this == UMP45
                ? .75
                : category == Category.SMG
                        ? .87
                        : category == Category.SHOTGUN
                                ? .70
                                : category == Category.SNIPER ? .99 : this == M4A1S ? .94 : .98;
    }

    double damageScale(double distance) {
        // Compress the former distance curve to 64%: new 64 blocks = old 100 blocks.
        return Math.pow(rangeFalloff(), distance / (16 * .64));
    }

    double shotSpread(boolean scoped, int burst) {
        if (scoped && scope()) return category == Category.SNIPER ? .0008 : .002;
        // The first rounds are deliberately hard to control; a sustained spray stabilizes.
        if (this == NEGEV) return burst < 12 ? spread : .003;
        return spread;
    }

    double movementSpread(double horizontalSpeed) {
        if (horizontalSpeed <= .003) return 0;
        // .26 walk speed produces roughly .28 blocks/tick before weapon mobility.
        // Keep a small noise floor; slow movement has a modest penalty, running a large one.
        double fraction = Math.clamp((horizontalSpeed - .003) / (.28 * mobility - .003), 0, 1);
        double runningPenalty =
                switch (this) {
                    case GLOCK -> .030;
                    case USP -> .040;
                    case P250 -> .055;
                    case DUAL_BERETTAS -> .050;
                    case FIVE_SEVEN -> .045;
                    case TEC9 -> .020;
                    case CZ75 -> .060;
                    case DEAGLE -> .100;
                    case MAC10 -> .032;
                    case MP9 -> .026;
                    case UMP45 -> .042;
                    case MP7 -> .036;
                    case P90 -> .038;
                    case GALIL -> .095;
                    case FAMAS -> .090;
                    case AK47 -> .100;
                    case M4A1S -> .080;
                    case M4A4 -> .085;
                    case AUG -> .100;
                    case SG553 -> .105;
                    case SSG08 -> .155;
                    case AWP -> .200;
                    case NOVA -> .050;
                    case XM1014 -> .060;
                    case MAG7 -> .045;
                    case SAWED_OFF -> .055;
                    case NEGEV -> .135;
                };
        return runningPenalty * fraction * fraction;
    }

    double firingSpreadStep() {
        if (this == NEGEV) return .00035; // Preserve its deliberate sustained-fire stabilization.
        if (this == DEAGLE) return .010;
        return switch (category) {
            case PISTOL, SNIPER -> .006;
            case SMG -> .0035;
            case RIFLE -> .0045;
            case SHOTGUN -> .003;
            case HEAVY -> .004;
        };
    }

    /** Time to recover one shot's accuracy penalty; longer bursts take longer to settle. */
    int accuracyRecoveryTicks() {
        if (this == DEAGLE) return 12;
        return switch (category) {
            case PISTOL, RIFLE, SHOTGUN -> 8;
            case SMG -> 6;
            case SNIPER -> 12;
            case HEAVY -> 10;
        };
    }

    double recoilPitch(int burst, boolean scoped) {
        if (!automatic()) return 0;
        if (this == NEGEV) return burst < 12 ? 2 + burst * .2 : .7;
        return Math.min(5.5, Math.max(0, burst - 1) * .35) * (scoped ? .6 : 1);
    }

    int killReward() {
        return this == AWP || this == CZ75
                ? 100
                : category == Category.SHOTGUN
                        ? 900
                        : category == Category.SMG && this != P90 ? 600 : 300;
    }

    boolean allowed(int side) {
        return this.side < 0 || this.side == side;
    }

    static CsWeapon find(String id) {
        for (var w : values()) if (w.id.equals(id)) return w;
        return null;
    }
}
