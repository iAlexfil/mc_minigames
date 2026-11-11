package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure timing/scoring plus actual routed combat handlers, without starting a server. */
public final class OneShotCheck {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    public static void main(String[] args) throws Exception {
        ammunition();
        shieldsAndLives();
        scoring();
        combat(Arrays.asList(args).contains("--with-server"));
        integration();
        System.out.println(
                "One Shot: finite ammunition, ten-second empty refill, bonus/kill ammo, one-hit"
                    + " wards, respawn epochs, lethal arrow handlers, native sword damage, death"
                    + " deduplication, kill-only ties and integration passed");
    }

    private static OneShotState state() {
        var s = new OneShotState(List.of(A, B, C));
        for (UUID id : List.of(A, B, C)) s.spawn(id);
        return s;
    }

    private static void ammunition() {
        var s = state();
        check(s.arrows(A) == 1, "A life starts with exactly one arrow");
        check(s.fire(A, 10) && !s.fire(A, 10), "One arrow cannot fire twice");
        check(
                s.refillIn(A, 10) == 200 && !s.refill(A, 209),
                "Empty refill waits ten whole seconds");
        check(
                s.refill(A, 210) && s.arrows(A) == 1 && !s.refill(A, 211),
                "Deadline grants exactly one arrow once");
        s.fire(A, 300);
        s.addArrow(A);
        check(!s.refill(A, 1000) && s.arrows(A) == 1, "A pickup cancels the old automatic refill");
        s.addArrow(A);
        check(
                s.fire(A, 1100) && s.arrows(A) == 1 && s.refillIn(A, 1100) == 0,
                "Extra ammunition survives a shot");
        check(
                s.fire(A, 1200) && !s.refill(A, 1399) && s.refill(A, 1400),
                "Timer restarts only when the final extra arrow is fired");
        s.fire(A, 1500);
        check(s.death(B, A) && s.arrows(A) == 1, "A kill immediately returns an arrow");
        check(
                !s.refill(A, 1700) && s.arrows(A) == 1,
                "Kill reward and timed refill cannot award two arrows");
        check(
                !s.death(B, A) && s.kills(A) == 1 && s.arrows(A) == 1,
                "A duplicate death cannot farm score or arrows");
        s.spawn(B);
        s.death(B, A);
        check(s.arrows(A) == 2, "Sword kills also add an arrow when one is already carried");
        while (s.addArrow(A)) {}
        check(
                s.arrows(A) == 64 && !s.addArrow(A),
                "A full vanilla arrow stack refuses another bonus");
    }

    private static void shieldsAndLives() {
        var s = state();
        int life = s.life(A);
        check(s.giveShield(A) && !s.giveShield(A), "Only one shield charge can be carried");
        check(s.block(A) && !s.block(A), "A shield absorbs exactly one hit");
        s.giveShield(A);
        s.fire(A, 0);
        s.death(A, B);
        check(
                !s.alive(A) && !s.shield(A) && !s.refill(A, 500),
                "A dead player cannot keep shields or refill ammunition");
        check(!s.validLife(A, life), "A dead player's old arrows are invalid");
        s.spawn(A);
        check(
                s.life(A) > life && s.arrows(A) == 1 && !s.shield(A),
                "Respawn starts a new life with a clean one-arrow kit");
        check(
                !s.validLife(A, life) && s.validLife(A, s.life(A)),
                "Respawn cannot reactivate old projectiles");
        s.disconnect(A);
        check(!s.alive(A) && !s.addArrow(A), "A disconnected participant cannot collect pickups");
        s.close();
        s.spawn(A);
        check(
                !s.alive(A) && !s.fire(B, 0) && !s.giveShield(B),
                "Finished matches reject mutations");
    }

    private static void scoring() {
        var s = state();
        s.death(C, A);
        s.spawn(C);
        s.death(C, B);
        s.death(A, null);
        check(
                s.result(List.of(A, B, C)).places().equals(List.of(List.of(A, B), List.of(C))),
                "Equal kill counts share places despite different death counts");
        check(
                s.result(List.of(A, B, C)).awards().get(A) == 3
                        && s.result(List.of(A, B, C)).awards().get(B) == 3,
                "Tied leaders share podium points");
        check(
                s.result(List.of(B, C)).place(B) == 1 && s.result(List.of(B, C)).place(A) == 0,
                "Absent players cannot win");
        s.spawn(A);
        int before = s.kills(A);
        s.death(A, A);
        check(s.kills(A) == before, "Suicide awards no kill");
        check(
                state().result(List.of(A, B, C)).places().equals(List.of(List.of(A, B, C))),
                "Zero-kill players remain tied");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void combat(boolean withServer) throws Exception {
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "equals" -> p == a[0];
                                            case "hashCode" -> System.identityHashCode(p);
                                            case "spawnParticle" -> null;
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + m.getName());
                                        });
        Player attacker = actor(A, world), victim = actor(B, world);
        var context =
                new PartyRoundContext(
                        null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
        var session = new OneShotSession(null, null, context);
        var s = state();
        set(session, "world", world);
        set(session, "state", s);
        var phase = OneShotSession.class.getDeclaredField("phase");
        phase.setAccessible(true);
        phase.set(session, Enum.valueOf((Class) phase.getType(), "FIGHT"));
        var connected = OneShotSession.class.getDeclaredField("connected");
        connected.setAccessible(true);
        ((Set<UUID>) connected.get(session)).addAll(List.of(A, B));
        var sword =
                new EntityDamageByEntityEvent(
                        attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, 4);
        session.onEntityDamage(sword);
        session.onEntityDamageByEntity(sword);
        check(
                !sword.isCancelled() && sword.getDamage() == 4,
                "Wooden-sword hits retain native damage and are routed once");
        var fall = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FALL, null, 3);
        session.onEntityDamage(fall);
        check(fall.isCancelled(), "Environmental damage does not consume combat shields");
        // Sound/particle feedback requires Paper registries; the MockBukkit harness covers it.
        if (withServer) {
            s.giveShield(B);
            var absorbedSword =
                    new EntityDamageByEntityEvent(
                            attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, 4);
            session.onEntityDamage(absorbedSword);
            session.onEntityDamageByEntity(absorbedSword);
            check(
                    absorbedSword.isCancelled() && !s.shield(B),
                    "The real melee handler consumes one ward and prevents damage");
            s.giveShield(B);
            boolean[] valid = {true};
            AbstractArrow arrow = arrow(attacker, world, s.life(A), valid);
            var shot =
                    new EntityDamageByEntityEvent(
                            arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE, null, 1);
            session.onEntityDamageByEntity(shot);
            check(
                    shot.isCancelled() && !valid[0] && !s.shield(B),
                    "A shield absorbs and removes the entire lethal arrow");
            var repeat =
                    new EntityDamageByEntityEvent(
                            arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE, null, 1);
            session.onEntityDamageByEntity(repeat);
            check(
                    repeat.isCancelled(),
                    "The removed arrow cannot hit again after the shield breaks");
        }
        boolean[] live = {true};
        var lethal =
                new EntityDamageByEntityEvent(
                        arrow(attacker, world, s.life(A), live),
                        victim,
                        EntityDamageEvent.DamageCause.PROJECTILE,
                        null,
                        1);
        session.onEntityDamageByEntity(lethal);
        check(
                !lethal.isCancelled() && lethal.getFinalDamage() >= 1000 && !live[0],
                "Even a weak unshielded arrow receives lethal damage");
        int old = s.life(A);
        s.death(A, null);
        s.spawn(A);
        var stale =
                new EntityDamageByEntityEvent(
                        arrow(attacker, world, old, new boolean[] {true}),
                        victim,
                        EntityDamageEvent.DamageCause.PROJECTILE,
                        null,
                        1000);
        session.onEntityDamageByEntity(stale);
        check(stale.isCancelled(), "Old-life arrows cannot kill after a respawn");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Player actor(UUID id, World world) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class[] {Player.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getWorld" -> world;
                                    case "getGameMode" -> GameMode.SURVIVAL;
                                    case "isDead" -> false;
                                    case "getLocation" -> new Location(world, 0, 81, 0);
                                    case "setNoDamageTicks", "sendActionBar", "playSound" -> null;
                                    default -> throw new AssertionError("Player: " + m.getName());
                                });
    }

    private static AbstractArrow arrow(Player owner, World world, int life, boolean[] valid) {
        PersistentDataContainer data =
                (PersistentDataContainer)
                        Proxy.newProxyInstance(
                                PersistentDataContainer.class.getClassLoader(),
                                new Class[] {PersistentDataContainer.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("get")) return life;
                                    throw new AssertionError("PDC: " + m.getName());
                                });
        return (AbstractArrow)
                Proxy.newProxyInstance(
                        AbstractArrow.class.getClassLoader(),
                        new Class[] {AbstractArrow.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getShooter" -> owner;
                                    case "getWorld" -> world;
                                    case "getPersistentDataContainer" -> data;
                                    case "isValid" -> valid[0];
                                    case "remove" -> {
                                        valid[0] = false;
                                        yield null;
                                    }
                                    default -> throw new AssertionError("Arrow: " + m.getName());
                                });
    }

    private static void integration() throws Exception {
        var game = new PartyGame((PartyConfig) null);
        check(
                game.modes().contains("one_shot")
                        && game.maxPlayers("one_shot") == 16
                        && game.icon("one_shot") == Material.BOW,
                "Menu exposes a 16-player bow mode");
        check(
                game.createSession(null, "one_shot") instanceof OneShotSession,
                "Standalone entry creates One Shot");
        var context =
                new PartyRoundContext(
                        null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
        check(
                PartyRoundFactory.create("one_shot", null, null, context) instanceof OneShotSession
                        && PartyGamesState.MODES.contains("one_shot"),
                "Mixed matches use the same One Shot session");
        try (var in = OneShotCheck.class.getResourceAsStream("/config.yml")) {
            var y =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
            check(
                    y.getBoolean("queues.party.one_shot.enabled")
                            && y.getInt("queues.party.one_shot.minPlayers") == 2
                            && y.getInt("queues.party.one_shot.maxPlayers") == 16,
                    "The public queue is enabled for 2–16 players");
        }
        try (var in = OneShotCheck.class.getResourceAsStream("/games/party.yml")) {
            var y =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
            check(
                    y.getInt("oneShot.durationSeconds") == 180
                            && y.getInt("oneShot.bonusSeconds") == 6,
                    "Default match and pickup timings are configured");
            check(
                    List.of(y.getString("partyGames.modes").split(",")).contains("one_shot"),
                    "Mixed defaults include One Shot");
        }
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
