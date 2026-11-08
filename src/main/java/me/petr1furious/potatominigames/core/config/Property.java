package me.petr1furious.potatominigames.core.config;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;

public final class Property<T> {
    private final String name;
    private final PropertyCodec<T> codec;
    private final T defaultValue;
    private T value;
    private String type;
    private Supplier<List<String>> choices;
    private boolean strict;
    private Predicate<T> check;

    Property(String name, PropertyCodec<T> codec, T defaultValue) {
        this.name = name;
        this.codec = codec;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    /** Label shown in command hints, e.g. "duration like 5min"; defaults to the codec's. */
    public Property<T> type(String label) {
        this.type = label;
        return this;
    }

    /** Values suggested on tab, without restricting what can be set. */
    public Property<T> suggest(Supplier<List<String>> values) {
        this.choices = values;
        this.strict = false;
        return this;
    }

    /** The only values that can be set by command. */
    public Property<T> oneOf(Supplier<List<String>> values) {
        this.choices = values;
        this.strict = true;
        return this;
    }

    /** Rejects parsed values failing the check. */
    public Property<T> validate(Predicate<T> check) {
        this.check = check;
        return this;
    }

    public String typeLabel() {
        if (type != null) {
            return type;
        }
        return strict ? "one of " + String.join("|", choices.get()) : codec.typeName();
    }

    public Component typeComponent() {
        if (type == null && strict) {
            return I18n.tr("core.config.type.one_of", String.join("|", choices.get()));
        }
        return typeComponent(type != null ? type : codec.typeName());
    }

    /** Known built-in UI labels only; extension labels stay literal. */
    public static Component typeComponent(String label) {
        return switch (label) {
            case "text" -> I18n.tr("core.config.type.text");
            case "list" -> I18n.tr("core.config.type.list");
            case "int" -> I18n.tr("core.config.type.int");
            case "number" -> I18n.tr("core.config.type.number");
            case "boolean" -> I18n.tr("core.config.type.boolean");
            case "material" -> I18n.tr("core.config.type.material");
            case "x y z" -> I18n.tr("core.config.type.vector");
            case "duration like 5min" -> I18n.tr("core.config.type.duration");
            case "#RRGGBB colour" -> I18n.tr("core.config.type.color");
            case "block, empty for platformMaterial" -> I18n.tr("core.config.type.block");
            case "tick or day/noon/night/..." -> I18n.tr("core.config.type.time");
            case "weight, 0 removes the item" -> I18n.tr("core.config.type.weight");
            default -> Component.text(label);
        };
    }

    /** Declared choices, else the codec's (booleans, materials). */
    public List<String> choices() {
        return choices != null ? choices.get() : codec.choices();
    }

    public String renderDefault() {
        return defaultValue == null ? "<unset>" : codec.render(defaultValue);
    }

    PropertyCodec<T> codec() {
        return codec;
    }

    T defaultValue() {
        return defaultValue;
    }

    /** @throws IllegalArgumentException with a user-facing reason */
    T parse(String raw) {
        if (strict) {
            List<String> allowed = choices.get();
            String match = allowed.stream().filter(raw::equalsIgnoreCase).findFirst().orElse(null);
            if (match == null) {
                throw I18n.exception("core.config.options", String.join(", ", allowed));
            }
            raw = match;
        }
        T parsed;
        try {
            parsed = codec.parse(raw);
        } catch (RuntimeException exception) {
            throw I18n.exception("core.config.expected", typeComponent());
        }
        if (check != null && !check.test(parsed)) {
            throw I18n.exception("core.config.expected", typeComponent());
        }
        return parsed;
    }

    void parseAndSet(String raw) {
        value = parse(raw);
    }

    Object toYaml(String raw) {
        return codec.toYaml(parse(raw));
    }

    String render() {
        return value == null ? "<unset>" : codec.render(value);
    }

    void reset() {
        value = defaultValue;
    }

    void load(ConfigurationSection section) {
        value = codec.fromYaml(section, name, defaultValue);
    }

    void save(ConfigurationSection section) {
        section.set(name, value == null ? null : codec.toYaml(value));
    }
}
