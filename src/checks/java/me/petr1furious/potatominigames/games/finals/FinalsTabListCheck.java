package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Method;
import java.util.Locale;

/** Tab rows must preserve enemy health privacy and use the viewer's locale. */
public final class FinalsTabListCheck {
    private static int checks;
    private static Method entry;

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsTabList");
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Finals needs private Tab rows for class, kills and allied health", error);
        }
        entry = type.getDeclaredMethod("entry", Locale.class, String.class, int.class,
                FinalsClass.class, int.class, Double.class, Double.class, boolean.class);
        entry.setAccessible(true);
        Endpoint viewer = new Endpoint("Alice", Locale.ENGLISH);
        Endpoint target = new Endpoint("Bob", Locale.ENGLISH);
        String allied = row(viewer, target, FinalsClass.MEDIUM, 7, true, false);
        require(allied.contains("Bob") && allied.contains("Medium (M)") && allied.contains("7 kills"),
                "A contestant's row contains their name, equipped class and match kills");
        require(allied.contains("24/25 HP"), "Fractional health rounds up while using the actual maximum");

        String enemy = row(viewer, target, FinalsClass.LIGHT, 3, false, false);
        require(enemy.contains("Light (S)") && enemy.contains("3 kills"), "Enemies retain public class and kills");
        require(!enemy.contains("HP") && !enemy.contains("24/25"), "Enemy health is never included or queried");
        String enemyDead = row(viewer, target, FinalsClass.HEAVY, 4, false, true);
        require(enemyDead.contains("Heavy (L)") && !enemyDead.contains("Respawning") && !enemyDead.contains("HP"),
                "Enemy respawn state does not disclose a private teammate health indicator");
        String dead = row(viewer, target, FinalsClass.MEDIUM, 7, true, true);
        require(dead.contains("Respawning") && !dead.contains("HP"),
                "An allied respawn camera is shown as respawning without reading its full health");

        target.health = 2.1;
        target.maximum = 35;
        String changed = row(viewer, target, FinalsClass.HEAVY, 8, true, false);
        require(changed.contains("3/35 HP") && changed.contains("8 kills") && changed.contains("Heavy (L)"),
                "Damage, kill credit and a newly equipped class appear in subsequent rows");
        target.health = 99;
        require(row(viewer, target, FinalsClass.HEAVY, 8, true, false).contains("35/35 HP"),
                "Health display never exceeds the actual maximum");
        target.health = 0;
        require(row(viewer, target, FinalsClass.HEAVY, 8, true, false).contains("0/35 HP"),
                "Zero health is retained rather than rounded to one");

        viewer.locale = Locale.forLanguageTag("ru-RU");
        String russian = row(viewer, target, FinalsClass.HEAVY, 8, true, true);
        require(russian.contains("Bob") && russian.matches("(?s).*[А-Яа-яЁё].*") && !russian.contains("Respawning"),
                "Class, kills and respawn labels use the viewer's Russian locale");
        viewer.locale = Locale.GERMAN;
        String fallback = row(viewer, target, FinalsClass.MEDIUM, 0, false, false);
        require(fallback.contains("Medium (M)") && fallback.contains("0 kills"),
                "Other client languages fall back to English");
        I18n.validateCatalogs();
        System.out.println("FinalsTabListCheck: " + checks + " checks passed");
    }

    private static String row(Endpoint viewer, Endpoint target, FinalsClass build, int kills,
                              boolean teammate, boolean respawning) throws Exception {
        Component result = (Component) entry.invoke(null, viewer.locale, target.name, 0,
                build, kills, teammate && !respawning ? Double.valueOf(target.health) : null,
                target.maximum, teammate && respawning);
        return PlainTextComponentSerializer.plainText().serialize(result);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Endpoint {
        final String name;
        Locale locale;
        double health = 23.5, maximum = 25;

        Endpoint(String name, Locale locale) {
            this.name = name;
            this.locale = locale;
        }
    }
}
