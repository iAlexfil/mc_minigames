package me.petr1furious.potatominigames.games.finals;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Match-local rechargeable resources. Deadlines are measured in session ticks. */
final class FinalsGadgetState {
    enum Kind {
        EVASIVE_DASH(3, 90),
        FRAG_GRENADE(2, 240),
        SONAR_GRENADE(2, 200),
        VANISHING_BOMB(1, 240),
        HEALING_BEAM(1, 120),
        JUMP_PAD(1, 300),
        DEFIBRILLATOR(1, 300),
        CHARGE_N_SLAM(1, 180),
        RPG7(1, 320),
        DOME_SHIELD(1, 400),
        BARRICADE(2, 240);

        final int capacity;
        final long recharge;

        Kind(int capacity, long recharge) {
            this.capacity = capacity;
            this.recharge = recharge;
        }
    }

    private static final class Resource {
        int charges;
        long nextCharge;
        long nextUse;

        Resource(Kind kind) {
            charges = kind.capacity;
        }
    }

    private final Map<UUID, EnumMap<Kind, Resource>> resources = new HashMap<>();

    private Resource resource(UUID player, Kind kind, long now) {
        Resource resource =
                resources
                        .computeIfAbsent(player, ignored -> new EnumMap<>(Kind.class))
                        .computeIfAbsent(kind, Resource::new);
        if (resource.charges < kind.capacity && now >= resource.nextCharge) {
            long added = 1 + (now - resource.nextCharge) / kind.recharge;
            resource.charges = (int) Math.min(kind.capacity, resource.charges + added);
            resource.nextCharge =
                    resource.charges == kind.capacity
                            ? 0
                            : resource.nextCharge + added * kind.recharge;
        }
        return resource;
    }

    int charges(UUID player, Kind kind, long now) {
        return resource(player, kind, now).charges;
    }

    long remaining(UUID player, Kind kind, long now) {
        Resource resource = resource(player, kind, now);
        return resource.charges == kind.capacity ? 0 : Math.max(0, resource.nextCharge - now);
    }

    boolean ready(UUID player, Kind kind, long now) {
        Resource resource = resource(player, kind, now);
        return resource.charges > 0 && now >= resource.nextUse;
    }

    boolean use(UUID player, Kind kind, long now) {
        Resource resource = resource(player, kind, now);
        if (resource.charges == 0 || now < resource.nextUse) return false;
        if (resource.charges == kind.capacity) resource.nextCharge = now + kind.recharge;
        resource.charges--;
        resource.nextUse = now + 6;
        return true;
    }

    void forget(UUID player) {
        resources.remove(player);
    }

    void clear() {
        resources.clear();
    }

    static boolean crossesDome(
            double targetX,
            double targetY,
            double targetZ,
            double sourceX,
            double sourceY,
            double sourceZ,
            double x,
            double y,
            double z,
            double radius) {
        double tx = targetX - x, ty = targetY - y, tz = targetZ - z;
        double sx = sourceX - x, sy = sourceY - y, sz = sourceZ - z;
        return tx * tx + ty * ty + tz * tz <= radius * radius
                && sx * sx + sy * sy + sz * sz > radius * radius;
    }

    static boolean shouldRestoreGlow(boolean originalGlow, boolean currentGlow, boolean carrier) {
        return !originalGlow && currentGlow && !carrier;
    }
}
