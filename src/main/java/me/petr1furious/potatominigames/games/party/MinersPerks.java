package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Per-player charges, clocks and private ore outlines; no independent scheduler survives the round. */
final class MinersPerks {
    private static final class State {
        final Player player;
        int lucky, sift;
        long visionUntil, auditUntil;
        final Map<MinersArena.Pos, BlockDisplay> outlines = new HashMap<>();
        final Map<MinersOre, Integer> audit = new EnumMap<>(MinersOre.class);
        State(Player player) { this.player = player; }
        boolean empty() { return lucky == 0 && sift == 0 && visionUntil == 0 && auditUntil == 0; }
    }
    private final Map<UUID, State> states = new HashMap<>();

    boolean activate(Player player, MinersPerk perk, long tick, MinersArena arena, Plugin plugin) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State(player));
        switch (perk) {
            case LUCK -> { if (state.lucky > 0) return false; state.lucky = 3; }
            case SIFTER -> { if (state.sift > 0) return false; state.sift = 20; }
            case VISION -> {
                if (state.visionUntil > tick) return false;
                state.visionUntil = tick + 60;
                refreshVision(state, arena, plugin);
            }
            case AUDIT -> {
                if (state.auditUntil > tick) return false;
                state.auditUntil = tick + 400;
                refreshAudit(state, arena);
            }
            default -> { return false; }
        }
        return true;
    }
    boolean lucky(UUID id) {
        State state = states.get(id);
        if (state == null || state.lucky == 0) return false;
        state.lucky--;
        return true;
    }
    boolean sift(UUID id, Random random) {
        State state = states.get(id);
        if (state == null || state.sift == 0) return false;
        state.sift--;
        return random.nextInt(100) < 15;
    }
    void tick(long tick, MinersArena arena, Plugin plugin, Predicate<Player> playing) {
        for (UUID id : List.copyOf(states.keySet())) {
            State state = states.get(id);
            if (!state.player.isOnline() || !playing.test(state.player)) { remove(id); continue; }
            if (state.visionUntil > 0 && tick >= state.visionUntil) {
                clearOutlines(state); state.visionUntil = 0;
            } else if (state.visionUntil > tick && tick % 5 == 0) refreshVision(state, arena, plugin);
            if (state.auditUntil > 0 && tick >= state.auditUntil) {
                state.audit.clear(); state.auditUntil = 0;
            } else if (state.auditUntil > tick && tick % 20 == 0) refreshAudit(state, arena);
            if (state.empty()) states.remove(id);
        }
    }
    private static void refreshAudit(State state, MinersArena arena) {
        state.audit.clear();
        for (MinersOre ore : arena.oresNear(state.player.getLocation(), 5).values())
            state.audit.merge(ore, 1, Integer::sum);
    }
    private static void refreshVision(State state, MinersArena arena, Plugin plugin) {
        Map<MinersArena.Pos, MinersOre> nearby = arena.oresNear(state.player.getLocation(), 8);
        for (MinersArena.Pos p : List.copyOf(state.outlines.keySet()))
            if (!nearby.containsKey(p) || !state.outlines.get(p).isValid()) state.outlines.remove(p).remove();
        for (var entry : nearby.entrySet()) {
            MinersArena.Pos p = entry.getKey();
            if (state.outlines.containsKey(p)) continue;
            Location at = new Location(state.player.getWorld(), p.x(), p.y(), p.z());
            BlockDisplay outline = at.getWorld().spawn(at, BlockDisplay.class, display -> {
                // Set before world insertion so neither existing viewers nor later joiners see it.
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBlock(entry.getValue().material.createBlockData());
                display.setBrightness(new Display.Brightness(15, 15));
                display.setGlowing(true);
                display.setGlowColorOverride(color(entry.getValue()));
                display.setViewRange(1);
            });
            state.outlines.put(p, outline);
            state.player.showEntity(plugin, outline);
        }
    }
    private static Color color(MinersOre ore) {
        return Color.fromRGB(switch (ore) {
            case COAL -> 0xAAAAAA;
            case IRON -> 0xE2C6AD;
            case DIAMOND -> 0x50E5ED;
            case GOLD -> 0xFFD44A;
            case LAPIS -> 0x4169E1;
            case REDSTONE -> 0xEF4242;
            case EMERALD -> 0x49DC78;
            case PERKS -> 0xD26CFF;
        });
    }
    List<Component> hud(Player player, long tick) {
        State state = states.get(player.getUniqueId());
        if (state == null) return List.of();
        List<Component> lines = new ArrayList<>();
        if (state.lucky > 0) lines.add(I18n.tr("party.rounds.miners.perk.luck.remaining", state.lucky).color(NamedTextColor.GREEN));
        if (state.sift > 0) lines.add(I18n.tr("party.rounds.miners.perk.sifter.remaining", state.sift).color(NamedTextColor.GRAY));
        if (state.visionUntil > tick) lines.add(I18n.tr("party.rounds.miners.perk.vision.remaining", (state.visionUntil - tick + 19) / 20).color(NamedTextColor.AQUA));
        if (state.auditUntil > tick) {
            lines.add(I18n.tr("party.rounds.miners.perk.audit.remaining", (state.auditUntil - tick + 19) / 20).color(NamedTextColor.LIGHT_PURPLE));
            for (MinersOre ore : MinersOre.values())
                lines.add(I18n.tr("party.rounds.miners.perk.audit.line", Component.translatable(ore.material.translationKey()),
                        state.audit.getOrDefault(ore, 0)).color(NamedTextColor.WHITE));
        }
        return List.copyOf(lines);
    }
    private static void clearOutlines(State state) {
        for (BlockDisplay outline : state.outlines.values()) outline.remove();
        state.outlines.clear();
    }
    void remove(UUID id) {
        State state = states.remove(id);
        if (state != null) clearOutlines(state);
    }
    void clear() { for (UUID id : List.copyOf(states.keySet())) remove(id); }
}
