package me.petr1furious.potatominigames.games.finals;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Listener-relative music with eligibility measured from the moving cashout. */
public final class FinalsCashoutMusicCheck {
    private static final String PIGSTEP = "minecraft:music_disc.pigstep";
    private static final String LAVA = "minecraft:music_disc.lava_chicken";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsCashoutMusic");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Active cashouts need nearby listener-relative music with cleanup", missing);
        }
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Music music = new Music(constructor.newInstance());
        World city = world("city"), elsewhere = world("elsewhere");
        Actor source = new Actor(city, 0, 70, 0, UUID.randomUUID());
        Listener near = new Listener(city, 0, 70, 0);
        Listener boundary = new Listener(city, 32, 70, 0);
        Listener far = new Listener(city, 32.001, 70, 0);
        Listener otherWorld = new Listener(elsewhere, 0, 70, 0);
        Listener offline = new Listener(city, 0, 70, 0);
        offline.online = false;
        List<Player> audience = List.of(near.player, boundary.player, far.player, otherWorld.player, offline.player);
        source.tracked.addAll(audience);
        source.tracked.remove(near.player);

        music.update(null, audience);
        music.clear();
        require(near.plays.isEmpty() && near.stops.isEmpty(), "Inactive cashouts produce no sound or broad stop packets");
        music.update(source.entity, audience);
        require(near.plays.size() == 1, "Eligible listeners hear their own-player sound even before tracking the cashout body");
        source.tracked.add(near.player);
        music.update(source.entity, audience);
        require(near.plays.size() == 1 && boundary.plays.size() == 1, "Online listeners within the inclusive thirty-two-block radius hear music");
        require(far.plays.isEmpty() && otherWorld.plays.isEmpty() && offline.plays.isEmpty(),
                "Distant, other-world and offline players receive no music");
        playback(near.lastPlay(), near.player, PIGSTEP);
        for (int tick = 0; tick < 30; tick++) music.update(source.entity, audience);
        require(near.plays.size() == 1 && boundary.plays.size() == 1, "Repeated active updates do not restart the track");

        source.location = new Location(city, 1, 69, 0);
        music.update(source.entity, List.of(near.player, boundary.player));
        require(near.plays.size() == 1 && boundary.plays.size() == 1,
                "Moving or falling the same cashout does not restart music for listeners still in range");
        source.location = new Location(city, -40, 69, 0);
        boundary.location = new Location(elsewhere, 1, 69, 0);
        music.update(source.entity, List.of(near.player, boundary.player));
        require(near.stops.size() == 1 && boundary.stops.size() == 1,
                "A cashout moving out of range or a listener changing worlds stops the current track");
        stopped(near.lastStop(), PIGSTEP);
        stopped(boundary.lastStop(), PIGSTEP);

        source.location = new Location(city, 1, 69, 0);
        Listener late = new Listener(city, 1, 69, 0);
        source.tracked.add(late.player);
        music.update(source.entity, List.of(late.player));
        require(late.plays.size() == 1, "A listener joining an ongoing cashout hears its current track");
        playback(late.lastPlay(), late.player, PIGSTEP);
        music.update(source.entity, List.of());
        require(late.stops.size() == 1, "Leaving the allowed audience stops music even while standing nearby");
        stopped(late.lastStop(), PIGSTEP);

        near.location = source.location.clone();
        music.update(source.entity, List.of(near.player));
        int played = near.plays.size();
        near.online = false;
        music.update(source.entity, List.of(near.player));
        require(near.plays.size() == played, "Offline audience members never receive new play packets");
        near.online = true;
        music.update(source.entity, List.of(near.player));
        require(near.plays.size() == played + 1, "Reconnect starts the active track again after stale listener state is removed");
        int stopped = near.stops.size();
        music.remove(near.player);
        require(near.stops.size() == stopped + 1, "Explicit participant removal stops that listener's music");
        stopped(near.lastStop(), PIGSTEP);
        music.remove(near.player);
        require(near.stops.size() == stopped + 1, "Removing an already absent listener is idempotent");

        music.update(source.entity, List.of(near.player));
        playback(near.lastPlay(), near.player, PIGSTEP);
        music.clear();
        stopped(near.lastStop(), PIGSTEP);
        stopped = near.stops.size();
        music.clear();
        music.update(null, List.of(near.player));
        require(near.stops.size() == stopped, "Repeated cleanup does not send extra stop packets or consume another track");
        music.update(source.entity, List.of(near.player));
        playback(near.lastPlay(), near.player, LAVA);

        Actor replacement = new Actor(city, 1, 69, 0, UUID.randomUUID());
        played = near.plays.size(); stopped = near.stops.size();
        music.update(replacement.entity, List.of(near.player));
        require(near.plays.size() == played + 1 && near.stops.size() == stopped + 1,
                "An untracked replacement immediately restarts listener-relative playback of the same track");
        replacement.tracked.add(near.player);
        music.update(replacement.entity, List.of(near.player));
        require(near.plays.size() == played + 1 && near.stops.size() == stopped + 1,
                "Beginning to track the replacement cashout does not restart listener-relative music");
        stopped(near.lastStop(), LAVA);
        playback(near.lastPlay(), near.player, LAVA);
        Actor sameId = new Actor(city, 1, 69, 0, replacement.id);
        sameId.tracked.add(near.player);
        played = near.plays.size();
        music.update(sameId.entity, List.of(near.player));
        require(near.plays.size() == played, "Source identity uses its UUID instead of Java wrapper identity");

        music.update(null, List.of(near.player));
        stopped(near.lastStop(), LAVA);
        stopped = near.stops.size();
        music.update(null, List.of(near.player));
        require(near.stops.size() == stopped, "An inactive cashout clears listeners once");
        music.update(source.entity, List.of(near.player));
        playback(near.lastPlay(), near.player, PIGSTEP);
        music.clear();
        System.out.println("FinalsCashoutMusicCheck: " + checks + " checks passed");
    }

    private record Played(Entity source, String sound, SoundCategory category, float volume, float pitch) {}
    private record Stopped(String sound, SoundCategory category) {}

    private static void playback(Played played, Entity source, String sound) {
        require(played.source == source && played.sound.equals(sound) && played.category == SoundCategory.RECORDS
                        && Math.abs(played.volume - .30f) < .00001f && played.pitch == 1f,
                "Music uses the listener's own player, selected vanilla track, RECORDS category, 30% volume and normal pitch");
    }

    private static void stopped(Stopped stopped, String sound) {
        require(stopped.sound.equals(sound) && stopped.category == SoundCategory.RECORDS,
                "Cleanup stops only the exact cashout track in the RECORDS category");
    }

    private static final class Music {
        final Object helper;
        Music(Object helper) { this.helper = helper; }
        void update(Entity source, Collection<Player> audience) throws Exception {
            invoke("update", new Class<?>[] {Entity.class, Collection.class}, source, audience);
        }
        void remove(Player player) throws Exception { invoke("remove", new Class<?>[] {Player.class}, player); }
        void clear() throws Exception { invoke("clear", new Class<?>[0]); }
        void invoke(String name, Class<?>[] parameters, Object... arguments) throws Exception {
            Method method = helper.getClass().getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            try { method.invoke(helper, arguments); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw (Error) failure.getCause();
            }
        }
    }

    private static final class Actor {
        final UUID id;
        final Entity entity;
        final Set<Player> tracked = new HashSet<>();
        Location location;
        Actor(World world, double x, double y, double z, UUID id) {
            this.id = id; location = new Location(world, x, y, z);
            entity = proxy(Entity.class, (receiver, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "getTrackedBy" -> tracked;
                case "isValid" -> true;
                case "isDead" -> false;
                case "equals" -> receiver == args[0];
                case "hashCode" -> System.identityHashCode(receiver);
                default -> throw new AssertionError("Unexpected source operation " + method.getName());
            });
        }
    }

    private static final class Listener {
        final UUID id = UUID.randomUUID();
        final Player player;
        final List<Played> plays = new ArrayList<>();
        final List<Stopped> stops = new ArrayList<>();
        Location location;
        boolean online = true;
        Listener(World world, double x, double y, double z) {
            location = new Location(world, x, y, z);
            player = proxy(Player.class, (receiver, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "isOnline" -> online;
                case "equals" -> receiver == args[0];
                case "hashCode" -> System.identityHashCode(receiver);
                case "playSound" -> {
                    require(args.length == 5 && args[0] instanceof Entity && args[1] instanceof String,
                            "Playback uses Player.playSound(Entity,String,SoundCategory,float,float)");
                    plays.add(new Played((Entity) args[0], (String) args[1], (SoundCategory) args[2], (float) args[3], (float) args[4]));
                    yield null;
                }
                case "stopSound" -> {
                    require(args.length == 2 && args[0] instanceof String && args[1] instanceof SoundCategory,
                            "Stopping music never stops every sound or a whole category");
                    stops.add(new Stopped((String) args[0], (SoundCategory) args[1]));
                    yield null;
                }
                default -> throw new AssertionError("Unexpected listener operation " + method.getName());
            });
        }
        Played lastPlay() { return plays.getLast(); }
        Stopped lastStop() { return stops.getLast(); }
    }

    private static World world(String name) {
        UUID id = UUID.randomUUID();
        return proxy(World.class, (receiver, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "getUID" -> id;
            case "equals" -> receiver == args[0];
            case "hashCode" -> System.identityHashCode(receiver);
            case "toString" -> name;
            default -> throw new AssertionError("Unexpected world operation " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
