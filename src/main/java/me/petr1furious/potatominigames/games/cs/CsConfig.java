package me.petr1furious.potatominigames.games.cs;

import me.petr1furious.potatominigames.core.config.*;

final class CsConfig {
    private final PropertyBag bag = new PropertyBag("cs");
    final Property<Integer> half =
            bag.define("roundsPerHalf", PropertyCodec.INT, 15).validate(n -> n >= 1 && n <= 15);
    final Property<Integer> freeze =
            bag.define("freezeSeconds", PropertyCodec.INT, 15).validate(n -> n >= 3 && n <= 30);
    final Property<Integer> round =
            bag.define("roundSeconds", PropertyCodec.INT, 115).validate(n -> n >= 30 && n <= 300);
    final Property<Integer> bomb =
            bag.define("bombSeconds", PropertyCodec.INT, 40).validate(n -> n >= 15 && n <= 60);
    final Property<Integer> buy =
            bag.define("buySeconds", PropertyCodec.INT, 20).validate(n -> n >= 5 && n <= 60);

    CsConfig(ConfigRegistry registry) {
        registry.register(bag, "games/cs.yml", "");
    }
}
