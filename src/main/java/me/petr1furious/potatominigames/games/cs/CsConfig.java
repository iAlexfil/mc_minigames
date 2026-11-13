package me.petr1furious.potatominigames.games.cs;

import me.petr1furious.potatominigames.core.config.*;

import org.bukkit.configuration.ConfigurationSection;

final class CsConfig {
    static final int DEFAULT_HALF = 12;
    private static final String FILE = "games/cs.yml";
    private final PropertyBag bag = new PropertyBag("cs");
    final Property<Integer> half =
            bag.define("roundsPerHalf", PropertyCodec.INT, DEFAULT_HALF)
                    .validate(n -> n >= 1 && n <= 15);
    final Property<Integer> freeze =
            bag.define("freezeSeconds", PropertyCodec.INT, 15).validate(n -> n >= 3 && n <= 30);
    final Property<Integer> round =
            bag.define("roundSeconds", PropertyCodec.INT, 115).validate(n -> n >= 30 && n <= 300);
    final Property<Integer> bomb =
            bag.define("bombSeconds", PropertyCodec.INT, 40).validate(n -> n >= 15 && n <= 60);
    final Property<Integer> buy =
            bag.define("buySeconds", PropertyCodec.INT, 20).validate(n -> n >= 5 && n <= 60);

    final Property<Integer> dmDuration =
            bag.define("deathmatch.durationSeconds", PropertyCodec.INT, 300)
                    .validate(n -> n >= 60 && n <= 1800);
    final Property<Integer> dmCountdown =
            bag.define("deathmatch.countdownSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 3 && n <= 30);

    CsConfig(ConfigRegistry registry) {
        if (migrateFormat(registry.file(FILE))) registry.saveFile(FILE);
        registry.hide(FILE, "formatVersion");
        registry.register(bag, FILE, "");
    }

    /** Upgrade the previous MR15 default once; retain deliberately customized half lengths. */
    static boolean migrateFormat(ConfigurationSection config) {
        if (config.getInt("formatVersion", 1) >= 2) return false;
        if (config.getInt("roundsPerHalf", 15) == 15) config.set("roundsPerHalf", DEFAULT_HALF);
        config.set("formatVersion", 2);
        return true;
    }
}
