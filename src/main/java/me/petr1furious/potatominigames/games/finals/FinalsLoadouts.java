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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Persistent personal choices; a session applies them only at its next safe spawn. */
final class FinalsLoadouts implements Listener {
    record Choice(FinalsClass playerClass, FinalsWeapon weapon) {
        Choice {
            Objects.requireNonNull(playerClass);
            Objects.requireNonNull(weapon);
            if (weapon.playerClass() != playerClass)
                throw new IllegalArgumentException("Weapon class mismatch");
        }

        static Choice defaults() {
            return new Choice(FinalsClass.MEDIUM, FinalsWeapon.AKM);
        }
    }

    private final JavaPlugin plugin;
    private final Path file;
    private final Map<UUID, Choice> choices = new HashMap<>();
    private final Consumer<Player> changed;

    FinalsLoadouts(JavaPlugin plugin, Consumer<Player> changed) {
        this.plugin = plugin;
        this.changed = changed;
        this.file = plugin.getDataFolder().toPath().resolve("finals/loadouts.yml");
        if (Files.exists(file)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    FinalsClass type = FinalsClass.parse(yaml.getString(key + ".class"));
                    FinalsWeapon weapon = FinalsWeapon.parse(yaml.getString(key + ".weapon"));
                    if (type != null && weapon != null && weapon.playerClass() == type)
                        choices.put(id, new Choice(type, weapon));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    Choice choice(UUID id) {
        return choices.getOrDefault(id, Choice.defaults());
    }

    private void choose(Player player, Choice choice) throws CommandFailure {
        UUID id = player.getUniqueId();
        Choice previous = choices.put(id, choice);
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            choices.forEach((owner, value) -> {
                yaml.set(owner + ".class", value.playerClass().id());
                yaml.set(owner + ".weapon", value.weapon().id());
            });
            Path temp = Files.createTempFile(file.getParent(), "loadouts-", ".yml");
            try {
                yaml.save(temp.toFile());
                try {
                    Files.move(
                            temp,
                            file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            if (previous == null) choices.remove(id);
            else choices.put(id, previous);
            plugin.getLogger().warning("Could not save Finals loadouts: " + error.getMessage());
            throw new CommandFailure(I18n.tr("finals.loadout.save_failed"));
        }
        player.sendMessage(
                I18n.tr(
                        "finals.loadout.saved",
                        choice.playerClass().display(),
                        choice.weapon().display()));
        changed.accept(player);
    }

    void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
                Commands.literal("loadout")
                        .executes(ctx ->
                                GameCommands.run(ctx, c -> open(GameCommands.requirePlayer(c)))));
        root.then(
                Commands.literal("class")
                        .then(
                                Commands.argument("class", StringArgumentType.word())
                                        .suggests(
                                                Suggest.dynamic(() ->
                                                        List.of(
                                                                "S", "M", "L", "light", "medium",
                                                                "heavy")))
                                        .executes(ctx ->
                                                GameCommands.run(
                                                        ctx,
                                                        c -> {
                                                            Player player =
                                                                    GameCommands.requirePlayer(c);
                                                            FinalsClass type =
                                                                    FinalsClass.parse(
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "class"));
                                                            if (type == null)
                                                                throw new CommandFailure(
                                                                        I18n.tr(
                                                                                "finals.loadout.unknown_class"));
                                                            Choice current =
                                                                    choice(player.getUniqueId());
                                                            choose(
                                                                    player,
                                                                    new Choice(
                                                                            type,
                                                                            current.playerClass()
                                                                                            == type
                                                                                    ? current
                                                                                            .weapon()
                                                                                    : FinalsWeapon
                                                                                            .byClass(
                                                                                                    type)
                                                                                            .getFirst()));
                                                        }))));
        root.then(
                Commands.literal("weapon")
                        .then(
                                Commands.argument("weapon", StringArgumentType.word())
                                        .suggests(
                                                Suggest.dynamic(() ->
                                                        Arrays.stream(FinalsWeapon.values())
                                                                .map(FinalsWeapon::id)
                                                                .toList()))
                                        .executes(ctx ->
                                                GameCommands.run(
                                                        ctx,
                                                        c -> {
                                                            Player player =
                                                                    GameCommands.requirePlayer(c);
                                                            FinalsWeapon weapon =
                                                                    FinalsWeapon.parse(
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "weapon"));
                                                            if (weapon == null)
                                                                throw new CommandFailure(
                                                                        I18n.tr(
                                                                                "finals.loadout.unknown_weapon"));
                                                            choose(
                                                                    player,
                                                                    new Choice(
                                                                            weapon.playerClass(),
                                                                            weapon));
                                                        }))));
    }

    private static final class Menu implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Menu(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static boolean isMenu(Inventory inventory) {
        return inventory.getHolder(false) instanceof Menu;
    }

    static boolean viewing(Player player) {
        return isMenu(player.getOpenInventory().getTopInventory());
    }

    ItemStack button(Player player) {
        return item(
                player,
                Material.NETHER_STAR,
                I18n.tr("finals.loadout.button"),
                List.of(I18n.tr("finals.loadout.button_lore")),
                false);
    }

    void open(Player player) {
        Menu holder = new Menu(player.getUniqueId());
        holder.inventory =
                Bukkit.createInventory(
                        holder, 54, I18n.render(I18n.tr("finals.loadout.title"), player));
        paint(player, holder.inventory);
        player.openInventory(holder.inventory);
    }

    private void paint(Player player, Inventory inventory) {
        inventory.clear();
        Choice selected = choice(player.getUniqueId());
        for (FinalsClass type : FinalsClass.values()) {
            Material icon =
                    switch (type) {
                        case LIGHT -> Material.FEATHER;
                        case MEDIUM -> Material.IRON_CHESTPLATE;
                        case HEAVY -> Material.ANVIL;
                    };
            inventory.setItem(
                    11 + type.ordinal() * 2,
                    item(
                            player,
                            icon,
                            type.display(),
                            List.of(
                                    I18n.tr(
                                            "finals.loadout.stats",
                                            type.health(),
                                            Math.round(type.walkSpeed() * 500)),
                                    I18n.tr("finals.loadout.kit." + type.id()),
                                    I18n.tr("finals.loadout.select_class")),
                            type == selected.playerClass()));
        }
        List<FinalsWeapon> weapons = FinalsWeapon.byClass(selected.playerClass());
        for (int i = 0; i < weapons.size(); i++) {
            FinalsWeapon weapon = weapons.get(i);
            inventory.setItem(
                    28 + i,
                    item(
                            player,
                            weapon.material(),
                            weapon.display(),
                            List.of(
                                    weapon.isGun()
                                            ? I18n.tr(
                                                    "finals.loadout.gun_stats",
                                                    weapon.damage(),
                                                    weapon.range(),
                                                    weapon.magazine())
                                            : I18n.tr(
                                                    "finals.loadout.melee_stats",
                                                    weapon.damage(),
                                                    weapon.range()),
                                    I18n.tr("finals.loadout.select_weapon")),
                            weapon == selected.weapon()));
        }
        inventory.setItem(
                45,
                item(
                        player,
                        Material.BOOK,
                        I18n.tr("finals.loadout.controls"),
                        List.of(
                                I18n.tr("finals.loadout.help"),
                                I18n.tr("finals.loadout.next_spawn")),
                        false));
        inventory.setItem(
                49,
                item(player, Material.BARRIER, I18n.tr("finals.loadout.close"), List.of(), false));
    }

    private static ItemStack item(
            Player player,
            Material material,
            Component name,
            List<Component> lore,
            boolean selected) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(
                    name.colorIfAbsent(selected ? NamedTextColor.GREEN : NamedTextColor.GOLD));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(selected);
            meta.setUnbreakable(true);
        });
        return I18n.item(item, player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot == 49) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (player.isOnline()
                                        && player.getOpenInventory().getTopInventory()
                                                == menu.inventory) player.closeInventory();
                            });
            return;
        }
        Choice previous = choice(player.getUniqueId());
        Choice next = null;
        if (slot == 11 || slot == 13 || slot == 15) {
            FinalsClass type = FinalsClass.values()[(slot - 11) / 2];
            next =
                    new Choice(
                            type,
                            type == previous.playerClass()
                                    ? previous.weapon()
                                    : FinalsWeapon.byClass(type).getFirst());
        } else if (slot >= 28 && slot <= 32) {
            next =
                    new Choice(
                            previous.playerClass(),
                            FinalsWeapon.byClass(previous.playerClass()).get(slot - 28));
        }
        if (next != null) {
            try {
                choose(player, next);
                paint(player, event.getInventory());
            } catch (CommandFailure error) {
                player.sendMessage(error.text());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isMenu(event.getInventory())) event.setCancelled(true);
    }
}
