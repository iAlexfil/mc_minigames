package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsGadgetState.Kind.*;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Bounded, session-owned deployables and selected specialization effects. */
final class FinalsGadgetEffects {
    private final FinalsCombatContext context;
    private final FinalsGadgets gadgets;
    private final List<Device> devices = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final Map<UUID, Mesh> meshes = new HashMap<>();
    private final Map<UUID, Grapple> grapples = new HashMap<>();
    private final Map<UUID, Winch> winches = new HashMap<>();
    private final Map<Block, Hole> holes = new LinkedHashMap<>();
    private final Map<UUID, Flash> flashes = new HashMap<>();
    private final Map<ViewKey, HiddenView> smokeViews = new HashMap<>();

    private static final class Device {
        final UUID owner;
        final FinalsGadgetState.Kind kind;
        final ItemDisplay display;
        final Location center;
        final long arms, expires;
        double health;
        int interceptions = 6;
        long nextShot;

        Device(
                Player owner,
                FinalsGadgetState.Kind kind,
                ItemDisplay display,
                Location center,
                long now) {
            this.owner = owner.getUniqueId();
            this.kind = kind;
            this.display = display;
            this.center = center.clone();
            arms = now + (kind == GUARDIAN_TURRET ? 40 : 20);
            expires = now + 600;
            health = kind == GUARDIAN_TURRET ? 20 : kind == APS_TURRET ? 12 : 4;
        }

        BoundingBox box() {
            double height = kind == GUARDIAN_TURRET || kind == APS_TURRET ? .9 : .25;
            return new BoundingBox(
                    center.getX() - .4,
                    center.getY(),
                    center.getZ() - .4,
                    center.getX() + .4,
                    center.getY() + height,
                    center.getZ() + .4);
        }
    }

    private record Cloud(UUID owner, FinalsGadgetState.Kind kind, Location center, long expires) {}

    private record Mesh(Player owner, long expires) {}

    private record Grapple(Player owner, Location anchor, long expires) {}

    private record Winch(Player owner, UUID target, long expires) {}

    private record Hole(UUID owner, BlockState previous, long expires) {}

    private record Flash(Player player, PotionEffect previous, long started, long expires) {}

    private record ViewKey(UUID viewer, UUID target) {}

    private record HiddenView(Player viewer, Player target) {}

    FinalsGadgetEffects(FinalsCombatContext context, FinalsGadgets gadgets) {
        this.context = context;
        this.gadgets = gadgets;
    }

    /** Returning true consumes an active toggle, without using a second recharge charge. */
    boolean toggle(Player player, FinalsGadgetState.Kind kind) {
        if (kind == MESH_SHIELD && meshes.remove(player.getUniqueId()) != null) return true;
        if (kind == GRAPPLING_HOOK && grapples.remove(player.getUniqueId()) != null) return true;
        if (kind != BREACH_CHARGE) return false;
        List<Device> owned =
                devices.stream()
                        .filter(d ->
                                d.owner.equals(player.getUniqueId())
                                        && d.kind == BREACH_CHARGE
                                        && context.tick() >= d.arms)
                        .toList();
        if (owned.isEmpty()) return false;
        for (Device device : owned) {
            remove(device);
            gadgets.detonate(player, FRAG_GRENADE, device.center.clone().add(0, .2, 0));
            gadgets.destroyAround(device.center, 2.5);
        }
        return true;
    }

    boolean activate(Player player, FinalsGadgetState.Kind kind) {
        boolean used =
                switch (kind) {
                    case GRAPPLING_HOOK -> grapple(player);
                    case GUARDIAN_TURRET,
                            APS_TURRET,
                            BREACH_CHARGE,
                            EXPLOSIVE_MINE,
                            GAS_MINE,
                            PYRO_MINE ->
                            place(player, kind);
                    case DEMATERIALIZER -> dematerialize(player);
                    case MESH_SHIELD -> {
                        meshes.put(player.getUniqueId(), new Mesh(player, context.tick() + 120));
                        yield true;
                    }
                    case WINCH_CLAW -> winch(player);
                    default -> false;
                };
        if (used) gadgets.reveal(player);
        else player.sendActionBar(I18n.tr("finals.gadget.no_target"));
        return used;
    }

    private boolean place(Player player, FinalsGadgetState.Kind kind) {
        Block block = gadgets.placement(player);
        if (block == null) return false;
        Location center = block.getLocation().add(.5, 0, .5);
        if (devices.stream().anyMatch(d -> d.center.distanceSquared(center) < 1)) return false;
        ItemDisplay display =
                context.world()
                        .spawn(
                                center.clone().add(0, .3, 0),
                                ItemDisplay.class,
                                entity -> {
                                    entity.setItemStack(new ItemStack(kind.material()));
                                    entity.setPersistent(false);
                                    entity.setGravity(false);
                                    entity.setInvulnerable(true);
                                    entity.setItemDisplayTransform(
                                            ItemDisplay.ItemDisplayTransform.FIXED);
                                });
        context.track(display);
        List<Device> owned =
                devices.stream()
                        .filter(d -> d.owner.equals(player.getUniqueId()) && d.kind == kind)
                        .toList();
        if (owned.size() >= kind.capacity) remove(owned.getFirst());
        devices.add(new Device(player, kind, display, center, context.tick()));
        player.playSound(center, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, .6f, 1.3f);
        return true;
    }

    boolean detonate(Player source, FinalsGadgetState.Kind kind, Location center) {
        if (!context.inside(center))
            return kind != FRAG_GRENADE && kind != RPG7 && kind != SONAR_GRENADE;
        switch (kind) {
            case SMOKE_GRENADE, GAS_GRENADE, PYRO_GRENADE -> {
                // Fire consumes gas; smoke extinguishes fire. Keep every owner's area effects
                // bounded.
                if (kind == PYRO_GRENADE || kind == SMOKE_GRENADE)
                    clouds.removeIf(c ->
                            c.kind == (kind == PYRO_GRENADE ? GAS_GRENADE : PYRO_GRENADE)
                                    && c.center.distanceSquared(center) < 36);
                List<Cloud> owned =
                        clouds.stream()
                                .filter(c -> c.owner.equals(source.getUniqueId()) && c.kind == kind)
                                .toList();
                if (owned.size() >= 2) clouds.remove(owned.getFirst());
                clouds.add(
                        new Cloud(
                                source.getUniqueId(), kind, center.clone(), context.tick() + 160));
                return true;
            }
            case FLASHBANG -> {
                for (Player target : context.players()) {
                    if (!context.active(target)
                            || context.friendly(source, target)
                            || target.getEyeLocation().distanceSquared(center) > 64
                            || !gadgets.clearLine(center, target.getEyeLocation())) continue;
                    Flash old = flashes.get(target.getUniqueId());
                    PotionEffect previous =
                            old == null
                                    ? target.getPotionEffect(PotionEffectType.BLINDNESS)
                                    : old.previous;
                    if (previous != null && previous.getDuration() > 60 && old == null) continue;
                    target.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.BLINDNESS, 60, 0, false, false, true));
                    flashes.put(
                            target.getUniqueId(),
                            new Flash(
                                    target,
                                    previous,
                                    old == null ? context.tick() : old.started,
                                    context.tick() + 60));
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 2);
                }
                context.world().spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
                return true;
            }
            case GOO_GRENADE -> {
                Vector facing = source.getLocation().getDirection();
                boolean alongX = Math.abs(facing.getZ()) >= Math.abs(facing.getX());
                Block base = center.getBlock();
                if (!base.getType().isAir()) base = base.getRelative(0, 1, 0);
                for (int width = -1; width <= 1; width++)
                    for (int y = 0; y <= 1; y++) {
                        Block block = base.getRelative(alongX ? width : 0, y, alongX ? 0 : width);
                        if (gadgets.placeable(block))
                            gadgets.putCover(source, block, Material.WHITE_CONCRETE, 300);
                    }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean grapple(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit =
                context.world()
                        .rayTraceBlocks(
                                eye, eye.getDirection(), 22, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return false;
        Location anchor = hit.getHitPosition().toLocation(context.world());
        if (hit.getHitBlockFace() != null)
            anchor.add(hit.getHitBlockFace().getDirection().multiply(.6));
        if (!context.inside(anchor)) return false;
        grapples.put(player.getUniqueId(), new Grapple(player, anchor, context.tick() + 40));
        return true;
    }

    private boolean winch(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double reach = clearReach(eye, direction, 14);
        Player closest = null;
        for (Player target : context.players()) {
            if (!context.active(target) || context.friendly(player, target)) continue;
            RayTraceResult hit =
                    target.getBoundingBox().expand(.25).rayTrace(eye.toVector(), direction, reach);
            if (hit != null) {
                reach = eye.toVector().distance(hit.getHitPosition());
                closest = target;
            }
        }
        if (context.tryPullCashbox(player, eye, direction, reach)
                || context.tryPullCashout(player, eye, direction, reach)) {
            winches.put(player.getUniqueId(), new Winch(player, null, context.tick() + 20));
            return true;
        }
        if (closest == null) return false;
        winches.put(
                player.getUniqueId(),
                new Winch(player, closest.getUniqueId(), context.tick() + 16));
        return true;
    }

    private boolean dematerialize(Player player) {
        if (holes.values().stream().filter(h -> h.owner.equals(player.getUniqueId())).count() >= 27)
            return false;
        Location eye = player.getEyeLocation();
        RayTraceResult hit =
                context.world()
                        .rayTraceBlocks(eye, eye.getDirection(), 6, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return false;
        Block center = hit.getHitBlock();
        Vector face = hit.getHitBlockFace().getDirection();
        boolean changed = false;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                Block block =
                        center.getRelative(
                                face.getX() != 0 ? 0 : a,
                                face.getY() != 0 ? 0 : face.getX() != 0 ? a : b,
                                face.getZ() != 0 ? 0 : b);
                if (!context.destructible(block)
                        || !context.inside(block.getLocation())
                        || !block.getType().isSolid()
                        || holes.containsKey(block)
                        || gadgets.isTemporaryCover(block)
                        || block.getState() instanceof TileState) continue;
                holes.put(
                        block,
                        new Hole(player.getUniqueId(), block.getState(), context.tick() + 160));
                block.setType(Material.AIR, false);
                changed = true;
            }
        return changed;
    }

    boolean weaponBlocked(Player player) {
        Mesh mesh = meshes.get(player.getUniqueId());
        return mesh != null && active(mesh);
    }

    private boolean active(Mesh mesh) {
        return context.tick() < mesh.expires
                && context.canAct(mesh.owner)
                && mesh.owner.getInventory().getHeldItemSlot() == 4;
    }

    boolean blocksDamage(Player target, Player source, Location origin) {
        for (Mesh mesh : meshes.values()) {
            if (!active(mesh) || !context.friendly(target, mesh.owner)) continue;
            Vector facing = horizontalFacing(mesh.owner.getLocation());
            Vector center =
                    mesh.owner.getLocation().toVector().add(new Vector(0, 1, 0)).add(facing);
            if (crossesMesh(
                    origin.toVector(),
                    target.getLocation().toVector().add(new Vector(0, 1, 0)),
                    center,
                    facing)) return true;
        }
        return false;
    }

    static boolean crossesMesh(Vector origin, Vector target, Vector center, Vector facing) {
        double from = origin.clone().subtract(center).dot(facing);
        double to = target.clone().subtract(center).dot(facing);
        if (from <= 0 || to > 0) return false;
        double fraction = from / (from - to);
        Vector contact =
                origin.clone()
                        .add(target.clone().subtract(origin).multiply(fraction))
                        .subtract(center);
        Vector side = new Vector(-facing.getZ(), 0, facing.getX());
        return Math.abs(contact.dot(side)) <= 2.5 && Math.abs(contact.getY()) <= 1.5;
    }

    double deviceDistance(Player source, Location eye, Vector direction, double reach) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Device device : devices) {
            Player owner = owner(device.owner);
            if (owner == null || context.friendly(source, owner) || !device.display.isValid())
                continue;
            RayTraceResult hit =
                    device.box().rayTrace(eye.toVector(), direction, Math.min(reach, nearest));
            if (hit != null) nearest = eye.toVector().distance(hit.getHitPosition());
        }
        return nearest;
    }

    boolean attackDevice(
            Player source, Location eye, Vector direction, double reach, double damage) {
        if (!context.canAct(source) || damage <= 0) return false;
        Device closest = null;
        double nearest = clearReach(eye, direction, reach);
        for (Device device : devices) {
            Player owner = owner(device.owner);
            if (owner == null || context.friendly(source, owner) || !device.display.isValid())
                continue;
            RayTraceResult hit = device.box().rayTrace(eye.toVector(), direction, nearest);
            if (hit != null) {
                nearest = eye.toVector().distance(hit.getHitPosition());
                closest = device;
            }
        }
        if (closest == null) return false;
        closest.health -= damage;
        if (closest.health <= 0) remove(closest);
        return true;
    }

    boolean intercept(Player source, Location from, Location to) {
        for (Device device : List.copyOf(devices)) {
            Player owner = owner(device.owner);
            if (device.kind != APS_TURRET
                    || context.tick() < device.arms
                    || owner == null
                    || !context.active(owner)
                    || context.friendly(source, owner)
                    || !segmentSphere(from.toVector(), to.toVector(), device.center.toVector(), 3)
                    || !gadgets.clearLine(device.center.clone().add(0, .5, 0), from)) continue;
            context.world().spawnParticle(Particle.ELECTRIC_SPARK, to, 8, .1, .1, .1, 0);
            if (--device.interceptions <= 0) remove(device);
            return true;
        }
        return false;
    }

    void blastDevices(Player source, Location center, double radius, double damage) {
        for (Device device : List.copyOf(devices)) {
            Player owner = owner(device.owner);
            if (owner == null
                    || context.friendly(source, owner)
                    || device.center.distanceSquared(center) > radius * radius
                    || !gadgets.clearLine(center, device.center.clone().add(0, .2, 0))) continue;
            device.health -= damage;
            if (device.health <= 0) remove(device);
        }
    }

    private double clearReach(Location eye, Vector direction, double reach) {
        RayTraceResult wall =
                context.world()
                        .rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        return wall == null ? reach : eye.toVector().distance(wall.getHitPosition());
    }

    private Player owner(UUID id) {
        for (Player player : context.players()) if (id.equals(player.getUniqueId())) return player;
        return null;
    }

    private void remove(Device device) {
        devices.remove(device);
        device.display.remove();
    }

    void tick() {
        long now = context.tick();
        meshes.values().removeIf(mesh -> !active(mesh));
        for (Grapple grapple : List.copyOf(grapples.values())) {
            Player player = grapple.owner;
            Vector delta = grapple.anchor.toVector().subtract(player.getEyeLocation().toVector());
            if (now >= grapple.expires
                    || !context.canAct(player)
                    || delta.lengthSquared() < 1
                    || !gadgets.clearLine(player.getEyeLocation(), grapple.anchor)) {
                grapples.remove(player.getUniqueId());
                continue;
            }
            Vector velocity = delta.normalize().multiply(.85);
            Location next = player.getLocation().add(velocity);
            if (!context.inside(next) || !clearMovement(player, velocity)) {
                grapples.remove(player.getUniqueId());
                continue;
            }
            player.setFallDistance(0);
            player.setVelocity(velocity);
            if (now % 4 == 0)
                line(player.getEyeLocation(), grapple.anchor, Particle.ELECTRIC_SPARK);
        }
        for (Winch winch : List.copyOf(winches.values())) {
            Player player = winch.owner;
            if (now >= winch.expires || !context.canAct(player)) {
                winches.remove(player.getUniqueId());
                continue;
            }
            Location eye = player.getEyeLocation();
            if (winch.target == null) {
                if (!context.tryPullCashbox(player, eye, eye.getDirection(), 14)
                        && !context.tryPullCashout(player, eye, eye.getDirection(), 14))
                    winches.remove(player.getUniqueId());
                continue;
            }
            Player target = owner(winch.target);
            if (target == null
                    || !context.active(target)
                    || !player.hasLineOfSight(target)
                    || player.getLocation().distanceSquared(target.getLocation()) > 225) {
                winches.remove(player.getUniqueId());
                continue;
            }
            Vector direction = horizontalFacing(player.getLocation());
            Location goal = player.getLocation().add(direction.multiply(1.5));
            Vector delta = goal.toVector().subtract(target.getLocation().toVector());
            if (delta.lengthSquared() < .6) {
                winches.remove(player.getUniqueId());
                continue;
            }
            Vector velocity =
                    delta.normalize().multiply(.65).setY(Math.clamp(delta.getY(), -.2, .3));
            Location next = target.getLocation().add(velocity);
            if (!context.inside(next) || !clearMovement(target, velocity)) {
                winches.remove(player.getUniqueId());
                continue;
            }
            target.setVelocity(velocity);
            if (now % 4 == 0) line(eye, target.getEyeLocation(), Particle.CRIT);
        }
        for (Device device : List.copyOf(devices)) tickDevice(device);
        clouds.removeIf(cloud ->
                now >= cloud.expires
                        || owner(cloud.owner) == null
                        || !context.active(owner(cloud.owner)));
        if (now % 4 == 0) {
            for (Cloud cloud : List.copyOf(clouds)) tickCloud(cloud);
            refreshSmoke();
            for (Mesh mesh : meshes.values()) drawMesh(mesh);
        }
        for (var entry : List.copyOf(holes.entrySet()))
            if (now >= entry.getValue().expires) restoreHole(entry.getKey());
        for (Flash flash : List.copyOf(flashes.values()))
            if (now >= flash.expires || !context.active(flash.player)) restoreFlash(flash);
    }

    private void tickDevice(Device device) {
        if (!devices.contains(device)) return;
        Player source = owner(device.owner);
        long now = context.tick();
        if (source == null
                || !context.active(source)
                || !device.display.isValid()
                || now >= device.expires
                || !device.center.clone().add(0, -.05, 0).getBlock().getType().isSolid()) {
            remove(device);
            return;
        }
        if (now < device.arms || device.kind == APS_TURRET || device.kind == BREACH_CHARGE) return;
        double reach = device.kind == GUARDIAN_TURRET ? 14 : 2.3;
        Player nearest = null;
        double distance = reach * reach;
        Location origin = device.center.clone().add(0, .65, 0);
        for (Player target : context.players()) {
            if (!context.active(target) || context.friendly(source, target)) continue;
            double d = target.getLocation().distanceSquared(device.center);
            if (d <= distance && gadgets.clearLine(origin, target.getLocation().add(0, .9, 0))) {
                nearest = target;
                distance = d;
            }
        }
        if (nearest == null) return;
        if (device.kind == GUARDIAN_TURRET) {
            if (now < device.nextShot) return;
            device.nextShot = now + 10;
            context.damage(nearest, source, 1.4, origin);
            line(origin, nearest.getLocation().add(0, .9, 0), Particle.CRIT);
        } else {
            remove(device);
            FinalsGadgetState.Kind grenade =
                    device.kind == GAS_MINE
                            ? GAS_GRENADE
                            : device.kind == PYRO_MINE ? PYRO_GRENADE : FRAG_GRENADE;
            gadgets.detonate(source, grenade, device.center.clone().add(0, .2, 0));
        }
    }

    private void tickCloud(Cloud cloud) {
        Player source = owner(cloud.owner);
        if (source == null) return;
        Particle particle =
                cloud.kind == SMOKE_GRENADE
                        ? Particle.CAMPFIRE_COSY_SMOKE
                        : cloud.kind == GAS_GRENADE ? Particle.SPORE_BLOSSOM_AIR : Particle.FLAME;
        context.world()
                .spawnParticle(particle, cloud.center.clone().add(0, .8, 0), 25, 1.8, .8, 1.8, .01);
        if (cloud.kind == SMOKE_GRENADE || context.tick() % 12 != 0) return;
        for (Player target : List.copyOf(context.players())) {
            if (!clouds.contains(cloud)) return;
            if (!context.active(target)
                    || !target.equals(source) && context.friendly(source, target)
                    || target.getLocation().distanceSquared(cloud.center) > 12.25
                    || !gadgets.clearLine(
                            cloud.center.clone().add(0, .3, 0), target.getLocation().add(0, .8, 0)))
                continue;
            context.damage(target, source, cloud.kind == GAS_GRENADE ? 1.2 : 1.8, cloud.center);
        }
        if (cloud.kind == PYRO_GRENADE) blastDevices(source, cloud.center, 3.5, 2);
    }

    private void drawMesh(Mesh mesh) {
        Location center = mesh.owner.getLocation().add(0, 1, 0);
        Vector forward = horizontalFacing(center);
        center.add(forward);
        Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        for (double x = -2.5; x <= 2.5; x += .5)
            for (double y : new double[] {-1, 1.5})
                context.world()
                        .spawnParticle(
                                Particle.END_ROD,
                                center.clone().add(side.clone().multiply(x)).add(0, y, 0),
                                1,
                                0,
                                0,
                                0,
                                0);
    }

    private void line(Location from, Location to, Particle particle) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.min(24, Math.max(1, (int) delta.length()));
        for (int i = 0; i <= steps; i++)
            context.world()
                    .spawnParticle(
                            particle,
                            from.clone().add(delta.clone().multiply((double) i / steps)),
                            1,
                            0,
                            0,
                            0,
                            0);
    }

    static boolean segmentSphere(Vector from, Vector to, Vector center, double radius) {
        Vector delta = to.clone().subtract(from);
        double length = delta.lengthSquared();
        double t =
                length == 0
                        ? 0
                        : Math.clamp(center.clone().subtract(from).dot(delta) / length, 0, 1);
        return from.clone().add(delta.multiply(t)).distanceSquared(center) <= radius * radius;
    }

    private static Vector horizontalFacing(Location point) {
        double yaw = Math.toRadians(point.getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** Sweep the complete player body, including shoulders and head, against real voxel shapes. */
    private boolean clearMovement(Player player, Vector movement) {
        BoundingBox body = player.getBoundingBox();
        Vector center = body.getCenter();
        double length = movement.length();
        if (length < 1e-7) return true;
        BoundingBox swept = body.clone().union(body.clone().shift(movement));
        double hx = body.getWidthX() / 2 - 1e-6;
        double hy = body.getHeight() / 2 - 1e-6;
        double hz = body.getWidthZ() / 2 - 1e-6;
        for (int x = (int) Math.floor(swept.getMinX()); x <= Math.floor(swept.getMaxX()); x++)
            for (int y = (int) Math.floor(swept.getMinY()); y <= Math.floor(swept.getMaxY()); y++)
                for (int z = (int) Math.floor(swept.getMinZ());
                        z <= Math.floor(swept.getMaxZ());
                        z++) {
                    Block block = context.world().getBlockAt(x, y, z);
                    for (BoundingBox local : block.getCollisionShape().getBoundingBoxes()) {
                        BoundingBox obstacle = local.clone().shift(x, y, z).expand(hx, hy, hz);
                        if (obstacle.contains(center)
                                || obstacle.rayTrace(center, movement, length) != null)
                            return false;
                    }
                }
        return true;
    }

    boolean smokeHides(Player viewer, Player target) {
        return smokeViews.containsKey(new ViewKey(viewer.getUniqueId(), target.getUniqueId()));
    }

    private void refreshSmoke() {
        Map<ViewKey, HiddenView> wanted = new HashMap<>();
        for (Player viewer : context.players())
            for (Player target : context.players()) {
                if (!context.active(viewer)
                        || !context.active(target)
                        || context.friendly(viewer, target)) continue;
                boolean blocked =
                        clouds.stream()
                                .anyMatch(c ->
                                        c.kind == SMOKE_GRENADE
                                                && segmentSphere(
                                                        viewer.getEyeLocation().toVector(),
                                                        target.getEyeLocation().toVector(),
                                                        c.center.toVector(),
                                                        3.5));
                if (!blocked) continue;
                ViewKey key = new ViewKey(viewer.getUniqueId(), target.getUniqueId());
                HiddenView old = smokeViews.get(key);
                if (old != null && (old.viewer != viewer || old.target != target)) restoreView(old);
                if (old == null || old.viewer != viewer || old.target != target)
                    viewer.hidePlayer(context.plugin(), target);
                wanted.put(key, new HiddenView(viewer, target));
            }
        for (var entry : smokeViews.entrySet())
            if (!wanted.containsKey(entry.getKey())) restoreView(entry.getValue());
        smokeViews.clear();
        smokeViews.putAll(wanted);
    }

    private void restoreView(HiddenView view) {
        if (!gadgets.cloakedFor(view.viewer, view.target))
            view.viewer.showPlayer(context.plugin(), view.target);
    }

    private void restoreHole(Block block) {
        Hole hole = holes.get(block);
        if (hole == null) return;
        if (gadgets.isTemporaryCover(block)) return;
        if (!block.getType().isAir()
                && block.getType() != Material.WATER
                && block.getType() != Material.LAVA) {
            holes.remove(block);
            return;
        }
        BoundingBox volume =
                new BoundingBox(
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        block.getX() + 1,
                        block.getY() + 1,
                        block.getZ() + 1);
        for (Player player : context.players())
            if (context.active(player) && player.getBoundingBox().overlaps(volume)) return;
        hole.previous.update(true, false);
        holes.remove(block);
    }

    private void restoreFlash(Flash flash) {
        flashes.remove(flash.player.getUniqueId());
        PotionEffect current = flash.player.getPotionEffect(PotionEffectType.BLINDNESS);
        long remaining = Math.max(0, flash.expires - context.tick());
        if (current != null
                && (current.getAmplifier() != 0 || current.getDuration() > remaining + 2)) return;
        flash.player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (flash.previous == null) return;
        int oldRemaining = flash.previous.getDuration() - (int) (context.tick() - flash.started);
        if (oldRemaining > 0)
            flash.player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.BLINDNESS,
                            oldRemaining,
                            flash.previous.getAmplifier(),
                            flash.previous.isAmbient(),
                            flash.previous.hasParticles(),
                            flash.previous.hasIcon()));
    }

    void forget(UUID id) {
        meshes.remove(id);
        grapples.remove(id);
        winches.remove(id);
        winches.values().removeIf(winch -> id.equals(winch.target));
        for (Device device : List.copyOf(devices)) if (device.owner.equals(id)) remove(device);
        clouds.removeIf(cloud -> cloud.owner.equals(id));
        for (var entry : List.copyOf(holes.entrySet()))
            if (entry.getValue().owner.equals(id)) {
                Hole hole = entry.getValue();
                holes.put(entry.getKey(), new Hole(id, hole.previous, context.tick()));
                restoreHole(entry.getKey());
            }
        Flash flash = flashes.get(id);
        if (flash != null) restoreFlash(flash);
        for (var entry : List.copyOf(smokeViews.entrySet()))
            if (entry.getKey().viewer.equals(id) || entry.getKey().target.equals(id)) {
                restoreView(entry.getValue());
                smokeViews.remove(entry.getKey());
            }
        refreshSmoke();
    }

    void clear() {
        for (Device device : List.copyOf(devices)) remove(device);
        clouds.clear();
        meshes.clear();
        grapples.clear();
        winches.clear();
        for (Flash flash : List.copyOf(flashes.values())) restoreFlash(flash);
        for (HiddenView view : smokeViews.values()) restoreView(view);
        smokeViews.clear();
        for (Block block : List.copyOf(holes.keySet())) restoreHole(block);
        // Session cleanup discards the arena; occupied holes must never suffocate a departing
        // player.
        holes.clear();
    }
}
