package me.petr1furious.potatominigames.games.cs;

import java.util.*;

/** Per-player loadouts, finite spawn protection and individual standings for CS deathmatch. */
final class CsDeathmatch {
    static final int RESPAWN_TICKS = 60, PROTECTION_TICKS = 60;

    private record Loadout(CsWeapon primary, CsWeapon secondary) {}

    private final Map<UUID, Loadout> loadouts = new HashMap<>();
    private final Map<UUID, Long> protection = new HashMap<>();

    private Loadout loadout(UUID id) {
        return loadouts.getOrDefault(id, new Loadout(CsWeapon.AK47, CsWeapon.USP));
    }

    void equip(CsMatch.Actor actor, long tick) {
        Loadout kit = loadout(actor.id);
        actor.primary = new CsGun(kit.primary);
        actor.secondary = new CsGun(kit.secondary);
        protect(actor.id, tick);
    }

    boolean select(CsMatch match, UUID id, CsWeapon weapon, long tick) {
        var actor = match.actor(id);
        if (actor == null || !actor.online || weapon == null) return false;
        var old = loadout(id);
        loadouts.put(
                id,
                new Loadout(
                        weapon.primary ? weapon : old.primary,
                        weapon.primary ? old.secondary : weapon));
        boolean immediate =
                actor.alive && (match.phase == CsMatch.Phase.FREEZE || protectedSpawn(id, tick));
        if (immediate) {
            CsGun gun = new CsGun(weapon);
            gun.deploy(tick);
            if (weapon.primary) actor.primary = gun;
            else actor.secondary = gun;
        }
        return immediate;
    }

    void protect(UUID id, long tick) {
        protection.put(id, tick + PROTECTION_TICKS);
    }

    boolean protectedSpawn(UUID id, long tick) {
        return tick < protection.getOrDefault(id, 0L);
    }

    void attack(UUID id) {
        protection.remove(id);
    }

    static List<CsMatch.Actor> ranking(CsMatch match) {
        return match.actors().stream()
                .filter(a -> a.online)
                .sorted(
                        Comparator.<CsMatch.Actor>comparingInt(a -> a.kills)
                                .reversed()
                                .thenComparingInt(a -> a.deaths)
                                .thenComparing(a -> a.id))
                .toList();
    }

    static int place(CsMatch match, CsMatch.Actor actor) {
        return 1
                + (int)
                        match.actors().stream()
                                .filter(a ->
                                        a.online
                                                && (a.kills > actor.kills
                                                        || a.kills == actor.kills
                                                                && a.deaths < actor.deaths))
                                .count();
    }
}
