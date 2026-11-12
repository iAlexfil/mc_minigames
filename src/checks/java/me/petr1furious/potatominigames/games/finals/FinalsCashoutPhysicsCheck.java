package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/** Regression checks for swept cashout gravity; no server required. */
public final class FinalsCashoutPhysicsCheck {
    private static Method fall;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> physics;
        try {
            physics =
                    Class.forName(
                            "me.petr1furious.potatominigames.games.finals.FinalsCashoutPhysics");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "Cashouts need simulated gravity instead of fixed gold blocks", missing);
        }
        Method displayPosition = physics.getDeclaredMethod("displayPosition", Location.class);
        displayPosition.setAccessible(true);
        for (float[] angles : new float[][]{{87.30008f, 76.34995f}, {179.84937f, 71.54993f}, {37, 90}}) {
            Location saved = new Location(null, 700.3, 96.5, 700.8, angles[0], angles[1]);
            Location display = (Location) displayPosition.invoke(null, saved);
            close(display.getYaw(), 0, "Saved editor yaw must not rotate a cashout around its corner");
            close(display.getPitch(), 0, "Saved editor pitch must not tilt a cashout into its support");
            close(display.getX(), 699.8, "The display starts at the physics body's exact minimum X");
            close(display.getY(), 96.5, "The display rests at the physics body's surface height");
            close(display.getZ(), 700.3, "The display starts at the physics body's exact minimum Z");
            close(saved.getYaw(), angles[0], "Rendering leaves saved player-facing yaw intact");
            close(saved.getPitch(), angles[1], "Rendering leaves saved player-facing pitch intact");
        }
        fall =
                physics.getDeclaredMethod(
                        "fall",
                        double.class,
                        double.class,
                        double.class,
                        double.class,
                        Collection.class);
        fall.setAccessible(true);
        Object first = step(10, 0, List.of());
        close(value(first, "y"), 9.92, "An unsupported station begins falling");
        close(value(first, "velocity"), -.08, "Gravity accelerates downward");
        Object resting = step(5, 0, List.of(new BoundingBox(0, 4, 0, 1, 5, 1)));
        close(value(resting, "y"), 5, "A supported body stays on its floor");
        close(value(resting, "velocity"), 0, "A resting body does not accumulate velocity");
        Object thin = step(8, -1.4, List.of(new BoundingBox(0, 6, 0, 1, 6.5, 1)));
        close(value(thin, "y"), 6.52, "A fall above a slab continues until contact");
        Object landed = step(6.52, -1.48, List.of(new BoundingBox(0, 6, 0, 1, 6.5, 1)));
        close(value(landed, "y"), 6.5, "Swept collision prevents tunneling through thin terrain");
        close(value(landed, "velocity"), 0, "Landing cancels falling velocity");
        Object edge = step(8, -1.4, List.of(new BoundingBox(.98, 6, 0, 1.3, 7, 1)));
        close(value(edge, "y"), 7, "Even partial footprint support catches the station");
        Object beside = step(8, -1.4, List.of(new BoundingBox(1.1, 6, 0, 2, 7, 1)));
        close(value(beside, "y"), 6.52, "Terrain outside the footprint does not stop falling");
        Object nearest =
                step(
                        8,
                        -1.4,
                        List.of(
                                new BoundingBox(0, 6, 0, 1, 6.8, 1),
                                new BoundingBox(0, 6, 0, 1, 7.3, 1)));
        close(value(nearest, "y"), 7.3, "The first surface along the sweep wins");
        Object capped = step(20, -100, List.of());
        close(value(capped, "y"), 18.5, "Terminal speed bounds each terrain query");
        Object removed = step(5, 0, List.of());
        close(value(removed, "y"), 4.92, "Destroying support restarts gravity immediately");
        Object embeddedSlab = step(5.25, 0, List.of(new BoundingBox(0, 5, 0, 1, 5.5, 1)));
        close(value(embeddedSlab, "y"), 5.5,
                "A cashout initially embedded in a slab is lifted onto its surface");
        close(value(embeddedSlab, "velocity"), 0, "Resolving initial penetration cancels downward momentum");
        Object risingTerrain = step(5, -.8, List.of(new BoundingBox(0, 5, 0, 1, 6, 1)));
        close(value(risingTerrain, "y"), 6, "Terrain placed inside a live cashout lifts the entire body clear");
        Object fence = step(5, 0, List.of(new BoundingBox(.375, 4, .375, .625, 5.5, .625)));
        close(value(fence, "y"), 5.5, "A fence extending above its block catches an overlapping cashout");
        Object visualEdge = step(8, -1.4, List.of(new BoundingBox(.995, 6, 0, 1.2, 7, 1)));
        close(value(visualEdge, "y"), 7, "The full visible block footprint collides at its outer edge");
        Object touchingCeiling = step(5, 0, List.of(new BoundingBox(0, 6, 0, 1, 7, 1),
                new BoundingBox(0, 4, 0, 1, 5, 1)));
        close(value(touchingCeiling, "y"), 5, "Touching a ceiling without overlap does not teleport the body upward");
        Object cascading = step(5.25, -.8, List.of(new BoundingBox(0, 6.25, 0, 1, 6.75, 1),
                new BoundingBox(0, 5, 0, 1, 5.5, 1)));
        close(value(cascading, "y"), 6.75, "Penetration correction rechecks the complete body against higher terrain");
        Method movement;
        try {
            movement = physics.getDeclaredMethod("clipMovement", org.bukkit.util.Vector.class,
                    org.bukkit.util.Vector.class, Collection.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Winch movement must sweep the complete cashout body against terrain", missing);
        }
        movement.setAccessible(true);
        var start = new org.bukkit.util.Vector(.5, 5, .5);
        var desired = new org.bukkit.util.Vector(.35, -.2, 0);
        var floor = new BoundingBox(-2, 4, -2, 3, 5, 3);
        var dragged = (org.bukkit.util.Vector) movement.invoke(null, start, desired, List.of(floor));
        close(dragged.getX(), .35, "Dragging across a supporting floor permits horizontal movement");
        close(dragged.getY(), 0, "Dragging toward lower terrain never sinks into the current floor");
        var wall = new BoundingBox(1.2, 5, 0, 1.3, 6, 1);
        var stopped = (org.bukkit.util.Vector) movement.invoke(null, start,
                new org.bukkit.util.Vector(.35, 0, 0), List.of(wall));
        require(stopped.getX() > .19 && stopped.getX() <= .2,
                "The complete moving cashout stops at a thin wall before penetrating it");
        var ceiling = new BoundingBox(0, 6.1, 0, 1, 6.2, 1);
        var lifted = (org.bukkit.util.Vector) movement.invoke(null, start,
                new org.bukkit.util.Vector(0, .35, 0), List.of(ceiling));
        require(lifted.getY() > .09 && lifted.getY() <= .1,
                "Upward pulling respects the cashout's full head clearance");
        var away = (org.bukkit.util.Vector) movement.invoke(null, start,
                new org.bukkit.util.Vector(-.35, 0, 0), List.of(new BoundingBox(1, 5, 0, 2, 6, 1)));
        close(away.getX(), -.35, "A body touching a wall can be pulled safely away from it");
        require(start.equals(new org.bukkit.util.Vector(.5, 5, .5))
                        && desired.equals(new org.bukkit.util.Vector(.35, -.2, 0)),
                "Swept pull calculations never mutate source position or requested direction");
        System.out.println("FinalsCashoutPhysicsCheck: " + checks + " checks passed");
    }

    private static Object step(double y, double velocity, Collection<BoundingBox> boxes)
            throws Exception {
        return fall.invoke(null, .5, y, .5, velocity, boxes);
    }

    private static double value(Object result, String name) throws Exception {
        Method accessor = result.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return ((Number) accessor.invoke(result)).doubleValue();
    }

    private static void close(double actual, double expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 1e-8)
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
