package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.*;

/** Fixed block states keep native footholds and swept collision geometry identical. */
record HoleWallBlock(Material material, Shape shape) {
    enum Shape {
        FULL,
        LOWER_SLAB,
        UPPER_SLAB,
        STAIRS,
        FENCE
    }

    static final HoleWallBlock STONE = new HoleWallBlock(Material.STONE_BRICKS, Shape.FULL);

    String data(HoleWallPhysics.Direction direction) {
        String properties =
                switch (shape) {
                    case FULL -> "";
                    case LOWER_SLAB -> "[type=bottom]";
                    case UPPER_SLAB -> "[type=top]";
                    case STAIRS -> "[facing=north,half=bottom,shape=straight]";
                    case FENCE ->
                            direction.dx == 0
                                    ? "[north=false,south=false,east=true,west=true]"
                                    : "[north=true,south=true,east=false,west=false]";
                };
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT) + properties;
    }

    List<BoundingBox> boxes(HoleWallPhysics.Direction direction) {
        return switch (shape) {
            case FULL -> List.of(new BoundingBox(0, 0, 0, 1, 1, 1));
            case LOWER_SLAB -> List.of(new BoundingBox(0, 0, 0, 1, .5, 1));
            case UPPER_SLAB -> List.of(new BoundingBox(0, .5, 0, 1, 1, 1));
            case STAIRS ->
                    List.of(
                            new BoundingBox(0, 0, 0, 1, .5, 1),
                            new BoundingBox(0, .5, 0, 1, 1, .5));
            case FENCE ->
                    direction.dx == 0
                            ? List.of(new BoundingBox(0, 0, .375, 1, 1.5, .625))
                            : List.of(new BoundingBox(.375, 0, 0, .625, 1.5, 1));
        };
    }
}
