package me.petr1furious.potatominigames.games.party;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/** Respawning kill race on Desert Ruins, with authoritative finite ammunition and one-hit wards. */
final class OneShotSession implements PartyRoundSession {
    private enum Phase {
        COUNTDOWN,
        FIGHT,
        RESULT,
        DONE
    }

    private enum BonusKind {
        ARROW,
        SHIELD
    }

    private record Hit(UUID attacker, long tick) {}

    private record Bonus(Item item, BonusKind kind, long expires) {}

    private static final NamespacedKey LIFE_KEY =
            new NamespacedKey("potatominigames", "one_shot_life");
    private static final int PROTECTION_TICKS = 40;
    private static final int BONUS_LIFETIME = 600;
    private static final int MAX_BONUSES = 3;
    private static final int[][] BONUS_SPOTS = {
        {0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}, {7, 7}, {-7, 7}, {7, -7}, {-7, -7}
    };
    private final SessionServices services;
    private final PartyConfig config;
    private final PartyRoundContext context;
    private final Random random = new Random();
    private final CountdownFreeze freeze = new CountdownFreeze();
    private final DeathPause deathPause = new DeathPause();
    private final ArenaProjectiles shots = new ArenaProjectiles();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Set<UUID> connected = new LinkedHashSet<>();
    private final Set<UUID> dirtyAmmo = new HashSet<>();
    private final Map<UUID, Long> respawnAt = new HashMap<>(), protectedUntil = new HashMap<>();
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Bonus> bonuses = new LinkedHashMap<>();
    private OneShotState state;
    private ArmsRaceArena arena;
    private World world;
    private BossBar bar;
    private BukkitTask ticker;
    private Phase phase = Phase.COUNTDOWN;
    private long tick, countdownEnd, matchEnd, resultEnd, nextBonus, aloneSince = -1;
    private int durationTicks, bonusTicks;
    private boolean internalTeleport, completed;
    private PartyRoundResult finished;
    private List<Component> summary = List.of();

    OneShotSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        this.context = context;
        this.services = context.services();
        this.config = config;
    }

    private static Component tr(String key, Object... args) {
        return I18n.tr("party.rounds.one_shot." + key, args);
    }

    private Component name(UUID id) {
        return Component.text(names.get(id));
    }

    private Player player(UUID id) {
        return connected.contains(id) ? Bukkit.getPlayer(id) : null;
    }

    private boolean active(Player p) {
        return state != null
                && connected.contains(p.getUniqueId())
                && state.alive(p.getUniqueId())
                && !p.isDead()
                && p.getGameMode() == GameMode.SURVIVAL
                && p.getWorld().equals(world);
    }

    private boolean fighting(Player p) {
        return phase == Phase.FIGHT && active(p);
    }

    private boolean protectedSpawn(UUID id) {
        return tick < protectedUntil.getOrDefault(id, 0L);
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (RuntimeException | CommandFailure failure) {
            freeze.releaseAll();
            throw failure;
        }
    }

    private void start() throws CommandFailure {
        for (var p : services.participants().all()) {
            names.put(p.id(), p.name());
            connected.add(p.id());
        }
        if (names.isEmpty() || names.size() > 16) throw new CommandFailure(tr("participants"));
        state = new OneShotState(names.keySet());
        countdownEnd =
                Math.clamp(
                                context.embedded()
                                        ? context.options().countdownSeconds()
                                        : config.survivalCountdownSeconds.get(),
                                context.embedded() ? 0 : 3,
                                15)
                        * 20L;
        durationTicks = config.oneShotSeconds.get() * 20;
        bonusTicks = config.oneShotBonusSeconds.get() * 20;
        world = context.createVoid("one_shot");
        services.arena().applyStandardGameRules(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        arena = new ArmsRaceArena(world, PvpArena.Layout.DESERT_RUINS);
        arena.build();
        services.arena()
                .applyBorder(world, arena.center(), PvpArena.BORDER_SIZE, PvpArena.BORDER_SIZE, 0);
        bar =
                services.bossBars()
                        .create(tr("title"), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        for (Player p : services.participants().onlinePlayers()) spawn(p);
        services.messages().broadcast(tr("rules"));
        services.messages()
                .broadcast(I18n.tr("party.duels.arena.selected", arena.layout().title()));
        updateHud();
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                services.plugin(),
                                () -> {
                                    try {
                                        tick();
                                    } catch (RuntimeException failure) {
                                        services.plugin()
                                                .getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "One Shot match failed",
                                                        failure);
                                        context.finish(EndReason.ABORTED, PartyRoundResult.empty());
                                    }
                                },
                                1,
                                1);
    }

    private void teleport(Player p, Location at) {
        boolean previous = internalTeleport;
        internalTeleport = true;
        try {
            p.teleport(at);
        } finally {
            internalTeleport = previous;
        }
    }

    private void nativeRespawn(Player p) {
        boolean previous = internalTeleport;
        internalTeleport = true;
        try {
            if (p.isDead()) p.spigot().respawn();
        } finally {
            internalTeleport = previous;
        }
    }

    private void spawn(Player p) {
        UUID id = p.getUniqueId();
        freeze.release(id);
        nativeRespawn(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        List<Location> enemies =
                connected.stream()
                        .filter(other -> !other.equals(id))
                        .map(this::player)
                        .filter(Objects::nonNull)
                        .filter(this::active)
                        .map(Player::getLocation)
                        .toList();
        p.setItemOnCursor(null);
        p.closeInventory();
        services.playerState().resetForRound(p, GameMode.SURVIVAL);
        p.setAbsorptionAmount(0);
        p.setNoDamageTicks(0);
        p.setSaturation(20);
        state.spawn(id);
        respawnAt.remove(id);
        deathPause.forget(id);
        lastHit.remove(id);
        dirtyAmmo.remove(id);
        protectedUntil.put(id, (phase == Phase.COUNTDOWN ? countdownEnd : tick) + PROTECTION_TICKS);
        ItemStack bow = new ItemStack(Material.BOW), sword = new ItemStack(Material.WOODEN_SWORD);
        bow.editMeta(meta -> meta.setUnbreakable(true));
        sword.editMeta(meta -> meta.setUnbreakable(true));
        p.getInventory().setItem(0, bow);
        p.getInventory().setItem(1, sword);
        syncAmmo(p);
        p.getInventory().setHeldItemSlot(0);
        teleport(p, arena.spawn(enemies, random));
        if (phase == Phase.COUNTDOWN) freeze.freeze(p);
        p.showBossBar(bar);
    }

    private void syncAmmo(Player p) {
        p.getInventory()
                .setItem(
                        8,
                        state.arrows(p.getUniqueId()) == 0
                                ? null
                                : new ItemStack(Material.ARROW, state.arrows(p.getUniqueId())));
    }

    private void waitForRespawn(Player p) {
        if (p.isDead()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setItemOnCursor(null);
        p.closeInventory();
        services.playerState().resetForRound(p, GameMode.SPECTATOR);
        services.playerState().makeSpectator(p, 4);
        teleport(p, arena.spectator());
        p.showBossBar(bar);
    }

    private void tick() {
        if (phase == Phase.DONE || context.ended() || !services.session().isRunning()) return;
        tick++;
        if (connected.isEmpty()) {
            context.finish(EndReason.ALL_PLAYERS_LEFT, PartyRoundResult.empty());
            return;
        }
        if (phase == Phase.COUNTDOWN) {
            PartySounds.countdown(services.participants().onlinePlayers(), countdownEnd - tick);
            if (tick >= countdownEnd) {
                phase = Phase.FIGHT;
                freeze.releaseAll();
                matchEnd = tick + durationTicks;
                nextBonus = tick;
                services.messages().broadcast(I18n.tr("party.rounds.go"));
            }
        }
        if (phase == Phase.RESULT) {
            if (tick >= resultEnd) finish();
            return;
        }
        if (phase != Phase.FIGHT) return;
        for (UUID id : List.copyOf(respawnAt.keySet())) {
            Player p = player(id);
            if (p == null) continue;
            if (tick >= respawnAt.get(id) && !deathPause.pending(id, tick)) spawn(p);
            else if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR) waitForRespawn(p);
        }
        for (UUID id : connected) {
            Player p = player(id);
            if (p == null || !active(p)) continue;
            if (state.refill(id, tick)) {
                dirtyAmmo.add(id);
                p.sendActionBar(tr("refilled"));
            }
            if (dirtyAmmo.remove(id)) syncAmmo(p);
        }
        if (tick % 20 == 0) shots.expire(tick);
        bonuses.values()
                .removeIf(b -> {
                    if (!b.item().isValid() || tick >= b.expires()) {
                        b.item().remove();
                        return true;
                    }
                    return false;
                });
        if (tick >= nextBonus) {
            spawnBonus();
            nextBonus = tick + bonusTicks;
        }
        if (tick >= matchEnd) {
            requestFinish();
            return;
        }
        if (!services.isTest() && connected.size() == 1) {
            if (aloneSince < 0) aloneSince = tick;
            if (tick - aloneSince >= 300) requestFinish();
        } else aloneSince = -1;
    }

    private void spawnBonus() {
        if (bonuses.size() >= MAX_BONUSES) return;
        List<int[]> candidates = new ArrayList<>(List.of(BONUS_SPOTS));
        Collections.shuffle(candidates, random);
        for (int[] spot : candidates) {
            Location at = new Location(world, spot[0] + .5, PvpArena.FLOOR + 1.25, spot[1] + .5);
            if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable())
                continue;
            if (bonuses.values().stream()
                    .anyMatch(b -> b.item().getLocation().distanceSquared(at) < 4)) continue;
            if (connected.stream()
                    .map(this::player)
                    .filter(Objects::nonNull)
                    .filter(this::active)
                    .anyMatch(p -> p.getLocation().distanceSquared(at) < 2.25)) continue;
            BonusKind kind = random.nextBoolean() ? BonusKind.ARROW : BonusKind.SHIELD;
            Item item =
                    world.dropItem(
                            at,
                            new ItemStack(
                                    kind == BonusKind.ARROW ? Material.ARROW : Material.SHIELD));
            item.setGravity(false);
            item.setVelocity(new org.bukkit.util.Vector());
            item.setInvulnerable(true);
            item.setPickupDelay(0);
            bonuses.put(item.getUniqueId(), new Bonus(item, kind, tick + BONUS_LIFETIME));
            return;
        }
    }

    private Player attacker(Entity source) {
        if (source instanceof Player p) return p;
        return source instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p
                ? p
                : null;
    }

    private boolean validArrow(AbstractArrow arrow, Player shooter) {
        Integer life = arrow.getPersistentDataContainer().get(LIFE_KEY, PersistentDataType.INTEGER);
        return arrow.isValid()
                && arrow.getWorld().equals(world)
                && life != null
                && state.validLife(shooter.getUniqueId(), life);
    }

    private void removeArrow(Entity source) {
        if (source instanceof AbstractArrow) source.remove();
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        // Entity attacks are processed once, in the highest-priority routed handler below.
        if (!(event instanceof EntityDamageByEntityEvent)) event.setCancelled(true);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = attacker(event.getDamager());
        boolean ranged = event.getDamager() instanceof AbstractArrow;
        if (attacker == null
                || attacker.getUniqueId().equals(victim.getUniqueId())
                || !fighting(attacker)
                || !fighting(victim)
                || ranged && !validArrow((AbstractArrow) event.getDamager(), attacker)) {
            event.setCancelled(true);
            removeArrow(event.getDamager());
            return;
        }
        protectedUntil.remove(attacker.getUniqueId());
        if (protectedSpawn(victim.getUniqueId())) {
            event.setCancelled(true);
            removeArrow(event.getDamager());
            return;
        }
        if (event.getFinalDamage() <= 0 && !ranged) return;
        if (state.block(victim.getUniqueId())) {
            event.setCancelled(true);
            removeArrow(event.getDamager());
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, .8f, 1.2f);
            world.spawnParticle(
                    Particle.END_ROD, victim.getLocation().add(0, 1, 0), 12, .4, .5, .4, .03);
            victim.sendActionBar(tr("shield_broken"));
            attacker.sendActionBar(tr("shield_blocked"));
            return;
        }
        if (ranged) {
            victim.setNoDamageTicks(0);
            event.setDamage(1000);
            removeArrow(event.getDamager());
        }
        lastHit.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), tick));
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player p)
                || !fighting(p)
                || event.getBow() == null
                || event.getBow().getType() != Material.BOW
                || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !state.fire(p.getUniqueId(), tick)) {
            event.setCancelled(true);
            return;
        }
        arrow.setDamage(1000);
        arrow.setCritical(false);
        arrow.setPierceLevel(0);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.getPersistentDataContainer()
                .set(LIFE_KEY, PersistentDataType.INTEGER, state.life(p.getUniqueId()));
        protectedUntil.remove(p.getUniqueId());
        dirtyAmmo.add(p.getUniqueId());
        shots.track(p.getUniqueId(), arrow, tick);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitBlock() != null) event.getEntity().remove();
    }

    private UUID credited(UUID victim) {
        Hit hit = lastHit.get(victim);
        Player killer = hit == null ? null : player(hit.attacker());
        return hit != null && tick - hit.tick() <= 160 && killer != null && fighting(killer)
                ? hit.attacker()
                : null;
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        UUID id = victim.getUniqueId();
        if (!connected.contains(id)) return;
        UUID killer = credited(id);
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.deathScreenMessageOverride(
                I18n.render(
                        killer == null
                                ? I18n.tr("party.duels.death.you_died")
                                : I18n.tr("party.duels.death.killed_by", name(killer)),
                        victim));
        if (phase != Phase.FIGHT || !state.alive(id)) return;
        boolean scored = state.death(id, killer);
        lastHit.remove(id);
        protectedUntil.remove(id);
        dirtyAmmo.remove(id);
        shots.clear(id);
        deathPause.begin(id, tick);
        respawnAt.put(id, tick + DeathPause.TICKS);
        services.messages()
                .broadcast(
                        killer == null
                                ? I18n.tr("party.duels.death.died", name(id))
                                : I18n.tr("party.duels.death.killed", name(killer), name(id)));
        if (scored) {
            Player p = player(killer);
            dirtyAmmo.add(killer);
            p.showTitle(
                    Title.title(
                            tr("you_killed"),
                            name(id).append(Component.text(" • ")).append(tr("kill_refill")),
                            Title.Times.times(
                                    Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(200))));
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, .8f, 1.2f);
        }
        updateHud();
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player p) || !fighting(p)) return;
        Bonus bonus = bonuses.get(event.getItem().getUniqueId());
        if (bonus == null) return;
        boolean accepted =
                bonus.kind() == BonusKind.ARROW
                        ? state.addArrow(p.getUniqueId())
                        : state.giveShield(p.getUniqueId());
        if (!accepted) return;
        bonuses.remove(event.getItem().getUniqueId());
        event.getItem().remove();
        syncAmmo(p);
        p.sendActionBar(tr(bonus.kind() == BonusKind.ARROW ? "bonus_arrow" : "bonus_shield"));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .8f, 1.2f);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (phase != Phase.DONE && names.containsKey(event.getPlayer().getUniqueId()))
            event.setRespawnLocation(arena.spectator());
    }

    @Override
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        Player p = event.getPlayer();
        if (phase == Phase.DONE || !connected.contains(p.getUniqueId())) return;
        if (phase == Phase.RESULT) {
            waitForRespawn(p);
            freeze.freeze(p);
        } else if (respawnAt.containsKey(p.getUniqueId())) waitForRespawn(p);
    }

    private void requestFinish() {
        if (phase != Phase.FIGHT) return;
        finished = state.result(connected);
        phase = Phase.RESULT;
        resultEnd = tick + DeathPause.TICKS;
        state.close();
        shots.clearAll();
        clearBonuses();
        for (UUID id : connected) {
            Player p = player(id);
            if (p != null && !p.isDead()) freeze.freeze(p);
        }
        updateHud();
    }

    private void finish() {
        List<Component> lines = new ArrayList<>();
        for (var group : finished.places())
            for (UUID id : group)
                lines.add(
                        tr(
                                "standing",
                                finished.place(id),
                                name(id),
                                state.kills(id),
                                state.deaths(id)));
        summary = List.copyOf(lines);
        completed = true;
        context.finish(EndReason.FINISHED, finished);
    }

    private void clearBonuses() {
        bonuses.values().forEach(b -> b.item().remove());
        bonuses.clear();
    }

    @Override
    public void onEnd(EndReason reason) {
        phase = Phase.DONE;
        if (ticker != null) ticker.cancel();
        freeze.releaseAll();
        shots.clearAll();
        clearBonuses();
        context.hideBars();
        if (state != null) {
            if (!context.embedded() && reason == EndReason.FINISHED)
                roundResult().awards().forEach((id, points) -> services.award("party", id, points));
            state.close();
        }
        respawnAt.clear();
        protectedUntil.clear();
        lastHit.clear();
        dirtyAmmo.clear();
        deathPause.clear();
        if (arena != null) arena.cleanup();
    }

    @Override
    public PartyRoundResult roundResult() {
        return finished != null
                ? finished
                : state == null ? PartyRoundResult.empty() : state.result(connected);
    }

    @Override
    public List<Component> summary() {
        return summary;
    }

    @Override
    public Component globalResult() {
        if (!completed) return null;
        PartyRoundResult result = roundResult();
        return result.places().isEmpty()
                ? null
                : tr(
                        "winners",
                        Component.join(
                                net.kyori.adventure.text.JoinConfiguration.commas(true),
                                result.places().getFirst().stream().map(this::name).toList()));
    }

    @Override
    public void onSecondTick(long elapsed) {
        updateHud();
    }

    private void updateHud() {
        if (bar == null || phase == Phase.DONE) return;
        long remaining =
                Math.max(
                        0, ((phase == Phase.COUNTDOWN ? countdownEnd : matchEnd) - tick + 19) / 20);
        bar.name(
                phase == Phase.COUNTDOWN
                        ? tr("countdown", remaining)
                        : phase == Phase.RESULT
                                ? tr("results")
                                : tr("timer", TimeFormat.clock(remaining)));
        bar.progress(
                phase == Phase.RESULT
                        ? 0
                        : Math.clamp(
                                (float) (remaining * 20)
                                        / Math.max(
                                                1,
                                                phase == Phase.COUNTDOWN
                                                        ? countdownEnd
                                                        : durationTicks),
                                0,
                                1));
        for (Player viewer : services.session().audiencePlayers()) {
            List<Component> lines = new ArrayList<>();
            UUID id = viewer.getUniqueId();
            if (connected.contains(id)) {
                lines.add(tr("stats", state.kills(id), state.deaths(id)));
                lines.add(tr("ammo", state.arrows(id)));
                lines.add(tr(state.shield(id) ? "shield_ready" : "shield_empty"));
                if (respawnAt.containsKey(id))
                    lines.add(tr("respawn", Math.max(0, (respawnAt.get(id) - tick + 19) / 20)));
                else if (state.arrows(id) == 0)
                    lines.add(tr("refill", (state.refillIn(id, tick) + 19) / 20));
                if (protectedSpawn(id)) lines.add(tr("protection"));
            }
            for (UUID top : state.leaderboard(connected).stream().limit(5).toList())
                lines.add(tr("leader", name(top), state.kills(top)));
            services.sidebar().update(viewer, tr("title"), lines);
            viewer.showBossBar(bar);
        }
    }

    @Override
    public boolean isInGame(Player p) {
        return phase != Phase.DONE && active(p);
    }

    @Override
    public boolean mayFollowPlayers(Player p) {
        return false;
    }

    @Override
    public Location spectatorLocation(Player p) {
        return arena == null ? null : arena.spectator();
    }

    private void disconnect(Player p) {
        UUID id = p.getUniqueId();
        context.withdraw(id);
        connected.remove(id);
        freeze.release(id);
        deathPause.forget(id);
        respawnAt.remove(id);
        protectedUntil.remove(id);
        lastHit.remove(id);
        dirtyAmmo.remove(id);
        shots.clear(id);
        if (state != null) state.disconnect(id);
    }

    @Override
    public void onParticipantQuit(Player p) {
        disconnect(p);
    }

    @Override
    public void onParticipantLeave(Player p) {
        disconnect(p);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        UUID id = p.getUniqueId();
        if (context.embedded() || phase == Phase.DONE || !names.containsKey(id)) return;
        connected.add(id);
        if (phase == Phase.COUNTDOWN) spawn(p);
        else {
            respawnAt.put(id, tick + DeathPause.TICKS);
            waitForRespawn(p);
            if (phase == Phase.RESULT) freeze.freeze(p);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (internalTeleport
                || phase == Phase.DONE
                || !connected.contains(event.getPlayer().getUniqueId())) return;
        if (freeze.hold(event)) return;
        if (respawnAt.containsKey(event.getPlayer().getUniqueId())) {
            if (event.hasChangedPosition()) event.setTo(event.getFrom());
        } else if (fighting(event.getPlayer()) && event.getTo().getY() < PvpArena.FLOOR - .5)
            event.getPlayer().setHealth(0);
    }

    @Override
    public void onPlayerJump(PlayerJumpEvent event) {
        freeze.onJump(event);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!internalTeleport
                && phase != Phase.DONE
                && connected.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!fighting(event.getPlayer())) event.setCancelled(true);
        else if (event.getClickedBlock() != null)
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
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
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }
}
