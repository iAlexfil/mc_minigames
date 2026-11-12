package me.petr1furious.potatominigames.games.finals;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.command.Suggest;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Spectator-friendly controls; every action rechecks the current match's game-master role. */
final class FinalsEventCommands implements Listener {
    private final Supplier<FinalsSession> current;

    FinalsEventCommands(JavaPlugin plugin, Supplier<FinalsSession> current) {
        this.current = current;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
                Commands.literal("events")
                        .executes(ctx ->
                                GameCommands.run(ctx, c -> open(GameCommands.requirePlayer(c)))));
        root.then(
                Commands.literal("event")
                        .executes(ctx ->
                                GameCommands.run(ctx, c -> open(GameCommands.requirePlayer(c))))
                        .then(
                                Commands.argument("event", StringArgumentType.word())
                                        .suggests(
                                                Suggest.dynamic(() ->
                                                        Stream.concat(
                                                                        Arrays.stream(
                                                                                        FinalsGameEvents
                                                                                                .Kind
                                                                                                .values())
                                                                                .map(
                                                                                        FinalsGameEvents
                                                                                                        .Kind
                                                                                                ::id),
                                                                        Stream.of("stop", "status"))
                                                                .toList()))
                                        .executes(ctx ->
                                                GameCommands.run(
                                                        ctx,
                                                        c ->
                                                                execute(
                                                                        GameCommands.requirePlayer(
                                                                                c),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        c,
                                                                                        "event"))))));
    }

    private FinalsSession requireMaster(Player player) throws CommandFailure {
        FinalsSession session = current.get();
        if (session == null || !session.isGameMaster(player))
            throw new CommandFailure(I18n.tr("finals.gm.only"));
        return session;
    }

    void execute(Player player, String action) throws CommandFailure {
        FinalsSession session = requireMaster(player);
        FinalsGameEvents events = session.gameEvents();
        if (action.equalsIgnoreCase("status")) {
            player.sendMessage(status(events));
            return;
        }
        if (action.equalsIgnoreCase("stop")) {
            if (events == null || events.activeKind() == null)
                player.sendMessage(I18n.tr("finals.event.idle"));
            else events.stop();
            return;
        }
        FinalsGameEvents.Kind kind =
                Arrays.stream(FinalsGameEvents.Kind.values())
                        .filter(value -> value.id().equals(action.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(null);
        if (kind == null) throw new CommandFailure(I18n.tr("finals.event.unknown"));
        if (!session.gameEventsReady() || events == null)
            throw new CommandFailure(I18n.tr("finals.event.not_ready"));
        if (!events.start(kind)) throw new CommandFailure(I18n.tr("finals.event.busy"));
    }

    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final FinalsSession session;
        Inventory inventory;

        Menu(Player owner, FinalsSession session) {
            this.owner = owner.getUniqueId();
            this.session = session;
        }

        public Inventory getInventory() {
            return inventory;
        }
    }

    static boolean isMenu(Inventory inventory) {
        return inventory.getHolder(false) instanceof Menu;
    }

    void open(Player player) throws CommandFailure {
        Menu menu = new Menu(player, requireMaster(player));
        menu.inventory =
                Bukkit.createInventory(menu, 27, I18n.render(I18n.tr("finals.gm.menu"), player));
        paint(player, menu);
        player.openInventory(menu.inventory);
    }

    private static Component status(FinalsGameEvents events) {
        return events == null || events.activeKind() == null
                ? I18n.tr("finals.event.idle")
                : I18n.tr(
                        "finals.event.status",
                        I18n.tr(events.activeKind().nameKey()),
                        events.remainingSeconds());
    }

    private void paint(Player player, Menu menu) {
        FinalsGameEvents events = menu.session.gameEvents();
        menu.inventory.setItem(
                4,
                item(
                        player,
                        Material.CLOCK,
                        status(events),
                        List.of(I18n.tr("finals.event.rules"))));
        FinalsGameEvents.Kind[] kinds = FinalsGameEvents.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            FinalsGameEvents.Kind kind = kinds[i];
            menu.inventory.setItem(
                    10 + i * 3,
                    item(
                            player,
                            kind.icon(),
                            I18n.tr(kind.nameKey()),
                            List.of(
                                    I18n.tr(kind.nameKey() + ".lore"),
                                    I18n.tr("finals.event.start_hint"))));
        }
        menu.inventory.setItem(
                22, item(player, Material.BARRIER, I18n.tr("finals.event.stop"), List.of()));
        menu.inventory.setItem(
                26, item(player, Material.SPYGLASS, I18n.tr("finals.event.refresh"), List.of()));
    }

    private static ItemStack item(
            Player player, Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.colorIfAbsent(NamedTextColor.GOLD));
            meta.lore(lore);
        });
        return I18n.item(item, player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId())
                || player.getOpenInventory().getTopInventory() != menu.inventory
                || current.get() != menu.session
                || !menu.session.isGameMaster(player)) return;
        int slot = event.getRawSlot();
        String action = null;
        if (slot == 22) action = "stop";
        FinalsGameEvents.Kind[] kinds = FinalsGameEvents.Kind.values();
        for (int i = 0; i < kinds.length; i++) if (slot == 10 + i * 3) action = kinds[i].id();
        if (action != null) {
            try {
                execute(player, action);
            } catch (CommandFailure error) {
                player.sendMessage(error.text());
            }
        }
        paint(player, menu);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isMenu(event.getInventory())) event.setCancelled(true);
    }
}
