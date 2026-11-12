package me.petr1furious.potatominigames.games.finals;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Packed native palette checks independent of a running server. */
public final class FinalsSlimeSnapshotCheck {
    private static int checks;

    public static void main(String[] args) {
        var entire = new FinalsMapData.Bounds(-32, 64, 16, -17, 79, 31);
        var clipped = new FinalsMapData.Bounds(-31, 64, 16, -18, 71, 31);
        List<BinaryTag> colors = new ArrayList<>();
        for (String material : List.of("concrete", "wool"))
            for (String color :
                    List.of(
                            "white",
                            "orange",
                            "magenta",
                            "light_blue",
                            "yellow",
                            "lime",
                            "pink",
                            "gray",
                            "light_gray",
                            "cyan",
                            "purple",
                            "blue",
                            "brown",
                            "green",
                            "red",
                            "black")) colors.add(state("minecraft:" + color + "_" + material));
        var source = packed(colors, i -> (i / 16) % 32);
        require(
                FinalsSlimeSnapshot.restrict(source, entire, -32, 64, 16) == source,
                "Full safe sections retain native encoded palettes without a block-by-block copy");
        var result = FinalsSlimeSnapshot.restrict(source, clipped, -32, 64, 16);
        require(
                result.getList("palette").size() == 33,
                "Clipping can add air and grow a palette across the five/six-bit threshold");
        boolean contents = true;
        for (int i = 0; i < 4096; i++) {
            boolean inside = (i & 15) >= 1 && (i & 15) <= 14 && (i >>> 8) < 8;
            BinaryTag expected = inside ? colors.get((i / 16) % 32) : state("minecraft:air");
            contents &= decode(result, i).equals(expected);
        }
        require(
                contents,
                "All negative-coordinate boundary cells survive repacking, including long-word"
                        + " padding");
        require(
                decode(source, 0).equals(colors.getFirst())
                        && source.getList("palette").size() == 32,
                "Cropping never mutates the editor's detached source palette");

        List<BinaryTag> dangerous =
                List.of(state("minecraft:stone"), state("minecraft:command_block"));
        var outside = packed(dangerous, i -> (i & 15) == 0 ? 1 : 0);
        var croppedOutside = FinalsSlimeSnapshot.restrict(outside, clipped, -32, 64, 16);
        require(
                croppedOutside.getList("palette").stream()
                        .noneMatch(tag ->
                                ((CompoundBinaryTag) tag)
                                        .getString("Name")
                                        .equals("minecraft:command_block")),
                "Unsafe blocks outside the exact selection are clipped instead of rejecting the"
                        + " map");
        var unused = packed(dangerous, i -> 0);
        require(
                FinalsSlimeSnapshot.restrict(unused, entire, -32, 64, 16).getList("palette").size()
                        == 1,
                "An unused unsafe palette entry does not reject an otherwise safe section");
        try {
            FinalsSlimeSnapshot.restrict(
                    packed(dangerous, i -> i == 1 ? 1 : 0), clipped, -32, 64, 16);
            throw new AssertionError("Unsafe blocks inside the selection must fail");
        } catch (IllegalArgumentException expected) {
            require(
                    expected.getMessage().contains("command")
                            || expected.getMessage().contains("unsafe"),
                    "Inside unsafe blocks produce the map's localized rejection");
        }
        var absent = FinalsSlimeSnapshot.restrict(source, clipped, -32, 80, 16);
        require(
                absent.getList("palette").size() == 1
                        && absent.getList("palette")
                                .getCompound(0)
                                .getString("Name")
                                .equals("minecraft:air"),
                "Sections outside a legacy selection's Y range become empty");

        var nativeAir = packed(List.of(StringBinaryTag.stringBinaryTag("minecraft:air")), i -> 0);
        require(
                FinalsSlimeSnapshot.restrict(nativeAir, clipped, -32, 64, 16) == nativeAir,
                "Native compact string air palettes are accepted");
        var stairs =
                CompoundBinaryTag.builder()
                        .putString("id", "minecraft:oak_stairs")
                        .put(
                                "properties",
                                CompoundBinaryTag.builder()
                                        .putString("facing", "west")
                                        .putString("half", "top")
                                        .build())
                        .build();
        var nativeMixed =
                packed(
                        List.of(
                                StringBinaryTag.stringBinaryTag("minecraft:air"),
                                StringBinaryTag.stringBinaryTag("minecraft:stone"),
                                stairs),
                        i -> i % 3);
        require(
                FinalsSlimeSnapshot.restrict(nativeMixed, entire, -32, 64, 16) == nativeMixed,
                "Full native mixed palettes retain their original representation");
        var mixedResult = FinalsSlimeSnapshot.restrict(nativeMixed, clipped, -32, 64, 16);
        require(
                decode(mixedResult, 1).equals(StringBinaryTag.stringBinaryTag("minecraft:stone"))
                        && decode(mixedResult, 2).equals(stairs)
                        && decode(mixedResult, 0)
                                .equals(StringBinaryTag.stringBinaryTag("minecraft:air")),
                "Clipped native strings and property compounds retain their exact block states");
        require(
                decode(FinalsSlimeSnapshot.restrict(nativeMixed, clipped, -32, 80, 16), 0)
                        .equals(StringBinaryTag.stringBinaryTag("minecraft:air")),
                "Sections outside the height selection use native compact air encoding");
        var onlyProperties =
                FinalsSlimeSnapshot.restrict(packed(List.of(stairs), i -> 0), clipped, -32, 64, 16);
        require(
                decode(onlyProperties, 1).equals(stairs)
                        && decode(onlyProperties, 0)
                                .equals(StringBinaryTag.stringBinaryTag("minecraft:air")),
                "Modern property-only palettes use modern air even without an input string");
        var nativeDangerous =
                List.<BinaryTag>of(
                        StringBinaryTag.stringBinaryTag("minecraft:stone"),
                        StringBinaryTag.stringBinaryTag("minecraft:command_block"));
        require(
                FinalsSlimeSnapshot.restrict(
                                        packed(nativeDangerous, i -> (i & 15) == 0 ? 1 : 0),
                                        clipped,
                                        -32,
                                        64,
                                        16)
                                .getList("palette")
                                .size()
                        == 2,
                "Native compact unsafe entries outside the selection are clipped");
        try {
            FinalsSlimeSnapshot.restrict(
                    packed(nativeDangerous, i -> i == 1 ? 1 : 0), clipped, -32, 64, 16);
            throw new AssertionError("Native compact unsafe entries inside must fail");
        } catch (IllegalArgumentException expected) {
            require(
                    expected.getMessage().contains("command")
                            || expected.getMessage().contains("unsafe"),
                    "Native compact unsafe entries inside reject the map");
        }
        try {
            var dangerousProperties =
                    CompoundBinaryTag.builder()
                            .putString("id", "minecraft:command_block")
                            .put(
                                    "properties",
                                    CompoundBinaryTag.builder().putString("facing", "up").build())
                            .build();
            FinalsSlimeSnapshot.restrict(
                    packed(List.of(dangerousProperties), i -> 0), clipped, -32, 64, 16);
            throw new AssertionError("Native id/property unsafe entries inside must fail");
        } catch (IllegalArgumentException expected) {
            require(
                    expected.getMessage().contains("command")
                            || expected.getMessage().contains("unsafe"),
                    "Native id/property unsafe entries inside reject the map");
        }
        System.out.println("FinalsSlimeSnapshotCheck: " + checks + " checks passed");
    }

    private static CompoundBinaryTag state(String name) {
        return CompoundBinaryTag.builder().putString("Name", name).build();
    }

    private static CompoundBinaryTag packed(List<BinaryTag> palette, IntUnaryOperator value) {
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        int perLong = 64 / bits;
        long[] data = new long[(4096 + perLong - 1) / perLong];
        for (int i = 0; i < 4096; i++)
            data[i / perLong] |= (long) value.applyAsInt(i) << ((i % perLong) * bits);
        return CompoundBinaryTag.builder()
                .put("palette", ListBinaryTag.heterogeneousListBinaryTag().add(palette).build())
                .putLongArray("data", data)
                .build();
    }

    private static BinaryTag decode(CompoundBinaryTag states, int index) {
        var palette = states.getList("palette");
        if (palette.size() == 1) return palette.get(0);
        int width = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        int perLong = 64 / width;
        long packed = states.getLongArray("data")[index / perLong];
        return palette.get((int) ((packed >>> (index % perLong * width)) & ((1L << width) - 1)));
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
