package me.petr1furious.potatominigames.i18n;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/** Locale behavior, safe rich arguments and strict shipped-catalog validation, not wording snapshots. */
public final class I18nCheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            fixtureProcess(args[0]);
            return;
        }
        I18n.register();
        try {
            Component rich = Component.text("Alex123", NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/play"))
                    .hoverEvent(HoverEvent.showText(Component.text("player detail")));
            Component message = I18n.tr("common.summary.winner", rich).color(NamedTextColor.GREEN);
            Component english = I18n.render(message, Locale.US);
            Component russian = I18n.render(message, Locale.forLanguageTag("ru-RU"));
            check(!cyrillic(I18n.plain(message, Locale.US)), "English selected a Russian translation");
            check(cyrillic(I18n.plain(message, Locale.forLanguageTag("ru-RU"))), "Russian client did not get Russian");
            check(I18n.render(message, Locale.forLanguageTag("ru-BY")).equals(russian), "Russian regions differ");
            check(I18n.render(message, Locale.GERMAN).equals(english), "Unsupported locale does not use English");
            check(I18n.render(message, nullLocale()).equals(english), "Missing locale does not use English");
            for (Component translated : List.of(english, russian)) {
                Component argument = findText(translated, "Alex123");
                check(argument != null && argument.color() == NamedTextColor.GOLD,
                        "Rich argument lost its own color");
                check(rich.clickEvent().equals(argument.clickEvent()), "Translation lost argument click event");
                check(rich.hoverEvent().equals(argument.hoverEvent()), "Translation lost argument hover event");
            }
            String payload = "<red>not parsed</red>";
            check(I18n.plain(I18n.tr("common.summary.winner", payload), Locale.ENGLISH).contains(payload),
                    "Untrusted argument was parsed as MiniMessage");
            expectInvalid(() -> I18n.tr("not.a.real.key"));
            expectInvalid(() -> I18n.tr("common.summary.winner"));
            expectInvalid(() -> I18n.tr("common.summary.winner", "one", "extra"));
            expectInvalid(() -> I18n.tr("common.boolean.yes", "extra"));
            IllegalArgumentException failure = I18n.exception("common.command.player_only");
            check(cyrillic(I18n.plain(I18n.message(failure), Locale.forLanguageTag("ru"))),
                    "Exception lost its language-independent component");
            check(!cyrillic(failure.getMessage()), "Technical exception text is not the English fallback");

            for (String key : I18n.keys()) {
                Object[] values = new Object[I18n.arguments(key)];
                for (int i = 0; i < values.length; i++) values[i] = "__I18N_ARGUMENT_" + i + "__";
                Component translated = I18n.tr(key, values);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String serialized = GsonComponentSerializer.gson().serialize(I18n.render(translated, locale));
                    for (Object value : values) check(serialized.contains(value.toString()), "Argument missing from " + key);
                }
            }
            fixtureChecks();
            System.out.println("i18n: English/Russian fallback, rich literal arguments, all catalogs and invalid-data rejection passed");
        } finally {
            I18n.unregister();
        }
    }

    private static Locale nullLocale() { return null; }

    private static boolean cyrillic(String text) {
        return text.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.CYRILLIC);
    }

    private static Component findText(Component component, String value) {
        if (component instanceof TextComponent text && text.content().equals(value)) return component;
        for (Component child : component.children()) {
            Component found = findText(child, value);
            if (found != null) return found;
        }
        return null;
    }

    private static void fixtureProcess(String mode) {
        if (mode.equals("invalid")) {
            try {
                I18n.register();
            } catch (IllegalStateException | ExceptionInInitializerError expected) {
                return;
            }
            throw new AssertionError("Invalid catalogs were accepted");
        }
        I18n.register();
        try {
            Component component = I18n.tr("probe.reorder", "FIRST_ARGUMENT", "SECOND_ARGUMENT");
            String english = I18n.plain(component, Locale.ENGLISH);
            String russian = I18n.plain(component, Locale.forLanguageTag("ru"));
            check(english.indexOf("FIRST_ARGUMENT") < english.indexOf("SECOND_ARGUMENT"), "English argument order changed");
            check(russian.indexOf("SECOND_ARGUMENT") < russian.indexOf("FIRST_ARGUMENT"), "Russian cannot reorder arguments");
            check(english.contains("FIRST_ARGUMENT") && russian.contains("SECOND_ARGUMENT"), "Fixture lost arguments");
            for (String key : List.of("open", "open.subtitle", "open.instructions", "finale", "finale.subtitle")) {
                String id = "probe." + key;
                check(I18n.has(id), "A dotted child key erased its parent or sibling: " + id);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru")))
                    check(!I18n.plain(I18n.tr(id), locale).contains("potatominigames."),
                            "Overlapping flat keys must render in both locales: " + id);
            }
        } finally {
            I18n.unregister();
        }
    }

    private static void fixtureChecks() throws Exception {
        Path directory = Files.createTempDirectory("mc-i18n-check-");
        try {
            Path root = directory.resolve("i18n");
            Files.createDirectories(root.resolve("en"));
            Files.createDirectories(root.resolve("ru"));
            Files.writeString(root.resolve("catalogs.txt"), "probe\n");
            Path english = root.resolve("en/probe.yml");
            Path russian = root.resolve("ru/probe.yml");
            String overlapping = "open: \"Mines open\"\n"
                    + "open.subtitle: \"Fight begins\"\n"
                    + "open.instructions: \"Keep mining\"\n"
                    + "finale.subtitle: \"Border shrinks\"\n"
                    + "finale: \"Finale\"\n";
            String validEnglish = "reorder: \"<arg:0> then <arg:1>\"\n" + overlapping;
            Files.writeString(english, validEnglish);
            Files.writeString(russian, "reorder: \"<arg:1> затем <arg:0>\"\n" + overlapping);
            runFixture(directory, "valid");
            for (String invalid : List.of("other: \"missing key\"\n", "reorder: \"<arg:0>\"\n",
                    "reorder: [broken", "reorder: 42\n", "reorder: \"<red><arg:0> <arg:1>\"\n")) {
                Files.writeString(russian, invalid);
                runFixture(directory, "invalid");
            }
            Files.writeString(english, "reorder: \"<arg:1>\"\n");
            Files.writeString(russian, "reorder: \"<arg:1>\"\n");
            runFixture(directory, "invalid");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void runFixture(Path directory, String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = directory + System.getProperty("path.separator") + System.getProperty("java.class.path");
        Process process = new ProcessBuilder(java, "-cp", classpath, I18nCheck.class.getName(), mode)
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        check(process.waitFor() == 0, "Catalog fixture failed: " + output);
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected a translation key/argument error");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
