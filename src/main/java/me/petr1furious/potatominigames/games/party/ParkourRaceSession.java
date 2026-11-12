package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native traversal in parallel lanes; the clock rolls back only the current attempt. */
final class ParkourRaceSession extends PartySurvivalSession {
    private static final Set<Material> BUILDING =
            Set.of(
                    Material.CYAN_WOOL,
                    Material.SCAFFOLDING,
                    Material.LADDER,
                    Material.SLIME_BLOCK,
                    Material.COBWEB,
                    Material.HONEY_BLOCK,
                    Material.FLOWER_POT);
    private final Map<UUID, Set<Entity>> entities = new HashMap<>();
    private final Map<UUID, Long> resetReady = new HashMap<>();
    private final Set<UUID> failed = new HashSet<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final int duration;
    private ParkourCourse course;
    private ParkourArena arena;
    private ParkourGuide guide;
    private ParkourRace race;
    private boolean ready;
    private long allFinished = -1;

    ParkourRaceSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "parkour_race", context);
        duration = config == null ? 240 : config.parkourRaceSeconds.get();
    }

    @Override
    void buildArena() {
        course = new ParkourCourse(random);
        race = new ParkourRace(names.keySet(), course.stages());
        arena = new ParkourArena(world, names.keySet(), course);
        guide = new ParkourGuide(services.plugin(), arena, course);
        world.setGameRule(GameRules.BLOCK_DROPS, true);
    }

    @Override
    boolean prepareArena() {
        if (!ready) ready = arena.buildSlice(4096);
        return ready;
    }

    @Override
    Component preparationStatus() {
        return ready
                ? null
                : I18n.tr(
                        "party.rounds.parkour_race.preparing", Math.round(arena.progress() * 100));
    }

    @Override
    Location spawn(int index, int count) {
        return arena.checkpoint(new ArrayList<>(names.keySet()).get(index), 0);
    }

    @Override
    Location viewpoint() {
        return arena.viewpoint();
    }

    @Override
    double outHeight() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    int durationSeconds() {
        return duration;
    }

    @Override
    long arenaTime() {
        return 6000;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    boolean survivalAutoFinish() {
        return false;
    }

    @Override
    Component instructions() {
        return I18n.tr(
                "party.rounds.parkour_race.instructions", course.stages(), course.stages() + 1);
    }

    @Override
    void beginPlay() {
        for (Player p : alivePlayers()) {
            kit(p);
            stageInfo(p);
        }
    }

    private boolean racing(Player p) {
        return playing()
                && !context.ended()
                && !failed.contains(p.getUniqueId())
                && alive(p)
                && !race.finished(p.getUniqueId())
                && p.getGameMode() == GameMode.SURVIVAL
                && p.getWorld().equals(world);
    }

    private boolean editable(Player p, Block block) {
        return racing(p) && arena.mutable(p.getUniqueId(), race.checkpoint(p.getUniqueId()), block);
    }

    private void kit(Player p) {
        // Clear the cursor before closing: a full old inventory could drop its cursor stack.
        p.setItemOnCursor(null);
        p.clearActiveItem();
        p.closeInventory();
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        List<ParkourCourse.Supply> supplies = course.supplies(race.checkpoint(p.getUniqueId()));
        int slot = 0;
        for (var supply : supplies) {
            ItemStack item = supplyItem(supply);
            if (supply.material() == Material.ELYTRA) p.getInventory().setChestplate(item);
            else if (supply.material() == Material.LEATHER_BOOTS) p.getInventory().setBoots(item);
            else p.getInventory().setItem(slot++, item);
        }
        ItemStack clock = new ItemStack(Material.CLOCK);
        clock.editMeta(meta -> {
            meta.displayName(
                    I18n.tr("party.rounds.parkour_race.clock.name").color(NamedTextColor.GOLD));
            meta.lore(
                    List.of(
                            I18n.tr("party.rounds.parkour_race.clock.lore")
                                    .color(NamedTextColor.GRAY),
                            I18n.tr("party.rounds.parkour_race.clock.help_lore")
                                    .color(NamedTextColor.GRAY)));
        });
        p.getInventory().setItem(8, I18n.item(clock, p));
        p.getInventory().setHeldItemSlot(0);
        p.setCooldown(Material.ENDER_PEARL, 0);
        p.setCooldown(Material.WIND_CHARGE, 0);
        p.setCooldown(Material.FIREWORK_ROCKET, 0);
        p.setCooldown(Material.TRIDENT, 0);
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
        p.setFreezeTicks(0);
        p.setGliding(false);
        p.setFallDistance(0);
        p.setNoDamageTicks(0);
        p.setAbsorptionAmount(0);
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(Math.min(20, maxHealth == null ? 20 : maxHealth.getValue()));
        p.setRemainingAir(p.getMaximumAir());
        p.setFoodLevel(20);
        p.setSaturation(20);
    }

    static ItemStack supplyItem(ParkourCourse.Supply supply) {
        ItemStack item = new ItemStack(supply.material(), supply.count());
        if (supply.potion() != null)
            item.editMeta(
                    PotionMeta.class,
                    meta -> meta.setBasePotionType(PotionType.valueOf(supply.potion())));
        if (supply.enchantment() != null) {
            var enchantment =
                    java.util.Objects.requireNonNull(
                            Registry.ENCHANTMENT.get(
                                    NamespacedKey.minecraft(supply.enchantment())));
            item.addEnchantment(enchantment, supply.level());
        }
        return item;
    }

    private void stageInfo(Player p) {
        int checkpoint = race.checkpoint(p.getUniqueId());
        var stage = course.stage(checkpoint);
        if (guide != null) guide.update(p, checkpoint);
        p.sendMessage(
                I18n.tr(
                        "party.rounds.parkour_race.task",
                        checkpoint + 1,
                        course.stages(),
                        I18n.tr(stage.key() + ".name"),
                        I18n.tr(stage.key() + ".hint")));
    }

    private void clearEntities(UUID id) {
        for (Entity entity : entities.getOrDefault(id, Set.of()))
            if (entity.isValid()) {
                if (entity instanceof Vehicle) entity.eject();
                entity.remove();
            }
        entities.remove(id);
    }

    private void reset(Player p) {
        UUID id = p.getUniqueId();
        if (!race.reset(id)) return;
        failed.remove(id);
        skipped.remove(id);
        clearEntities(id);
        arena.reset(id);
        teleport(p, arena.checkpoint(id, race.checkpoint(id)));
        p.setVelocity(new Vector());
        p.setFireTicks(0);
        kit(p);
        resetReady.put(id, gameTick() + 8);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .6f, 1);
        p.sendActionBar(
                I18n.tr(
                        "party.rounds.parkour_race.returned",
                        race.checkpoint(id) + 1,
                        course.stages() + 1));
    }

    @Override
    void playTick(long elapsed) {
        for (Player p : alivePlayers()) {
            UUID id = p.getUniqueId();
            if (race.finished(id)) continue;
            if (guide != null && gameTick() % 10 == 0) guide.update(p, race.checkpoint(id));
            if (failed.remove(id)) {
                boolean bypass = skipped.remove(id);
                reset(p);
                p.sendActionBar(
                        I18n.tr(
                                bypass
                                        ? "party.rounds.parkour_race.need_checkpoint"
                                        : "party.rounds.parkour_race.failed"));
                continue;
            }
            int checkpoint = race.checkpoint(id);
            Location at = p.getLocation();
            if (!arena.attempt(id, checkpoint, at)) {
                reset(p);
                continue;
            }
            race.position(id, arena.distance(id, checkpoint, at));
            if (arena.landed(p, checkpoint + 1) && race.reach(id, checkpoint + 1, elapsed)) {
                clearEntities(id);
                // The next task starts without old water, blocks, drops or movement equipment.
                arena.reset(id);
                p.setGliding(false);
                p.setVelocity(new Vector());
                p.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, .7f, 1.5f);
                if (race.finished(id)) {
                    if (guide != null) guide.remove(id);
                    p.sendMessage(
                            text(I18n.tr("party.rounds.parkour_race.finish", seconds(elapsed))));
                    spectate(p);
                } else {
                    kit(p);
                    stageInfo(p);
                    p.sendActionBar(
                            I18n.tr(
                                    "party.rounds.parkour_race.checkpoint",
                                    checkpoint + 2,
                                    course.stages() + 1));
                }
            }
        }
        entities.values().forEach(set -> set.removeIf(entity -> !entity.isValid()));
        // Native pearls and wind charges may never outlive their lane/attempt or cross a wall.
        for (var entry : entities.entrySet())
            for (Entity entity : List.copyOf(entry.getValue()))
                if ((entity instanceof Projectile || entity instanceof Vehicle)
                        && !arena.attempt(
                                entry.getKey(),
                                race.checkpoint(entry.getKey()),
                                entity.getLocation())) {
                    if (entity instanceof Vehicle) {
                        entity.eject();
                        failed.add(entry.getKey());
                    }
                    entity.remove();
                    entry.getValue().remove(entity);
                }
        if (context.eligible().stream().allMatch(race::finished)) {
            if (allFinished < 0) allFinished = elapsed;
            standings.holdResults(true);
            if (elapsed - allFinished >= 60) finish(List.of());
        }
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0);
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (!playing() || context.ended() || !alive(p) || race.finished(id)) return;
        if (failed.contains(id) || !arena.attempt(id, race.checkpoint(id), event.getTo())) {
            // Reject the packet before moving into a future section, even at elytra/rocket speed.
            event.setCancelled(true);
            if (event.getTo().getY() >= ParkourCourse.BUILD_BOTTOM
                    && event.getTo().getY() < ParkourCourse.TOP - 1) skipped.add(id);
            failed.add(id);
            p.setGliding(false);
            p.setVelocity(new Vector());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void glide(EntityToggleGlideEvent event) {
        if (event.isGliding()
                && event.getEntity() instanceof Player p
                && p.getWorld().equals(world)
                && (!racing(p)
                        || course.stage(race.checkpoint(p.getUniqueId()))
                                != ParkourCourse.Kind.FLIGHT)) event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!racing(p)) {
            event.setCancelled(true);
            return;
        }
        if (event.getItem() != null && event.getItem().getType() == Material.CLOCK) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                if (event.getAction().isRightClick()
                        && gameTick() >= resetReady.getOrDefault(p.getUniqueId(), 0L)) reset(p);
                else if (event.getAction().isLeftClick()) stageInfo(p);
            }
        }
        // All other interactions remain native, including mining, bucket use and item cooldowns.
    }

    @Override
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        var item =
                event.getHand() == EquipmentSlot.HAND
                        ? p.getInventory().getItemInMainHand()
                        : p.getInventory().getItemInOffHand();
        if (item.getType() == Material.CLOCK) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND
                    && racing(p)
                    && gameTick() >= resetReady.getOrDefault(p.getUniqueId(), 0L)) reset(p);
        }
    }

    @Override
    protected void handleBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!editable(p, event.getBlock())
                || !BUILDING.contains(event.getBlock().getType())
                || !arena.remember(
                        p.getUniqueId(),
                        race.checkpoint(p.getUniqueId()),
                        event.getBlock(),
                        event.getBlockReplacedState().getBlockData())) event.setCancelled(true);
    }

    @Override
    protected void handleBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!editable(p, event.getBlock()) || !arena.changed(p.getUniqueId(), event.getBlock()))
            event.setCancelled(true);
        else {
            event.setExpToDrop(0);
            if (!course.recoverMaterials(race.checkpoint(p.getUniqueId())))
                event.setDropItems(false);
        }
    }

    @Override
    protected void handleDamage(EntityDamageEvent event) {
        if (!event.getEntity().getWorld().equals(world)) return;
        if (!(event.getEntity() instanceof Player p) || !racing(p)) {
            event.setCancelled(true);
            return;
        }
        boolean environment =
                event.getCause() == EntityDamageEvent.DamageCause.FALL
                        || event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                        || event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                        || event.getCause() == EntityDamageEvent.DamageCause.FREEZE;
        if (!environment) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= p.getHealth()) {
            event.setCancelled(true);
            // Resolve next tick, before checkpoint detection, rather than teleporting inside native
            // damage.
            failed.add(p.getUniqueId());
        }
    }

    @Override
    protected boolean allowTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (race.finished(p.getUniqueId()))
            return event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                    && event.getTo().getWorld().equals(world);
        return racing(p)
                && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && arena.attempt(p.getUniqueId(), race.checkpoint(p.getUniqueId()), event.getTo())
                && clearBody(event.getTo());
    }

    private static boolean clearBody(Location at) {
        for (int x = (int) Math.floor(at.getX() - .3); x <= Math.floor(at.getX() + .3 - 1e-6); x++)
            for (int y = at.getBlockY(); y <= Math.floor(at.getY() + 1.8 - 1e-6); y++)
                for (int z = (int) Math.floor(at.getZ() - .3);
                        z <= Math.floor(at.getZ() + .3 - 1e-6);
                        z++) if (!at.getWorld().getBlockAt(x, y, z).isPassable()) return false;
        return true;
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Player p = projectileOwner(event.getEntity());
        if (p == null
                || !racing(p)
                || !(event.getEntity() instanceof EnderPearl
                        || event.getEntity() instanceof WindCharge
                        || event.getEntity() instanceof Firework)) {
            event.setCancelled(true);
            return;
        }
        entities.computeIfAbsent(p.getUniqueId(), ignored -> new LinkedHashSet<>())
                .add(event.getEntity());
    }

    private Player projectileOwner(Projectile shot) {
        if (shot instanceof Firework rocket && rocket.getAttachedTo() instanceof Player p) return p;
        if (shot.getShooter() instanceof Player p) return p;
        if (shot instanceof Firework rocket) {
            UUID id = rocket.getSpawningEntity();
            if (id == null) id = arena.owner(shot.getLocation());
            return id == null ? null : Bukkit.getPlayer(id);
        }
        return null;
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        Player p = projectileOwner(event.getEntity());
        if (p == null) return;
        if (!racing(p)
                || event.getHitEntity() instanceof Player other
                        && !other.getUniqueId().equals(p.getUniqueId())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    private boolean foreignPush(Player target, Entity source) {
        if (source instanceof Projectile shot && shot.getShooter() instanceof Entity shooter)
            source = shooter;
        return !racing(target)
                || source != null && !source.getUniqueId().equals(target.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void knockback(EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player target
                && target.getWorld().equals(world)
                && (!racing(target)
                        || event instanceof EntityPushedByEntityAttackEvent push
                                && foreignPush(target, push.getPushedBy())))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void legacyKnockback(org.bukkit.event.entity.EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player target
                && target.getWorld().equals(world)
                && (!racing(target)
                        || event instanceof EntityKnockbackByEntityEvent push
                                && foreignPush(target, push.getSourceEntity())))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void emptyBucket(PlayerBucketEmptyEvent event) {
        if (!event.getPlayer().getWorld().equals(world)) return;
        Player p = event.getPlayer();
        boolean allowed =
                event.getBucket() == Material.WATER_BUCKET
                        || event.getBucket() == Material.POWDER_SNOW_BUCKET
                                && race.checkpoint(p.getUniqueId()) >= 0
                                && course.stage(race.checkpoint(p.getUniqueId()))
                                        == ParkourCourse.Kind.POWDER_SNOW_DROP;
        if (!allowed
                || !editable(p, event.getBlock())
                || !arena.remember(
                        p.getUniqueId(),
                        race.checkpoint(p.getUniqueId()),
                        event.getBlock(),
                        event.getBlock().getBlockData())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void fillBucket(PlayerBucketFillEvent event) {
        if (!event.getPlayer().getWorld().equals(world)) return;
        Player p = event.getPlayer();
        if (!editable(p, event.getBlock())
                || !arena.remember(
                        p.getUniqueId(),
                        race.checkpoint(p.getUniqueId()),
                        event.getBlock(),
                        event.getBlock().getBlockData())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void frost(EntityBlockFormEvent event) {
        if (!event.getBlock().getWorld().equals(world)
                || event.getNewState().getType() != Material.FROSTED_ICE) return;
        if (!(event.getEntity() instanceof Player p)
                || !racing(p)
                || !arena.rememberFrost(
                        p.getUniqueId(), race.checkpoint(p.getUniqueId()), event.getBlock()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void vehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!vehicle.getWorld().equals(world)) return;
        UUID id = arena.owner(vehicle.getLocation());
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (p == null
                || !racing(p)
                || !arena.attempt(id, race.checkpoint(id), vehicle.getLocation())) {
            event.setCancelled(true);
            return;
        }
        var kind = course.stage(race.checkpoint(id));
        if (!kind.vehicle()
                || (kind == ParkourCourse.Kind.MINECART_RIDE
                        ? !(vehicle instanceof Minecart)
                        : !(vehicle instanceof Boat))) {
            event.setCancelled(true);
            return;
        }
        vehicle.setPersistent(false);
        entities.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(vehicle);
    }

    private UUID vehicleOwner(Entity vehicle) {
        return entities.entrySet().stream()
                .filter(e -> e.getValue().contains(vehicle))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void vehicleEnter(VehicleEnterEvent event) {
        if (!event.getVehicle().getWorld().equals(world)) return;
        UUID owner = vehicleOwner(event.getVehicle());
        if (!(event.getEntered() instanceof Player p)
                || !racing(p)
                || !p.getUniqueId().equals(owner)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void vehicleMove(VehicleMoveEvent event) {
        if (!event.getVehicle().getWorld().equals(world)) return;
        UUID owner = vehicleOwner(event.getVehicle());
        if (owner != null
                && !race.finished(owner)
                && !arena.attempt(owner, race.checkpoint(owner), event.getTo())) {
            failed.add(owner);
            event.getVehicle().setVelocity(new Vector());
        }
    }

    private boolean journal(Block block) {
        UUID id = arena.owner(block.getLocation());
        return playing()
                && !context.ended()
                && id != null
                && connected.contains(id)
                && standings.alive(id)
                && !race.finished(id)
                && arena.remember(id, race.checkpoint(id), block, block.getBlockData());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void waterFlow(BlockFromToEvent event) {
        if (!event.getBlock().getWorld().equals(world)) return;
        UUID owner = arena.owner(event.getBlock().getLocation());
        if (owner == null
                || !arena.attempt(owner, race.checkpoint(owner), event.getBlock().getLocation())
                || !owner.equals(arena.owner(event.getToBlock().getLocation()))
                || !journal(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void waterLevel(FluidLevelChangeEvent event) {
        if (event.getBlock().getWorld().equals(world) && !journal(event.getBlock()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void itemSpawn(ItemSpawnEvent event) {
        if (!event.getLocation().getWorld().equals(world)) return;
        UUID id = arena.owner(event.getLocation());
        if (id == null
                || !playing()
                || context.ended()
                || !standings.alive(id)
                || race.finished(id)
                || !course.recoverMaterials(race.checkpoint(id))
                || !arena.attempt(id, race.checkpoint(id), event.getLocation()))
            event.setCancelled(true);
        else entities.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(event.getEntity());
    }

    @Override
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)
                || !racing(p)
                || !p.getUniqueId().equals(arena.owner(event.getItem().getLocation())))
            event.setCancelled(true);
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)
                || !racing(p)
                || event.getClickedInventory() == null
                || !event.getClickedInventory().equals(p.getInventory())
                || event.getSlot() == 8
                || event.getHotbarButton() == 8
                || event.getCurrentItem() != null
                        && event.getCurrentItem().getType() == Material.CLOCK
                || event.getCursor().getType() == Material.CLOCK
                || event.getAction()
                        == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR)
            event.setCancelled(true);
    }

    @Override
    public boolean isInGame(Player p) {
        return alive(p) && race != null && !race.finished(p.getUniqueId());
    }

    @Override
    void playerOut(Player p) {
        if (guide != null) guide.remove(p.getUniqueId());
        failed.remove(p.getUniqueId());
        skipped.remove(p.getUniqueId());
        clearEntities(p.getUniqueId());
        if (arena != null) arena.reset(p.getUniqueId());
    }

    @Override
    public PartyRoundResult roundResult() {
        return race == null ? PartyRoundResult.empty() : race.result(context.eligible());
    }

    @Override
    Component status() {
        return I18n.tr(
                "party.rounds.parkour_race.status",
                names.keySet().stream().filter(race::finished).count(),
                Math.max(0, duration - elapsed() / 20));
    }

    @Override
    List<Component> sidebarLines(Player p, Component line) {
        UUID id = p.getUniqueId();
        int place = race.result(context.eligible()).place(id);
        if (place == 0) return List.of(I18n.tr("party.rounds.parkour_race.spectating"), line);
        List<Component> lines =
                new ArrayList<>(
                        List.of(
                                I18n.tr(
                                        "party.rounds.parkour_race.place",
                                        place,
                                        context.eligible().size()),
                                I18n.tr(
                                        "party.rounds.parkour_race.progress",
                                        race.checkpoint(id),
                                        course.stages()),
                                I18n.tr(
                                        "party.rounds.parkour_race.time",
                                        seconds(race.finished(id) ? race.time(id) : elapsed()))));
        if (race.finished(id)) lines.add(I18n.tr("party.rounds.parkour_race.waiting"));
        else {
            lines.add(I18n.tr(course.stage(race.checkpoint(id)).key() + ".name"));
            lines.add(
                    I18n.tr(
                            "party.rounds.parkour_race.target",
                            race.checkpoint(id) + 2,
                            course.stages() + 1));
            Location goal = arena.checkpoint(id, race.checkpoint(id) + 1);
            if (p.getWorld().equals(goal.getWorld()))
                lines.add(ParkourGuide.height(goal.getY() - p.getLocation().getY()));
            lines.add(I18n.tr("party.rounds.parkour_race.clock.hint"));
        }
        return List.copyOf(lines);
    }

    @Override
    void cleanup() {
        if (guide != null) guide.clear();
        for (UUID id : List.copyOf(entities.keySet())) clearEntities(id);
        resetReady.clear();
        failed.clear();
        skipped.clear();
        if (arena != null) arena.clear();
        if (race != null) race.close();
    }
}
