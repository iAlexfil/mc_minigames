import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import me.petr1furious.potatominigames.games.finals.FinalsMapArena;
import me.petr1furious.potatominigames.games.finals.FinalsState;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/** Real CraftPlayer visibility and entity tracking, with only the network socket replaced. */
public final class FinalsMarkerDeliverySmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsMarkerDeliverySmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        ServerLevel level = ((CraftWorld) world).getHandle();
        for (int x = 29; x <= 35; x++) for (int z = 29; z <= 33; z++) world.getChunkAt(x, z).load();
        Endpoint owner = new Endpoint(level, "MarkerOwner", 500, 65, 498);
        Endpoint outsider = new Endpoint(level, "MarkerOutsider", 502, 65, 498);
        FixtureArena arena = new FixtureArena(world);
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHighlights", true,
                main.getClass().getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(JavaPlugin.class);
        constructor.setAccessible(true);
        Object highlights = constructor.newInstance(main);
        Method update = type.getDeclaredMethod("update", FinalsMapArena.class, FinalsState.Phase.class,
                int.class, Collection.class, Map.class, Function.class);
        update.setAccessible(true);
        Method clear = type.getDeclaredMethod("clear");
        clear.setAccessible(true);
        try {
            refresh(update, highlights, arena, owner, outsider);
            List<Display> markers = markers(highlights, owner.player);
            List<Display> opposingMarkers = markers(highlights, outsider.player);
            require(markers.size() == 2, "A real player receives a vault label and outline descriptor");
            require(opposingMarkers.size() == 2 && Collections.disjoint(markers, opposingMarkers),
                    "The opposing real contestant receives its own separate label and outline");
            require(owner.wire.packets.isEmpty(), "No marker spawn is sent before its chunk is sent");
            require(outsider.wire.packets.isEmpty(), "The opposing contestant also waits for its first chunk delivery");
            for (Display marker : markers) {
                require(marker.isValid(), "Initial markers are native valid entities");
                require(owner.player.canSee(marker) && !outsider.player.canSee(marker),
                        "Initial showEntity records private permission even before chunk delivery");
            }
            owner.sentArea(29, 35, 29, 33);
            outsider.sentArea(29, 35, 29, 33);
            track(level, markers, owner, outsider);
            track(level, opposingMarkers, owner, outsider);
            delivered(markers, owner, outsider, "Initial delayed chunk delivery");
            delivered(opposingMarkers, outsider, owner, "Opposing contestant initial delayed chunk delivery");

            owner.wire.packets.clear();
            refresh(update, highlights, arena, owner, outsider);
            require(markers(highlights, owner.player).equals(markers),
                    "Moving the initial eye projection onto the nearby vault reuses native displays");
            checkVisible(level, markers, owner, outsider, "Nearby vault after initial chunk delivery");
            checkVisible(level, opposingMarkers, outsider, owner, "Opposing contestant nearby vault after chunk delivery");
            owner.wire.packets.clear();
            outsider.wire.packets.clear();
            refresh(update, highlights, arena, owner, outsider);
            track(level, markers, owner, outsider);
            require(markers(highlights, owner.player).equals(markers),
                    "An unchanged refresh preserves the same label and outline entities");
            require(owner.wire.packets.stream().noneMatch(ClientboundAddEntityPacket.class::isInstance),
                    "An unchanged refresh sends no duplicate marker spawn packets to the owner");
            require(outsider.wire.packets.stream().noneMatch(ClientboundAddEntityPacket.class::isInstance),
                    "An unchanged refresh never spawns the private markers for another player");

            arena.vault = new Location(world, 1000, 90, 500);
            owner.player.getHandle().setPos(511.75, 65, 498);
            refresh(update, highlights, arena, owner, outsider);
            checkVisible(level, markers, owner, outsider, "Projection across a loaded chunk boundary");
            owner.player.getHandle().setPos(500, 65, 498);
            arena.vault = new Location(world, 500, 65, 500);
            refresh(update, highlights, arena, owner, outsider);
            checkVisible(level, markers, owner, outsider, "Returning from projection to the nearby vault");

            // Native onTrackingEnd clears CraftPlayer's per-entity visibility override. Reproduce
            // the entity lifecycle itself, without substituting fake canSee/showEntity behavior.
            Location target = new Location(world, 20_000, 65, 20_000);
            require(!world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4),
                    "Tracking-loss destination starts inaccessible");
            for (Display marker : markers) {
                require(marker.teleport(target), "A private native " + marker.getType() + " moves to the inaccessible chunk");
                require(!owner.player.canSee(marker),
                        "Native tracking loss clears the owner's visibility override for " + marker.getType());
            }
            world.getChunkAt(target.getBlockX() >> 4, target.getBlockZ() >> 4).load();
            for (Display marker : markers) {
                require(marker.isValid(), "The same native " + marker.getType() + " is valid again after its chunk reloads");
                require(!owner.player.canSee(marker),
                        "Chunk reload alone does not restore private visibility for " + marker.getType());
            }
            refresh(update, highlights, arena, owner, outsider);
            require(markers(highlights, owner.player).equals(markers),
                    "Tracking-loss recovery restores the original label and outline without replacing either entity");
            checkVisible(level, markers(highlights, owner.player), owner, outsider,
                    "Refresh after native tracking loss and reload");
            checkVisible(level, markers(highlights, outsider.player), outsider, owner,
                    "Opposing contestant after another view's tracking loss");
            smoke.getLogger().info("FINALS_MARKER_DELIVERY_SMOKE_PASS: " + assertions + " native assertions");
            return assertions;
        } finally {
            try { invoke(clear, highlights); }
            finally { owner.close(); outsider.close(); }
        }
    }

    private void checkVisible(ServerLevel level, List<Display> markers, Endpoint owner, Endpoint outsider,
            String context) throws Exception {
        for (Display marker : markers) {
            require(marker.isValid(), context + ": display remains native-valid");
            require(owner.player.canSee(marker), context + ": owner retains private visibility");
            require(!outsider.player.canSee(marker), context + ": other players cannot see it");
            ChunkMap.TrackedEntity tracker = level.getChunkSource().chunkMap.entityMap.get(marker.getEntityId());
            require(tracker != null, context + ": native tracker exists");
            tracker.removePlayer(owner.handle);
        }
        owner.wire.packets.clear();
        outsider.wire.packets.clear();
        track(level, markers, owner, outsider);
        delivered(markers, owner, outsider, context);
    }

    private static void track(ServerLevel level, List<Display> markers, Endpoint... endpoints) {
        for (Display marker : markers) {
            ChunkMap.TrackedEntity tracker = level.getChunkSource().chunkMap.entityMap.get(marker.getEntityId());
            if (tracker == null) throw new AssertionError("No tracker for " + marker);
            for (Endpoint endpoint : endpoints) tracker.updatePlayer(endpoint.handle);
        }
    }

    private void delivered(List<Display> markers, Endpoint owner, Endpoint outsider, String context) throws Exception {
        for (Display marker : markers) {
            int id = marker.getEntityId();
            require(owner.wire.packets.stream().anyMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                            && add.getId() == id), context + ": owner receives actual entity spawn for " + marker.getType()
                            + "; packets=" + owner.wire.describe());
            ClientboundSetEntityDataPacket data = owner.wire.packets.stream()
                    .filter(packet -> packet instanceof ClientboundSetEntityDataPacket metadata && metadata.id() == id)
                    .map(ClientboundSetEntityDataPacket.class::cast).findFirst()
                    .orElseThrow(() -> new AssertionError(context + ": missing initial display metadata " + marker));
            if (marker instanceof TextDisplay) {
                Object text = metadata(data, net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_ID");
                require(text instanceof net.minecraft.network.chat.Component component && !component.getString().isEmpty(),
                        context + ": initial metadata contains the nonempty vault label");
            } else {
                Object flags = metadata(data, net.minecraft.world.entity.Entity.class, "DATA_SHARED_FLAGS_ID");
                require(flags instanceof Byte value && (value & 0x40) != 0,
                        context + ": initial metadata contains the glowing flag");
                Object state = metadata(data, net.minecraft.world.entity.Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
                require(state instanceof net.minecraft.world.level.block.state.BlockState block
                                && block.is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK),
                        context + ": initial metadata contains the emerald block model");
            }
            require(outsider.wire.packets.stream().noneMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                            && add.getId() == id || packet instanceof ClientboundSetEntityDataPacket dataPacket
                            && dataPacket.id() == id), context + ": no marker spawn or metadata reaches the outsider");
        }
    }

    private static Object metadata(ClientboundSetEntityDataPacket packet, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        int id = ((EntityDataAccessor<?>) field.get(null)).id();
        return packet.packedItems().stream().filter(value -> value.id() == id)
                .map(value -> (Object) value.value()).findFirst().orElse(null);
    }

    private static void refresh(Method update, Object highlights, FixtureArena arena, Endpoint owner, Endpoint outsider) throws Exception {
        invoke(update, highlights, arena, null, -1, List.of(owner.player, outsider.player),
                Map.of(owner.player.getUniqueId(), 0, outsider.player.getUniqueId(), 1),
                (Function<Player, Location>) Player::getLocation);
    }

    private static List<Display> markers(Object highlights, Player owner) throws Exception {
        Map<?, ?> views = (Map<?, ?>) field(highlights, "views");
        Map<?, ?> overlays = (Map<?, ?>) field(views.get(owner.getUniqueId()), "overlays");
        List<Display> displays = new ArrayList<>();
        for (Object overlay : overlays.values()) {
            displays.add((Display) field(overlay, "label"));
            Object outline = field(overlay, "outline");
            if (outline != null) displays.add((Display) outline);
        }
        return displays;
    }

    private static Object field(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Endpoint implements AutoCloseable {
        final ServerPlayer handle;
        final org.bukkit.craftbukkit.entity.CraftPlayer player;
        final RecordingConnection wire = new RecordingConnection();
        final PlayerChunkLoaderData chunks;
        final PlayerList registry;
        final List<ServerPlayer> registeredPlayers;

        @SuppressWarnings("unchecked") Endpoint(ServerLevel level, String name, double x, double y, double z) throws Exception {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            handle.setPos(x, y, z);
            new ServerGamePacketListenerImpl(server, wire, handle, CommonListenerCookie.createInitial(profile, false));
            player = handle.getBukkitEntity();
            chunks = new PlayerChunkLoaderData(level, handle);
            handle.moonrise$setChunkLoader(chunks);
            for (String nameField : List.of("lastSendDistance", "lastLoadDistance", "lastTickDistance")) {
                Field field = chunks.getClass().getDeclaredField(nameField);
                field.setAccessible(true);
                field.setInt(chunks, 10);
            }
            registry = server.getPlayerList();
            Field list = PlayerList.class.getDeclaredField("players");
            list.setAccessible(true);
            registeredPlayers = (List<ServerPlayer>) list.get(registry);
            registry.getPlayersByUUID().put(profile.id(), handle);
            registeredPlayers.add(handle);
            wire.packets.clear();
        }

        void sentArea(int minX, int maxX, int minZ, int maxZ) {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                chunks.getSentChunksRaw().add(net.minecraft.world.level.ChunkPos.pack(x, z));
        }

        @Override public void close() {
            registeredPlayers.remove(handle);
            registry.getPlayersByUUID().remove(handle.getUUID(), handle);
            handle.moonrise$setChunkLoader(null);
            wire.channel.close();
            handle.getTextFilter().leave();
        }
    }

    private static final class RecordingConnection extends Connection {
        final List<Packet<?>> packets = new ArrayList<>();
        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
        }
        @Override public void send(Packet<?> packet) { record(packet); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback) { record(packet); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) { record(packet); }
        private void record(Packet<?> packet) {
            if (packet instanceof BundlePacket<?> bundle) bundle.subPackets().forEach(this::record);
            else packets.add(packet);
        }
        String describe() { return packets.stream().map(packet -> packet.getClass().getSimpleName()).toList().toString(); }
    }

    private static final class FixtureArena implements FinalsMapArena {
        final World world;
        Location vault;
        FixtureArena(World world) { this.world = world; vault = new Location(world, 500, 65, 500); }
        public World world() { return world; }
        public void build() {}
        public Location center() { return new Location(world, 500, 65, 500); }
        public Location spawn(int team, int memberIndex) { return center(); }
        public Location vault() { return vault.clone(); }
        public List<Location> vaultLocations() { return List.of(vault()); }
        public void nextVault() {}
        public Location station(int station) { return center().add(station * 8, 0, 0); }
        public List<Location> cashoutLocations() { return List.of(station(0), station(1)); }
        public void nextCashout(int station) {}
        public boolean contains(Location location) { return world.equals(location.getWorld()); }
        public boolean isCover(Block block) { return false; }
        public int stationAt(Block block) { return -1; }
        public boolean isVault(Block block) { return false; }
        public double borderSize() { return 4096; }
    }
}
