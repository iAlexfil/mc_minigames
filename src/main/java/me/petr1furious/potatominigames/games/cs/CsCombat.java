package me.petr1furious.potatominigames.games.cs;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Server-authoritative hitscan, hit groups, recovery, movement spread and limited wallbangs. */
final class CsCombat {
    private final CsSession session;
    private final Map<UUID, Long> knifeAt = new HashMap<>(), tagged = new HashMap<>();
    private final Map<UUID, Double> punch = new HashMap<>();

    CsCombat(CsSession session) {
        this.session = session;
    }

    void tag(Player p, boolean armoured) {
        tagged.put(p.getUniqueId(), session.now() + 10);
        punch.merge(p.getUniqueId(), armoured ? .15 : 2.0, (a, b) -> Math.min(6, a + b));
    }

    void right(Player p) {
        if (!session.canAct(p)) return;
        var gun = session.gun(p);
        if (gun == null) return;
        if (gun.weapon.automatic()) gun.heldUntil = session.now() + 6;
        fire(p, gun);
    }

    void left(Player p) {
        if (!session.canAct(p)) return;
        CsGun gun = session.gun(p);
        if (gun != null && gun.weapon == CsWeapon.AWP) {
            gun.scoped = !gun.scoped;
            if (gun.scoped) p.startUsingItem(EquipmentSlot.HAND);
            else p.clearActiveItem();
            p.sendActionBar(CsItems.tr(gun.scoped ? "scope.on" : "scope.off"));
        }
    }

    void switchSlot(Player p, int previous, int next) {
        var a = session.actor(p);
        if (a == null) return;
        var old = a.gun(previous);
        if (old != null) old.switchAway();
        p.clearActiveItem();
        var gun = a.gun(next);
        if (gun != null) gun.deploy(session.now());
    }

    void reload(Player p) {
        var gun = session.gun(p);
        if (session.canAct(p) && gun != null && gun.reload(session.now())) {
            p.clearActiveItem();
            gun.scoped = false;
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, .5f, 1.4f);
        }
    }

    void tick() {
        for (Player p : session.players()) {
            var a = session.actor(p);
            if (a == null) continue;
            for (CsGun gun : new CsGun[] {a.primary, a.secondary})
                if (gun != null) gun.tick(session.now());
            CsGun gun = session.gun(p);
            if (session.canAct(p)
                    && !session.shopOpen(p)
                    && gun != null
                    && gun.weapon.automatic()
                    && session.now() < gun.heldUntil) fire(p, gun);
            punch.computeIfPresent(p.getUniqueId(), (id, n) -> n < .02 ? null : n * .85);
            if (session.canAct(p)) {
                double mobility = gun == null ? 1 : gun.weapon.mobility;
                if (gun != null && gun.scoped) mobility *= .45;
                if (session.now() < tagged.getOrDefault(p.getUniqueId(), 0L)) mobility *= .45;
                p.setWalkSpeed((float) (.26 * mobility));
                if (p.isSprinting()) p.setSprinting(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void fire(Player p, CsGun gun) {
        if (!session.canAct(p) || session.shopOpen(p) || !gun.fire(session.now())) {
            if (gun.magazine == 0) reload(p);
            return;
        }
        session.cancelChannel(p.getUniqueId());
        var w = gun.weapon;
        Location eye = p.getEyeLocation();
        double spread = w.spread;
        if (w == CsWeapon.AWP && gun.scoped) spread = .0008;
        if (p.isSneaking()) spread *= .65;
        if (p.getVelocity().clone().setY(0).lengthSquared() > .01)
            spread += w == CsWeapon.AWP ? .14 : .045;
        if (!p.isOnGround()) spread += .10;
        double recoilPitch =
                w.primary && w != CsWeapon.AWP && w != CsWeapon.NOVA
                        ? Math.min(5.5, (gun.burst - 1) * .35)
                        : 0;
        double recoilYaw =
                w.primary
                        ? Math.sin((gun.burst - 1) * .72) * Math.min(2.3, (gun.burst - 1) * .12)
                        : 0;
        Location aim = eye.clone();
        aim.setPitch(
                (float)
                        Math.max(
                                -89,
                                eye.getPitch()
                                        - recoilPitch
                                        - punch.getOrDefault(p.getUniqueId(), 0.0)));
        aim.setYaw((float) (eye.getYaw() + recoilYaw));
        Map<HitGroup, Impact> hits = new LinkedHashMap<>();
        for (int pellet = 0; pellet < w.pellets; pellet++) {
            Vector direction = spread(aim.getDirection(), spread);
            Impact hit = trace(p, eye, direction, w == CsWeapon.AWP ? 160 : 100, w);
            if (pellet == 0) tracer(eye, direction, hit == null ? 100 : hit.distance);
            if (hit != null)
                hits.merge(
                        new HitGroup(hit.target.getUniqueId(), hit.head, hit.leg),
                        hit,
                        (a, b) ->
                                new Impact(
                                        a.target,
                                        a.damage + b.damage,
                                        a.head || b.head,
                                        a.leg && b.leg,
                                        Math.min(a.distance, b.distance)));
        }
        session.world()
                .playSound(
                        p.getLocation(),
                        w == CsWeapon.AWP
                                ? Sound.ENTITY_FIREWORK_ROCKET_BLAST
                                : Sound.ENTITY_FIREWORK_ROCKET_SHOOT,
                        w == CsWeapon.USP ? .30f : .85f,
                        w == CsWeapon.AWP ? .6f : w == CsWeapon.NOVA ? .8f : 1.6f);
        for (Impact hit : hits.values())
            session.hurt(hit.target, p.getUniqueId(), hit.damage, hit.head, hit.leg, w, false);
        if (!hits.isEmpty()) p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, .5f, 1.2f);
        p.sendActionBar(
                CsItems.tr(
                        gun.reloadAt >= 0 ? "hud.reloading" : "hud.ammo",
                        gun.magazine,
                        gun.reserve));
        if (gun.magazine == 0) reload(p);
    }

    private record HitGroup(UUID target, boolean head, boolean leg) {}

    private record Impact(
            Player target, double damage, boolean head, boolean leg, double distance) {}

    private Impact trace(
            Player shooter, Location origin, Vector direction, double distance, CsWeapon weapon) {
        Location at = origin.clone();
        double travelled = 0, scale = 1;
        int walls = 0;
        while (travelled < distance) {
            RayTraceResult hit =
                    session.world()
                            .rayTrace(
                                    at,
                                    direction,
                                    distance - travelled,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    .025,
                                    e ->
                                            e instanceof Player target
                                                    && !target.getUniqueId()
                                                            .equals(shooter.getUniqueId())
                                                    && session.active(target));
            if (hit == null) return null;
            double segment = at.toVector().distance(hit.getHitPosition());
            travelled += segment;
            if (hit.getHitEntity() instanceof Player target) {
                double y = hit.getHitPosition().getY(),
                        height = target.getHeight(),
                        feet = target.getLocation().getY();
                boolean head = y > feet + height - .38, leg = y < feet + .58;
                double multiplier = head ? 4 : leg ? .75 : y < feet + 1.0 ? 1.25 : 1;
                double falloff =
                        Math.pow(
                                weapon == CsWeapon.MP9 || weapon == CsWeapon.MAC10 ? .87 : .98,
                                travelled / 16);
                return new Impact(
                        target, weapon.damage * multiplier * scale * falloff, head, leg, travelled);
            }
            Block block = hit.getHitBlock();
            if (block == null) return null;
            String material = block.getType().name();
            boolean glass = material.contains("GLASS");
            if (glass) session.arena().breakGlass(block);
            double max =
                    glass
                            ? 1.1
                            : material.contains("PLANK")
                                            || material.contains("WOOD")
                                            || material.contains("TRAPDOOR")
                                            || material.contains("FENCE")
                                    ? 1.25
                                    : material.contains("LEAVES")
                                            ? 2.5
                                            : weapon == CsWeapon.AWP || weapon == CsWeapon.AK47
                                                    ? .65
                                                    : 0;
            if (walls++ >= 2 || max == 0) return null;
            Location exit =
                    new Location(
                            session.world(),
                            hit.getHitPosition().getX(),
                            hit.getHitPosition().getY(),
                            hit.getHitPosition().getZ());
            double thickness = 0;
            do {
                exit.add(direction.clone().multiply(.1));
                thickness += .1;
            } while (thickness < max && !exit.getBlock().isPassable());
            if (!exit.getBlock().isPassable()) return null;
            scale *= glass ? .9 : .5;
            scale *= Math.max(.25, 1 - thickness * .4);
            at = exit.add(direction.clone().multiply(.05));
            travelled += thickness + .05;
        }
        return null;
    }

    private Vector spread(Vector forward, double amount) {
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < .0001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        double angle = session.random().nextDouble() * Math.PI * 2,
                r = Math.sqrt(session.random().nextDouble()) * amount;
        return forward.clone()
                .add(right.multiply(Math.cos(angle) * r))
                .add(up.multiply(Math.sin(angle) * r))
                .normalize();
    }

    private void tracer(Location from, Vector direction, double distance) {
        for (double d = .5; d < Math.min(distance, 32); d += 4)
            session.world()
                    .spawnParticle(
                            Particle.CRIT,
                            from.clone().add(direction.clone().multiply(d)),
                            1,
                            0,
                            0,
                            0,
                            0);
    }

    void knife(Player p, boolean heavy, Player expected) {
        if (!session.canAct(p)
                || p.getInventory().getHeldItemSlot() != 2
                || session.now() < knifeAt.getOrDefault(p.getUniqueId(), 0L)) return;
        knifeAt.put(p.getUniqueId(), session.now() + (heavy ? 20 : 10));
        var hit =
                session.world()
                        .rayTrace(
                                p.getEyeLocation(),
                                p.getEyeLocation().getDirection(),
                                heavy ? 2.7 : 3,
                                FluidCollisionMode.NEVER,
                                true,
                                .12,
                                e ->
                                        e instanceof Player target
                                                && target != p
                                                && session.active(target));
        if (hit == null
                || !(hit.getHitEntity() instanceof Player target)
                || expected != null && target != expected) return;
        Vector to =
                target.getLocation()
                        .toVector()
                        .subtract(p.getLocation().toVector())
                        .setY(0)
                        .normalize();
        boolean back = target.getEyeLocation().getDirection().setY(0).normalize().dot(to) > .55;
        session.hurt(
                target,
                p.getUniqueId(),
                back ? (heavy ? 180 : 90) : (heavy ? 65 : 40),
                false,
                false,
                null,
                false);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, .6f, 1);
    }

    void clear() {
        knifeAt.clear();
        tagged.clear();
        punch.clear();
    }
}
