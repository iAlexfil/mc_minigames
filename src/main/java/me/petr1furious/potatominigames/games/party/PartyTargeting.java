package me.petr1furious.potatominigames.games.party;

import org.bukkit.entity.Player;

import java.util.Collection;

final class PartyTargeting {
    static Player nearestVisible(Player caster, Collection<Player> candidates, double range) {
        Player nearest = null;
        double bestDistance = range * range;
        for (Player target : candidates) {
            if (target == null || target.equals(caster) || !PvpClass.visibleTarget(caster, target, range)) continue;
            double distance = caster.getLocation().distanceSquared(target.getLocation());
            if (nearest == null || distance < bestDistance) {
                nearest = target;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    static Player select(Player caster, Collection<Player> candidates, double range) {
        Player selected = null;
        double best = .75, bestDistance = Double.MAX_VALUE;
        var eye = caster.getEyeLocation();
        var facing = eye.getDirection();
        for (Player p : candidates) {
            if (p.equals(caster)
                    || !p.getWorld().equals(caster.getWorld())
                    || !caster.hasLineOfSight(p)) continue;
            var delta = p.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = delta.lengthSquared();
            if (distance > range * range || distance < 1.e-6) continue;
            double alignment = delta.normalize().dot(facing);
            if (alignment > best || alignment == best && distance < bestDistance) {
                best = alignment;
                bestDistance = distance;
                selected = p;
            }
        }
        return selected;
    }
}
