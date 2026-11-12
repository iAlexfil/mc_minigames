package me.petr1furious.potatominigames.games.finals;

import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.List;

/** A thrown item's entire swept volume must hit a reachable cashout before depositing. */
public final class FinalsCashboxFlightCheck {
    private static Method hit;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try {
            type =
                    Class.forName(
                            "me.petr1furious.potatominigames.games.finals.FinalsCashboxFlight");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "Thrown cashboxes need swept collision with live cashouts", missing);
        }
        hit =
                type.getDeclaredMethod(
                        "hit", Vector.class, Vector.class, Vector.class, List.class, double.class);
        hit.setAccessible(true);
        Vector from = new Vector(-4, .5, 0), to = new Vector(4, .5, 0);
        Vector half = new Vector(.125, .125, .125), cashout = new Vector(0, 0, 0);
        List<Vector> stations = List.of(cashout, new Vector(5, 0, 0));
        expect(
                0,
                from,
                to,
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "A fast throw crossing the entire cashout between ticks deposits");
        expect(
                1,
                from,
                to,
                half,
                List.of(new Vector(3, 0, 0), cashout),
                Double.POSITIVE_INFINITY,
                "The first cashout along the flight wins even when listed second");
        expect(
                0,
                new Vector(0, .5, 0),
                to,
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "Starting overlap counts before a cashbox leaves the station");
        expect(
                -1,
                new Vector(-4, .5, 2),
                new Vector(4, .5, 2),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "A throw beside the entire station volume misses");
        expect(
                -1,
                from,
                to,
                half,
                stations,
                3,
                "Terrain blocking the flight before contact prevents depositing");
        expect(
                0,
                from,
                to,
                half,
                stations,
                3.4,
                "Terrain beyond the first item-edge contact does not block depositing");
        expect(
                0,
                new Vector(-.6, .5, 0),
                new Vector(-.6, .5, 0),
                half,
                stations,
                0,
                "A stationary overlapping item deposits, including a station falling onto it");
        expect(
                -1,
                new Vector(-3, .5, 0),
                new Vector(-3, .5, 0),
                half,
                stations,
                0,
                "Zero movement outside a station cannot invent a hit");
        expect(
                0,
                new Vector(-4, .5, .625),
                new Vector(4, .5, .625),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "The item's outer edge grazing the station counts as contact");
        expect(
                -1,
                new Vector(-4, .5, .626),
                new Vector(4, .5, .626),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "A near miss beyond the item's actual half width remains a miss");
        expect(
                0,
                new Vector(-4, 1.125, 0),
                new Vector(4, 1.125, 0),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "The item's bottom touching the station top counts as contact");
        expect(
                -1,
                new Vector(-4, 1.126, 0),
                new Vector(4, 1.126, 0),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "A throw above the station and item height misses");
        expect(
                0,
                new Vector(-10, .5, -7),
                new Vector(-2, .5, -7),
                half,
                List.of(new Vector(-6, 0, -7)),
                Double.POSITIVE_INFINITY,
                "Collision works in negative world coordinates");
        expect(
                0,
                new Vector(-4, -3.5, 0),
                new Vector(4, -3.5, 0),
                half,
                List.of(new Vector(0, -4, 0)),
                Double.POSITIVE_INFINITY,
                "Collision uses a falling cashout's current height");
        expect(
                0,
                new Vector(-2, .5, 0),
                new Vector(-.625, .5, 0),
                half,
                stations,
                Double.POSITIVE_INFINITY,
                "Contact exactly at the end of the flight segment counts");
        expect(
                -1,
                from,
                to,
                half,
                List.of(),
                Double.POSITIVE_INFINITY,
                "A missing station cannot receive the cashbox");
        expect(
                -1,
                from,
                to,
                half,
                stations,
                -1,
                "A fully blocked movement interval cannot deposit");
        expect(
                0,
                new Vector(-4, .5, 1),
                new Vector(4, .5, 1),
                new Vector(.25, .125, .5),
                stations,
                Double.POSITIVE_INFINITY,
                "Each item half-extent is used independently");
        require(
                from.equals(new Vector(-4, .5, 0))
                        && to.equals(new Vector(4, .5, 0))
                        && half.equals(new Vector(.125, .125, .125))
                        && cashout.equals(new Vector(0, 0, 0)),
                "Collision leaves caller vectors and live station positions unchanged");
        System.out.println("FinalsCashboxFlightCheck: " + checks + " checks passed");
    }

    private static void expect(
            int expected,
            Vector from,
            Vector to,
            Vector half,
            List<Vector> stations,
            double maxDistance,
            String message)
            throws Exception {
        int actual = (Integer) hit.invoke(null, from, to, half, stations, maxDistance);
        require(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
