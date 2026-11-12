package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.games.finals.FinalsGadgetState.Kind;

import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.*;
import java.util.*;

/** Personal loadouts must remain legal, immutable and persistent across weapon changes. */
public final class FinalsLoadoutsCheck {
    private static int checks;
    private static Constructor<?> constructor;

    public static void main(String[] args) throws Exception {
        try {
            constructor =
                    FinalsLoadouts.Choice.class.getDeclaredConstructor(
                            FinalsClass.class, FinalsWeapon.class, Kind.class, List.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(
                    "Loadouts must persist one specialization and three selected gadgets", missing);
        }
        constructor.setAccessible(true);
        Kind smoke = Kind.valueOf("SMOKE_GRENADE"), flash = Kind.valueOf("FLASHBANG");
        List<Kind> picks = new ArrayList<>(List.of(Kind.FRAG_GRENADE, smoke, flash));
        Object choice = make(FinalsClass.LIGHT, FinalsWeapon.M11, Kind.EVASIVE_DASH, picks);
        picks.set(0, Kind.RPG7);
        require(
                gadgets(choice).equals(List.of(Kind.FRAG_GRENADE, smoke, flash)),
                "Choice copies its selected gadget list");
        try {
            gadgets(choice).clear();
            throw new AssertionError("Choice exposes mutable gadgets");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        Object changed = call(choice, "withWeapon", FinalsWeapon.class, FinalsWeapon.XP54);
        require(
                call(changed, "specialization") == Kind.EVASIVE_DASH
                        && gadgets(changed).equals(gadgets(choice)),
                "Changing a same-class weapon preserves specialization and gadgets");
        Object medium = call(choice, "withClass", FinalsClass.class, FinalsClass.MEDIUM);
        require(
                call(medium, "playerClass") == FinalsClass.MEDIUM
                        && call(medium, "specialization") == Kind.HEALING_BEAM,
                "Changing class selects a legal specialization for the new class");
        require(
                gadgets(medium)
                        .equals(List.of(Kind.FRAG_GRENADE, Kind.JUMP_PAD, Kind.DEFIBRILLATOR)),
                "A new class receives its three default gadgets");
        require(
                gadgets(call(choice, "withClass", FinalsClass.class, FinalsClass.LIGHT))
                        .equals(gadgets(choice)),
                "Selecting the current class preserves custom equipment");
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.M11,
                Kind.HEALING_BEAM,
                List.of(Kind.FRAG_GRENADE, smoke, flash));
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.M11,
                Kind.FRAG_GRENADE,
                List.of(Kind.FRAG_GRENADE, smoke, flash));
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.M11,
                Kind.EVASIVE_DASH,
                List.of(smoke, smoke, flash));
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.M11,
                Kind.EVASIVE_DASH,
                List.of(Kind.RPG7, smoke, flash));
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.M11,
                Kind.EVASIVE_DASH,
                List.of(Kind.EVASIVE_DASH, smoke, flash));
        reject(FinalsClass.LIGHT, FinalsWeapon.M11, Kind.EVASIVE_DASH, List.of(smoke, flash));
        reject(
                FinalsClass.LIGHT,
                FinalsWeapon.AKM,
                Kind.EVASIVE_DASH,
                List.of(Kind.FRAG_GRENADE, smoke, flash));

        Method read =
                FinalsLoadouts.class.getDeclaredMethod(
                        "readChoice", YamlConfiguration.class, String.class);
        Method write =
                FinalsLoadouts.class.getDeclaredMethod(
                        "writeChoice",
                        YamlConfiguration.class,
                        String.class,
                        FinalsLoadouts.Choice.class);
        read.setAccessible(true);
        write.setAccessible(true);
        YamlConfiguration yaml = new YamlConfiguration();
        write.invoke(null, yaml, "player", choice);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        require(
                choice.equals(read.invoke(null, reloaded, "player")),
                "YAML round trip retains every custom selection");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("player.class", "heavy");
        legacy.set("player.weapon", "spear");
        Object migrated = read.invoke(null, legacy, "player");
        require(
                call(migrated, "weapon") == FinalsWeapon.SPEAR
                        && call(migrated, "specialization") == Kind.CHARGE_N_SLAM,
                "Legacy two-field loadouts keep their weapon and receive default specialization");
        require(
                gadgets(migrated).equals(List.of(Kind.RPG7, Kind.DOME_SHIELD, Kind.BARRICADE)),
                "Legacy loadouts receive a valid complete gadget kit");
        yaml.set("player.specialization", "unknown");
        yaml.set("player.gadgets", List.of("rpg7", "rpg7", "unknown"));
        Object repaired = read.invoke(null, yaml, "player");
        require(
                call(repaired, "weapon") == FinalsWeapon.M11
                        && call(repaired, "specialization") == Kind.EVASIVE_DASH,
                "Invalid persisted gadget choices do not discard the valid selected weapon");
        require(
                gadgets(repaired)
                        .equals(
                                List.of(
                                        Kind.FRAG_GRENADE,
                                        Kind.SONAR_GRENADE,
                                        Kind.VANISHING_BOMB)),
                "Corrupt or cross-class selections fall back to a legal complete kit");
        System.out.println("FinalsLoadoutsCheck: " + checks + " checks passed");
    }

    private static Object make(
            FinalsClass type, FinalsWeapon weapon, Kind specialization, List<Kind> gadgets)
            throws Exception {
        try {
            return constructor.newInstance(type, weapon, specialization, gadgets);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof IllegalArgumentException invalid) throw invalid;
            throw failure;
        }
    }

    private static void reject(
            FinalsClass type, FinalsWeapon weapon, Kind specialization, List<Kind> gadgets)
            throws Exception {
        try {
            make(type, weapon, specialization, gadgets);
            throw new AssertionError("Illegal loadout accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static Object call(Object object, String name) throws Exception {
        return object.getClass().getDeclaredMethod(name).invoke(object);
    }

    private static Object call(Object object, String name, Class<?> parameter, Object value)
            throws Exception {
        return object.getClass().getDeclaredMethod(name, parameter).invoke(object, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Kind> gadgets(Object choice) throws Exception {
        return (List<Kind>) call(choice, "gadgets");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
