package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        BARRICADE(2, 240),
        CLOAKING_DEVICE(1, 280),
        GRAPPLING_HOOK(1, 160),
        GUARDIAN_TURRET(1, 600),
        DEMATERIALIZER(3, 300),
        MESH_SHIELD(1, 300),
        WINCH_CLAW(1, 240),
        SMOKE_GRENADE(2, 280),
        FLASHBANG(2, 220),
        GAS_GRENADE(1, 300),
        PYRO_GRENADE(1, 300),
        GOO_GRENADE(2, 240),
        BREACH_CHARGE(2, 300),
        EXPLOSIVE_MINE(2, 300),
        GAS_MINE(2, 300),
        PYRO_MINE(2, 300),
        APS_TURRET(1, 500);

        final int capacity;
        final long recharge;

        Kind(int capacity, long recharge) {
            this.capacity = capacity;
            this.recharge = recharge;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        Component display() {
            return I18n.tr("finals.gadget." + id() + ".name");
        }

        static Kind parse(String id) {
            if (id == null) return null;
            try {
                return valueOf(id.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        boolean specialization() {
            return switch (this) {
                case EVASIVE_DASH,
                        CLOAKING_DEVICE,
                        GRAPPLING_HOOK,
                        HEALING_BEAM,
                        GUARDIAN_TURRET,
                        DEMATERIALIZER,
                        CHARGE_N_SLAM,
                        MESH_SHIELD,
                        WINCH_CLAW ->
                        true;
                default -> false;
            };
        }

        boolean allowed(FinalsClass role) {
            if (role == null) return false;
            return switch (this) {
                case EVASIVE_DASH, CLOAKING_DEVICE, GRAPPLING_HOOK, VANISHING_BOMB, BREACH_CHARGE ->
                        role == FinalsClass.LIGHT;
                case HEALING_BEAM,
                        GUARDIAN_TURRET,
                        DEMATERIALIZER,
                        JUMP_PAD,
                        DEFIBRILLATOR,
                        GAS_MINE,
                        APS_TURRET ->
                        role == FinalsClass.MEDIUM;
                case CHARGE_N_SLAM,
                        MESH_SHIELD,
                        WINCH_CLAW,
                        RPG7,
                        DOME_SHIELD,
                        BARRICADE,
                        PYRO_MINE ->
                        role == FinalsClass.HEAVY;
                case EXPLOSIVE_MINE -> role != FinalsClass.LIGHT;
                case SONAR_GRENADE -> role != FinalsClass.HEAVY;
                default -> true;
            };
        }

        static List<Kind> specializations(FinalsClass role) {
            return Arrays.stream(values())
                    .filter(kind -> kind.specialization() && kind.allowed(role))
                    .toList();
        }

        static List<Kind> gadgets(FinalsClass role) {
            return Arrays.stream(values())
                    .filter(kind -> !kind.specialization() && kind.allowed(role))
                    .toList();
        }

        static Kind defaultSpecialization(FinalsClass role) {
            return switch (role) {
                case LIGHT -> EVASIVE_DASH;
                case MEDIUM -> HEALING_BEAM;
                case HEAVY -> CHARGE_N_SLAM;
            };
        }

        static List<Kind> defaultGadgets(FinalsClass role) {
            return switch (role) {
                case LIGHT -> List.of(FRAG_GRENADE, SONAR_GRENADE, VANISHING_BOMB);
                case MEDIUM -> List.of(FRAG_GRENADE, JUMP_PAD, DEFIBRILLATOR);
                case HEAVY -> List.of(RPG7, DOME_SHIELD, BARRICADE);
            };
        }

        Material material() {
            return switch (this) {
                case EVASIVE_DASH -> Material.FEATHER;
                case FRAG_GRENADE -> Material.FIREWORK_STAR;
                case SONAR_GRENADE -> Material.ENDER_EYE;
                case VANISHING_BOMB -> Material.INK_SAC;
                case HEALING_BEAM -> Material.BLAZE_ROD;
                case JUMP_PAD -> Material.SLIME_BLOCK;
                case DEFIBRILLATOR -> Material.SHEARS;
                case CHARGE_N_SLAM -> Material.RAVAGER_SPAWN_EGG;
                case RPG7 -> Material.FIRE_CHARGE;
                case DOME_SHIELD -> Material.HEART_OF_THE_SEA;
                case BARRICADE -> Material.IRON_BARS;
                case CLOAKING_DEVICE -> Material.ENDER_PEARL;
                case GRAPPLING_HOOK -> Material.FISHING_ROD;
                case GUARDIAN_TURRET -> Material.DISPENSER;
                case DEMATERIALIZER -> Material.ECHO_SHARD;
                case MESH_SHIELD -> Material.SHIELD;
                case WINCH_CLAW -> Material.TRIPWIRE_HOOK;
                case SMOKE_GRENADE -> Material.GRAY_DYE;
                case FLASHBANG -> Material.GLOWSTONE_DUST;
                case GAS_GRENADE -> Material.LIME_DYE;
                case PYRO_GRENADE -> Material.BLAZE_POWDER;
                case GOO_GRENADE -> Material.SLIME_BALL;
                case BREACH_CHARGE -> Material.TNT;
                case EXPLOSIVE_MINE -> Material.HEAVY_WEIGHTED_PRESSURE_PLATE;
                case GAS_MINE -> Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
                case PYRO_MINE -> Material.MAGMA_CREAM;
                case APS_TURRET -> Material.OBSERVER;
            };
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
