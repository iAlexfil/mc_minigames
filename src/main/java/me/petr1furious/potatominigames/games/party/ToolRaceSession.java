package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

final class ToolRaceSession extends PartySurvivalSession {
    private final int duration;
    private ToolRaceArena arena;
    private ToolRaceTrack track;

    ToolRaceSession(SessionServices s, PartyConfig c, PartyRoundContext context) {
        super(s, c, "tool_race", context);
        duration = c.toolRaceSeconds.get();
    }

    void buildArena() {
        track = new ToolRaceTrack(names.keySet(), ToolRaceTrack.generate(random));
        arena = new ToolRaceArena(world, names.keySet());
        arena.build(track);
    }

    Location spawn(int i, int n) {
        return arena.spawn(new ArrayList<>(names.keySet()).get(i));
    }

    Location viewpoint() {
        return new Location(world, .5, 88, .5, 0, 80);
    }

    double outHeight() {
        return 25;
    }

    int durationSeconds() {
        return duration;
    }

    boolean podiumAwards() {
        return true;
    }

    boolean survivalAutoFinish() {
        return false;
    }

    Component instructions() {
        return I18n.tr("party.rounds.tool_race.instructions");
    }

    void beginPlay() {
        for (var p : alivePlayers()) {
            p.getInventory().setItem(0, PvpClass.item(Material.IRON_AXE));
            p.getInventory().setItem(1, PvpClass.item(Material.IRON_SHOVEL));
            p.getInventory().setItem(2, PvpClass.item(Material.IRON_PICKAXE));
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!playing()
                || !alive(e.getPlayer())
                || track.finished(id)
                || e.getAction() != Action.LEFT_CLICK_BLOCK
                || e.getClickedBlock() == null
                || arena.depth(id, e.getClickedBlock()) != track.progress(id)) e.setCancelled(true);
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent e) {
        var p = e.getPlayer();
        UUID id = p.getUniqueId();
        int depth = arena.depth(id, e.getBlock());
        if (!playing() || !alive(p) || !track.mine(id, depth, elapsed())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (track.finished(id)) {
            p.sendMessage(text(I18n.tr("party.rounds.tool_race.finish",
                    String.format(java.util.Locale.ROOT, "%.2f", elapsed() / 20.0))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.4f);
            spectate(p);
        }
    }

    void playTick(long elapsed) {
        if (context.eligible().stream().allMatch(track::finished)) finish(List.of());
    }

    @Override
    public PartyRoundResult roundResult() {
        return track == null ? PartyRoundResult.empty() : track.result(context.eligible());
    }

    Component status() {
        return I18n.tr("party.rounds.tool_race.status",
                names.keySet().stream().filter(track::finished).count(),
                Math.max(0, duration - elapsed() / 20));
    }

    @Override
    public boolean isInGame(org.bukkit.entity.Player p) {
        return alive(p) && !track.finished(p.getUniqueId());
    }

    void cleanup() {
        if (track != null) track.close();
    }
}
