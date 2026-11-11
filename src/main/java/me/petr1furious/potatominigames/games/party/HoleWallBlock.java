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
        FENCE,
        FENCE_POST,
        BARS,
        CHAIN,
        ROD,
        WALL_POST,
        LECTERN,
        CORE,
        POT
    }

    static final HoleWallBlock STONE = new HoleWallBlock(Material.STONE_BRICKS, Shape.FULL);

    HoleWallBlock upperSlab() {
        Material slab =
                switch (material) {
                    case STONE_BRICKS -> Material.STONE_BRICK_SLAB;
                    case DARK_PRISMARINE -> Material.DARK_PRISMARINE_SLAB;
                    case PRISMARINE_BRICKS -> Material.PRISMARINE_BRICK_SLAB;
                    case PURPUR_BLOCK -> Material.PURPUR_SLAB;
                    default -> throw new IllegalStateException("No matching slab for " + material);
                };
        return new HoleWallBlock(slab, Shape.UPPER_SLAB);
    }

    String data(HoleWallPhysics.Direction direction) {
        String properties =
                switch (shape) {
                    case FULL, CORE, POT -> "";
                    case LOWER_SLAB -> "[type=bottom]";
                    case UPPER_SLAB -> "[type=top]";
                    case STAIRS -> "[facing=north,half=bottom,shape=straight]";
                    case FENCE ->
                            direction.dx == 0
                                    ? "[north=false,south=false,east=true,west=true]"
                                    : "[north=true,south=true,east=false,west=false]";
                    case FENCE_POST, BARS -> "[north=false,south=false,east=false,west=false]";
                    case CHAIN -> "[axis=y]";
                    case ROD -> "[facing=up]";
                    case WALL_POST -> "[up=true,north=none,south=none,east=none,west=none]";
                    case LECTERN -> "[facing=north]";
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
            case FENCE_POST -> List.of(new BoundingBox(.375, 0, .375, .625, 1.5, .625));
            case BARS -> List.of(new BoundingBox(.4375, 0, .4375, .5625, 1, .5625));
            case CHAIN -> List.of(new BoundingBox(.40625, 0, .40625, .59375, 1, .59375));
            case ROD -> List.of(new BoundingBox(.375, 0, .375, .625, 1, .625));
            case WALL_POST -> List.of(new BoundingBox(.25, 0, .25, .75, 1.5, .75));
            case LECTERN ->
                    List.of(
                            new BoundingBox(0, 0, 0, 1, .125, 1),
                            new BoundingBox(.25, .125, .25, .75, .875, .75));
            case CORE -> List.of(new BoundingBox(.25, 0, .25, .75, .5, .75));
            case POT -> List.of(new BoundingBox(.3125, 0, .3125, .6875, .375, .6875));
        };
    }
}
