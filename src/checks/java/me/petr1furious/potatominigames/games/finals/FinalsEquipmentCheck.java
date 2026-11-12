package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Class restrictions and selectable kits must match the original equipment roles. */
public final class FinalsEquipmentCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> type = FinalsGadgetState.Kind.class;
        Method specializations;
        try {
            specializations = type.getDeclaredMethod("specializations", FinalsClass.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(
                    "Every class needs selectable specializations and gadgets", missing);
        }
        Method gadgets = type.getDeclaredMethod("gadgets", FinalsClass.class);
        Method defaults = type.getDeclaredMethod("defaultGadgets", FinalsClass.class);
        Method defaultSpec = type.getDeclaredMethod("defaultSpecialization", FinalsClass.class);
        Method allowed = type.getDeclaredMethod("allowed", FinalsClass.class);
        Method specialization = type.getDeclaredMethod("specialization");
        for (FinalsClass role : FinalsClass.values()) {
            List<?> specs = (List<?>) specializations.invoke(null, role);
            List<?> options = (List<?>) gadgets.invoke(null, role);
            List<?> initial = (List<?>) defaults.invoke(null, role);
            require(specs.size() == 3, "Each class has three working specialization options");
            require(
                    initial.size() == 3
                            && Set.copyOf(initial).size() == 3
                            && options.containsAll(initial),
                    "Default kits have three distinct class-eligible gadgets");
            require(
                    specs.contains(defaultSpec.invoke(null, role)),
                    "Default specialization is selectable");
            for (Object kind : specs)
                require(
                        (boolean) allowed.invoke(kind, role)
                                && (boolean) specialization.invoke(kind),
                        "Specialization options respect class and slot");
            for (Object kind : options)
                require(
                        (boolean) allowed.invoke(kind, role)
                                && !(boolean) specialization.invoke(kind),
                        "Gadget options respect class and slot");
            for (String name :
                    List.of(
                            "SMOKE_GRENADE",
                            "FLASHBANG",
                            "GAS_GRENADE",
                            "PYRO_GRENADE",
                            "GOO_GRENADE"))
                require(
                        options.contains(kind(name)),
                        "Shared grenades are available for every class: " + name);
        }
        require(
                (boolean) allowed.invoke(kind("BREACH_CHARGE"), FinalsClass.LIGHT)
                        && !(boolean) allowed.invoke(kind("BREACH_CHARGE"), FinalsClass.HEAVY),
                "Breach Charge belongs to Light");
        require(
                (boolean) allowed.invoke(kind("APS_TURRET"), FinalsClass.MEDIUM)
                        && !(boolean) allowed.invoke(kind("APS_TURRET"), FinalsClass.LIGHT),
                "APS Turret belongs to Medium");
        require(
                (boolean) allowed.invoke(kind("EXPLOSIVE_MINE"), FinalsClass.MEDIUM)
                        && (boolean) allowed.invoke(kind("EXPLOSIVE_MINE"), FinalsClass.HEAVY)
                        && !(boolean) allowed.invoke(kind("EXPLOSIVE_MINE"), FinalsClass.LIGHT),
                "Explosive Mines have the correct two classes");
        require(
                (boolean) allowed.invoke(kind("GAS_MINE"), FinalsClass.MEDIUM)
                        && !(boolean) allowed.invoke(kind("GAS_MINE"), FinalsClass.HEAVY),
                "Gas Mine belongs to Medium");
        require(
                (boolean) allowed.invoke(kind("PYRO_MINE"), FinalsClass.HEAVY)
                        && !(boolean) allowed.invoke(kind("PYRO_MINE"), FinalsClass.MEDIUM),
                "Pyro Mine belongs to Heavy");
        for (FinalsGadgetState.Kind kind : FinalsGadgetState.Kind.values()) {
            for (String suffix : List.of("name", "lore")) {
                String key = "finals.gadget." + kind.id() + "." + suffix;
                require(
                        I18n.has(key),
                        "Every selectable item has a translated " + suffix + ": " + kind);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String rendered =
                            PlainTextComponentSerializer.plainText()
                                    .serialize(I18n.render(I18n.tr(key), locale));
                    require(
                            !rendered.isBlank() && !rendered.contains("potatominigames."),
                            "Dynamic item labels render in " + locale + ": " + kind);
                }
            }
        }
        I18n.validateCatalogs();
        System.out.println("FinalsEquipmentCheck: " + checks + " checks passed");
    }

    private static FinalsGadgetState.Kind kind(String name) {
        return FinalsGadgetState.Kind.valueOf(name);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
