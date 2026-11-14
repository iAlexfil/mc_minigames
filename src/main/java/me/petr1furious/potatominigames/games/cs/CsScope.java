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
        // FOV is (factor + 1) / 2. Faster scoped weapons need a higher factor so
        // compensation fits Bukkit's maximum walk speed of 1 without slowing them down.
        double zoomFactor = weapon.category == CsWeapon.Category.SNIPER ? .11 : .14;
        return Math.max(zoomFactor, .26 * weapon.scopedSpeed() / 250.0);
    }

    static float walkSpeed(double normalSpeed, CsWeapon weapon) {
        // Preserve the weapon's real scoped/unscoped speed ratio, including hit tagging.
        return (float) (normalSpeed * weapon.scopedMobility() / speedFactor(weapon));
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
