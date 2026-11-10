package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spell-first SkyWars-style survival on an imported map; standalone or a Party Games round. */
final class MagicBattleSession implements PartyRoundSession {
    private enum Phase {
        BUILDING,
        COUNTDOWN,
        FIGHT,
        RESULT,
        DONE
    }

    private record Hit(UUID caster, long tick) {}

    private final SessionServices services;
    private final PartyRoundContext context;
    private final PartyConfig config;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Map<UUID, MagicMana> mana = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Long> recoveries = new HashMap<>();
    private final Map<UUID, String> languages = new HashMap<>();
    private final Map<MagicBattleArena.Pos, Long> chests = new HashMap<>();
    private final CountdownFreeze freeze = new CountdownFreeze();
    private SurvivalStandings standings;
    private MagicBattleArena arena;
    private MagicCombat magic;
    private BossBar bar;
    private BukkitTask ticker;
    private Phase phase = Phase.BUILDING;
    private long tick, deadline, fightAt;
    private int duration, countdown, chestRecharge;
    private boolean overtime, internalTeleport, spellDamage;
    private UUID champion;
    private List<Component> result = List.of();

    MagicBattleSession(SessionServices services, PartyConfig config) {
        this(services, config, PartyRoundContext.standalone(services));
    }

    MagicBattleSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        this.services = context.services();
        this.config = config;
        this.context = context;
    }

    private Component name(UUID id) {
        return names.containsKey(id)
                ? Component.text(names.get(id))
                : I18n.tr("party.magic.player");
    }

    private Player player(UUID id) {
        return connected.contains(id) ? Bukkit.getPlayer(id) : null;
    }

    private boolean alive(Player p) {
        return standings != null
                && connected.contains(p.getUniqueId())
                && standings.alive(p.getUniqueId())
                && !p.isDead();
    }

    private boolean fighting(Player p) {
        return phase == Phase.FIGHT
                && alive(p)
                && p.getWorld().equals(arena.world())
                && p.getGameMode() == GameMode.SURVIVAL;
    }

    private List<Player> survivors() {
        return standings.survivors().stream()
                .map(this::player)
                .filter(p -> p != null && alive(p))
                .toList();
    }

    private void broadcast(Component message) {
        services.messages().broadcast(message.colorIfAbsent(NamedTextColor.GOLD));
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException error) {
            cleanup();
            throw error;
        }
    }

    private void start() throws CommandFailure {
        for (var p : services.participants().all()) {
            names.put(p.id(), p.name());
            connected.add(p.id());
            mana.put(p.id(), new MagicMana());
        }
        if (names.isEmpty() || names.size() > 12)
            throw new CommandFailure(I18n.tr("party.magic.players"));
        standings = new SurvivalStandings(names.keySet());
        duration =
                (context.embedded()
                                ? Math.min(120, config.magicSeconds.get())
                                : config.magicSeconds.get())
                        * 20;
        countdown =
                (context.embedded()
                                ? context.options().countdownSeconds()
                                : config.magicCountdown.get())
                        * 20;
        chestRecharge = config.magicChestSeconds.get() * 20;
        var world = context.createVoid("party_magic");
        services.arena().applyStandardGameRules(world);
        world.setViewDistance(6);
        world.setSimulationDistance(4);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        arena = new MagicBattleArena(world);
        services.arena().applyBorder(world, arena.center(), 190, 190, 0);
        magic =
                new MagicCombat(
                        arena,
                        this::fighting,
                        this::survivors,
                        mana,
                        this::spellHit,
                        this::teleport);
        bar =
                services.bossBars()
                        .create(
                                I18n.tr("party.magic.preparing"),
                                BossBar.Color.PURPLE,
                                BossBar.Overlay.PROGRESS);
        for (Player p : services.participants().onlinePlayers()) {
            if (p.isDead()) p.spigot().respawn();
            p.closeInventory();
            p.setItemOnCursor(null);
            services.playerState().resetForRound(p, GameMode.SPECTATOR);
            teleport(p, arena.spectator());
            freeze.freeze(p);
            p.showBossBar(bar);
        }
        broadcast(I18n.tr("party.magic.rules"));
        broadcast(I18n.tr("party.magic.controls"));
        broadcast(I18n.tr("party.magic.map_credit"));
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                services.plugin(),
                                () -> {
                                    try {
                                        tickGame();
                                    } catch (RuntimeException failure) {
                                        services.plugin()
                                                .getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Magic battle round failed",
                                                        failure);
                                        context.finish(EndReason.ABORTED, PartyRoundResult.empty());
                                    }
                                },
                                1,
                                1);
        hud();
    }

    private boolean teleport(Player p, Location at) {
        internalTeleport = true;
        try {
            return p.teleport(at);
        } finally {
            internalTeleport = false;
        }
    }

    private void equip(Player p, int spawn) {
        freeze.release(p.getUniqueId());
        if (p.isDead()) p.spigot().respawn();
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        p.setAbsorptionAmount(0);
        p.setHealth(20);
        refreshItems(p);
        p.getInventory().setItem(6, new ItemStack(Material.WHITE_WOOL, 64));
        p.getInventory().setHeldItemSlot(0);
        teleport(p, arena.spawn(spawn));
        freeze.freeze(p);
    }

    private void refreshItems(Player p) {
        for (var school : MagicSpell.School.values()) refreshWand(p, school);
        p.getInventory().setItem(7, MagicItems.guide(p));
        languages.put(p.getUniqueId(), I18n.language(p.locale()));
    }

    private void refreshWand(Player p, MagicSpell.School school) {
        p.getInventory()
                .setItem(school.ordinal(), MagicItems.wand(school, mana.get(p.getUniqueId()), p));
    }

    private void tickGame() {
        if (phase == Phase.DONE || context.ended() || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (phase == Phase.BUILDING) {
            if (arena.buildSlice(2048)) {
                List<UUID> order = new ArrayList<>(names.keySet());
                Collections.shuffle(order);
                List<Integer> spawns = new ArrayList<>();
                for (int i = 0; i < 12; i++) spawns.add(i);
                Collections.shuffle(spawns);
                for (int i = 0; i < order.size(); i++) {
                    Player p = player(order.get(i));
                    if (p != null && alive(p)) equip(p, spawns.get(i));
                }
                phase = Phase.COUNTDOWN;
                deadline = tick + countdown;
            }
        } else if (phase == Phase.COUNTDOWN && tick >= deadline) {
            freeze.releaseAll();
            phase = Phase.FIGHT;
            fightAt = tick;
            broadcast(I18n.tr("party.magic.fight"));
        } else if (phase == Phase.FIGHT) {
            for (Player p : survivors()) {
                if (!p.getWorld().equals(arena.world())) {
                    out(p);
                    continue;
                }
                if (p.getLocation().getY() < 25) {
                    p.damage(1000, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
                    if (standings.alive(p.getUniqueId())) out(p);
                    continue;
                }
                mana.get(p.getUniqueId()).tick();
                if (tick % 20 == 0
                        && Math.hypot(p.getLocation().getX() - .5, p.getLocation().getZ() - .5) < 6
                        && Math.abs(p.getLocation().getY() - 64) < 12)
                    mana.get(p.getUniqueId()).restore(5);
            }
            magic.tick(tick);
            if (!overtime && tick - fightAt >= duration) {
                overtime = true;
                services.arena().applyBorder(arena.world(), arena.center(), 190, 12, 30);
                broadcast(I18n.tr("party.magic.overtime"));
            }
            if (overtime && tick - fightAt >= duration + 600 && tick % 20 == 0)
                for (Player p : survivors())
                    p.damage(2 + (tick - fightAt - duration - 600) / 100.0);
            if (standings.decided(services.isTest(), tick)) finishBattle();
            if (tick % 10 == 0) for (Player p : survivors()) preview(p);
            if (tick % 20 == 0)
                for (var pos : arena.chests())
                    if (chests.getOrDefault(pos, 0L) <= tick)
                        arena.world()
                                .spawnParticle(
                                        Particle.ENCHANT,
                                        new Location(
                                                arena.world(),
                                                pos.x() + .5,
                                                pos.y() + 1,
                                                pos.z() + .5),
                                        1,
                                        .2,
                                        .1,
                                        .2,
                                        0);
        } else if (phase == Phase.RESULT && tick >= deadline) {
            context.finish(EndReason.FINISHED, roundResult());
            return;
        }
        for (UUID id : List.copyOf(recoveries.keySet()))
            if (tick >= recoveries.get(id)) {
                Player p = player(id);
                if (p != null) {
                    if (p.isDead()) p.spigot().respawn();
                    if (p.getGameMode() != GameMode.SPECTATOR) spectate(p);
                }
                recoveries.remove(id);
            }
        if (tick % 5 == 0)
            for (Player p : survivors()) {
                if (!I18n.language(p.locale()).equals(languages.get(p.getUniqueId())))
                    refreshItems(p);
                actionBar(p);
            }
        if (tick % 20 == 0 || phase == Phase.BUILDING) hud();
    }

    private void preview(Player p) {
        MagicSpell.School school = MagicItems.school(p.getInventory().getItemInMainHand());
        if (school == null) return;
        MagicSpell spell = mana.get(p.getUniqueId()).selected(school);
        if (!spell.aimed()) return;
        var aim = MagicCombat.aim(p, spell.range());
        if (arena.inside(aim.point()))
            MagicCombat.ring(
                    aim.point(),
                    spell.radius(),
                    mana.get(p.getUniqueId()).ready(spell, tick) ? school.color : 0xEF4242,
                    p);
    }

    private void actionBar(Player p) {
        MagicMana state = mana.get(p.getUniqueId());
        if (state == null) return;
        if (magic != null && magic.channelRemaining(p, tick) > 0) {
            p.sendActionBar(
                    I18n.tr("party.magic.channeling", (magic.channelRemaining(p, tick) + 19) / 20));
            return;
        }
        if (magic != null && magic.silenceRemaining(p, tick) > 0) {
            p.sendActionBar(
                    I18n.tr("party.magic.silenced", (magic.silenceRemaining(p, tick) + 19) / 20));
            return;
        }
        MagicSpell.School school = MagicItems.school(p.getInventory().getItemInMainHand());
        Component spell =
                school == null
                        ? I18n.tr("party.magic.select_wand")
                        : state.selected(school).title();
        long cooldown =
                school == null ? 0 : (state.remaining(state.selected(school), tick) + 19) / 20;
        p.sendActionBar(
                I18n.tr("party.magic.action", (int) state.mana(), spell, cooldown)
                        .color(NamedTextColor.AQUA));
    }

    private void hud() {
        if (bar == null || phase == Phase.DONE) return;
        Component status =
                switch (phase) {
                    case BUILDING -> I18n.tr("party.magic.preparing");
                    case COUNTDOWN ->
                            I18n.tr(
                                    "party.magic.countdown",
                                    Math.max(0, (deadline - tick + 19) / 20));
                    case RESULT -> I18n.tr("party.magic.results");
                    default ->
                            overtime
                                    ? I18n.tr("party.magic.overtime_short")
                                    : I18n.tr(
                                            "party.magic.timer",
                                            TimeFormat.clock(
                                                    Math.max(
                                                            0,
                                                            (duration - (tick - fightAt)) / 20)));
                };
        bar.name(status);
        bar.progress(
                phase == Phase.BUILDING
                        ? arena.progress()
                        : (float) standings.survivors().size() / names.size());
        for (Player p : services.session().audiencePlayers()) {
            p.showBossBar(bar);
            List<Component> lines = new ArrayList<>();
            lines.add(status.color(NamedTextColor.YELLOW));
            lines.add(
                    I18n.tr("party.magic.remaining", standings.survivors().size(), names.size())
                            .color(NamedTextColor.WHITE));
            MagicMana state = mana.get(p.getUniqueId());
            if (state != null)
                lines.add(
                        I18n.tr("party.magic.mana", (int) state.mana(), 100)
                                .color(NamedTextColor.AQUA));
            lines.add(I18n.tr("party.magic.fountain").color(NamedTextColor.LIGHT_PURPLE));
            services.sidebar().update(p, I18n.tr("party.magic.title"), lines);
        }
    }

    private void cycle(Player p) {
        MagicSpell.School school = MagicItems.school(p.getInventory().getItemInMainHand());
        if (school != null
                && mana.containsKey(p.getUniqueId())
                && mana.get(p.getUniqueId()).cycle(school, p.isSneaking(), tick)) {
            refreshWand(p, school);
            actionBar(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .4f, 1.4f);
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !alive(p)) return;
        if (MagicItems.guide(event.getItem()) && event.getAction().isRightClick()) {
            MagicItems.openBook(p, mana.get(p.getUniqueId()));
            return;
        }
        MagicSpell.School school = MagicItems.school(event.getItem());
        if (school != null) {
            if (event.getAction().isLeftClick()) {
                if (phase == Phase.FIGHT || phase == Phase.COUNTDOWN) cycle(p);
                return;
            }
            if (!event.getAction().isRightClick() || !fighting(p)) return;
            if (magic.silenceRemaining(p, tick) > 0 || magic.channelRemaining(p, tick) > 0) {
                actionBar(p);
                return;
            }
            MagicMana state = mana.get(p.getUniqueId());
            MagicSpell spell = state.selected(school);
            if (!state.ready(spell, tick)) {
                p.sendActionBar(
                        I18n.tr(
                                "party.magic.not_ready",
                                spell.mana,
                                (state.remaining(spell, tick) + 19) / 20));
                return;
            }
            if (magic.cast(p, spell, tick)) {
                state.spend(spell, tick);
                actionBar(p);
            } else p.sendActionBar(I18n.tr("party.magic.invalid_target"));
        } else if (fighting(p)
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.CHEST
                && event.getAction().isRightClick()) {
            var pos = MagicBattleArena.Pos.of(event.getClickedBlock());
            if (!arena.chests().contains(pos)) return;
            long wait = chests.getOrDefault(pos, 0L) - tick;
            if (wait > 0) {
                p.sendActionBar(I18n.tr("party.magic.chest_wait", (wait + 19) / 20));
                return;
            }
            MagicMana state = mana.get(p.getUniqueId());
            if (state.mana() >= MagicMana.MAX) {
                p.sendActionBar(I18n.tr("party.magic.mana_full"));
                return;
            }
            state.restore(35);
            chests.put(pos, tick + chestRecharge);
            ItemStack wool = p.getInventory().getItem(6);
            int count = wool == null ? 0 : wool.getAmount();
            p.getInventory()
                    .setItem(6, new ItemStack(Material.WHITE_WOOL, Math.min(64, count + 16)));
            p.sendActionBar(I18n.tr("party.magic.chest_used"));
        } else if (fighting(p)
                && event.getItem() != null
                && event.getItem().getType() == Material.WHITE_WOOL) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    private boolean spellHit(Player caster, Player target, double amount) {
        if (!fighting(caster) || !fighting(target)) return false;
        double before = target.getHealth() + target.getAbsorptionAmount();
        spellDamage = true;
        try {
            target.damage(
                    amount,
                    DamageSource.builder(DamageType.INDIRECT_MAGIC)
                            .withCausingEntity(caster)
                            .withDirectEntity(caster)
                            .build());
        } finally {
            spellDamage = false;
        }
        return target.getHealth() + target.getAbsorptionAmount() < before;
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !fighting(p)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent hit) {
            if (!spellDamage || !(hit.getDamager() instanceof Player caster) || !fighting(caster)) {
                event.setCancelled(true);
                return;
            }
            if (caster != p && event.getDamage() > 0)
                lastHit.put(p.getUniqueId(), new Hit(caster.getUniqueId(), tick));
        }
        if (magic.shielded(p, tick)) event.setDamage(event.getDamage() * .5);
        if (event.getDamage() > 0) {
            magic.interrupt(p);
            if (event.getFinalDamage() > 0
                    && event.getCause() != EntityDamageEvent.DamageCause.VOID
                    && magic.preventDeath(p, event.getFinalDamage(), tick))
                event.setCancelled(true);
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p)
                || !fighting(p)
                || !spellDamage
                || !(event.getDamager() instanceof Player caster)
                || !fighting(caster)) event.setCancelled(true);
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        event.setCancelled(true);
        if (alive(event.getPlayer())) cycle(event.getPlayer());
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!names.containsKey(p.getUniqueId()) || !standings.alive(p.getUniqueId())) return;
        Hit hit = lastHit.get(p.getUniqueId());
        UUID killer = hit != null && tick - hit.tick() <= 200 ? hit.caster() : null;
        event.deathMessage(null);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Component message =
                killer == null
                        ? I18n.tr("party.magic.death")
                        : I18n.tr("party.magic.killed_by", name(killer));
        event.deathScreenMessageOverride(I18n.render(message, p));
        broadcast(
                killer == null
                        ? I18n.tr("party.magic.died", p.getName())
                        : I18n.tr("party.magic.killed", name(killer), p.getName()));
        out(p);
    }

    private void out(Player p) {
        if (!standings.eliminate(p.getUniqueId(), tick)) return;
        magic.remove(p.getUniqueId());
        freeze.release(p.getUniqueId());
        if (p.isDead()) recoveries.put(p.getUniqueId(), tick + DeathPause.TICKS);
        else spectate(p);
    }

    private void spectate(Player p) {
        if (p.isDead()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.closeInventory();
        p.setItemOnCursor(null);
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        p.setAbsorptionAmount(0);
        teleport(p, arena.spectator());
        if (bar != null) p.showBossBar(bar);
    }

    private void finishBattle() {
        phase = Phase.RESULT;
        deadline = tick + DeathPause.TICKS;
        magic.clear();
        freeze.releaseAll();
        champion = standings.survivors().size() == 1 ? standings.survivors().getFirst() : null;
        result = new ArrayList<>();
        result.add(
                champion == null
                        ? I18n.tr("party.magic.draw")
                        : I18n.tr("party.magic.winner", name(champion)));
        int place = 0;
        for (UUID id : standings.ranking(names.keySet(), tick))
            result.add(I18n.tr("party.magic.standing", ++place, name(id)));
        broadcast(result.getFirst());
        for (Player p : survivors()) {
            p.setFireTicks(0);
            freeze.freeze(p);
        }
        hud();
    }

    private void leave(Player p) {
        context.withdraw(p.getUniqueId());
        connected.remove(p.getUniqueId());
        if (standings != null) standings.eliminate(p.getUniqueId(), tick);
        if (magic != null) magic.remove(p.getUniqueId());
        freeze.release(p.getUniqueId());
        recoveries.remove(p.getUniqueId());
        p.closeInventory();
        p.setItemOnCursor(null);
    }

    @Override
    public void onParticipantQuit(Player p) {
        leave(p);
    }

    @Override
    public void onParticipantLeave(Player p) {
        leave(p);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        connected.add(p.getUniqueId());
        spectate(p);
    }

    @Override
    public void onParticipantJoin(Player p) {
        spectate(p);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (arena != null && !standings.alive(event.getPlayer().getUniqueId()))
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (!standings.alive(event.getPlayer().getUniqueId())) {
            recoveries.remove(event.getPlayer().getUniqueId());
            spectate(event.getPlayer());
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!internalTeleport) freeze.hold(event);
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        freeze.onJump(event);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport
                && standings != null
                && standings.alive(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (fighting(event.getPlayer())
                && MagicItems.school(event.getPlayer().getInventory().getItemInMainHand()) == null)
            arena.remove(event.getBlock());
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!fighting(event.getPlayer())
                || event.getBlock().getType() != Material.WHITE_WOOL
                || !arena.canBuild(event.getBlock())
                || !event.getBlockReplacedState().getType().isAir()) event.setCancelled(true);
        else arena.placed(event.getBlock());
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (alive(event.getPlayer())) actionBar(event.getPlayer());
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)
                || !alive(p)
                || (phase != Phase.FIGHT && phase != Phase.COUNTDOWN)) return;
        MagicSpell spell =
                MagicItems.bookChoice(event.getView().getTopInventory(), event.getRawSlot(), p);
        if (spell == null) return;
        var book = event.getView().getTopInventory();
        Bukkit.getScheduler()
                .runTask(
                        services.plugin(),
                        () -> {
                            if (!alive(p)
                                    || (phase != Phase.FIGHT && phase != Phase.COUNTDOWN)
                                    || p.getOpenInventory().getTopInventory() != book) return;
                            mana.get(p.getUniqueId()).select(spell);
                            refreshWand(p, spell.school);
                            p.getInventory().setHeldItemSlot(spell.school.ordinal());
                            p.closeInventory();
                            actionBar(p);
                        });
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onSecondTick(long seconds) {
        hud();
    }

    @Override
    public boolean isInGame(Player p) {
        return alive(p);
    }

    @Override
    public boolean mayFollowPlayers(Player p) {
        return phase != Phase.BUILDING && phase != Phase.COUNTDOWN && !alive(p);
    }

    @Override
    public Location spectatorLocation(Player viewer) {
        return arena == null ? null : arena.spectator();
    }

    private void cleanup() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (magic != null) magic.clear();
        freeze.releaseAll();
        recoveries.clear();
        chests.clear();
        mana.clear();
        phase = Phase.DONE;
    }

    @Override
    public void onEnd(EndReason reason) {
        context.hideBars();
        cleanup();
        if (!context.embedded() && reason == EndReason.FINISHED && !services.isTest())
            roundResult().awards().forEach((id, points) -> services.award("party", id, points));
    }

    @Override
    public PartyRoundResult roundResult() {
        return standings == null
                ? PartyRoundResult.empty()
                : standings.result(context.eligible(), tick);
    }

    @Override
    public boolean ready() {
        return phase != Phase.BUILDING;
    }

    @Override
    public List<Component> summary() {
        return List.copyOf(result);
    }

    @Override
    public Component globalResult() {
        return champion == null ? null : I18n.tr("party.magic.global_winner", name(champion));
    }
}
