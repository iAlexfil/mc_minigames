package me.petr1furious.potatominigames.games.cs;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

/** Bouncing timed utility; smoke obscures entity visibility without blocking bullets. */
final class CsGrenades {
    private static final class Flight {
        final UUID owner;
        final CsMatch.Utility kind;
        final Item item;
        final long fuse;
        final CsGrenadePhysics.ThrowMode mode;
        Location at;
        Vector velocity;

        Flight(
                UUID owner,
                CsMatch.Utility kind,
                Item item,
                Location at,
                Vector velocity,
                long fuse,
                CsGrenadePhysics.ThrowMode mode) {
            this.owner = owner;
            this.kind = kind;
            this.item = item;
            this.at = at;
            this.velocity = velocity;
            this.fuse = fuse;
            this.mode = mode;
        }
    }

    private record Zone(Location at, UUID owner, long end) {}

    private record Hidden(UUID viewer, UUID target) {}

    private final CsSession session;
    private final CsFireFloor fireFloor;
    private final List<Flight> flights = new ArrayList<>();
    private final List<Zone> smoke = new ArrayList<>(), fire = new ArrayList<>();
    private final Map<UUID, Long> flashed = new HashMap<>();
    private final Set<UUID> smokeDarkness = new HashSet<>();
    private final Set<Hidden> hidden = new HashSet<>();
    private final Map<UUID, Long> throwAt = new HashMap<>();

    CsGrenades(CsSession session) {
        this.session = session;
        fireFloor = new CsFireFloor(session.world());
    }

    @SuppressWarnings("deprecation")
    boolean toss(Player p, CsMatch.Utility kind, CsGrenadePhysics.ThrowMode mode) {
        var a = session.actor(p);
        if (flights.size() >= 40
                || session.now() < throwAt.getOrDefault(p.getUniqueId(), 0L)
                || !session.canAct(p)
                || !session.match().consume(a, kind)) return false;
        throwAt.put(p.getUniqueId(), session.now() + 16);
        // Start at native throwable release height; a forward offset could skip thin cover.
        Location at = p.getEyeLocation().subtract(0, .1, 0);
        Item item =
                session.world()
                        .dropItem(at, CsItems.physical(new ItemStack(CsItems.material(kind))));
        item.setGravity(false);
        item.setInvulnerable(true);
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setVelocity(new Vector());
        flights.add(
                new Flight(
                        p.getUniqueId(),
                        kind,
                        item,
                        at,
                        CsGrenadePhysics.launch(
                                p.getEyeLocation().getDirection(),
                                p.getVelocity(),
                                p.isOnGround(),
                                mode),
                        session.now() + 30,
                        mode));
        return true;
    }

    void tick() {
        for (Flight f : List.copyOf(flights)) {
            boolean ground = false;
            CsGrenadePhysics.airTick(f.velocity, f.mode);
            for (int step = 0; step < 4; step++) {
                Vector move = f.velocity.clone().multiply(.25);
                double length = move.length();
                if (length < .0001) break;
                var hit =
                        session.world()
                                .rayTraceBlocks(
                                        f.at,
                                        move.clone().normalize(),
                                        length,
                                        FluidCollisionMode.NEVER,
                                        true);
                if (hit != null && hit.getHitBlockFace() != null) {
                    Vector normal = hit.getHitBlockFace().getDirection();
                    f.at =
                            new Location(
                                            session.world(),
                                            hit.getHitPosition().getX(),
                                            hit.getHitPosition().getY(),
                                            hit.getHitPosition().getZ())
                                    .add(normal.clone().multiply(.12));
                    f.velocity
                            .subtract(normal.clone().multiply(2 * f.velocity.dot(normal)))
                            .multiply(.45);
                    if (normal.getY() > .5) {
                        ground = true;
                        if (Math.abs(f.velocity.getY()) < .1) f.velocity.setY(0);
                    }
                    session.world().playSound(f.at, Sound.BLOCK_STONE_HIT, .25f, 1.5f);
                } else f.at.add(move);
            }
            f.item.teleport(f.at);
            if (session.now() >= f.fuse || f.kind == CsMatch.Utility.FIRE && ground) {
                detonate(f);
                f.item.remove();
                flights.remove(f);
            } else if (!session.arena().inside(f.at)) {
                f.item.remove();
                flights.remove(f);
            }
        }
        smoke.removeIf(z -> session.now() >= z.end);
        fire.removeIf(z ->
                session.now() >= z.end
                        || smoke.stream().anyMatch(s -> s.at.distanceSquared(z.at) < 49));
        fireFloor.update(fire.stream().map(Zone::at).toList(), session.viewers(), session.now());
        if (session.now() % 5 == 0) {
            if (session.now() % 10 == 0) smokeClouds();
            smokeDarkness();
            for (Zone z : fire) {
                for (Player target : session.players())
                    if (session.active(target)
                            && target.getLocation().distanceSquared(z.at) < 16
                            && line(z.at.clone().add(0, .2, 0), target.getLocation().add(0, .3, 0)))
                        session.hurt(
                                target, z.owner, 10, false, true, null, CsMatch.KillCause.FIRE);
            }
            visibility();
        }
        flashed.entrySet().removeIf(e -> e.getValue() <= session.now());
    }

    /** Prioritize the six nearest clouds; forced particles remain visible along long sightlines. */
    private void smokeClouds() {
        var dust = new Particle.DustOptions(Color.fromRGB(110, 115, 120), 4);
        for (Player viewer : session.players()) {
            Location eye = viewer.getEyeLocation();
            for (Zone z :
                    smoke.stream()
                            .filter(zone ->
                                    eye.getWorld().equals(zone.at.getWorld())
                                            && eye.distanceSquared(zone.at) < 256 * 256)
                            .sorted(
                                    Comparator.comparingDouble(
                                            zone -> eye.distanceSquared(zone.at)))
                            .limit(6)
                            .toList()) {
                Location center = z.at.clone().add(0, 1, 0);
                // Large dust fills the cloud with one batched packet instead of a packet per puff.
                viewer.spawnParticle(Particle.DUST, center, 110, 1.3, 1.3, 1.3, 0, dust, true);
                viewer.spawnParticle(
                        Particle.CAMPFIRE_SIGNAL_SMOKE, center, 12, 2, 1, 2, .005, null, true);
            }
        }
    }

    private void smokeDarkness() {
        for (Player p : session.players()) {
            UUID id = p.getUniqueId();
            boolean inside =
                    session.active(p)
                            && smoke.stream()
                                    .anyMatch(z ->
                                            p.getEyeLocation()
                                                            .distanceSquared(
                                                                    z.at.clone().add(0, 1, 0))
                                                    < 3.7 * 3.7);
            if (inside) {
                smokeDarkness.add(id);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false));
            } else if (smokeDarkness.remove(id)) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
                int remainingFlash =
                        (int) Math.max(0, flashed.getOrDefault(id, 0L) - session.now());
                if (remainingFlash > 0)
                    p.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.DARKNESS, remainingFlash, 0, false, false));
            }
        }
    }

    private void detonate(Flight f) {
        Location at = f.at;
        switch (f.kind) {
            case SMOKE -> {
                if (smoke.size() >= 20) smoke.removeFirst();
                smoke.add(new Zone(at.clone(), f.owner, session.now() + 360));
                session.world().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1, .7f);
            }
            case FIRE -> {
                if (smoke.stream().noneMatch(z -> z.at.distanceSquared(at) < 49))
                    fire.add(new Zone(at.clone(), f.owner, session.now() + 140));
            }
            case HE -> {
                session.world().spawnParticle(Particle.EXPLOSION, at, 1);
                session.world().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
                for (Player p : session.players())
                    if (session.active(p)) {
                        double distance = p.getLocation().distance(at);
                        if (distance < 10
                                && line(at.clone().add(0, .2, 0), p.getLocation().add(0, 1, 0)))
                            session.hurt(
                                    p,
                                    f.owner,
                                    98 * Math.pow(1 - distance / 10, 1.4),
                                    false,
                                    false,
                                    null,
                                    CsMatch.KillCause.HE);
                    }
            }
            case FLASH -> {
                session.world().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1, 1.6f);
                session.world().spawnParticle(Particle.FLASH, at, 1, Color.WHITE);
                for (Player p : session.players())
                    if (session.active(p)
                            && p.getEyeLocation().distanceSquared(at) < 576
                            && line(at, p.getEyeLocation())) {
                        Vector to = at.toVector().subtract(p.getEyeLocation().toVector());
                        double distance = to.length();
                        double dot =
                                distance < .01
                                        ? 1
                                        : p.getEyeLocation().getDirection().dot(to.normalize());
                        int ticks =
                                (int) (100 * (1 - distance / 24) * (.2 + .8 * Math.max(0, dot)));
                        if (ticks > 5) {
                            flashed.merge(p.getUniqueId(), session.now() + ticks, Math::max);
                            p.addPotionEffect(
                                    new PotionEffect(
                                            PotionEffectType.BLINDNESS, ticks, 0, false, false));
                            p.addPotionEffect(
                                    new PotionEffect(
                                            PotionEffectType.DARKNESS, ticks, 0, false, false));
                            p.sendActionBar(CsItems.tr("flash"));
                        }
                    }
            }
        }
    }

    private boolean line(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        return delta.lengthSquared() < .01
                || session.world()
                                .rayTraceBlocks(
                                        from,
                                        delta.clone().normalize(),
                                        delta.length(),
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null;
    }

    boolean obscured(Player viewer, Player target) {
        return flashed.getOrDefault(viewer.getUniqueId(), 0L) > session.now()
                || smoke.stream()
                        .anyMatch(z ->
                                intersects(
                                        viewer.getEyeLocation(),
                                        target.getEyeLocation(),
                                        z.at.clone().add(0, 1, 0),
                                        3.7));
    }

    private void visibility() {
        Set<Hidden> required = new HashSet<>();
        for (Player viewer : session.players())
            for (Player target : session.players()) {
                if (viewer == target || !session.active(target)) continue;
                boolean dead =
                        !session.active(viewer)
                                && (session.deathmatch()
                                        || session.actor(viewer).team
                                                != session.actor(target).team);
                boolean flash = flashed.getOrDefault(viewer.getUniqueId(), 0L) > session.now();
                boolean obscured =
                        smoke.stream()
                                .anyMatch(z ->
                                        intersects(
                                                viewer.getEyeLocation(),
                                                target.getEyeLocation(),
                                                z.at.clone().add(0, 1, 0),
                                                3.7));
                Hidden pair = new Hidden(viewer.getUniqueId(), target.getUniqueId());
                if (dead || flash || obscured) {
                    required.add(pair);
                    if (hidden.add(pair)) viewer.hideEntity(session.plugin(), target);
                }
            }
        for (Hidden pair : List.copyOf(hidden))
            if (!required.contains(pair)) {
                Player viewer = session.player(pair.viewer), target = session.player(pair.target);
                if (viewer != null && target != null) viewer.showEntity(session.plugin(), target);
                hidden.remove(pair);
            }
    }

    static boolean intersects(Location a, Location b, Location center, double radius) {
        Vector start = a.toVector(),
                delta = b.toVector().subtract(start),
                offset = center.toVector().subtract(start);
        double length = delta.lengthSquared();
        double t = length < .001 ? 0 : Math.clamp(offset.dot(delta) / length, 0, 1);
        return start.add(delta.multiply(t)).distanceSquared(center.toVector()) < radius * radius;
    }

    void release(UUID id) {
        fireFloor.release(id);
        for (Hidden pair : List.copyOf(hidden))
            if (pair.viewer.equals(id) || pair.target.equals(id)) {
                Player viewer = org.bukkit.Bukkit.getPlayer(pair.viewer),
                        target = org.bukkit.Bukkit.getPlayer(pair.target);
                if (viewer != null && target != null) viewer.showEntity(session.plugin(), target);
                hidden.remove(pair);
            }
        flashed.remove(id);
        clearEffects(id);
        smokeDarkness.remove(id);
    }

    private void clearEffects(UUID id) {
        Player p = org.bukkit.Bukkit.getPlayer(id);
        if (p != null) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    void clear() {
        fireFloor.clear();
        throwAt.clear();
        flights.forEach(f -> f.item.remove());
        flights.clear();
        smoke.clear();
        fire.clear();
        for (Hidden pair : hidden) {
            Player a = org.bukkit.Bukkit.getPlayer(pair.viewer),
                    b = org.bukkit.Bukkit.getPlayer(pair.target);
            if (a != null && b != null) a.showEntity(session.plugin(), b);
        }
        hidden.clear();
        Set<UUID> affected = new HashSet<>(flashed.keySet());
        affected.addAll(smokeDarkness);
        affected.forEach(this::clearEffects);
        smokeDarkness.clear();
        flashed.clear();
    }
}
