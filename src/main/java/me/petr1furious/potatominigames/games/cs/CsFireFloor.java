package me.petr1furious.potatominigames.games.cs;

import io.papermc.paper.math.BlockPosition;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/** Client-only burning floor: no native ignition, spread, terrain changes or extra damage. */
final class CsFireFloor {
    private record View(Player player, Set<BlockPosition> shown) {}

    private final World world;
    private final BlockData flame = Bukkit.createBlockData(Material.FIRE);
    private final Map<Location, Set<BlockPosition>> plans = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();

    CsFireFloor(World world) {
        this.world = world;
    }

    void update(Collection<Location> centers, Collection<Player> audience, long tick) {
        Set<Location> sources = new HashSet<>(centers);
        boolean refresh = tick % 20 == 0;
        boolean changed = !plans.keySet().equals(sources);
        if (!changed && tick % 5 != 0) return;
        // Refresh terrain queries after broken glass and refresh packets after chunk loads.
        if (refresh) plans.clear();
        plans.keySet().retainAll(sources);
        Set<BlockPosition> required = new HashSet<>();
        for (Location center : sources)
            required.addAll(plans.computeIfAbsent(center.clone(), this::footprint));
        Set<UUID> present = new HashSet<>();
        for (Player player : audience) {
            if (!player.isOnline() || !world.equals(player.getWorld())) continue;
            UUID id = player.getUniqueId();
            present.add(id);
            View previous = views.get(id);
            if (previous != null && previous.player != player) {
                restore(previous);
                views.remove(id);
            }
            View view = views.computeIfAbsent(id, ignored -> new View(player, new HashSet<>()));
            Map<BlockPosition, BlockData> updates = new HashMap<>();
            for (BlockPosition position : view.shown)
                if (!required.contains(position)) updates.put(position, actual(position));
            for (BlockPosition position : required)
                if (refresh || !view.shown.contains(position)) updates.put(position, flame);
            if (!updates.isEmpty()) player.sendMultiBlockChange(updates);
            view.shown.clear();
            view.shown.addAll(required);
        }
        for (UUID id : List.copyOf(views.keySet())) if (!present.contains(id)) release(id);
    }

    private Set<BlockPosition> footprint(Location center) {
        Set<BlockPosition> positions = new HashSet<>();
        Location origin = center.clone().add(0, .2, 0);
        for (int x = center.getBlockX() - 4; x <= center.getBlockX() + 4; x++)
            for (int z = center.getBlockZ() - 4; z <= center.getBlockZ() + 4; z++)
                for (int y = Math.max(world.getMinHeight() + 1, center.getBlockY() - 4);
                        y <= Math.min(world.getMaxHeight() - 1, center.getBlockY() + 4);
                        y++) {
                    Location feet = new Location(world, x + .5, y, z + .5);
                    if (feet.distanceSquared(center) >= 16) continue;
                    var block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()
                            || !block.getRelative(BlockFace.DOWN)
                                    .getBlockData()
                                    .isFaceSturdy(BlockFace.UP, BlockSupport.FULL)) continue;
                    Vector delta =
                            feet.clone().add(0, .3, 0).toVector().subtract(origin.toVector());
                    if (delta.lengthSquared() > .0001
                            && world.rayTraceBlocks(
                                            origin,
                                            delta.clone().normalize(),
                                            delta.length(),
                                            FluidCollisionMode.NEVER,
                                            true)
                                    != null) continue;
                    positions.add(io.papermc.paper.math.Position.block(x, y, z));
                }
        return positions;
    }

    private BlockData actual(BlockPosition position) {
        return world.getBlockAt(position.blockX(), position.blockY(), position.blockZ())
                .getBlockData();
    }

    private void restore(View view) {
        if (!view.player.isOnline() || !world.equals(view.player.getWorld())) return;
        Map<BlockPosition, BlockData> updates = new HashMap<>();
        for (BlockPosition position : view.shown) updates.put(position, actual(position));
        if (!updates.isEmpty()) view.player.sendMultiBlockChange(updates);
    }

    void release(UUID id) {
        View view = views.remove(id);
        if (view != null) restore(view);
    }

    void clear() {
        views.values().forEach(this::restore);
        views.clear();
        plans.clear();
    }
}
