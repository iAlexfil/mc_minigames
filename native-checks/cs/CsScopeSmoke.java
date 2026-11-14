import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Real native movement attributes with a player transport that mirrors CraftPlayer.setWalkSpeed.
 */
final class CsScopeSmoke {
    static void run(ClassLoader loader, World world) throws Exception {
        Zombie body =
                world.spawn(
                        world.getSpawnLocation().add(0, 5, 0),
                        Zombie.class,
                        false,
                        zombie -> {
                            zombie.setBaby(false);
                            zombie.setAI(false);
                            zombie.setSilent(true);
                        });
        try {
            var attribute = body.getAttribute(Attribute.MOVEMENT_SPEED);
            require(
                    attribute.getModifiers().isEmpty(),
                    "player-like fixture starts without random zombie/baby movement modifiers");
            float[] walking = {.2f};
            UUID id = UUID.randomUUID();
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getWalkSpeed" -> walking[0];
                                                case "setWalkSpeed" -> {
                                                    require(
                                                            (float) args[0] > 0
                                                                    && (float) args[0] <= 1,
                                                            "compensation stays inside native"
                                                                    + " walk-speed limits");
                                                    walking[0] = (float) args[0];
                                                    attribute.setBaseValue(walking[0] / 2);
                                                    yield null;
                                                }
                                                case "getAttribute" ->
                                                        args[0] == Attribute.MOVEMENT_SPEED
                                                                ? attribute
                                                                : null;
                                                case "toString" -> "CsScopeSmokePlayer";
                                                default ->
                                                        throw new UnsupportedOperationException(
                                                                method.getName());
                                            });
            String pkg = "me.petr1furious.potatominigames.games.cs.";
            Class<?> scopeType = Class.forName(pkg + "CsScope", true, loader);
            Class<?> weaponType = Class.forName(pkg + "CsWeapon", true, loader);
            Constructor<?> constructor = scopeType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object scope = constructor.newInstance();
            Method apply =
                    scopeType.getDeclaredMethod("apply", Player.class, weaponType, double.class);
            Method stop = scopeType.getDeclaredMethod("stop", Player.class);
            Method clear = scopeType.getDeclaredMethod("clear");
            apply.setAccessible(true);
            stop.setAccessible(true);
            clear.setAccessible(true);
            for (Object weapon : weaponType.getEnumConstants()) {
                String name = weapon.toString();
                if (!java.util.Set.of("AWP", "SSG08", "AUG", "SG553").contains(name)) continue;
                double expectedFov =
                        switch (name) {
                            case "AWP" -> .555;
                            case "SSG08" -> .6196;
                            default -> .578;
                        };
                double scopedRatio =
                        switch (name) {
                            case "AWP" -> .5;
                            case "SSG08" -> 1;
                            case "AUG" -> 150.0 / 220;
                            default -> 150.0 / 210;
                        };
                attribute.setBaseValue(.1);
                walking[0] = .2f;
                int before = attribute.getModifiers().size();
                apply.invoke(scope, player, weapon, .2);
                double ratio = attribute.getValue() / (walking[0] / 2);
                require(
                        Math.abs((ratio + 1) / 2 - expectedFov) < .000001,
                        "native FOV ratio for " + name);
                require(
                        Math.abs(attribute.getValue() - .1 * scopedRatio) < .000001,
                        "compensated real walking speed for " + name);
                apply.invoke(scope, player, weapon, .2);
                require(
                        attribute.getModifiers().size() == before + 1,
                        "scope never stacks modifiers");
                stop.invoke(scope, player);
                require(
                        attribute.getModifiers().size() == before && walking[0] == .2f,
                        "scope stop restores attributes and walk speed");
                apply.invoke(scope, player, weapon, .2);
                clear.invoke(scope);
                require(
                        attribute.getModifiers().size() == before && walking[0] == .2f,
                        "scope cleanup restores attributes and walk speed");
            }
        } finally {
            body.remove();
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
