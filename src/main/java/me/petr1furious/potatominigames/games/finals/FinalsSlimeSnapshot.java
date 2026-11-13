package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.Bounds;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.utils.NibbleArray;
import com.infernalsuite.asp.api.world.SlimeChunk;
import com.infernalsuite.asp.api.world.SlimeChunkSection;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentMap;

/**
 * Captures native chunk palettes once, then compresses one Slime template off the server thread.
 */
final class FinalsSlimeSnapshot {
    private static final CompoundBinaryTag AIR =
            CompoundBinaryTag.builder().putString("Name", "minecraft:air").build();
    private static final CompoundBinaryTag AIR_STATES =
            CompoundBinaryTag.builder().put("palette", ListBinaryTag.from(List.of(AIR))).build();
    private static final StringBinaryTag COMPACT_AIR =
            StringBinaryTag.stringBinaryTag("minecraft:air");
    private static final CompoundBinaryTag COMPACT_AIR_STATES =
            CompoundBinaryTag.builder()
                    .put("palette", ListBinaryTag.from(List.of(COMPACT_AIR)))
                    .build();
    private static final Set<String> UNSAFE =
            Set.of(
                    "minecraft:command_block",
                    "minecraft:chain_command_block",
                    "minecraft:repeating_command_block",
                    "minecraft:structure_block",
                    "minecraft:jigsaw",
                    "minecraft:nether_portal",
                    "minecraft:end_portal",
                    "minecraft:end_gateway");

    private FinalsSlimeSnapshot() {}

    static FinalsMapSnapshot.Work capture(
            JavaPlugin plugin, World world, Bounds bounds, Path target) {
        try {
            return new Capture(plugin, world, bounds, target);
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (plugin != null)
                plugin.getLogger().warning("Finals native Slime snapshot unavailable: " + failure);
            throw FinalsMaps.failure("slime_unavailable");
        }
    }

    /** Chunk capture occupies the first 90%; compression completes the remaining stage. */
    static double progress(FinalsMapSnapshot.Work work) {
        if (!(work instanceof Capture capture)) return 0;
        return capture.completed ? 1 : .9 * capture.captured / capture.total;
    }

    private static final class Capture implements FinalsMapSnapshot.Work {
        private final JavaPlugin plugin;
        private final World world;
        private final Bounds bounds;
        private final AdvancedSlimePaperAPI asp;
        private final NativeChunks nativeChunks;
        private final FileTarget loader;
        private final SlimeWorld empty;
        private final Map<Long, SlimeChunk> chunks = new LinkedHashMap<>();
        private final long total;
        private int cx, cz;
        private long captured;
        private CompletableFuture<Void> writing;
        private boolean cancelled, completed;

        Capture(JavaPlugin plugin, World world, Bounds bounds, Path target)
                throws ReflectiveOperationException {
            this.plugin = plugin;
            this.world = world;
            this.bounds = bounds;
            this.nativeChunks = new NativeChunks();
            this.asp = AdvancedSlimePaperAPI.instance();
            this.loader = new FileTarget(target);
            SlimePropertyMap properties = new SlimePropertyMap();
            properties.setValue(
                    SlimeProperties.ENVIRONMENT,
                    world.getEnvironment().name().toLowerCase(Locale.ROOT));
            properties.setValue(
                    SlimeProperties.CHUNK_SECTION_MIN, Math.floorDiv(world.getMinHeight(), 16));
            properties.setValue(
                    SlimeProperties.CHUNK_SECTION_MAX, Math.floorDiv(world.getMaxHeight() - 1, 16));
            properties.setValue(SlimeProperties.SAVE_BLOCK_TICKS, false);
            properties.setValue(SlimeProperties.SAVE_FLUID_TICKS, false);
            properties.setValue(SlimeProperties.SAVE_POI, false);
            properties.setValue(SlimeProperties.ALLOW_MONSTERS, false);
            properties.setValue(SlimeProperties.ALLOW_ANIMALS, false);
            empty =
                    asp.createEmptyWorld(
                            "finals_snapshot_" + UUID.randomUUID(), false, properties, loader);
            cx = Math.floorDiv(bounds.minX(), 16);
            cz = Math.floorDiv(bounds.minZ(), 16);
            total =
                    ((long) Math.floorDiv(bounds.maxX(), 16) - cx + 1)
                            * ((long) Math.floorDiv(bounds.maxZ(), 16) - cz + 1);
        }

        @Override
        public boolean step() {
            if (cancelled) throw FinalsMaps.failure("io");
            if (completed) return true;
            if (writing != null) {
                if (!writing.isDone()) return false;
                awaitWriting();
                return true;
            }
            boolean unload = !world.isChunkLoaded(cx, cz);
            Chunk chunk = world.getChunkAt(cx, cz);
            boolean ticket = plugin != null && chunk.addPluginChunkTicket(plugin);
            try {
                SlimeChunk source = nativeChunks.snapshot(chunk);
                chunks.put(key(cx, cz), clipped(source, bounds, world.getMinHeight()));
            } finally {
                if (ticket) chunk.removePluginChunkTicket(plugin);
                if (unload) world.unloadChunkRequest(cx, cz);
            }
            captured++;
            if (++cz > Math.floorDiv(bounds.maxZ(), 16)) {
                cz = Math.floorDiv(bounds.minZ(), 16);
                cx++;
            }
            if (captured == total) {
                // The worker only sees detached NBT values; it never queries Bukkit chunks.
                SlimeWorld snapshot = new CapturedWorld(empty, Map.copyOf(chunks));
                chunks.clear();
                writing =
                        CompletableFuture.runAsync(() -> {
                            try {
                                if (!loader.cancelled()) asp.saveWorld(snapshot);
                            } catch (IOException failure) {
                                throw new CompletionException(failure);
                            }
                        });
            }
            return false;
        }

        private void awaitWriting() {
            try {
                writing.join();
                if (cancelled || loader.cancelled()) throw FinalsMaps.failure("io");
                completed = true;
            } catch (CompletionException failure) {
                if (plugin != null)
                    plugin.getLogger()
                            .warning("Finals Slime template write failed: " + failure.getCause());
                throw FinalsMaps.failure("io");
            }
        }

        @Override
        public boolean waiting() {
            return writing != null && !writing.isDone();
        }

        @Override
        public void finish() {
            while (writing == null && !completed) step();
            if (!completed) awaitWriting();
        }

        @Override
        public void cancel() {
            cancelled = true;
            chunks.clear();
            loader.cancel();
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * ASP's serializer consumes this interface; saved files are read back as its normal world type.
     */
    private record CapturedWorld(SlimeWorld base, Map<Long, SlimeChunk> chunks)
            implements SlimeWorld {
        public String getName() {
            return base.getName();
        }

        public SlimeLoader getLoader() {
            return base.getLoader();
        }

        public SlimeChunk getChunk(int x, int z) {
            return chunks.get(key(x, z));
        }

        public Collection<SlimeChunk> getChunkStorage() {
            return chunks.values();
        }

        public ConcurrentMap<String, BinaryTag> getExtraData() {
            return base.getExtraData();
        }

        public Collection<CompoundBinaryTag> getWorldMaps() {
            return List.of();
        }

        public SlimePropertyMap getPropertyMap() {
            return base.getPropertyMap();
        }

        public boolean isReadOnly() {
            return false;
        }

        public int getDataVersion() {
            return base.getDataVersion();
        }

        public PersistentDataContainer getPersistentDataContainer() {
            return base.getPersistentDataContainer();
        }

        public SlimeWorld clone(String name) {
            throw new UnsupportedOperationException("Read the saved template before cloning");
        }

        public SlimeWorld clone(String name, SlimeLoader loader) {
            throw new UnsupportedOperationException("Read the saved template before cloning");
        }
    }

    private record Section(
            CompoundBinaryTag states,
            CompoundBinaryTag biomes,
            NibbleArray blockLight,
            NibbleArray skyLight)
            implements SlimeChunkSection {
        public CompoundBinaryTag getBlockStatesTag() {
            return states;
        }

        public CompoundBinaryTag getBiomeTag() {
            return biomes;
        }

        public NibbleArray getBlockLight() {
            return blockLight;
        }

        public NibbleArray getSkyLight() {
            return skyLight;
        }
    }

    private record SavedChunk(
            int x,
            int z,
            SlimeChunkSection[] sections,
            CompoundBinaryTag heights,
            List<CompoundBinaryTag> tiles)
            implements SlimeChunk {
        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        public SlimeChunkSection[] getSections() {
            return sections;
        }

        public CompoundBinaryTag getHeightMaps() {
            return heights;
        }

        public List<CompoundBinaryTag> getTileEntities() {
            return tiles;
        }

        public List<CompoundBinaryTag> getEntities() {
            return List.of();
        }

        public Map<String, BinaryTag> getExtraData() {
            return Map.of();
        }

        public CompoundBinaryTag getUpgradeData() {
            return null;
        }

        public ListBinaryTag getBlockTicks() {
            return null;
        }

        public ListBinaryTag getFluidTicks() {
            return null;
        }

        public CompoundBinaryTag getPoiChunkSections() {
            return null;
        }
    }

    private static SlimeChunk clipped(SlimeChunk source, Bounds bounds, int minY) {
        int x0 = source.getX() * 16, z0 = source.getZ() * 16;
        boolean edge =
                x0 < bounds.minX()
                        || x0 + 15 > bounds.maxX()
                        || z0 < bounds.minZ()
                        || z0 + 15 > bounds.maxZ();
        SlimeChunkSection[] sections = source.getSections();
        SlimeChunkSection[] result = new SlimeChunkSection[sections.length];
        for (int i = 0; i < sections.length; i++) {
            SlimeChunkSection section = sections[i];
            if (section == null) continue;
            int bottom = minY + i * 16;
            CompoundBinaryTag states =
                    restrict(section.getBlockStatesTag(), bounds, x0, bottom, z0);
            // Native NBT tags are immutable; nibble arrays are copied before the worker sees them.
            result[i] =
                    new Section(
                            states,
                            section.getBiomeTag(),
                            copy(section.getBlockLight()),
                            copy(section.getSkyLight()));
        }
        List<CompoundBinaryTag> tiles =
                source.getTileEntities().stream()
                        .filter(tag ->
                                contains(bounds, tag.getInt("x"), tag.getInt("y"), tag.getInt("z")))
                        .toList();
        // ASP recomputes absent heightmaps while materializing boundary chunks.
        return new SavedChunk(
                source.getX(),
                source.getZ(),
                result,
                edge ? CompoundBinaryTag.empty() : source.getHeightMaps(),
                List.copyOf(tiles));
    }

    private static NibbleArray copy(NibbleArray array) {
        return array == null ? null : new NibbleArray(array.getBacking().clone());
    }

    private static boolean contains(Bounds bounds, int x, int y, int z) {
        return x >= bounds.minX()
                && x <= bounds.maxX()
                && z >= bounds.minZ()
                && z <= bounds.maxZ()
                && y >= bounds.minY()
                && y <= bounds.maxY();
    }

    /**
     * Clips partial chunks without touching the editor or scanning every block in full safe
     * sections.
     */
    static CompoundBinaryTag restrict(
            CompoundBinaryTag states, Bounds bounds, int x0, int y0, int z0) {
        // Modern Minecraft encodes default states as strings, alongside property compounds.
        ListBinaryTag palette = states.getList("palette");
        if (palette.isEmpty()) throw FinalsMaps.failure("template");
        boolean whole = contains(bounds, x0, y0, z0) && contains(bounds, x0 + 15, y0 + 15, z0 + 15);
        boolean possiblyUnsafe = false;
        boolean compactFormat = false;
        for (BinaryTag value : palette) {
            if (UNSAFE.contains(stateName(value))) possiblyUnsafe = true;
            if (value instanceof StringBinaryTag
                    || value instanceof CompoundBinaryTag compound && compound.get("id") != null)
                compactFormat = true;
        }
        if (whole && !possiblyUnsafe) return states;
        if (y0 > bounds.maxY() || y0 + 15 < bounds.minY())
            return compactFormat ? COMPACT_AIR_STATES : AIR_STATES;
        if (palette.size() == 1 && stateName(palette.get(0)).equals("minecraft:air")) return states;
        long[] old = states.getLongArray("data");
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        if (palette.size() > 1 && old.length < (4096 + perLong - 1) / perLong)
            throw FinalsMaps.failure("template");
        // Keep the server's exact state schema: newer codecs use strings or id/properties,
        // while older codecs use Name/Properties. Only newly inserted air needs a format choice.
        BinaryTag air = compactFormat ? COMPACT_AIR : AIR;
        List<BinaryTag> compact = new ArrayList<>();
        Map<BinaryTag, Integer> indices = new HashMap<>();
        int[] values = new int[4096];
        for (int i = 0; i < 4096; i++) {
            int x = x0 + (i & 15), z = z0 + ((i >>> 4) & 15), y = y0 + (i >>> 8);
            BinaryTag selected = air;
            if (contains(bounds, x, y, z)) {
                int index =
                        palette.size() == 1
                                ? 0
                                : (int) ((old[i / perLong] >>> ((i % perLong) * bits)) & mask);
                if (index >= palette.size()) throw FinalsMaps.failure("template");
                selected = palette.get(index);
                if (UNSAFE.contains(stateName(selected))) throw FinalsMaps.failure("unsafe_block");
            }
            BinaryTag entry = selected;
            values[i] =
                    indices.computeIfAbsent(
                            entry,
                            ignored -> {
                                compact.add(entry);
                                return compact.size() - 1;
                            });
        }
        CompoundBinaryTag.Builder builder =
                CompoundBinaryTag.builder()
                        .put(
                                "palette",
                                compactFormat
                                        ? ListBinaryTag.heterogeneousListBinaryTag()
                                                .add(compact)
                                                .build()
                                        : ListBinaryTag.from(compact));
        if (compact.size() > 1) {
            int nextBits = Math.max(4, 32 - Integer.numberOfLeadingZeros(compact.size() - 1));
            int nextPerLong = 64 / nextBits;
            long[] packed = new long[(4096 + nextPerLong - 1) / nextPerLong];
            for (int i = 0; i < values.length; i++)
                packed[i / nextPerLong] |= (long) values[i] << ((i % nextPerLong) * nextBits);
            builder.putLongArray("data", packed);
        }
        return builder.build();
    }

    private static String stateName(BinaryTag value) {
        if (value instanceof StringBinaryTag text) return text.value();
        if (value instanceof CompoundBinaryTag compound)
            return compound.getString("id", compound.getString("Name"));
        throw FinalsMaps.failure("template");
    }

    private static final class FileTarget implements SlimeLoader {
        private final Path target;
        private boolean cancelled;

        FileTarget(Path target) {
            this.target = target;
        }

        synchronized boolean cancelled() {
            return cancelled;
        }

        synchronized void cancel() {
            cancelled = true;
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                /* Owner also cleans up. */
            }
        }

        public byte[] readWorld(String name) throws UnknownWorldException {
            throw new UnknownWorldException(name);
        }

        public boolean worldExists(String name) {
            return false;
        }

        public List<String> listWorlds() {
            return List.of();
        }

        public synchronized void saveWorld(String name, byte[] bytes) throws IOException {
            if (cancelled) throw new IOException("Snapshot was cancelled");
            Files.write(target, bytes);
        }

        public void deleteWorld(String name) throws UnknownWorldException {
            throw new UnknownWorldException(name);
        }
    }

    /** All server-version-specific calls stay here; the snapshot's public data uses ASP's API. */
    private static final class NativeChunks {
        private final Method handle;
        private final Object full;
        private final Constructor<?> wrapper;

        NativeChunks() throws ReflectiveOperationException {
            Class<?> status = Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus");
            full = status.getField("FULL").get(null);
            handle =
                    Class.forName("org.bukkit.craftbukkit.CraftChunk")
                            .getMethod("getHandle", status);
            wrapper =
                    Class.forName("com.infernalsuite.asp.level.chunk.NMSSlimeChunk")
                            .getConstructor(
                                    Class.forName("net.minecraft.world.level.chunk.LevelChunk"),
                                    SlimeChunk.class);
        }

        SlimeChunk snapshot(Chunk chunk) {
            try {
                return (SlimeChunk) wrapper.newInstance(handle.invoke(chunk, full), null);
            } catch (ReflectiveOperationException failure) {
                Throwable cause =
                        failure instanceof InvocationTargetException invoked
                                ? invoked.getCause()
                                : failure;
                throw new IllegalStateException("Could not snapshot native Finals chunk", cause);
            }
        }
    }
}
