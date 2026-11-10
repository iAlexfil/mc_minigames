package me.petr1furious.potatominigames.games.party;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.util.*;

/** Harmless visual judge; the beam points at an invisible anchor inside the reviewed model. */
final class BuildMemoryJury {
    private final ElderGuardian guardian;
    private final Silverfish anchor;
    private final List<BlockDisplay> highlights = new ArrayList<>();

    BuildMemoryJury(World world) {
        guardian =
                world.spawn(
                        new Location(world, 2.5, 84, 2.5),
                        ElderGuardian.class,
                        entity -> {
                            entity.setAI(false);
                            entity.setGravity(false);
                            entity.setInvulnerable(true);
                            entity.setSilent(true);
                            entity.setCollidable(false);
                            entity.setPersistent(false);
                            entity.setRemoveWhenFarAway(false);
                        });
        anchor =
                world.spawn(
                        new Location(world, 2.5, 84, 2.5),
                        Silverfish.class,
                        entity -> {
                            entity.setInvisible(true);
                            entity.setAI(false);
                            entity.setSilent(true);
                            entity.setCollidable(false);
                            entity.setGravity(false);
                            entity.setPersistent(false);
                            entity.setRemoveWhenFarAway(false);
                        });
        // Native guardians reject invulnerable targets and invisible armor stands. The session
        // cancels all damage to this invisible, immobile anchor; the judge itself has no AI.
        guardian.setTarget(anchor);
        guardian.setLaser(false);
    }

    void inspect(Location center, Collection<Location> errors) {
        guardian.setLaser(false);
        anchor.teleport(center);
        Location looking = guardian.getLocation();
        looking.setDirection(center.toVector().subtract(looking.toVector()));
        guardian.setRotation(looking.getYaw(), looking.getPitch());
        guardian.setLaser(true);
        for (Location cell : errors) {
            highlights.add(
                    cell.getWorld()
                            .spawn(
                                    cell.clone().add(-.005, -.005, -.005),
                                    BlockDisplay.class,
                                    display -> {
                                        display.setPersistent(false);
                                        display.setBlock(
                                                Material.RED_STAINED_GLASS.createBlockData());
                                        display.setGlowing(true);
                                        display.setGlowColorOverride(Color.RED);
                                        display.setBrightness(new Display.Brightness(15, 15));
                                        display.setViewRange(4);
                                        var transform = display.getTransformation();
                                        transform.getScale().set(1.01f);
                                        display.setTransformation(transform);
                                    }));
        }
        center.getWorld().playSound(center, Sound.ENTITY_GUARDIAN_ATTACK, .65f, 1.1f);
    }

    void stopBeam() {
        guardian.setLaser(false);
    }

    void reset() {
        stopBeam();
        highlights.forEach(Entity::remove);
        highlights.clear();
    }

    void close() {
        reset();
        guardian.remove();
        anchor.remove();
    }
}
