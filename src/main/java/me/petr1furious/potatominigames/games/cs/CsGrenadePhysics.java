package me.petr1furious.potatominigames.games.cs;

import org.bukkit.util.Vector;

/** Native potion/snowball arcs, with CS utility retaining its collision bounces and fuse. */
final class CsGrenadePhysics {
    enum ThrowMode {
        SHORT(.5, .05),
        LONG(1.5, .03);
        final double speed, gravity;

        ThrowMode(double speed, double gravity) {
            this.speed = speed;
            this.gravity = gravity;
        }
    }

    static Vector launch(Vector direction, Vector movement, boolean grounded, ThrowMode mode) {
        Vector velocity = direction.clone().normalize();
        if (mode == ThrowMode.SHORT) {
            // ThrowablePotionItem uses a -20-degree pitch offset on its vertical component.
            double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
            double tilt = Math.toRadians(20);
            velocity.setY(velocity.getY() * Math.cos(tilt) + horizontal * Math.sin(tilt));
        }
        velocity.normalize().multiply(mode.speed);
        return velocity.add(
                new Vector(movement.getX(), grounded ? 0 : movement.getY(), movement.getZ()));
    }

    static void airTick(Vector velocity, ThrowMode mode) {
        // ThrowableProjectile.tick applies gravity, then air drag, then moves.
        velocity.setY(velocity.getY() - mode.gravity).multiply(.99);
    }

    private CsGrenadePhysics() {}
}
