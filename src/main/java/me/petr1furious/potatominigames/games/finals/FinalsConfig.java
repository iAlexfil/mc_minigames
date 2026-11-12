package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/** Match rules, read by each new Finals session. */
public final class FinalsConfig {
    public static final String FILE = "games/finals.yml";

    private final PropertyBag bag = new PropertyBag("finals");
    public final Property<Integer> countdownSeconds =
            bag.define("countdownSeconds", PropertyCodec.INT, 18);
    public final Property<Integer> durationSeconds =
            bag.define("durationSeconds", PropertyCodec.INT, 600);
    public final Property<Integer> vaultOpenSeconds =
            bag.define("vaultOpenSeconds", PropertyCodec.INT, 5);
    public final Property<Integer> cashoutSeconds =
            bag.define("cashoutSeconds", PropertyCodec.INT, 90);
    public final Property<Integer> stealSeconds = bag.define("stealSeconds", PropertyCodec.INT, 5);
    public final Property<Integer> respawnSeconds =
            bag.define("respawnSeconds", PropertyCodec.INT, 5);
    public final Property<Integer> protectionSeconds =
            bag.define("protectionSeconds", PropertyCodec.INT, 2);
    public final Property<Integer> cashoutValue =
            bag.define("cashoutValue", PropertyCodec.INT, 10000);
    public final Property<Integer> targetScore =
            bag.define("targetScore", PropertyCodec.INT, 20000);

    public FinalsConfig(ConfigRegistry configs) {
        String timingVersion = "cashoutTimingVersion";
        String matchTimingVersion = "matchTimingVersion";
        configs.hide(FILE, timingVersion, matchTimingVersion);
        var persisted = configs.file(FILE);
        boolean changed = false;
        if (persisted.getInt(timingVersion, 0) < 1) {
            // The old shipped file stored 40 explicitly. Migrate it once so a later deliberate
            // 40-second setting stays intact; other existing durations remain unchanged.
            if (persisted.getInt("cashoutSeconds", 90) == 40)
                persisted.set("cashoutSeconds", 90);
            persisted.set(timingVersion, 1);
            changed = true;
        }
        if (persisted.getInt(matchTimingVersion, 0) < 1) {
            // Upgrade the former shipped default once; retain custom settings and later edits.
            if (persisted.getInt("durationSeconds", 600) == 360)
                persisted.set("durationSeconds", 600);
            persisted.set(matchTimingVersion, 1);
            changed = true;
        }
        if (changed) configs.saveFile(FILE);
        configs.register(bag, FILE, "");
    }
}
