package me.petr1furious.potatominigames.games.cs;

import org.bukkit.util.Vector;

/** Minecraft 26.3 snowball air motion, with CS utility retaining its collision bounces and fuse. */
final class CsGrenadePhysics {
    static Vector launch(Vector direction, Vector movement, boolean grounded) {
        Vector velocity = direction.clone().normalize().multiply(1.5);
        return velocity.add(
                new Vector(movement.getX(), grounded ? 0 : movement.getY(), movement.getZ()));
    }

    static void airTick(Vector velocity) {
        // ThrowableProjectile.tick applies gravity, then air drag, then moves.
        velocity.setY(velocity.getY() - .03).multiply(.99);
    }

    private CsGrenadePhysics() {}
}
