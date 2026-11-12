package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

/** Class-specific passive healing deadlines and lifecycle checks. */
@SuppressWarnings({"deprecation", "removal"})
public final class FinalsRegenerationCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var handler = FinalsRegeneration.class.getDeclaredMethod("onDamage", EntityDamageEvent.class)
                .getAnnotation(EventHandler.class);
        require(handler.priority() == EventPriority.MONITOR && handler.ignoreCancelled(),
                "Damage cooldown observes final accepted damage after normal handlers");
        for (FinalsClass type : FinalsClass.values()) {
            Fixture f = new Fixture();
            Fake player = new Fake(type.health());
            int delay = switch (type) { case LIGHT -> 160; case MEDIUM -> 180; case HEAVY -> 200; };
            f.helper.reset(player.id, 100);
            f.now = 100 + delay - 1; f.helper.tick(player.player, type);
            health(player, 1, type + " waits the entire class-specific delay");
            f.now++; f.helper.tick(player.player, type);
            health(player, 1.2, type + " begins healing at the exact delay boundary");
            f.helper.tick(player.player, type);
            health(player, 1.2, "Repeated calls in one tick cannot double healing");
            f.now++; f.helper.tick(player.player, type);
            health(player, 1.4, "Every class heals a fixed .2 health per following tick");
            f.now += 1000; f.helper.tick(player.player, type);
            health(player, 1.6, "Skipped ticks never produce a catch-up healing burst");
            f.helper.close();
        }

        Fixture f = new Fixture();
        Fake first = new Fake(15), second = new Fake(35);
        f.helper.reset(first.id, 0); f.helper.reset(second.id, 0);
        f.now = 159; f.damage(first, 2, false);
        f.now = 318; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1, "Positive damage restarts the full regeneration delay");
        f.now = 319; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.2, "Regeneration resumes at the new exact boundary");
        f.now = 320; f.damage(first, 2, true); f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.4, "Cancelled damage does not interrupt regeneration");
        f.now = 321; f.damage(first, 0, false); f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.6, "Zero final damage does not interrupt regeneration");
        f.inactive.add(first.id); f.now = 322; f.damage(first, 2, false);
        f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.6, "Inactive players are neither damaged in the cooldown tracker nor healed");
        f.inactive.clear(); f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.8, "Ignored inactive damage does not postpone later healing");
        first.dead = true; f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.8, "Dead players never regenerate");
        first.dead = false; first.online = false; f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 1.8, "Offline players never regenerate");
        first.online = true; first.health = 0; f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 0, "Zero health cannot be revived by passive healing");

        first.health = 7; first.maximum = 7.05; f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 7.05, "Healing clamps to the effective maximum health instead of class defaults");
        int writes = first.writes; f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        require(first.writes == writes, "A full-health player receives no redundant health update");
        first.health = 3; f.helper.reset(first.id, f.now);
        f.now += 159; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 3, "Respawn or statue revival starts a complete new delay");
        f.now++; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 3.2, "A reset life begins healing at its own boundary");
        f.now++; f.damage(first, 2, false); f.helper.tick(first.player, FinalsClass.LIGHT);
        f.helper.tick(second.player, FinalsClass.HEAVY);
        health(first, 3.2, "New damage interrupts a player who was already regenerating");
        health(second, 1.2, "Another player's damage does not postpone independent healing");
        f.helper.forget(first.id); f.now += 1000; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 3.2, "Forgetting a life removes its pending regeneration");
        f.helper.reset(first.id, f.now); f.now += 160; f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 3.4, "A later spawn can register a forgotten player again");
        f.helper.close(); f.helper.reset(first.id, f.now); f.now += 1000;
        f.damage(first, 1, false); f.helper.tick(first.player, FinalsClass.LIGHT);
        health(first, 3.4, "Closed regeneration cannot be restarted by damage, reset or ticks");
        System.out.println("FinalsRegenerationCheck: " + checks + " checks passed");
    }

    private static final class Fixture {
        long now;
        final Set<UUID> inactive = new HashSet<>();
        final FinalsRegeneration helper = new FinalsRegeneration(
                player -> !inactive.contains(player.getUniqueId()), () -> now);
        void damage(Fake player, double amount, boolean cancelled) {
            var event = new EntityDamageEvent(player.player, EntityDamageEvent.DamageCause.CUSTOM, null, amount);
            event.setCancelled(cancelled);
            helper.onDamage(event);
        }
    }

    private static final class Fake {
        final UUID id = UUID.randomUUID();
        final Player player;
        double health = 1, maximum;
        boolean dead, online = true;
        int writes;
        Fake(double maximum) {
            this.maximum = maximum;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "isDead" -> dead;
                        case "isOnline" -> online;
                        case "getHealth" -> health;
                        case "getMaxHealth" -> this.maximum;
                        case "setHealth" -> { health = (double) args[0]; writes++; yield null; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new AssertionError("Unexpected player operation: " + method.getName());
                    });
        }
    }

    private static void health(Fake player, double expected, String label) {
        require(Math.abs(player.health - expected) < 1e-9, label);
    }

    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
