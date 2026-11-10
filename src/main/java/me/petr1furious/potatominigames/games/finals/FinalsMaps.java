package me.petr1furious.potatominigames.games.finals;

import static me.petr1furious.potatominigames.games.finals.FinalsMapData.*;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Draft setup plus immutable structure snapshots. Matches only ever use disposable world copies.
 */
public final class FinalsMaps {
    private record Entry(Draft draft, Saved saved) {}

    private final JavaPlugin plugin;
    private final Path root, imports, templates, registry;
    private Map<String, Entry> entries = new LinkedHashMap<>();
    private String selected = "generated";

    public FinalsMaps(JavaPlugin plugin) {
        this.plugin = plugin;
        root = plugin.getDataFolder().toPath().resolve("finals");
        imports = root.resolve("imports");
        templates = root.resolve("templates");
        registry = root.resolve("maps.yml");
        try {
            Files.createDirectories(imports);
            Files.createDirectories(templates);
            load();
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load Finals map registry", error);
        }
    }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        result.add("generated");
        entries.forEach((name, entry) -> {
            if (entry.saved != null) result.add(name);
        });
        return List.copyOf(result);
    }

    public List<String> drafts() {
        return List.copyOf(entries.keySet());
    }

    public String selected() {
        return selected;
    }

    public void select(String name) {
        if (!"generated".equals(name)) {
            Entry entry = entry(name);
            if (entry.saved == null) throw failure("not_saved", name);
            loadTemplate(entry.saved);
        }
        commit(entries, name);
    }

    public FinalsMapArena open(SessionServices services, String nullableName) {
        String name = nullableName == null || nullableName.isBlank() ? selected : nullableName;
        if (name.equals("generated"))
            return new FinalsArena(services.worlds().createVoid("finals"));
        Entry entry = entry(name);
        if (entry.saved == null) throw failure("not_saved", name);
        Structure structure = loadTemplate(entry.saved);
        World world = services.worlds().createVoid("finals");
        world.setGameRule(org.bukkit.GameRules.COMMAND_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWNER_BLOCKS_WORK, false);
        if (entry.saved.bounds().minY() < world.getMinHeight()
                || entry.saved.bounds().maxY() >= world.getMaxHeight()) throw failure("height");
        return new FinalsImportedArena(world, entry.saved, structure);
    }

    public void create(String name, Location location) {
        FinalsMapFiles.name(name);
        if (entries.containsKey(name)) throw failure("exists", name);
        World world = location.getWorld();
        if (world == null
                || world.getName()
                        .startsWith(
                                me.petr1furious.potatominigames.core.world.ThrowawayWorldService
                                        .PREFIX)) throw failure("persistent_world");
        Draft draft = new Draft();
        draft.world = world.getName();
        Map<String, Entry> next = new LinkedHashMap<>(entries);
        next.put(name, new Entry(draft, null));
        commit(next, selected);
    }

    public void importWorld(String name, String source) {
        FinalsMapFiles.name(name);
        if (entries.containsKey(name)) throw failure("exists", name);
        String worldName =
                "finals_edit_" + name + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path destination = container.resolve(worldName);
        World world = null;
        Path loadedFolder = destination;
        boolean created = false;
        try {
            Path input = FinalsMapFiles.source(imports, source);
            if (Files.exists(destination)) throw failure("source");
            Files.createDirectory(destination);
            created = true;
            FinalsMapFiles.copyWorldContents(input, destination);
            world = new WorldCreator(worldName).generateStructures(false).createWorld();
            if (world == null) throw failure("world_load");
            prepareEditor(world);
            loadedFolder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (!loadedFolder.startsWith(container)
                    || !loadedFolder.getFileName().toString().equals(worldName))
                throw failure("world_load");
            Draft draft = new Draft();
            draft.world = worldName;
            draft.imported = true;
            // Paper 26.3 migrates a copied legacy world into its dimension directory.
            // Persist the actual API-reported location, so editor reload works on both layouts.
            draft.folder = container.relativize(loadedFolder).toString().replace('\\', '/');
            Map<String, Entry> next = new LinkedHashMap<>(entries);
            next.put(name, new Entry(draft, null));
            commit(next, selected);
        } catch (IOException | RuntimeException error) {
            if (world != null && world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, false);
            if (created && Bukkit.getWorld(worldName) == null) {
                cleanFailedImport(container, destination);
                if (!loadedFolder.equals(destination)) cleanFailedImport(container, loadedFolder);
            }
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            plugin.getLogger().warning("Finals map import failed: " + error.getMessage());
            throw failure("io");
        }
    }

    /**
     * Only the exact new import directory is eligible for rollback; original imports are read-only.
     */
    private void cleanFailedImport(Path container, Path destination) {
        if (!destination.startsWith(container)
                || destination.equals(container)
                || !destination.getFileName().toString().startsWith("finals_edit_")
                || !Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(destination)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (IOException cleanup) {
            plugin.getLogger().warning("Cannot remove incomplete Finals import: " + destination);
        }
    }

    public Location editorLocation(String name) {
        Entry entry = entry(name);
        World world = editor(entry.draft);
        return entry.draft.red.isEmpty()
                ? world.getSpawnLocation()
                : entry.draft.red.getFirst().at(world);
    }

    public void corner(String name, int index, Location location) {
        update(
                name,
                location,
                draft -> {
                    if (index == 0) draft.first = Point.of(location);
                    else draft.second = Point.of(location);
                });
    }

    public void spawn(String name, int team, Location location) {
        update(
                name,
                location,
                draft -> {
                    List<Point> spawns = team == 0 ? draft.red : draft.blue;
                    if (spawns.size() >= 16) throw failure("spawn_limit");
                    Point point = Point.of(location).centered();
                    if (spawns.stream().anyMatch(point::sameBlock))
                        throw failure("spawn_duplicate");
                    spawns.add(point);
                });
    }

    public void clearSpawns(String name, int team) {
        update(name, null, draft -> (team == 0 ? draft.red : draft.blue).clear());
    }

    public void vault(String name, Location location) {
        update(name, location, draft -> draft.vault = Point.of(location).centered());
    }

    public void station(String name, int station, Location location) {
        update(
                name,
                location,
                draft -> {
                    if (station == 0) draft.stationA = Point.of(location).centered();
                    else draft.stationB = Point.of(location).centered();
                });
    }

    public void save(String name) {
        Entry previous = entry(name);
        Draft draft = previous.draft;
        String filename = name + "-" + UUID.randomUUID() + ".nbt";
        Saved saved = Saved.from(draft, filename);
        World world = editor(draft);
        if (saved.bounds().minY() < world.getMinHeight()
                || saved.bounds().maxY() >= world.getMaxHeight()) throw failure("height");
        for (Point point : concat(saved.red(), saved.blue())) validateStanding(world, point);
        for (Point point : List.of(saved.vault(), saved.stationA(), saved.stationB()))
            validateStanding(world, point);
        // Commands, structure loaders, and portals would make the match copy capable of affecting
        // other worlds.
        for (int x = saved.bounds().minX(); x <= saved.bounds().maxX(); x++) {
            for (int y = saved.bounds().minY(); y <= saved.bounds().maxY(); y++) {
                for (int z = saved.bounds().minZ(); z <= saved.bounds().maxZ(); z++) {
                    switch (world.getBlockAt(x, y, z).getType()) {
                        case COMMAND_BLOCK,
                                CHAIN_COMMAND_BLOCK,
                                REPEATING_COMMAND_BLOCK,
                                STRUCTURE_BLOCK,
                                JIGSAW,
                                NETHER_PORTAL,
                                END_PORTAL,
                                END_GATEWAY ->
                                throw failure("unsafe_block");
                        default -> {}
                    }
                }
            }
        }
        Structure structure = Bukkit.getStructureManager().createStructure();
        structure.fill(saved.bounds().origin(world), saved.bounds().size(), false);
        Path snapshot = templates.resolve(filename);
        Path temporary = templates.resolve(filename + ".tmp");
        try {
            Bukkit.getStructureManager().saveStructure(temporary.toFile(), structure);
            FinalsMapFiles.replace(temporary, snapshot);
            Map<String, Entry> next = new LinkedHashMap<>(entries);
            next.put(name, new Entry(draft, saved));
            commit(next, selected);
        } catch (IOException error) {
            plugin.getLogger().warning("Finals snapshot failed: " + error.getMessage());
            throw failure("io");
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    static void validateStanding(World world, Point point) {
        int x = point.bx(), y = point.by(), z = point.bz();
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        var below = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable()
                || feet.isLiquid()
                || !head.isPassable()
                || head.isLiquid()
                || !below.getType().isSolid()
                || below.isPassable()
                || hazardous(feet.getType())
                || hazardous(head.getType())
                || hazardous(below.getType())) throw failure("standable");
    }

    private static boolean hazardous(org.bukkit.Material material) {
        return switch (material) {
            case FIRE,
                    SOUL_FIRE,
                    POWDER_SNOW,
                    SWEET_BERRY_BUSH,
                    WITHER_ROSE,
                    MAGMA_BLOCK,
                    CACTUS,
                    CAMPFIRE,
                    SOUL_CAMPFIRE,
                    POINTED_DRIPSTONE ->
                    true;
            default -> false;
        };
    }

    public Component info(String name) {
        Entry entry = entry(name);
        Draft draft = entry.draft;
        Component status =
                I18n.tr(
                        entry.saved == null
                                ? "finals.map.status.draft"
                                : "finals.map.status.saved");
        return I18n.tr(
                "finals.map.info",
                name,
                status,
                draft.world,
                draft.red.size(),
                draft.blue.size(),
                mark(draft.first),
                mark(draft.second),
                mark(draft.vault),
                mark(draft.stationA),
                mark(draft.stationB));
    }

    private Component mark(Point point) {
        return point == null
                ? I18n.tr("finals.map.status.unset")
                : Component.text(point.bx() + ", " + point.by() + ", " + point.bz());
    }

    private Entry entry(String name) {
        Entry entry = entries.get(name);
        if (entry == null) throw failure("unknown", name);
        return entry;
    }

    private void update(String name, Location location, Consumer<Draft> edit) {
        Entry previous = entry(name);
        Draft draft = previous.draft.copy();
        if (location != null
                && (location.getWorld() == null
                        || !location.getWorld().getName().equals(draft.world)))
            throw failure("wrong_world", draft.world);
        edit.accept(draft);
        Map<String, Entry> next = new LinkedHashMap<>(entries);
        next.put(name, new Entry(draft, previous.saved));
        commit(next, selected);
    }

    private World editor(Draft draft) {
        World world = Bukkit.getWorld(draft.world);
        if (world == null && draft.imported && draft.world.matches("finals_edit_[a-z0-9_-]+")) {
            Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
            Path folder;
            try {
                folder =
                        FinalsMapFiles.source(
                                container, draft.folder == null ? draft.world : draft.folder);
            } catch (IOException | IllegalArgumentException invalid) {
                throw failure("world_load");
            }
            if (!folder.getFileName().toString().equals(draft.world)
                    || (!Files.isRegularFile(folder.resolve("level.dat"))
                            && !Files.isDirectory(folder.resolve("region"))))
                throw failure("world_load");
            world = new WorldCreator(draft.world).generateStructures(false).createWorld();
            if (world != null) prepareEditor(world);
        }
        if (world == null) throw failure("world_unloaded", draft.world);
        return world;
    }

    private static void prepareEditor(World world) {
        world.setGameRule(org.bukkit.GameRules.COMMAND_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWNER_BLOCKS_WORK, false);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        world.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED, 0);
    }

    private Structure loadTemplate(Saved saved) {
        if (!saved.template().matches("[a-z0-9_-]+\\.nbt")) throw failure("template");
        Path path = templates.resolve(saved.template());
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path)
                    || Files.size(path) > FinalsMapFiles.MAX_FILE_BYTES) throw failure("template");
            Structure structure = Bukkit.getStructureManager().loadStructure(path.toFile());
            if (structure == null
                    || !structure.getSize().equals(saved.bounds().size())
                    || structure.getEntityCount() != 0) throw failure("template");
            return structure;
        } catch (IOException error) {
            throw failure("template");
        }
    }

    private void commit(Map<String, Entry> next, String selection) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("selected", selection);
        next.forEach((name, entry) -> {
            entry.draft.write(yaml.createSection("maps." + name + ".draft"));
            if (entry.saved != null) {
                ConfigurationSection section = yaml.createSection("maps." + name + ".saved");
                Draft snapshot = new Draft();
                snapshot.world = entry.draft.world;
                Bounds b = entry.saved.bounds();
                snapshot.first = new Point(b.minX(), b.minY(), b.minZ(), 0, 0);
                snapshot.second = new Point(b.maxX(), b.maxY(), b.maxZ(), 0, 0);
                snapshot.red = entry.saved.red();
                snapshot.blue = entry.saved.blue();
                snapshot.vault = entry.saved.vault();
                snapshot.stationA = entry.saved.stationA();
                snapshot.stationB = entry.saved.stationB();
                snapshot.write(section);
                section.set("template", entry.saved.template());
            }
        });
        Path temporary = root.resolve("maps-" + UUID.randomUUID() + ".tmp");
        try {
            yaml.save(temporary.toFile());
            FinalsMapFiles.replace(temporary, registry);
            entries = new LinkedHashMap<>(next);
            selected = selection;
        } catch (IOException error) {
            plugin.getLogger().warning("Finals map registry write failed: " + error.getMessage());
            throw failure("io");
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private void load() throws IOException {
        if (!Files.exists(registry)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(registry.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException(error);
        }
        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps != null)
            for (String name : maps.getKeys(false)) {
                try {
                    FinalsMapFiles.name(name);
                    Draft draft = Draft.read(maps.getConfigurationSection(name + ".draft"));
                    ConfigurationSection section = maps.getConfigurationSection(name + ".saved");
                    Saved saved =
                            section == null
                                    ? null
                                    : Saved.from(
                                            Draft.read(section), section.getString("template", ""));
                    if (saved != null) loadTemplate(saved);
                    entries.put(name, new Entry(draft, saved));
                } catch (RuntimeException invalid) {
                    plugin.getLogger()
                            .warning(
                                    "Ignoring invalid Finals map "
                                            + name
                                            + ": "
                                            + invalid.getMessage());
                }
            }
        String requested = yaml.getString("selected", "generated");
        selected = names().contains(requested) ? requested : "generated";
    }

    static IllegalArgumentException failure(String key, Object... arguments) {
        return I18n.exception("finals.map.error." + key, arguments);
    }
}
