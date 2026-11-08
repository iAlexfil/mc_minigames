package me.petr1furious.potatominigames.core.command;

import me.petr1furious.potatominigames.i18n.I18n;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import me.petr1furious.potatominigames.core.config.ConfigEditor;
import me.petr1furious.potatominigames.core.config.ConfigEditor.Kind;
import me.petr1furious.potatominigames.core.config.ConfigEditor.Node;

import static me.petr1furious.potatominigames.core.command.GameCommands.run;

/**
 * {@code config [path] [value]}: a dotted path mirroring the YAML; a value sets it, {@code default}
 * resets a declared setting, and lists take {@code add|remove <entry>}.
 */
public final class ConfigCommand {
    private static final String DEFAULT = "default";

    private final String root;
    private final ConfigEditor editor;

    /** @param root the command label, for click-to-edit */
    public ConfigCommand(String root, ConfigEditor editor) {
        this.root = root;
        this.editor = editor;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config")
                .executes(ctx -> run(ctx, c -> show(c, editor.resolve(""))))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(ctx, builder))
                        .executes(ctx -> run(ctx, this::execute)));
    }

    private void execute(CommandContext<CommandSourceStack> ctx) throws CommandFailure {
        String input = ctx.getArgument("args", String.class).strip();
        int space = input.indexOf(' ');
        String path = trimPath(space < 0 ? input : input.substring(0, space));
        String value = space < 0 ? "" : input.substring(space + 1).strip();
        Node node = editor.resolve(path);
        if (node == null) {
            throw unknown(path);
        }
        if (value.isEmpty()) {
            show(ctx, node);
            return;
        }
        if (!ctx.getSource().getSender().hasPermission(GameCommands.ADMIN_PERMISSION)) {
            throw new CommandFailure(I18n.tr("core.config.permission", GameCommands.ADMIN_PERMISSION));
        }
        switch (node.kind()) {
            case SECTION -> throw new CommandFailure(I18n.tr("core.config.is_section", path, String.join(", ", childNames(node))));
            case LIST -> editList(ctx, node, value);
            case VALUE -> setValue(ctx, node, value);
        }
    }

    private void setValue(CommandContext<CommandSourceStack> ctx, Node node, String value) throws CommandFailure {
        Component old = node.valueComponent();
        try {
            if (value.equalsIgnoreCase(DEFAULT)) {
                editor.reset(node);
            } else {
                editor.set(node, value);
            }
        } catch (IllegalArgumentException exception) {
            throw new CommandFailure(I18n.tr("core.config.invalid_value", node.path(), value, I18n.message(exception)));
        }
        Node updated = editor.resolve(node.path());
        Component now = updated != null ? updated.valueComponent() : Component.text(value);
        String messageKey = value.equalsIgnoreCase(DEFAULT) ? "core.config.changed_default"
                : !node.isDeclared() ? "core.config.changed_next" : "core.config.changed";
        ctx.getSource().getSender().sendMessage(I18n.tr(messageKey, node.path(),
                old.color(NamedTextColor.WHITE), now.color(NamedTextColor.GREEN)).color(NamedTextColor.GRAY));
    }

    private void editList(CommandContext<CommandSourceStack> ctx, Node node, String value) throws CommandFailure {
        int space = value.indexOf(' ');
        String verb = (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
        String entry = space < 0 ? "" : value.substring(space + 1).strip();
        if (!(verb.equals("add") || verb.equals("remove")) || entry.isEmpty()) {
            throw new CommandFailure(I18n.tr("core.config.list_usage", node.path(),
                    node.path() + " add <entry>", node.path() + " remove <entry>"));
        }
        String changed;
        try {
            changed = verb.equals("add") ? editor.listAdd(node, entry) : editor.listRemove(node, entry);
        } catch (IllegalArgumentException exception) {
            throw new CommandFailure(I18n.tr("core.config.path_error", node.path(), I18n.message(exception)));
        }
        ctx.getSource().getSender().sendMessage(I18n.tr("core.config.list_changed", node.path(),
                Component.text((verb.equals("add") ? "+ " : "- ") + changed, NamedTextColor.GREEN)).color(NamedTextColor.GRAY));
    }

    private CommandFailure unknown(String path) {
        int dot = path.lastIndexOf('.');
        Node parent = editor.resolve(dot < 0 ? "" : path.substring(0, dot));
        if (parent == null || parent.kind() != Kind.SECTION) {
            return new CommandFailure(I18n.tr("core.config.unknown_path", path));
        }
        return new CommandFailure(parent.path().isEmpty()
                ? I18n.tr("core.config.unknown_path_options", path, String.join(", ", childNames(parent)))
                : I18n.tr("core.config.unknown_path_under", path, parent.path(), String.join(", ", childNames(parent))));
    }

    private List<String> childNames(Node section) {
        return editor.children(section).stream().map(child -> lastSegment(child.path())).toList();
    }

    // Display.

    private void show(CommandContext<CommandSourceStack> ctx, Node node) {
        List<Component> lines = new ArrayList<>();
        switch (node.kind()) {
            case VALUE -> lines.add(valueLine(node, node.path(), 0));
            case LIST -> listLines(node, node.path(), 0, lines);
            case SECTION -> {
                int depth = 0;
                if (!node.path().isEmpty()) {
                    lines.add(Component.text(node.path() + ":", NamedTextColor.GOLD));
                    depth = 1;
                }
                sectionLines(node, depth, lines);
                if (lines.isEmpty()) {
                    lines.add(I18n.tr("core.config.empty").color(NamedTextColor.GRAY));
                }
            }
        }
        ctx.getSource().getSender().sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    private void sectionLines(Node section, int depth, List<Component> lines) {
        for (Node child : editor.children(section)) {
            String label = lastSegment(child.path());
            switch (child.kind()) {
                case VALUE -> lines.add(valueLine(child, label, depth));
                case LIST -> listLines(child, label, depth, lines);
                case SECTION -> {
                    lines.add(Component.text("  ".repeat(depth) + label + ":", NamedTextColor.GOLD));
                    sectionLines(child, depth + 1, lines);
                }
            }
        }
    }

    private Component valueLine(Node node, String label, int depth) {
        NamedTextColor color = node.type().equals("text") ? NamedTextColor.WHITE : NamedTextColor.AQUA;
        return Component.text()
                .append(Component.text("  ".repeat(depth) + label + ": ", NamedTextColor.GRAY))
                .append(node.valueComponent().color(color))
                .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " "))
                .hoverEvent(HoverEvent.showText(hover(node, I18n.tr("core.config.click_edit"))))
                .build();
    }

    private void listLines(Node node, String label, int depth, List<Component> lines) {
        List<String> entries = node.entries();
        String indent = "  ".repeat(depth);
        lines.add(Component.text()
                .append(Component.text(indent + label + ":" + (entries.isEmpty() ? " []" : ""), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " add "))
                .hoverEvent(HoverEvent.showText(hover(node, I18n.tr("core.config.click_add"))))
                .build());
        for (String entry : entries) {
            lines.add(Component.text()
                    .append(Component.text(indent + "  - ", NamedTextColor.GRAY))
                    .append(Component.text(entry, NamedTextColor.WHITE))
                    .clickEvent(ClickEvent.suggestCommand(command(node.path()) + " remove " + entry))
                    .hoverEvent(HoverEvent.showText(I18n.tr("core.config.click_remove").color(NamedTextColor.GRAY)))
                    .build());
        }
    }

    private static Component hover(Node node, Component action) {
        String fallback = node.defaultValue();
        return Component.text()
                .append(node.typeComponent().color(NamedTextColor.AQUA))
                .append(Component.newline())
                .append((fallback != null ? I18n.tr("core.config.default_value", node.defaultComponent())
                        : I18n.tr("core.config.file_only")).color(NamedTextColor.GRAY))
                .append(Component.newline())
                .append(action.color(NamedTextColor.YELLOW))
                .build();
    }

    private String command(String path) {
        return "/" + root + " config " + path;
    }

    // Completion.

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        int space = input.indexOf(' ');
        if (space < 0) {
            return suggestPath(ctx.getSource().getSender(), builder, input);
        }
        if (!ctx.getSource().getSender().hasPermission(GameCommands.ADMIN_PERMISSION)) {
            return builder.buildFuture();
        }
        Node node = editor.resolve(trimPath(input.substring(0, space)));
        String value = input.substring(space + 1);
        int start = builder.getStart() + space + 1;
        if (node == null || node.kind() == Kind.SECTION) {
            return builder.buildFuture();
        }
        if (node.kind() == Kind.LIST) {
            int verbEnd = value.indexOf(' ');
            if (verbEnd < 0) {
                SuggestionsBuilder verbs = builder.createOffset(start);
                offer(ctx.getSource().getSender(), verbs, "add", I18n.tr("core.config.suggest_add"), value);
                offer(ctx.getSource().getSender(), verbs, "remove", I18n.tr("core.config.suggest_remove"), value);
                return verbs.buildFuture();
            }
            SuggestionsBuilder entries = builder.createOffset(start + verbEnd + 1);
            if (value.substring(0, verbEnd).equalsIgnoreCase("remove")) {
                for (String entry : node.entries()) {
                    offer(ctx.getSource().getSender(), entries, entry, I18n.tr("core.config.suggest_listed"), value.substring(verbEnd + 1));
                }
            }
            return entries.buildFuture();
        }
        SuggestionsBuilder values = builder.createOffset(start);
        String current = node.value();
        if (node.isDeclared() || node.raw() != null) {
            offer(ctx.getSource().getSender(), values, current, I18n.tr("core.config.suggest_current", node.typeComponent()), value);
        }
        for (String choice : node.choices()) {
            if (!choice.equalsIgnoreCase(current)) {
                offer(ctx.getSource().getSender(), values, choice, node.typeComponent(), value);
            }
        }
        if (node.isDeclared() && !DEFAULT.equalsIgnoreCase(current)) {
            offer(ctx.getSource().getSender(), values, DEFAULT, I18n.tr("core.config.suggest_reset", node.defaultComponent()), value);
        }
        return values.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPath(CommandSender viewer, SuggestionsBuilder builder, String input) {
        int dot = input.lastIndexOf('.');
        Node parent = editor.resolve(dot < 0 ? "" : input.substring(0, dot));
        SuggestionsBuilder segments = builder.createOffset(builder.getStart() + dot + 1);
        if (parent == null || parent.kind() != Kind.SECTION) {
            return segments.buildFuture();
        }
        String typed = input.substring(dot + 1);
        for (Node child : editor.children(parent)) {
            String name = lastSegment(child.path());
            Component tooltip = switch (child.kind()) {
                case SECTION -> I18n.tr("core.config.suggest_section");
                case LIST -> I18n.tr("core.config.suggest_list", child.entries().size());
                case VALUE -> I18n.tr("core.config.suggest_value", child.typeComponent(), child.valueComponent());
            };
            offer(viewer, segments, child.kind() == Kind.SECTION ? name + "." : name, tooltip, typed);
        }
        return segments.buildFuture();
    }

    private static void offer(CommandSender viewer, SuggestionsBuilder builder, String text, Component tooltip, String typed) {
        if (text.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            builder.suggest(text, MessageComponentSerializer.message()
                    .serialize(I18n.render(tooltip.color(NamedTextColor.GRAY), viewer)));
        }
    }

    private static String trimPath(String path) {
        return path.endsWith(".") ? path.substring(0, path.length() - 1) : path;
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
