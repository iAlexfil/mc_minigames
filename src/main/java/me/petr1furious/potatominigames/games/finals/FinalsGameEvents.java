package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** One session-scoped, manually started game-show event with bounded arena effects. */
final class FinalsGameEvents {
    enum Kind {
        LOW_GRAVITY("low_gravity", Material.FEATHER),
        METEOR_SHOWER("meteor_shower", Material.FIRE_CHARGE),
        ORBITAL_LASERS("orbital_lasers", Material.BEACON);

        private final String id;
        private final Material icon;

        Kind(String id, Material icon) {
            this.id = id;
            this.icon = icon;
        }

        String id() {
            return id;
        }

        Material icon() {
            return icon;
        }

        String nameKey() {
            return "finals.event." + id;
        }
    }

    interface Context {
        JavaPlugin plugin();

        World world();

        long tick();

        Collection<Player> players();

        boolean active(Player player);

        boolean inside(Location point);

        boolean destructible(Block block);

        void breakBlock(Block block);

        void damage(Player target, double amount, Location origin);

        void announce(String key, Object... args);
    }

    private static final NamespacedKey GRAVITY =
            new NamespacedKey("potatominigames", "finals_event_gravity");
    private static final long DURATION = 600;
    private final Context context;
    private final RandomGenerator random;
    private final Map<UUID, Player> lightPlayers = new HashMap<>();
    private final Map<UUID, Still> stationary = new HashMap<>();
    private final List<Strike> strikes = new ArrayList<>();
    private Kind active;
    private long expires, nextMeteor;

    private record Still(Location anchor, long since) {}

    private record Strike(Location point, long impact, boolean meteor) {}

    FinalsGameEvents(Context context) {
        this(context, ThreadLocalRandom.current());
    }

    FinalsGameEvents(Context context, RandomGenerator random) {
        this.context = Objects.requireNonNull(context);
        this.random = Objects.requireNonNull(random);
    }

    Kind activeKind() {
        return active;
    }

    int remainingSeconds() {
        return active == null ? 0 : (int) Math.max(0, (expires - context.tick() + 19) / 20);
    }

    boolean start(Kind kind) {
        if (active != null || kind == null) return false;
        active = kind;
        expires = context.tick() + DURATION;
        nextMeteor = context.tick() + 20;
        context.announce("event.started", I18n.tr(kind.nameKey()), DURATION / 20);
        tick();
        return true;
    }

    void stop() {
        Kind old = active;
        clear();
        if (old != null) context.announce("event.ended", I18n.tr(old.nameKey()));
    }

    /** Match teardown is silent and also cancels every warned, not-yet-impacting strike. */
    void clear() {
        lightPlayers.values().forEach(this::normalGravity);
        lightPlayers.clear();
        stationary.clear();
        strikes.clear();
        active = null;
        expires = nextMeteor = 0;
    }

    void tick() {
        if (active == null) return;
        if (context.tick() >= expires) {
            stop();
            return;
        }
        List<Player> players = context.players().stream().filter(this::eligible).toList();
        if (active == Kind.LOW_GRAVITY) {
            gravity(players);
            return;
        }
        if (active == Kind.METEOR_SHOWER && context.tick() >= nextMeteor) {
            nextMeteor = context.tick() + 30;
            if (!players.isEmpty()) meteor(players.get(random.nextInt(players.size())));
        }
        if (active == Kind.ORBITAL_LASERS) lasers(players);
        // Damage can synchronously end the match and clear this helper.
        for (Strike strike : List.copyOf(strikes)) {
            if (active == null) return;
            if (!context.inside(strike.point())) {
                strikes.remove(strike);
                continue;
            }
            if (context.tick() >= strike.impact()) {
                strikes.remove(strike);
                blast(strike.point(), strike.meteor() ? 3.5 : 3, strike.meteor() ? 12 : 14);
            } else if (context.tick() % 4 == 0) warning(strike);
        }
    }

    private boolean eligible(Player player) {
        return player != null
                && player.isOnline()
                && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR
                && context.world().equals(player.getWorld())
                && context.active(player)
                && context.inside(player.getLocation());
    }

    private void normalGravity(Player player) {
        var attribute = player.getAttribute(Attribute.GRAVITY);
        if (attribute != null) attribute.removeModifier(GRAVITY);
    }

    private void gravity(List<Player> players) {
        Set<Player> current = new HashSet<>(players);
        lightPlayers
                .values()
                .removeIf(player -> {
                    if (current.contains(player)) return false;
                    normalGravity(player);
                    return true;
                });
        for (Player player : players) {
            var attribute = player.getAttribute(Attribute.GRAVITY);
            if (attribute == null) continue;
            if (attribute.getModifier(GRAVITY) == null)
                attribute.addTransientModifier(
                        new AttributeModifier(
                                GRAVITY, -.65, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            lightPlayers.put(player.getUniqueId(), player);
            player.setFallDistance(0);
            if (context.tick() % 20 == 0)
                context.world()
                        .spawnParticle(Particle.CLOUD, player.getLocation(), 2, .3, .1, .3, .01);
        }
    }

    private void meteor(Player target) {
        Location around =
                target.getLocation().add(random.nextDouble(-7, 7), 0, random.nextDouble(-7, 7));
        if (!context.inside(around)) return;
        Location top = around.clone();
        top.setY(Math.min(context.world().getMaxHeight() - 1, around.getY() + 24));
        // Stay within the selected arena height, including the small generated arena.
        while (top.getY() > around.getY() && !context.inside(top)) top.subtract(0, 1, 0);
        var hit =
                context.world()
                        .rayTraceBlocks(
                                top, new Vector(0, -1, 0), 48, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return;
        Location point = hit.getHitPosition().toLocation(context.world()).add(0, .1, 0);
        if (!context.inside(point)) return;
        strikes.add(new Strike(point, context.tick() + 40, true));
        context.world().playSound(point, "minecraft:entity.blaze.shoot", .8f, .5f);
    }

    private void lasers(List<Player> players) {
        Set<UUID> current = new HashSet<>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            current.add(id);
            Location point = player.getLocation();
            Still old = stationary.get(id);
            if (old == null || old.anchor().distanceSquared(point) > .25) {
                stationary.put(id, new Still(point, context.tick()));
            } else if (context.tick() - old.since() >= 60) {
                strikes.add(new Strike(point.clone().add(0, .1, 0), context.tick() + 40, false));
                stationary.put(id, new Still(point, context.tick()));
                player.sendActionBar(I18n.tr("finals.event.laser_warning"));
                context.world().playSound(point, "minecraft:block.beacon.power_select", 1f, 1.8f);
            }
        }
        stationary.keySet().retainAll(current);
    }

    private void warning(Strike strike) {
        Location point = strike.point();
        Particle.DustOptions dust =
                new Particle.DustOptions(strike.meteor() ? Color.ORANGE : Color.RED, 1.4f);
        for (int step = 0; step < 16; step++) {
            double angle = Math.PI * 2 * step / 16;
            Location marker = point.clone().add(Math.cos(angle) * 2, .15, Math.sin(angle) * 2);
            if (context.inside(marker))
                context.world().spawnParticle(Particle.DUST, marker, 1, dust);
        }
        if (strike.meteor()) {
            double height = Math.max(0, Math.min(14, (strike.impact() - context.tick()) * .35));
            Location falling = point.clone().add(0, height, 0);
            if (context.inside(falling)) {
                context.world().spawnParticle(Particle.FLAME, falling, 8, .18, .18, .18, .02);
                context.world().spawnParticle(Particle.SMOKE, falling, 5, .25, .25, .25, .01);
            }
        } else {
            for (int height = 0; height < 16; height++) {
                Location beam = point.clone().add(0, height, 0);
                if (context.inside(beam))
                    context.world().spawnParticle(Particle.DUST, beam, 2, dust);
            }
        }
    }

    /** Never delegates block damage to vanilla explosions, whose reach exceeds map protections. */
    private void blast(Location center, double radius, double damage) {
        if (!context.inside(center)) return;
        context.world().spawnParticle(Particle.EXPLOSION, center, 3, .3, .3, .3, 0);
        context.world().playSound(center, "minecraft:entity.generic.explode", 1.8f, .7f);
        double blockRadius = radius - .5;
        int reach = (int) Math.ceil(blockRadius);
        for (int dx = -reach; dx <= reach; dx++)
            for (int dy = -reach; dy <= reach; dy++)
                for (int dz = -reach; dz <= reach; dz++) {
                    Location point = center.clone().add(dx, dy, dz);
                    if (!context.inside(point)) continue;
                    Block block = point.getBlock();
                    Location blockCenter = block.getLocation().add(.5, .5, .5);
                    if (context.inside(blockCenter)
                            && center.distanceSquared(blockCenter) <= blockRadius * blockRadius
                            && context.destructible(block)) context.breakBlock(block);
                }
        for (Player player : List.copyOf(context.players())) {
            if (active == null) return;
            if (!eligible(player)) continue;
            Location body = player.getLocation().add(0, .8, 0);
            double distance = body.distance(center);
            if (distance >= radius) continue;
            Vector direction = body.toVector().subtract(center.toVector());
            if (distance > .01
                    && context.world()
                                    .rayTraceBlocks(
                                            center,
                                            direction.normalize(),
                                            distance,
                                            FluidCollisionMode.NEVER,
                                            true)
                            != null) continue;
            context.damage(player, damage * (1 - distance / radius), center.clone());
        }
    }
}
