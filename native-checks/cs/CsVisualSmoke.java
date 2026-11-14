import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.lang.reflect.*;
import java.util.*;

/** Native item components and terrain, with captured client-only block packets. */
final class CsVisualSmoke {
    private static final String PKG = "me.petr1furious.potatominigames.games.cs.";

    static void run(ClassLoader loader, World world) throws Exception {
        floor(loader, world);
        armour(loader);
        nameTags(loader);
        pistolItems(loader);
        tracers(loader, world);
    }

    private static void pistolItems(ClassLoader loader) throws Exception {
        Class<?> weapons = Class.forName(PKG + "CsWeapon", true, loader);
        Class<?> gunType = Class.forName(PKG + "CsGun", true, loader);
        var constructor = gunType.getDeclaredConstructor(weapons);
        Method semiauto = weapons.getDeclaredMethod("requiresTriggerRelease");
        Method item =
                Class.forName(PKG + "CsItems", true, loader)
                        .getDeclaredMethod("gun", gunType, Player.class);
        constructor.setAccessible(true);
        semiauto.setAccessible(true);
        item.setAccessible(true);
        for (Object weapon : weapons.getEnumConstants()) {
            ItemStack shown = (ItemStack) item.invoke(null, constructor.newInstance(weapon), null);
            var use = shown.getData(DataComponentTypes.CONSUMABLE);
            boolean needsRelease = (boolean) semiauto.invoke(weapon);
            require(
                    (use != null) == needsRelease,
                    "only semi-automatic pistols have a native button-release use state");
            if (needsRelease) {
                var effects = shown.getData(DataComponentTypes.USE_EFFECTS);
                require(
                        use.consumeSeconds() == 3600
                                && use.animation()
                                        == io.papermc.paper.datacomponent.item.consumable
                                                .ItemUseAnimation.NONE
                                && !use.hasConsumeParticles()
                                && use.sound().asString().equals("minecraft:intentionally_empty")
                                && effects != null
                                && effects.canSprint()
                                && effects.speedMultiplier() == 1
                                && !effects.interactVibrations(),
                        "native pistol components provide silent use without movement penalties");
            }
        }
    }

    private static void tracers(ClassLoader loader, World world) throws Exception {
        Class<?> type = Class.forName(PKG + "CsTracers", true, loader);
        List<Object> packets = new ArrayList<>();
        var constructor = type.getDeclaredConstructor(java.util.function.BiConsumer.class);
        constructor.setAccessible(true);
        Object streaks =
                constructor.newInstance(
                        (java.util.function.BiConsumer<Player, Object>)
                                (player, packet) -> packets.add(packet));
        Method draw =
                type.getDeclaredMethod(
                        "draw",
                        Location.class,
                        org.bukkit.util.Vector.class,
                        double.class,
                        Collection.class,
                        long.class);
        Method tick = type.getDeclaredMethod("tick", long.class);
        Method clear = type.getDeclaredMethod("clear");
        draw.setAccessible(true);
        tick.setAccessible(true);
        clear.setAccessible(true);
        Player client = viewer(world, new HashMap<>());
        Location from = new Location(world, 13.25, 100.75, 42.125, 90, -30);
        int entities = world.getEntities().size();
        try {
            draw.invoke(
                    streaks, from, new org.bukkit.util.Vector(0, 0, 1), 256d, List.of(client), 10L);
            require(packets.size() == 1, "one bundled display spawn/metadata message per shot");
            Object bundle = packets.getFirst();
            Iterable<?> children =
                    (Iterable<?>) bundle.getClass().getMethod("subPackets").invoke(bundle);
            List<Object> parts = new ArrayList<>();
            children.forEach(parts::add);
            require(
                    parts.size() == 2
                            && parts.get(0)
                                    .getClass()
                                    .getSimpleName()
                                    .equals("ClientboundAddEntityPacket")
                            && parts.get(1)
                                    .getClass()
                                    .getSimpleName()
                                    .equals("ClientboundSetEntityDataPacket"),
                    "spawn and actual native display metadata arrive atomically");
            Object spawn = parts.get(0);
            UUID id = (UUID) spawn.getClass().getMethod("getUUID").invoke(spawn);
            require(
                    world.getEntity(id) == null && world.getEntities().size() == entities,
                    "display is never spawned, tracked or ticked by the server world");
            double z = (double) spawn.getClass().getMethod("getZ").invoke(spawn);
            require(
                    Math.abs(z - (from.getZ() + 128.25)) < .000001,
                    "long-ray spawn preserves fractional coordinates at the exact midpoint");
            require(
                    (float) spawn.getClass().getMethod("getYRot").invoke(spawn) == 0
                            && (float) spawn.getClass().getMethod("getXRot").invoke(spawn) == 0,
                    "player aim angles are not applied twice on top of the display transform");
            List<?> metadata =
                    (List<?>) parts.get(1).getClass().getMethod("packedItems").invoke(parts.get(1));
            require(
                    !metadata.isEmpty(),
                    "display block, brightness and transformation have native metadata");
            tick.invoke(streaks, 12L);
            require(packets.size() == 1, "a beam stays visible for three ticks");
            tick.invoke(streaks, 13L);
            require(
                    packets.size() == 2
                            && packets.getLast()
                                    .getClass()
                                    .getSimpleName()
                                    .equals("ClientboundRemoveEntitiesPacket"),
                    "the beam is removed at its deadline without a scheduled task per shot");
            packets.clear();
            draw.invoke(
                    streaks, from, new org.bukkit.util.Vector(0, 0, 1), .2d, List.of(client), 20L);
            draw.invoke(streaks, from, new org.bukkit.util.Vector(), 256d, List.of(client), 20L);
            draw.invoke(streaks, from, new org.bukkit.util.Vector(0, 0, 1), 256d, List.of(), 20L);
            require(
                    packets.isEmpty(),
                    "empty, invalid and shorter-than-muzzle rays create no display");
            for (int i = 0; i < 129; i++)
                draw.invoke(
                        streaks,
                        from,
                        new org.bukkit.util.Vector(0, -1, 0),
                        50d,
                        List.of(client),
                        20L);
            require(
                    packets.size() == 130,
                    "excess automatic fire evicts the oldest beam at the 128 cap");
            clear.invoke(streaks);
            require(packets.size() == 258, "match cleanup removes every remaining beam");
            tick.invoke(streaks, 30L);
            clear.invoke(streaks);
            require(
                    packets.size() == 258 && world.getEntities().size() == entities,
                    "expiration and repeated cleanup leave no packets or world entities behind");
        } finally {
            clear.invoke(streaks);
        }
    }

    private static void nameTags(ClassLoader loader) throws Exception {
        UUID red = UUID.randomUUID(), ally = UUID.randomUUID(), blue = UUID.randomUUID();
        Class<?> model = Class.forName(PKG + "CsMatch", true, loader);
        var constructor =
                model.getDeclaredConstructor(
                        Map.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        boolean.class);
        constructor.setAccessible(true);
        Object match =
                constructor.newInstance(
                        Map.of(red, 0, ally, 0, blue, 1), 12, 2, 100, 20, 10, false);
        Method update =
                Class.forName(PKG + "CsNameTags", true, loader)
                        .getDeclaredMethod(
                                "update",
                                org.bukkit.scoreboard.Scoreboard.class,
                                model,
                                Map.class,
                                Integer.class);
        update.setAccessible(true);
        Map<UUID, String> names = Map.of(red, "Red", ally, "Ally", blue, "Blue");
        for (Integer viewerTeam : Arrays.asList(0, 1, null)) {
            var board = Bukkit.getScoreboardManager().getNewScoreboard();
            update.invoke(null, board, match, names, viewerTeam);
            for (String name : names.values()) {
                boolean friendly =
                        viewerTeam != null && (name.equals("Blue") ? 1 : 0) == viewerTeam;
                require(
                        board.getEntryTeam(name)
                                        .getOption(
                                                org.bukkit.scoreboard.Team.Option
                                                        .NAME_TAG_VISIBILITY)
                                == (friendly
                                        ? org.bukkit.scoreboard.Team.OptionStatus.ALWAYS
                                        : org.bukkit.scoreboard.Team.OptionStatus.NEVER),
                        "private scoreboard reveals only the viewer's teammates");
            }
            // Switching a viewer's allegiance must move entries out of the visible team.
            update.invoke(null, board, match, names, null);
            for (String name : names.values())
                require(
                        board.getEntryTeam(name)
                                        .getOption(
                                                org.bukkit.scoreboard.Team.Option
                                                        .NAME_TAG_VISIBILITY)
                                == org.bukkit.scoreboard.Team.OptionStatus.NEVER,
                        "spectators inherit no visible team names");
        }
    }

    private static void floor(ClassLoader loader, World world) throws Exception {
        Map<BlockPosition, BlockData> original = new HashMap<>();
        Location first = new Location(world, 96.5, 200.12, 96.5);
        Location second = first.clone().add(1, 0, 0);
        for (int x = 91; x <= 102; x++)
            for (int z = 91; z <= 102; z++)
                for (int y = 199; y <= 204; y++) {
                    var block = world.getBlockAt(x, y, z);
                    original.put(Position.block(x, y, z), block.getBlockData().clone());
                    block.setType(y == 199 ? Material.STONE : Material.AIR, false);
                }
        try {
            Map<BlockPosition, BlockData> client = new HashMap<>();
            Player viewer = viewer(world, client);
            Class<?> type = Class.forName(PKG + "CsFireFloor", true, loader);
            var constructor = type.getDeclaredConstructor(World.class);
            constructor.setAccessible(true);
            Object floor = constructor.newInstance(world);
            Method update =
                    type.getDeclaredMethod(
                            "update", Collection.class, Collection.class, long.class);
            Method clear = type.getDeclaredMethod("clear");
            update.setAccessible(true);
            clear.setAccessible(true);
            update.invoke(floor, List.of(first), List.of(viewer), 1L);
            require(!client.isEmpty(), "molotov sends visible ground fire");
            for (var entry : client.entrySet()) {
                var pos = entry.getKey();
                require(
                        entry.getValue().getMaterial() == Material.FIRE,
                        "client receives native fire blocks");
                require(
                        world.getBlockAt(pos.blockX(), pos.blockY(), pos.blockZ())
                                .getType()
                                .isAir(),
                        "visual fire never alters real terrain or adds native fire behavior");
            }
            var shared = Position.block(96, 200, 96);
            var exclusive = Position.block(93, 200, 96);
            require(
                    client.get(exclusive).getMaterial() == Material.FIRE,
                    "first zone covers its floor edge");
            update.invoke(floor, List.of(first, second), List.of(viewer), 2L);
            update.invoke(floor, List.of(second), List.of(viewer), 3L);
            require(
                    client.get(exclusive).getMaterial().isAir(),
                    "expired zone restores exclusive cells");
            require(
                    client.get(shared).getMaterial() == Material.FIRE,
                    "overlap remains until both zones expire");
            Map<BlockPosition, BlockData> spectator = new HashMap<>();
            Player late = viewer(world, spectator);
            update.invoke(floor, List.of(second), List.of(viewer, late), 5L);
            require(
                    spectator.get(shared).getMaterial() == Material.FIRE,
                    "late spectators receive active fire");
            world.getBlockAt(96, 200, 96).setType(Material.LAPIS_BLOCK, false);
            clear.invoke(floor);
            require(
                    client.get(shared).getMaterial() == Material.LAPIS_BLOCK
                            && spectator.get(shared).getMaterial() == Material.LAPIS_BLOCK,
                    "cleanup restores the current real block state for every viewer");
        } finally {
            for (var entry : original.entrySet()) {
                var pos = entry.getKey();
                world.getBlockAt(pos.blockX(), pos.blockY(), pos.blockZ())
                        .setBlockData(entry.getValue(), false);
            }
        }
    }

    private static Player viewer(World world, Map<BlockPosition, BlockData> client) {
        UUID id = UUID.randomUUID();
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getWorld" -> world;
                                    case "isOnline" -> true;
                                    case "sendMultiBlockChange" -> {
                                        ((Map<?, ?>) args[0])
                                                .forEach((pos, data) ->
                                                        client.put(
                                                                ((Position) pos).toBlock(),
                                                                ((BlockData) data).clone()));
                                        yield null;
                                    }
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    private static void armour(ClassLoader loader) throws Exception {
        UUID red = UUID.randomUUID(), blue = UUID.randomUUID();
        Class<?> model = Class.forName(PKG + "CsMatch", true, loader);
        var constructor =
                model.getDeclaredConstructor(
                        Map.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        boolean.class);
        constructor.setAccessible(true);
        Object match = constructor.newInstance(Map.of(red, 0, blue, 1), 12, 2, 100, 20, 10, false);
        Method actor = model.getDeclaredMethod("actor", UUID.class);
        actor.setAccessible(true);
        Method sync =
                Class.forName(PKG + "CsItems", true, loader)
                        .getDeclaredMethod("syncArmour", Player.class, model);
        sync.setAccessible(true);
        for (UUID id : List.of(red, blue)) {
            Map<String, ItemStack> worn = new HashMap<>();
            PlayerInventory inventory =
                    (PlayerInventory)
                            Proxy.newProxyInstance(
                                    PlayerInventory.class.getClassLoader(),
                                    new Class<?>[] {PlayerInventory.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().startsWith("set")) {
                                            worn.put(method.getName(), (ItemStack) args[0]);
                                            return null;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    });
            Player player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getInventory" -> inventory;
                                                case "locale" ->
                                                        id.equals(red)
                                                                ? Locale.ENGLISH
                                                                : Locale.forLanguageTag("ru");
                                                default ->
                                                        throw new UnsupportedOperationException(
                                                                method.getName());
                                            });
            Object state = actor.invoke(match, id);
            int color = id.equals(red) ? 0xB02E26 : 0x3C44AA;
            sync.invoke(null, player, match);
            require(worn.size() == 4, "default uniform fills every armor slot");
            for (ItemStack item : worn.values())
                require(
                        item.getItemMeta() instanceof LeatherArmorMeta leather
                                && leather.getColor().asRGB() == color,
                        "all default pieces use stable red/blue team colors");
            var vest = state.getClass().getDeclaredField("armour");
            var helmet = state.getClass().getDeclaredField("helmet");
            vest.setAccessible(true);
            helmet.setAccessible(true);
            vest.setDouble(state, 100);
            sync.invoke(null, player, match);
            require(
                    worn.get("setChestplate").getType() == Material.IRON_CHESTPLATE
                            && worn.get("setHelmet").getType() == Material.LEATHER_HELMET,
                    "vest purchase changes only the chest appearance");
            helmet.setBoolean(state, true);
            sync.invoke(null, player, match);
            require(
                    worn.get("setHelmet").getType() == Material.IRON_HELMET,
                    "owned helmet renders iron");
            for (ItemStack item : worn.values()) {
                var modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
                require(
                        modifiers == null || modifiers.modifiers().isEmpty(),
                        "cosmetic armor adds no native attributes");
                require(
                        item.getItemMeta().isUnbreakable(),
                        "native durability cannot change protection");
            }
            require(
                    vest.getDouble(state) == 100 && helmet.getBoolean(state),
                    "visual refresh preserves logical armor");
        }
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
