package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exercises real validation, import boundaries, snapshot isolation and match geometry without a
 * server.
 */
public final class FinalsMapsCheck {
    public static void main(String[] args) throws Exception {
        geometry();
        arenaCopies();
        imports();
        System.out.println("FinalsMapsCheck passed");
    }

    private static Draft draft() {
        Draft draft = new Draft();
        draft.world = "editor";
        draft.first = new Point(-16, 64, -16, 0, 0);
        draft.second = new Point(16, 80, 16, 0, 0);
        draft.red.add(new Point(-12.5, 65, .5, -90, 0));
        draft.blue.add(new Point(12.5, 65, .5, 90, 0));
        draft.vault = new Point(.5, 65, .5, 0, 0);
        draft.stationA = new Point(.5, 65, -11.5, 0, 0);
        draft.stationB = new Point(.5, 65, 12.5, 0, 0);
        return draft;
    }

    private static void geometry() {
        Draft draft = draft();
        Saved saved = Saved.from(draft, "test.nbt");
        require(saved.bounds().volume() == 18_513, "Inclusive cuboid size is stable");
        require(
                saved.bounds().contains(new Point(16.99, 80.99, 16.99, 0, 0)),
                "Upper block belongs to bounds");
        require(!saved.bounds().contains(new Point(17, 65, 0, 0, 0)), "Upper edge is exclusive");
        YamlConfiguration yaml = new YamlConfiguration();
        draft.write(yaml);
        Saved restored = Saved.from(Draft.read(yaml), "test.nbt");
        require(restored.equals(saved), "Draft positions, facing and snapshot geometry round-trip");
        draft.red.clear();
        require(saved.red().size() == 1, "Saved spawns are immutable copies");
        fails(() -> Saved.from(draft, "test.nbt"), "Missing team spawn rejected");
        Draft close = draft();
        close.stationB = close.vault;
        fails(() -> Saved.from(close, "test.nbt"), "Overlapping objectives rejected");
        Draft outside = draft();
        outside.red.set(0, new Point(-17, 65, 0, 0, 0));
        fails(() -> Saved.from(outside, "test.nbt"), "Out-of-bounds spawn rejected");
        Draft noFloor = draft();
        noFloor.red.set(0, new Point(-12, 64, 0, 0, 0));
        fails(() -> Saved.from(noFloor, "test.nbt"), "Spawn support must be inside snapshot");
        Draft overlap = draft();
        overlap.blue.set(0, overlap.red.getFirst());
        fails(() -> Saved.from(overlap, "test.nbt"), "Opposing spawns cannot overlap");
        fails(() -> new Bounds(0, 0, 0, 512, 4, 5), "Per-axis bound enforced");
        fails(() -> new Bounds(0, 0, 0, 100, 100, 100), "Million-block limit enforced");
        fails(
                () -> new Bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 5, 5),
                "Bounds arithmetic cannot overflow");
        fails(() -> new Point(Double.NaN, 65, 0, 0, 0), "Non-finite position rejected");
    }

    private static void arenaCopies() {
        Saved saved = Saved.from(draft(), "test.nbt");
        FakeWorld editor = new FakeWorld("editor");
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) editor.blocks.put(new Cell(x, 64, z), Material.STONE);
        editor.blocks.put(new Cell(6, 66, 6), Material.STONE);
        Map<Cell, Material> snapshot = Map.copyOf(editor.blocks);
        FakeWorld first = new FakeWorld("match-one"), second = new FakeWorld("match-two");
        Map<World, FakeWorld> destinations = Map.of(first.world, first, second.world, second);
        int[] placements = {0};
        Structure structure =
                (Structure)
                        Proxy.newProxyInstance(
                                Structure.class.getClassLoader(),
                                new Class<?>[] {Structure.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("place")) {
                                        Location origin = (Location) args[0];
                                        require(
                                                origin.getBlockX() == -16
                                                        && origin.getBlockY() == 64
                                                        && origin.getBlockZ() == -16,
                                                "Placement preserves source coordinates");
                                        require(
                                                Boolean.FALSE.equals(args[1]),
                                                "Entities are excluded from match copies");
                                        destinations.get(origin.getWorld()).blocks.putAll(snapshot);
                                        placements[0]++;
                                        return null;
                                    }
                                    return primitive(method.getReturnType());
                                });
        FinalsMapArena a = new FinalsImportedArena(first.world, saved, structure);
        a.build();
        a.build();
        require(placements[0] == 1, "Building a copy is idempotent");
        require(editor.blocks.equals(snapshot), "Objective markers never modify the editor map");
        require(a.isVault(first.block(0, 65, 0)), "Vault interaction coordinates are preserved");
        require(
                a.stationAt(first.block(0, 65, -12)) == 0
                        && a.stationAt(first.block(0, 65, 12)) == 1,
                "Both cashouts map to the correct station");
        require(!a.isVault(editor.block(0, 65, 0)), "Objective checks reject another world");
        require(
                a.borderSize() == 33 && a.center().getX() == .5 && a.center().getZ() == .5,
                "Custom map border follows snapshot bounds");
        require(
                a.spawn(0, 4).equals(a.spawn(0, 0)),
                "Configured team spawns cycle deterministically");
        require(!a.isCover(first.block(6, 64, 6)), "Base floor is protected");
        require(!a.isCover(first.block(-13, 64, 0)), "Spawn support is protected");
        require(!a.isCover(first.block(0, 65, 0)), "Objective markers are protected");
        require(a.isCover(first.block(6, 66, 6)), "Interior cover is destructible");
        first.blocks.put(new Cell(6, 66, 6), Material.AIR);
        FinalsMapArena b = new FinalsImportedArena(second.world, saved, structure);
        b.build();
        require(
                second.blocks.get(new Cell(6, 66, 6)) == Material.STONE,
                "A new match restores destroyed cover");
        require(editor.blocks.equals(snapshot), "Match destruction leaves the source intact");
        require(
                !a.contains(new Location(first.world, 17, 65, 0)),
                "Players beyond a custom edge are out of bounds");
        require(
                !a.contains(new Location(editor.world, 0, 65, 0)),
                "Containment rejects the editor world");
    }

    private static void imports() throws Exception {
        Path temporary = Files.createTempDirectory("finals-map-check-");
        try {
            Path imports = Files.createDirectory(temporary.resolve("imports"));
            Path source = Files.createDirectory(imports.resolve("original"));
            Files.createDirectory(source.resolve("region"));
            Files.writeString(source.resolve("level.dat"), "level-data");
            Files.writeString(source.resolve("region/r.0.0.mca"), "region-data");
            Files.writeString(source.resolve("uid.dat"), "original-uuid");
            Files.createDirectory(source.resolve("datapacks"));
            Files.writeString(source.resolve("datapacks/commands.txt"), "must-not-copy");
            Path copy = temporary.resolve("copy");
            FinalsMapFiles.copyWorld(FinalsMapFiles.source(imports, "original"), copy);
            require(
                    Files.readString(copy.resolve("level.dat")).equals("level-data"),
                    "Native world metadata copies");
            require(
                    Files.readString(copy.resolve("region/r.0.0.mca")).equals("region-data"),
                    "Native region data copies");
            require(
                    !Files.exists(copy.resolve("uid.dat"))
                            && !Files.exists(copy.resolve("datapacks")),
                    "Identity and executable data do not copy");
            Files.writeString(copy.resolve("region/r.0.0.mca"), "changed-copy");
            require(
                    Files.readString(source.resolve("region/r.0.0.mca")).equals("region-data"),
                    "Editing a copy preserves the original");
            for (String path :
                    new String[] {
                        "../outside",
                        "/outside",
                        "C:/outside",
                        "original/../original",
                        "original\\region",
                        ""
                    })
                failsChecked(
                        () -> FinalsMapFiles.source(imports, path),
                        "Import path rejected: " + path);
            for (String path :
                    new String[] {
                        "../escape", "/absolute", "C:/file", "root/../level.dat", "root\\file"
                    }) fails(() -> FinalsMapFiles.zipPath(path), "ZIP path rejected: " + path);
            Path valid = imports.resolve("valid.zip");
            zip(valid, "wrapped/level.dat", "wrapped/region/r.0.0.mca");
            Path unzipped = temporary.resolve("unzipped");
            FinalsMapFiles.copyWorld(valid, unzipped);
            require(
                    Files.exists(unzipped.resolve("region/r.0.0.mca")),
                    "Wrapped vanilla ZIP imports");
            Path nativeSource = Files.createDirectory(imports.resolve("native-world"));
            Files.writeString(nativeSource.resolve("level.dat"), "native-level");
            Files.createDirectories(nativeSource.resolve("dimensions/minecraft/overworld/region"));
            Files.writeString(
                    nativeSource.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"),
                    "native-region");
            Path nativeCopy = temporary.resolve("native-copy");
            FinalsMapFiles.copyWorld(nativeSource, nativeCopy);
            require(
                    Files.readString(nativeCopy.resolve("region/r.0.0.mca"))
                            .equals("native-region"),
                    "Native overworld folders normalize region storage");
            Path nativeZip = imports.resolve("native.zip");
            zip(
                    nativeZip,
                    "wrapped/level.dat",
                    "wrapped/dimensions/minecraft/overworld/region/r.0.0.mca");
            Path nativeUnzipped = temporary.resolve("native-unzipped");
            FinalsMapFiles.copyWorld(nativeZip, nativeUnzipped);
            require(
                    Files.exists(nativeUnzipped.resolve("region/r.0.0.mca")),
                    "Native overworld ZIPs normalize region storage");
            Path slip = imports.resolve("slip.zip");
            zip(slip, "../escape", "level.dat", "region/r.0.0.mca");
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(slip, temporary.resolve("slip")),
                    "ZIP traversal rejected before extraction");
            require(
                    !Files.exists(temporary.resolve("escape")),
                    "ZIP traversal writes nothing outside destination");
            Path duplicate = imports.resolve("duplicate.zip");
            zip(duplicate, "level.dat", "LEVEL.DAT", "region/r.0.0.mca");
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(duplicate, temporary.resolve("duplicate")),
                    "Case-colliding ZIP entries rejected");
            Path linkZip = imports.resolve("link.zip");
            zip(linkZip, "level.dat", "region/r.0.0.mca");
            markZipSymlink(linkZip);
            failsChecked(
                    () -> FinalsMapFiles.copyWorld(linkZip, temporary.resolve("zip-link")),
                    "Unix symlinks in ZIP rejected");
            try {
                Path link = Files.createSymbolicLink(imports.resolve("link"), source);
                failsChecked(
                        () -> FinalsMapFiles.source(imports, link.getFileName().toString()),
                        "Source symlinks rejected");
            } catch (UnsupportedOperationException
                    | java.nio.file.FileSystemException unavailable) {
                System.out.println(
                        "Source symlink creation unavailable on this host; ZIP symlink rejection"
                            + " was checked");
            }
            Path registry = temporary.resolve("maps.yml"),
                    pending = temporary.resolve("pending.yml");
            Files.writeString(registry, "old");
            Files.writeString(pending, "new");
            FinalsMapFiles.replace(pending, registry);
            require(
                    Files.readString(registry).equals("new"),
                    "Atomic replacement commits complete registry");
            failsChecked(
                    () -> FinalsMapFiles.replace(temporary.resolve("missing"), registry),
                    "Missing replacement fails");
            require(
                    Files.readString(registry).equals("new"),
                    "Failed replacement preserves last good registry");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void zip(Path path, String... entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                output.write("data".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void markZipSymlink(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            for (long p = 0; p + 46 < file.length(); p++) {
                file.seek(p);
                if (Integer.reverseBytes(file.readInt()) == 0x02014b50) {
                    file.seek(p + 38);
                    file.writeInt(Integer.reverseBytes(0120777 << 16));
                    return;
                }
            }
            throw new AssertionError("ZIP central directory missing");
        }
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void fails(Runnable action, String description) {
        failsChecked(action::run, description);
    }

    private static void failsChecked(Checked action, String description) {
        try {
            action.run();
        } catch (IllegalArgumentException | IOException expected) {
            return;
        } catch (Exception unexpected) {
            throw new AssertionError(description, unexpected);
        }
        throw new AssertionError(description);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private record Cell(int x, int y, int z) {}

    private static Object primitive(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        return null;
    }

    private static final class FakeWorld {
        final Map<Cell, Material> blocks = new HashMap<>();
        final World world;

        FakeWorld(String name) {
            world =
                    (World)
                            Proxy.newProxyInstance(
                                    World.class.getClassLoader(),
                                    new Class<?>[] {World.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getName", "toString" -> name;
                                                case "equals" -> proxy == args[0];
                                                case "hashCode" -> System.identityHashCode(proxy);
                                                case "getBlockAt" ->
                                                        block(
                                                                (int) args[0],
                                                                (int) args[1],
                                                                (int) args[2]);
                                                case "setGameRule", "setSpawnLocation" -> true;
                                                default -> primitive(method.getReturnType());
                                            });
        }

        Block block(int x, int y, int z) {
            Cell cell = new Cell(x, y, z);
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class<?>[] {Block.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getWorld" -> world;
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        case "getLocation" -> new Location(world, x, y, z);
                                        case "getType" -> blocks.getOrDefault(cell, Material.AIR);
                                        case "setType" -> {
                                            blocks.put(cell, (Material) args[0]);
                                            yield null;
                                        }
                                        default -> primitive(method.getReturnType());
                                    });
        }
    }
}
