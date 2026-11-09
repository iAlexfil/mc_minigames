package me.petr1furious.potatominigames.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

/** One catalog format for chat, HUD, titles, menus, items and user-facing errors. */
public final class I18n {
    private static final String PREFIX = "potatominigames.";
    private static final Pattern ARGUMENT = Pattern.compile("<(?:arg|argument):([0-9]+)>");
    private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"));
    private static final Map<String, Map<String, String>> CATALOGS = loadCatalogs();
    private static final Map<String, Integer> ARGUMENT_COUNTS = argumentCounts();
    private static final Map<String, TranslatableComponent> LABELS = new ConcurrentHashMap<>();
    private static final MiniMessageTranslator TRANSLATOR = new MiniMessageTranslator(MiniMessage.builder().strict(true).build()) {
        @Override public Key name() { return Key.key("potatominigames", "i18n"); }
        @Override protected String getMiniMessageString(String key, Locale locale) {
            if (!key.startsWith(PREFIX)) return null;
            return CATALOGS.get(language(locale)).get(key.substring(PREFIX.length()));
        }
    };
    private static final TranslatableComponentRenderer<Locale> RENDERER =
            TranslatableComponentRenderer.usingTranslationSource(TRANSLATOR);

    private I18n() {}

    public static void register() {
        validateCatalogs();
        GlobalTranslator.translator().addSource(TRANSLATOR);
    }

    public static void unregister() {
        GlobalTranslator.translator().removeSource(TRANSLATOR);
    }

    /** Russian client locales use Russian; all other clients and the console use English. */
    public static String language(Locale locale) {
        return locale != null && locale.getLanguage().equals("ru") ? "ru" : "en";
    }

    public static boolean has(String key) { return CATALOGS.get("en").containsKey(key); }

    public static TranslatableComponent tr(String key) {
        requireArguments(key, 0);
        return LABELS.computeIfAbsent(key, name -> Component.translatable(PREFIX + name));
    }

    /** Values become literal components, never parsed MiniMessage. Rich component arguments retain styling. */
    public static TranslatableComponent tr(String key, Object... arguments) {
        requireArguments(key, arguments.length);
        if (arguments.length == 0) return tr(key);
        List<ComponentLike> values = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            values.add(argument instanceof ComponentLike component ? component : Component.text(String.valueOf(argument)));
        }
        return Component.translatable(PREFIX + key, values);
    }

    public static Component render(Component message, Locale locale) {
        return RENDERER.render(message, locale == null ? Locale.ENGLISH : locale);
    }

    public static Component render(Component message, CommandSender viewer) {
        return render(message, viewer instanceof Player player ? player.locale() : Locale.ENGLISH);
    }

    public static String plain(Component message, Locale locale) {
        return PlainTextComponentSerializer.plainText().serialize(render(message, locale));
    }

    /** Render metadata on an item freshly created for this viewer, not on a shared template. */
    public static ItemStack item(ItemStack item, Player viewer) { return item(item, viewer.locale()); }

    public static ItemStack item(ItemStack item, Locale locale) {
        if (!item.hasItemMeta()) return item;
        var meta = item.getItemMeta();
        boolean changed = false;
        Component name = meta.displayName();
        if (name != null) {
            Component rendered = render(name, locale);
            if (!rendered.equals(name)) { meta.displayName(rendered); changed = true; }
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> rendered = new ArrayList<>(lore.size());
            for (Component line : lore) rendered.add(render(line, locale));
            if (!rendered.equals(lore)) { meta.lore(rendered); changed = true; }
        }
        if (changed) item.setItemMeta(meta);
        return item;
    }

    public static IllegalArgumentException exception(String key, Object... arguments) {
        return new LocalizedException(tr(key, arguments));
    }

    public static Component message(Throwable error) {
        return error instanceof LocalizedException localized ? localized.component
                : Component.text(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    private static final class LocalizedException extends IllegalArgumentException {
        private final Component component;
        LocalizedException(Component component) {
            super(plain(component, Locale.ENGLISH));
            this.component = component;
        }
    }

    public static Set<String> keys() { return CATALOGS.get("en").keySet(); }

    public static int arguments(String key) {
        Integer count = ARGUMENT_COUNTS.get(key);
        if (count == null) throw new IllegalArgumentException("Unknown translation key: " + key);
        return count;
    }

    private static void requireArguments(String key, int count) {
        int expected = arguments(key);
        if (expected != count) throw new IllegalArgumentException("Translation " + key + " expects " + expected + " arguments, got " + count);
    }

    private static Set<Integer> indexes(String value) {
        Set<Integer> result = new HashSet<>();
        var matcher = ARGUMENT.matcher(value);
        while (matcher.find()) result.add(Integer.parseInt(matcher.group(1)));
        return result;
    }

    private static Map<String, Integer> argumentCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (var entry : CATALOGS.get("en").entrySet()) {
            Set<Integer> indexes = indexes(entry.getValue());
            int count = indexes.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            if (indexes.size() != count) throw new IllegalStateException("Non-contiguous arguments in " + entry.getKey());
            counts.put(entry.getKey(), count);
        }
        return Map.copyOf(counts);
    }

    /** Also runs during build, so incomplete catalogs never become a staged server release. */
    public static void validateCatalogs() {
        if (!CATALOGS.get("en").keySet().equals(CATALOGS.get("ru").keySet()))
            throw new IllegalStateException("English and Russian translation keys differ");
        for (String key : keys()) {
            if (!indexes(CATALOGS.get("en").get(key)).equals(indexes(CATALOGS.get("ru").get(key))))
                throw new IllegalStateException("Translation argument mismatch: " + key);
            List<ComponentLike> values = new ArrayList<>(arguments(key));
            for (int i = 0; i < arguments(key); i++) values.add(Component.text("argument-" + i));
            Component message = Component.translatable(PREFIX + key, values);
            for (Locale locale : SUPPORTED_LOCALES) {
                try {
                    render(message, locale);
                } catch (RuntimeException error) {
                    throw new IllegalStateException("Invalid translation " + language(locale) + "/" + key, error);
                }
            }
        }
    }

    private static BufferedReader resource(String path) throws IOException {
        InputStream stream = I18n.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing localization resource: " + path);
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static Map<String, Map<String, String>> loadCatalogs() {
        try {
            List<String> domains;
            try (var reader = resource("i18n/catalogs.txt")) {
                domains = reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            }
            Map<String, Map<String, String>> languages = new HashMap<>();
            for (String language : List.of("en", "ru")) {
                Map<String, String> messages = new LinkedHashMap<>();
                for (String domain : domains) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    // Catalog keys are literal dotted IDs. Bukkit's default '.' separator
                    // replaces "miners.open" with a section when "miners.open.subtitle" loads.
                    // Catalog IDs never contain NUL, so none of their dots becomes a path.
                    yaml.options().pathSeparator('\0');
                    try (var reader = resource("i18n/" + language + "/" + domain.replace('.', '/') + ".yml")) {
                        yaml.load(reader);
                    }
                    for (var entry : yaml.getValues(true).entrySet()) {
                        if (entry.getValue() instanceof ConfigurationSection) continue;
                        String key = domain + "." + entry.getKey();
                        if (!(entry.getValue() instanceof String text) || text.isEmpty())
                            throw new IOException("Translation must be a nonempty string: " + language + "/" + key);
                        if (messages.putIfAbsent(key, text) != null)
                            throw new IOException("Duplicate translation key: " + key);
                    }
                }
                languages.put(language, Map.copyOf(messages));
            }
            return Map.copyOf(languages);
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load localization catalogs", error);
        }
    }
}
