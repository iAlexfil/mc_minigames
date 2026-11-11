package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.hud.*;
import me.petr1furious.potatominigames.core.player.*;
import me.petr1furious.potatominigames.core.session.*;
import me.petr1furious.potatominigames.core.text.Messages;
import me.petr1furious.potatominigames.core.world.ArenaService;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.*;
import java.util.*;

/** Component integration with real Bukkit inventory/event APIs, not native projectile physics. */
public final class OneShotMockCheck {
    private static final class PlayerFixture extends PlayerMock {
        // This MockBukkit version rejects the API's nullable armour array.
        private final org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock inventory =
                new org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock(this) {
                    @Override
                    public void setArmorContents(ItemStack[] items) {
                        super.setArmorContents(items == null ? new ItemStack[4] : items);
                    }
                };

        PlayerFixture(ServerMock server, String name) {
            super(server, name);
            if (getAttribute(Attribute.MAX_ABSORPTION) == null)
                attributes.put(
                        Attribute.MAX_ABSORPTION,
                        new org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock(
                                Attribute.MAX_ABSORPTION, 0));
        }

        @Override
        public PlayerInventory getInventory() {
            return inventory == null ? super.getInventory() : inventory;
        }
    }

    private static final class WorldFixture extends WorldMock {
        WorldFixture() {
            super(Material.AIR, 0);
            setName("one_shot_mock");
        }

        @Override
        public org.mockbukkit.mockbukkit.block.BlockMock createBlock(
                org.mockbukkit.mockbukkit.world.Coordinate coordinate) {
            var original = super.createBlock(coordinate);
            var block =
                    new org.mockbukkit.mockbukkit.block.BlockMock(
                            original.getType(), original.getLocation()) {
                        @Override
                        public boolean isPassable() {
                            return !getType().isSolid();
                        }

                        @Override
                        public Location getLocation() {
                            return super.getLocation().clone();
                        }
                    };
            try {
                ((Map) OneShotMockCheck.get(this, WorldMock.class, "blocks"))
                        .put(coordinate, block);
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
            return block;
        }

        @Override
        public void spawnParticle(
                Particle particle,
                Location at,
                int count,
                double x,
                double y,
                double z,
                double extra) {}
    }

    private static Object get(Object target, Class<?> owner, String name) throws Exception {
        var f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, Class<?> owner, String name, Object value)
            throws Exception {
        var f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object call(
            OneShotSession session, String name, Class<?>[] types, Object... args)
            throws Exception {
        var m = OneShotSession.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(session, args);
    }

    private static void ticks(OneShotSession session, int count) throws Exception {
        for (int i = 0; i < count; i++) call(session, "tick", new Class<?>[0]);
    }

    private static void check(boolean good, String reason) {
        if (!good) throw new AssertionError(reason);
    }

    private static EntityShootBowEvent shoot(
            OneShotSession session, PlayerFixture player, World world) {
        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        var event =
                new EntityShootBowEvent(
                        player,
                        player.getInventory().getItem(0),
                        player.getInventory().getItem(8),
                        arrow,
                        EquipmentSlot.HAND,
                        .1f,
                        true);
        session.onEntityShootBow(event);
        return event;
    }

    public static void main(String[] args) throws Exception {
        var server = MockBukkit.mock();
        try {
            OneShotCheck.main(new String[] {"--with-server"});
            var plugin = MockBukkit.createMockPlugin();
            var world = new WorldFixture();
            server.addWorld(world);
            var a = new PlayerFixture(server, "Archer");
            var b = new PlayerFixture(server, "Target");
            server.addPlayer(a);
            server.addPlayer(b);
            var players = List.<Player>of(a, b);
            var playerState = new PlayerStateService(plugin);
            var ctor = ActiveSession.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            var active =
                    (ActiveSession)
                            ctor.newInstance(
                                    plugin,
                                    null,
                                    new PartyGame((PartyConfig) null),
                                    "one_shot",
                                    Map.of(),
                                    playerState,
                                    null,
                                    null,
                                    null);
            set(active, ActiveSession.class, "phase", GamePhase.RUNNING);
            set(active, ActiveSession.class, "test", true);
            for (var p : players) active.participants().add(p);
            var services =
                    new SessionServices(
                            plugin,
                            active,
                            active.participants(),
                            playerState,
                            null,
                            new SidebarService(List::of),
                            new BossBarService(() -> players),
                            new ArenaService(),
                            null,
                            null,
                            new Messages(() -> Audience.audience(players), null),
                            null);
            var context =
                    new PartyRoundContext(
                            services,
                            active.participants().all().stream().toList(),
                            PartyRoundOptions.standalone(),
                            (reason, result) -> {});
            var session = new OneShotSession(services, null, context);
            var state = new OneShotState(players.stream().map(Player::getUniqueId).toList());
            var arena = new ArmsRaceArena(world, PvpArena.Layout.DESERT_RUINS);
            arena.build();
            set(session, OneShotSession.class, "world", world);
            set(session, OneShotSession.class, "arena", arena);
            set(session, OneShotSession.class, "state", state);
            set(
                    session,
                    OneShotSession.class,
                    "bar",
                    services.bossBars()
                            .create(
                                    Component.text("fixture"),
                                    net.kyori.adventure.bossbar.BossBar.Color.YELLOW,
                                    net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS));
            set(session, OneShotSession.class, "countdownEnd", 5L);
            set(session, OneShotSession.class, "durationTicks", 2000);
            set(session, OneShotSession.class, "bonusTicks", 120);
            var names = (Map<UUID, String>) get(session, OneShotSession.class, "names");
            var connected = (Set<UUID>) get(session, OneShotSession.class, "connected");
            for (var p : players) {
                names.put(p.getUniqueId(), p.getName());
                connected.add(p.getUniqueId());
                p.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                p.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                p.setItemOnCursor(new ItemStack(Material.DIAMOND));
                call(session, "spawn", new Class<?>[] {Player.class}, p);
                check(
                        p.getInventory().getItem(0).getType() == Material.BOW
                                && p.getInventory().getItem(0).getItemMeta().isUnbreakable(),
                        "Unbreakable bow");
                check(
                        p.getInventory().getItem(1).getType() == Material.WOODEN_SWORD,
                        "Wooden sword");
                check(
                        p.getInventory().getItem(8).getAmount() == 1
                                && p.getInventory().getHelmet() == null
                                && p.getInventory().getItemInOffHand().isEmpty(),
                        "One arrow, no old armour/offhand");
                check(p.getItemOnCursor().isEmpty(), "No cursor carryover");
            }
            var move = new PlayerMoveEvent(a, a.getLocation(), a.getLocation().add(1, 0, 0));
            session.onPlayerMove(move);
            check(move.getTo().equals(a.getLocation()), "Countdown freezes movement");
            ticks(session, 46);
            var shot = shoot(session, a, world);
            check(
                    !shot.isCancelled()
                            && shot.shouldConsumeItem()
                            && state.arrows(a.getUniqueId()) == 0,
                    "One model arrow and one vanilla consumption per shot");
            Arrow arrow = (Arrow) shot.getProjectile();
            check(
                    arrow.getDamage() >= 1000
                            && arrow.getPickupStatus() == AbstractArrow.PickupStatus.DISALLOWED
                            && arrow.getPierceLevel() == 0,
                    "Weak-shot damage and no pickup/piercing");
            // MockBukkit does not simulate BowItem's native post-event consumption.
            a.getInventory().setItem(8, null);
            session.onProjectileHit(
                    new ProjectileHitEvent(arrow, world.getBlockAt(0, PvpArena.FLOOR, 0)));
            check(!arrow.isValid(), "A missed arrow is removed on its block-hit callback");
            ticks(session, 199);
            check(a.getInventory().getItem(8) == null, "No premature ammo refill");
            ticks(session, 1);
            check(
                    a.getInventory().getItem(8).getAmount() == 1,
                    "Actual tick task restores one arrow at ten seconds");
            arrow.remove();
            var killShot = shoot(session, a, world);
            a.getInventory().setItem(8, null);
            var hit =
                    new EntityDamageByEntityEvent(
                            killShot.getProjectile(),
                            b,
                            EntityDamageEvent.DamageCause.PROJECTILE,
                            null,
                            1);
            session.onEntityDamageByEntity(hit);
            check(!hit.isCancelled() && hit.getFinalDamage() >= 1000, "Lethal projectile event");
            var death =
                    new PlayerDeathEvent(b, null, new ArrayList<>(), 0, Component.empty(), true);
            session.onPlayerDeath(death);
            check(
                    state.kills(a.getUniqueId()) == 1
                            && state.deaths(b.getUniqueId()) == 1
                            && state.arrows(a.getUniqueId()) == 1,
                    "Native death callback credits one kill and arrow");
            session.onPlayerDeath(death);
            check(state.kills(a.getUniqueId()) == 1, "Duplicate death rejected");
            ticks(session, 59);
            check(
                    !state.alive(b.getUniqueId()) && b.getGameMode() == GameMode.SPECTATOR,
                    "Early respawn cannot bypass the three-second hold");
            ticks(session, 1);
            check(
                    state.alive(b.getUniqueId())
                            && b.getGameMode() == GameMode.SURVIVAL
                            && b.getInventory().getItem(8).getAmount() == 1,
                    "Tick task respawns with a clean arrow kit");
            call(session, "clearBonuses", new Class<?>[0]);
            var bonuses = (Map<UUID, ?>) get(session, OneShotSession.class, "bonuses");
            boolean sawArrow = false, sawShield = false;
            for (int attempt = 0; attempt < 20 && !(sawArrow && sawShield); attempt++) {
                call(session, "clearBonuses", new Class<?>[0]);
                for (int i = 0; i < 5; i++) call(session, "spawnBonus", new Class<?>[0]);
                check(bonuses.size() == 3, "At most three active pickups");
                for (var entity : List.copyOf(world.getEntities()))
                    if (entity instanceof Item item && bonuses.containsKey(item.getUniqueId())) {
                        boolean shield = item.getItemStack().getType() == Material.SHIELD;
                        if (shield && !state.shield(a.getUniqueId()))
                            state.giveShield(a.getUniqueId());
                        var pickup = new EntityPickupItemEvent(a, item, 0);
                        if (shield) {
                            session.onPickupItem(pickup);
                            check(
                                    item.isValid() && bonuses.containsKey(item.getUniqueId()),
                                    "A redundant shield stays on the ground");
                            state.block(a.getUniqueId());
                        }
                        int before = state.arrows(a.getUniqueId());
                        session.onPickupItem(pickup);
                        check(
                                pickup.isCancelled()
                                        && !bonuses.containsKey(item.getUniqueId())
                                        && !item.isValid(),
                                "Pickup is applied once and removed");
                        if (shield) {
                            sawShield = true;
                            check(
                                    state.shield(a.getUniqueId())
                                            && !a.getInventory().contains(Material.SHIELD),
                                    "Shield is a virtual one-hit ward");
                        } else {
                            sawArrow = true;
                            check(
                                    state.arrows(a.getUniqueId()) == before + 1
                                            && a.getInventory().getItem(8).getAmount()
                                                    == before + 1,
                                    "Arrow pickup updates both model and inventory");
                        }
                    }
            }
            check(sawArrow && sawShield, "Both bonus kinds are reachable");
            check(session.globalResult() == null, "No winners announced before completion");
            session.onEnd(EndReason.ABORTED);
            check(
                    bonuses.isEmpty()
                            && world.getEntities().stream()
                                    .noneMatch(e -> e instanceof Projectile || e instanceof Item),
                    "End cleanup removes every pickup and projectile");
            System.out.println(
                    "ONE_SHOT_MOCK_PASS: real kits, countdown, shooting/ammo, exact tick refill,"
                        + " shield feedback, death credit, three-second respawn, both bonus"
                        + " pickups/cap, and cleanup. MockBukkit does not simulate native flight or"
                        + " client death screens.");
        } finally {
            MockBukkit.unmock();
        }
    }
}
