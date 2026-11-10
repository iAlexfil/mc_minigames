package me.petr1furious.potatominigames.games.finals;

import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/** Server-independent weapon roster and ammunition/cadence regressions. */
public final class FinalsWeaponsCheck {
    private static int checks;

    public static void main(String[] args) {
        classAndWeaponRoster();
        cadenceAndFiniteMagazine();
        reloadDeadlineAndCancellation();
        heldFireExpires();
        damageProfiles();
        shieldAndBackstabDirections();
        System.out.println("FinalsWeaponsCheck: " + checks + " checks passed");
    }

    private static void classAndWeaponRoster() {
        check(FinalsClass.parse("S") == FinalsClass.LIGHT, "S selects Light");
        check(FinalsClass.parse("m") == FinalsClass.MEDIUM, "M selects Medium");
        check(FinalsClass.parse("L") == FinalsClass.HEAVY, "L selects Heavy");
        check(FinalsClass.parse("heavy") == FinalsClass.HEAVY, "stable class IDs parse");
        check(FinalsClass.parse("invalid") == null, "invalid class is rejected");
        check(
                FinalsClass.LIGHT.health() == 15
                        && FinalsClass.MEDIUM.health() == 25
                        && FinalsClass.HEAVY.health() == 35,
                "class health is distinct");
        check(
                FinalsClass.LIGHT.walkSpeed() > FinalsClass.MEDIUM.walkSpeed()
                        && FinalsClass.MEDIUM.walkSpeed() > FinalsClass.HEAVY.walkSpeed(),
                "class speed trades off health");
        check(FinalsWeapon.values().length == 15, "exactly fifteen approved weapons");
        Set<String> names = new HashSet<>();
        for (FinalsClass type : FinalsClass.values()) {
            check(FinalsWeapon.byClass(type).size() == 5, "five weapons per class");
            for (FinalsWeapon weapon : FinalsWeapon.byClass(type)) {
                check(
                        weapon.playerClass() == type,
                        "class catalog cannot leak another class weapon");
                check(FinalsWeapon.parse(weapon.id()) == weapon, "weapon IDs round trip");
                check(names.add(weapon.id()), "weapon IDs are unique");
            }
        }
        check(
                names.equals(
                        Set.of(
                                "m11",
                                "xp54",
                                "sr84",
                                "sword",
                                "dagger",
                                "akm",
                                "fcar",
                                "model1887",
                                "riotshield",
                                "dualblades",
                                "m60",
                                "lewisgun",
                                "sa1216",
                                "sledgehammer",
                                "spear")),
                "the roster matches the approved original weapons");
        check(FinalsWeapon.parse("XP-54") == FinalsWeapon.XP54, "display punctuation accepted");
        check(
                FinalsWeapon.parse("Dual Blades") == FinalsWeapon.DUAL_BLADES,
                "spaced name accepted");
        check(FinalsWeapon.parse("bow") == null, "noncatalog weapons rejected");
    }

    private static void cadenceAndFiniteMagazine() {
        FinalsGunState gun = new FinalsGunState(3, 4, 10);
        check(gun.fire(100), "fresh magazine can fire");
        check(gun.ammo() == 2, "one shot consumes one round");
        check(!gun.fire(100) && !gun.fire(103), "cadence rejects duplicate and early events");
        check(gun.fire(104) && gun.fire(108), "deadline permits next shots");
        check(gun.ammo() == 0 && !gun.fire(112), "empty magazine cannot fire");
        check(gun.ammo() == 0, "ammo never becomes negative");
    }

    private static void reloadDeadlineAndCancellation() {
        FinalsGunState gun = new FinalsGunState(3, 4, 10);
        check(!gun.reload(0), "full magazine cannot reload");
        gun.fire(0);
        check(gun.reload(1), "partly empty magazine can reload");
        check(!gun.reload(2), "repeat reload cannot extend deadline");
        check(!gun.fire(10) && gun.ammo() == 2, "reload blocks fire until deadline");
        gun.advance(11);
        check(!gun.reloading() && gun.ammo() == 3, "reload completes at exact deadline");
        check(gun.fire(11), "completed reload can fire immediately");
        gun.reload(12);
        gun.cancelReload();
        gun.advance(1000);
        check(gun.ammo() == 2 && !gun.reloading(), "cancelled reload cannot refill later");
        check(gun.reload(1001), "cancelled reload may restart");
        check(gun.reloadRemaining(1003) == 8, "HUD reload time follows deadline");
        gun.close();
        gun.advance(2000);
        check(
                !gun.fire(2000) && !gun.reload(2000),
                "discarded life cannot fire or resurrect reload");
    }

    private static void heldFireExpires() {
        FinalsGunState gun = new FinalsGunState(20, 2, 20);
        gun.hold(20, 6);
        check(gun.held(20) && gun.held(25), "automatic fire uses a short trigger window");
        check(!gun.held(26), "trigger expires without fresh input");
        gun.hold(30, 6);
        gun.stopHolding();
        check(!gun.held(30), "switching away immediately stops automatic fire");
        gun.hold(40, 6);
        gun.close();
        check(!gun.held(40), "death/cleanup cannot leave automatic fire active");
    }

    private static void damageProfiles() {
        check(
                FinalsWeapon.SR84.damageAt(5, true) > FinalsWeapon.SR84.damageAt(5, false),
                "sniper rewards headshots");
        check(
                FinalsWeapon.MODEL_1887.pellets() > 1 && FinalsWeapon.SA1216.pellets() > 1,
                "both shotguns fire multiple pellets");
        check(
                FinalsWeapon.MODEL_1887.damageAt(20, true)
                        == FinalsWeapon.MODEL_1887.damageAt(20, false),
                "shotgun pellets have no accidental headshot multiplier");
        check(
                FinalsWeapon.M11.damageAt(24, false) < FinalsWeapon.M11.damageAt(1, false),
                "SMG damage falls with distance");
        check(FinalsWeapon.SPEAR.range() > FinalsWeapon.SWORD.range(), "spear extends melee reach");
        check(
                FinalsWeapon.SLEDGEHAMMER.cadenceTicks() > FinalsWeapon.SWORD.cadenceTicks(),
                "heavy hammer swings slower than sword");
        check(
                !FinalsWeapon.DAGGER.isGun() && FinalsWeapon.M60.automatic(),
                "melee and automatic profiles differ");
    }

    private static void shieldAndBackstabDirections() {
        Vector facing = new Vector(0, 0, 1);
        Vector front = new Vector(0, 1, 3);
        Vector rear = new Vector(0, -1, -3);
        Vector side = new Vector(3, 0, 0);
        check(FinalsWeapons.frontal(facing, front), "shield reduces a frontal hit");
        check(!FinalsWeapons.frontal(facing, rear), "shield does not reduce a rear hit");
        check(!FinalsWeapons.frontal(facing, side), "shield does not reduce a side hit");
        check(FinalsWeapons.behind(facing, rear), "dagger backstab detects an attacker behind");
        check(
                !FinalsWeapons.behind(facing, front) && !FinalsWeapons.behind(facing, side),
                "frontal and side dagger hits do not become backstabs");
        check(
                !FinalsWeapons.frontal(facing, new Vector())
                        && !FinalsWeapons.behind(facing, new Vector()),
                "overlapping contestants receive no undefined directional bonus");
        check(
                facing.equals(new Vector(0, 0, 1)) && front.equals(new Vector(0, 1, 3)),
                "direction checks do not mutate player vectors");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
