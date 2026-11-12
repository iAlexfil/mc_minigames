package me.petr1furious.potatominigames.games.cs;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent vanilla FOV zoom without forcing a spyglass that the client releases every tick. */
final class CsScope {
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "cs_scope");

    private record View(Player player, float previousSpeed) {}

    private final Map<UUID, View> views = new HashMap<>();

    static double speedFactor(CsWeapon weapon) {
        return weapon.category == CsWeapon.Category.SNIPER ? .2 : .4;
    }

    static float walkSpeed(double normalSpeed, CsWeapon weapon) {
        // The attribute multiplier narrows FOV. Compensate its real movement penalty so
        // scoped walking still equals 45% of the gun's usual speed, including hit tagging.
        return (float) (normalSpeed * .45 / speedFactor(weapon));
    }

    void apply(Player player, CsWeapon weapon, double normalSpeed) {
        var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        views.computeIfAbsent(player.getUniqueId(), id -> new View(player, player.getWalkSpeed()));
        double amount = speedFactor(weapon) - 1;
        var current = attribute.getModifier(KEY);
        if (current == null || Math.abs(current.getAmount() - amount) > .000001) {
            if (current != null) attribute.removeModifier(KEY);
            attribute.addTransientModifier(
                    new AttributeModifier(
                            KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        float speed = walkSpeed(normalSpeed, weapon);
        if (Math.abs(player.getWalkSpeed() - speed) > .000001) player.setWalkSpeed(speed);
    }

    void stop(Player player) {
        View view = views.remove(player.getUniqueId());
        if (view == null) return;
        var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(KEY);
        player.setWalkSpeed(view.previousSpeed);
    }

    void clear() {
        for (View view : java.util.List.copyOf(views.values())) stop(view.player);
    }
}
