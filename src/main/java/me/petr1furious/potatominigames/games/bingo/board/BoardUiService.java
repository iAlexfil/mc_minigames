package me.petr1furious.potatominigames.games.bingo.board;

import me.petr1furious.potatominigames.i18n.I18n;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.games.bingo.model.BoardEntry;
import me.petr1furious.potatominigames.games.bingo.model.GameDifficulty;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;
import me.petr1furious.potatominigames.games.bingo.model.TeamState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class BoardUiService {
    public enum ClickActionType {
        NONE,
        TOGGLE_MODE,
        CLAIM
    }

    public record ClickAction(ClickActionType type, int boardIndex) {
        static ClickAction none() {
            return new ClickAction(ClickActionType.NONE, -1);
        }

        static ClickAction toggle() {
            return new ClickAction(ClickActionType.TOGGLE_MODE, -1);
        }

        static ClickAction claim(int boardIndex) {
            return new ClickAction(ClickActionType.CLAIM, boardIndex);
        }
    }

    private static final int[] BOARD_SLOTS = {
            2, 3, 4, 5, 6,
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };
    private static final int TOGGLE_SLOT = 8;
    private static final int STATUS_SLOT = 17;

    private final NamespacedKey bingoCardKey;
    private final Map<UUID, Integer> displayModes = new HashMap<>();

    public BoardUiService(JavaPlugin plugin) {
        this.bingoCardKey = new NamespacedKey(plugin, "bingo_card");
    }

    public ItemStack createCard() {
        ItemStack card = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(I18n.tr("bingo.card.name").color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(bingoCardKey, PersistentDataType.BYTE, (byte) 1);
        card.setItemMeta(meta);
        return card;
    }

    public boolean isBingoCard(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(bingoCardKey, PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    /** @param owners lockout: the team that claimed each cell, else null */
    public void openBoard(Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex, TeamColor[] owners) {
        Inventory inventory = Bukkit.createInventory(new BoardViewHolder(), 45,
                I18n.render(I18n.tr("bingo.board.title").color(NamedTextColor.GOLD), player));
        fillBoard(inventory, player, board, team, mode, difficulty, rewardForIndex, owners);
        player.openInventory(inventory);
    }

    public void refreshOpenBoard(Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex, TeamColor[] owners) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!isBoardView(inventory.getHolder())) {
            return;
        }
        fillBoard(inventory, player, board, team, mode, difficulty, rewardForIndex, owners);
        player.updateInventory();
    }

    public boolean isBoardView(InventoryHolder holder) {
        return holder instanceof BoardViewHolder;
    }

    public ClickAction mapClick(Player player, int rawSlot) {
        if (rawSlot == TOGGLE_SLOT) {
            int next = (displayModes.getOrDefault(player.getUniqueId(), 0) + 1) % 4;
            displayModes.put(player.getUniqueId(), next);
            return ClickAction.toggle();
        }
        for (int i = 0; i < BOARD_SLOTS.length; i++) {
            if (BOARD_SLOTS[i] == rawSlot) {
                return ClickAction.claim(i);
            }
        }
        return ClickAction.none();
    }

    private void fillBoard(Inventory inventory, Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex, TeamColor[] owners) {
        int displayMode = displayModes.getOrDefault(player.getUniqueId(), 0);
        for (int i = 0; i < 25; i++) {
            BoardEntry entry = board.entries()[i];
            int slot = BOARD_SLOTS[i];
            if (entry == null) {
                inventory.setItem(slot, null);
                continue;
            }
            TeamColor owner = owners == null ? null : owners[i];
            if (owner != null && owner != team.color()) {
                inventory.setItem(slot, I18n.item(claimedCell(entry, owner), player));
                continue;
            }
            boolean collected = team.collected()[i];
            inventory.setItem(slot, I18n.item(boardCell(entry, collected, board.effectiveDifficulties()[i], displayMode,
                    mode, rewardForIndex.applyAsInt(i)), player));
        }
        inventory.setItem(TOGGLE_SLOT, I18n.item(toggleItem(displayMode), player));
        inventory.setItem(STATUS_SLOT, I18n.item(statusItem(mode, difficulty), player));
    }

    private static ItemStack toggleItem(int displayMode) {
        ItemStack stack = new ItemStack(Material.DEBUG_STICK, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(I18n.tr("bingo.board.change_display").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(I18n.tr("bingo.board.current_display", displayModeName(displayMode)).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack statusItem(GameMode mode, GameDifficulty difficulty) {
        Material icon = switch (mode) {
            case DEFAULT -> Material.NAME_TAG;
            case BUTFAST -> Material.DIAMOND;
            case COLLECTALL -> Material.NETHER_STAR;
            case LOCKOUT -> Material.SHIELD;
        };
        ItemStack stack = new ItemStack(icon, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(I18n.tr("bingo.board.mode", mode.displayName()).color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(I18n.tr("bingo.board.difficulty", difficulty.displayName()).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Lockout: another team's cell, in its colour; no longer collectable. */
    private static ItemStack claimedCell(BoardEntry entry, TeamColor owner) {
        ItemStack stack = new ItemStack(owner.woolMaterial(), 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(I18n.tr("bingo.board.claimed", owner.displayNameComponent()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(entryName(entry).color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack boardCell(BoardEntry entry, boolean collected, int effectiveDifficulty, int displayMode,
            GameMode mode, int rewardPoints) {
        if (collected && displayMode != 3) {
            ItemStack stack = new ItemStack(Material.BARRIER, 1);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(I18n.tr("bingo.board.completed").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
            return stack;
        }

        Material icon = entry.displayMaterial() == null ? Material.PAPER : entry.displayMaterial();
        ItemStack stack = new ItemStack(icon, 1);
        ItemMeta meta = stack.getItemMeta();
        if (displayMode == 3) {
            meta.displayName(
                    entryName(entry).color(collected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
            if (collected) {
                meta.lore(List.of(I18n.tr("bingo.board.completed").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)));
            }
            stack.setItemMeta(meta);
            return stack;
        }
        if (displayMode == 0) {
            stack.setItemMeta(meta);
            return stack;
        }
        if (displayMode == 1) {
            meta.displayName(
                    I18n.tr("bingo.board.item_difficulty", entryName(entry), effectiveDifficulty).color(NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
        } else if (displayMode == 2) {
            int shown = mode == GameMode.BUTFAST ? rewardPoints : effectiveDifficulty;
            meta.displayName(I18n.tr("bingo.board.item_reward", entryName(entry), shown).color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component displayModeName(int mode) {
        return I18n.tr(switch (mode) {
            case 1 -> "bingo.board.display.difficulty";
            case 2 -> "bingo.board.display.reward";
            case 3 -> "bingo.board.display.all";
            default -> "bingo.board.display.name";
        });
    }

    private static Component entryName(BoardEntry entry) {
        switch (entry.id()) {
            case "planks_any", "wooden_buttons_any", "wooden_doors_any", "wooden_pressure_plates_any",
                    "wooden_slabs_any", "wooden_stairs_any", "wooden_trapdoors_any", "fences_any",
                    "fence_gates_any", "boats_any", "chest_boats_any", "signs_any", "hanging_signs_any",
                    "wooden_shelves_any", "beds_any", "banners_any", "wool_carpets_any", "harnesses_any",
                    "logs_burnable", "wool", "crimson_stems", "warped_stems" -> {
                return I18n.tr("bingo.entry." + entry.id());
            }
            default -> {
                // Native material IDs use Minecraft's labels; custom IDs remain literal.
            }
        }
        Material material = entry.displayMaterial();
        return material != null && material.name().equalsIgnoreCase(entry.id())
                ? Component.translatable(material.translationKey())
                : Component.text(entry.id().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    private static final class BoardViewHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
