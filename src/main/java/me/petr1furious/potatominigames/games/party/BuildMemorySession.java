package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

final class BuildMemorySession extends PartySurvivalSession {
    private enum Phase {
        SHOW,
        BUILD,
        REVIEW
    }

    private final int stages;
    private BuildMemoryArena arena;
    private Phase phase = Phase.SHOW;
    private int stage = 1;
    private long deadline;
    private Component modelTitle = Component.empty();
    private final Map<UUID, BuildMemoryPattern> patterns = new LinkedHashMap<>();
    private List<Integer> models;

    BuildMemorySession(SessionServices s, PartyConfig c, PartyRoundContext context) {
        super(s, c, "build_memory", context);
        stages = c.buildMemoryStages.get();
    }

    void buildArena() {
        arena = new BuildMemoryArena(world, names.keySet());
        arena.build();
    }

    Location spawn(int i, int n) {
        return arena.spawn(new ArrayList<>(names.keySet()).get(i));
    }

    Location viewpoint() {
        return new Location(world, .5, 100, .5, 0, 75);
    }

    double outHeight() {
        return 76;
    }

    int durationSeconds() {
        return 500;
    }

    boolean podiumAwards() {
        return true;
    }

    Component instructions() {
        return I18n.tr("party.rounds.build_memory.instructions");
    }

    void beginPlay() {
        models = BuildMemoryPattern.deck(random);
        showPattern();
    }

    private void showPattern() {
        phase = Phase.SHOW;
        arena.screens(false);
        patterns.clear();
        BuildMemoryPattern shared =
                BuildMemoryPattern.generate(stage, models.get(stage - 1), random);
        modelTitle = shared.title();
        for (Player p : alivePlayers()) {
            var pattern = shared.rotate(random.nextInt(4));
            patterns.put(p.getUniqueId(), pattern);
            teleport(p, arena.spawn(p.getUniqueId()));
            clearKit(p);
            arena.show(p.getUniqueId(), pattern);
        }
        deadline = elapsed() + Math.max(5, 13 - stage) * 20L;
        announceTitle(
                I18n.tr("party.rounds.build_memory.memorize", modelTitle),
                I18n.tr("party.rounds.build_memory.orientation", stage),
                2);
        PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, .9f);
    }

    void playTick(long elapsed) {
        if (elapsed < deadline) {
            if (phase != Phase.REVIEW) PartySounds.countdown(alivePlayers(), deadline - elapsed);
            return;
        }
        switch (phase) {
            case SHOW -> {
                phase = Phase.BUILD;
                arena.screens(true);
                for (Player p : alivePlayers()) {
                    arena.clear(p.getUniqueId());
                    clearKit(p);
                    p.getInventory().setItem(6, PvpClass.item(Material.IRON_PICKAXE));
                    p.getInventory().setItem(7, PvpClass.item(Material.IRON_AXE));
                    p.getInventory().setItem(8, PvpClass.item(Material.SHEARS));
                    patterns.get(p.getUniqueId())
                            .inventory()
                            .forEach((m, n) -> p.getInventory().addItem(new ItemStack(m, n)));
                }
                deadline = elapsed + (20 + 2L * (stage - 1)) * 20;
                announceTitle(
                        I18n.tr("party.rounds.build_memory.build", modelTitle),
                        I18n.tr("party.rounds.build_memory.reproduce"),
                        2);
                PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .7f, 1.5f);
            }
            case BUILD -> {
                phase = Phase.REVIEW;
                standings.holdResults(true);
                List<Player> failed = new ArrayList<>();
                for (Player p : alivePlayers())
                    if (!patterns.get(p.getUniqueId()).matches(arena.capture(p.getUniqueId())))
                        failed.add(p);
                for (Player p : failed) eliminate(p);
                for (Player p : alivePlayers()) clearKit(p);
                PartySounds.play(alivePlayers(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .7f, 1.2f);
                patterns.forEach((id, pattern) -> arena.show(id, pattern));
                arena.screens(false);
                deadline = elapsed + 60;
                announceTitle(
                        I18n.tr("party.rounds.build_memory.review"),
                        failed.isEmpty()
                                ? I18n.tr("party.rounds.build_memory.correct")
                                : I18n.tr("party.rounds.build_memory.failed", failed.size()),
                        2);
            }
            case REVIEW -> {
                standings.holdResults(false);
                if (stage >= stages) finish(standings.survivors());
                else {
                    stage++;
                    showPattern();
                }
            }
        }
    }

    private void clearKit(Player p) {
        p.closeInventory();
        p.setItemOnCursor(null);
        p.getInventory().clear();
    }

    private boolean building(Player p) {
        return playing() && phase == Phase.BUILD && alive(p);
    }

    @Override
    protected void handleBlockPlace(BlockPlaceEvent e) {
        if (!building(e.getPlayer())
                || arena.localCell(e.getPlayer().getUniqueId(), e.getBlock()) == null
                || !e.getBlockReplacedState().getType().isAir()
                || !patterns.get(e.getPlayer().getUniqueId())
                        .inventory()
                        .containsKey(MemoryBlock.capture(e.getBlock().getBlockData()).item()))
            e.setCancelled(true);
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent e) {
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!building(p)
                || arena.localCell(p.getUniqueId(), e.getBlock()) == null
                || e.getBlock().getType().isAir()) return;
        var refund = arena.remove(p.getUniqueId(), e.getBlock(), blocks -> canRefund(p, blocks));
        if (refund.isEmpty()) {
            p.sendMessage(text(I18n.tr("party.rounds.build_memory.refund_space")));
            return;
        }
        for (MemoryBlock block : refund)
            p.getInventory().addItem(new ItemStack(block.item(), block.itemCount()));
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    private boolean canRefund(Player player, List<MemoryBlock> blocks) {
        var contents = player.getInventory().getStorageContents();
        int freeSlots = 0;
        for (ItemStack stack : contents) if (stack == null || stack.getType().isAir()) freeSlots++;
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (MemoryBlock block : blocks)
            counts.merge(block.item(), block.itemCount(), Integer::sum);
        for (var entry : counts.entrySet()) {
            ItemStack item = new ItemStack(entry.getKey());
            int remaining = entry.getValue();
            for (ItemStack stack : contents)
                if (stack != null && stack.isSimilar(item))
                    remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            if (remaining > 0)
                freeSlots -= (remaining + item.getMaxStackSize() - 1) / item.getMaxStackSize();
        }
        return freeSlots >= 0;
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!building(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (e.getClickedBlock() == null
                    || arena.localCell(e.getPlayer().getUniqueId(), e.getClickedBlock()) == null)
                e.setCancelled(true);
        } else if (e.getAction().isRightClick()) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        } else e.setCancelled(true);
    }

    @org.bukkit.event.EventHandler
    public void onSignOpen(io.papermc.paper.event.player.PlayerOpenSignEvent e) {
        if (e.getPlayer().getWorld().equals(world)) e.setCancelled(true);
    }

    @org.bukkit.event.EventHandler
    public void onAttachmentPhysics(BlockPhysicsEvent e) {
        if (!e.getBlock().getWorld().equals(world)) return;
        // Corrections remove dependent decorations and refund them atomically in arena.remove.
        // Native physics would drop their items, bypassing the exact kit and pickup protection.
        String name = e.getBlock().getType().name();
        if (name.endsWith("SIGN") || name.endsWith("TORCH") || name.endsWith("LANTERN"))
            e.setCancelled(true);
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)
                || !building(p)
                || e.getSlotType() == InventoryType.SlotType.CRAFTING
                || e.getSlotType() == InventoryType.SlotType.RESULT
                || e.getAction().name().contains("DROP")
                || e.getAction() == InventoryAction.CLONE_STACK) e.setCancelled(true);
    }

    Component status() {
        return I18n.tr(
                "party.rounds.build_memory.status",
                stage,
                I18n.tr(
                        switch (phase) {
                            case SHOW -> "party.rounds.build_memory.show";
                            case BUILD -> "party.rounds.build_memory.building";
                            case REVIEW -> "party.rounds.build_memory.review";
                        }),
                Math.max(0, (deadline - elapsed() + 19) / 20));
    }
}
