import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import me.petr1furious.potatominigames.games.finals.FinalsMaps;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Real registry persistence and one-based point removal, run in the private smoke server. */
public final class FinalsPointRemoveSmoke {
    private int assertions;

    public static int run(JavaPlugin main, World editor) throws Exception {
        return new FinalsPointRemoveSmoke().check(main, editor);
    }

    private int check(JavaPlugin main, World world) throws Exception {
        String name = "remove_" + UUID.randomUUID().toString().substring(0, 8);
        FinalsMaps maps = new FinalsMaps(main);
        Path registry = main.getDataFolder().toPath().resolve("finals/maps.yml");
        String draft = "maps." + name + ".draft.";
        String saved = "maps." + name + ".saved.";
        for (int x = 6000; x <= 6032; x++) {
            for (int z = 6000; z <= 6032; z++) {
                world.getBlockAt(x, 64, z).setType(Material.STONE, false);
                world.getBlockAt(x, 65, z).setType(Material.AIR, false);
                world.getBlockAt(x, 66, z).setType(Material.AIR, false);
            }
        }
        maps.create(name, at(world, 6004, 6016));
        maps.corner(name, 0, at(world, 6000, 6000));
        maps.corner(name, 1, at(world, 6032, 6032));
        maps.spawn(name, 0, at(world, 6004, 6016));
        maps.spawn(name, 0, at(world, 6004, 6018));
        maps.spawn(name, 1, at(world, 6028, 6016));
        maps.spawn(name, 1, at(world, 6028, 6018));
        maps.vaultAdd(name, at(world, 6016, 6016));
        maps.vaultAdd(name, at(world, 6016, 6020));
        maps.vaultAdd(name, at(world, 6016, 6012));
        maps.cashoutAdd(name, at(world, 6016, 6004));
        maps.cashoutAdd(name, at(world, 6016, 6028));
        maps.cashoutAdd(name, at(world, 6004, 6004));
        maps.save(name);
        YamlConfiguration initial = load(registry);
        String template = initial.getString(saved + "template");
        List<Double> originalVault = initial.getDoubleList(saved + "vaults.1");

        maps.vaultRemove(name, 2);
        maps.cashoutRemove(name, 1);
        maps.spawnRemove(name, 0, 1);
        maps.spawnRemove(name, 1, 2);
        maps = new FinalsMaps(main);
        YamlConfiguration changed = load(registry);
        require(changed.getConfigurationSection(draft + "vaults").getKeys(false).size() == 2,
                "Removing a middle vault leaves two persisted candidates");
        require(changed.getDoubleList(draft + "vaults.1").equals(initial.getDoubleList(draft + "vaults.2")),
                "Later vault numbers compact after removal");
        require(changed.getDoubleList(draft + "cashouts.0").equals(initial.getDoubleList(draft + "cashouts.1")),
                "Removing the first cashout compacts its remaining numbers");
        require(changed.getDoubleList(draft + "red.0").equals(initial.getDoubleList(draft + "red.1")),
                "Red spawn removal preserves the intended remaining spawn");
        require(changed.getDoubleList(draft + "blue.0").equals(initial.getDoubleList(draft + "blue.0")),
                "Blue spawn removal preserves its first spawn");
        require(!changed.contains(draft + "red.1") && !changed.contains(draft + "blue.1"),
                "Spawn removal clears stale trailing registry entries");
        require(template.equals(changed.getString(saved + "template"))
                        && originalVault.equals(changed.getDoubleList(saved + "vaults.1")),
                "Draft edits leave the saved immutable snapshot and saved candidates unchanged");

        byte[] before = Files.readAllBytes(registry);
        FinalsMaps current = maps;
        fails(() -> current.vaultRemove(name, 0));
        fails(() -> current.vaultRemove(name, -1));
        fails(() -> current.vaultRemove(name, 3));
        fails(() -> current.cashoutRemove(name, 3));
        fails(() -> current.spawnRemove(name, 0, 2));
        fails(() -> current.spawnRemove(name, 1, 0));
        fails(() -> current.spawnRemove(name, 2, 1));
        require(Arrays.equals(before, Files.readAllBytes(registry)),
                "Invalid point numbers and invalid teams never mutate the registry");

        maps.vaultRemove(name, 1);
        maps.vaultRemove(name, 1);
        maps.cashoutRemove(name, 1);
        maps.cashoutRemove(name, 1);
        maps.spawnRemove(name, 0, 1);
        maps.spawnRemove(name, 1, 1);
        FinalsMaps reloaded = new FinalsMaps(main);
        YamlConfiguration empty = load(registry);
        require(empty.getConfigurationSection(draft + "vaults").getKeys(false).isEmpty()
                        && empty.getConfigurationSection(draft + "cashouts").getKeys(false).isEmpty()
                        && !empty.contains(draft + "red") && !empty.contains(draft + "blue"),
                "Removing each last point leaves a reloadable incomplete draft");
        require(reloaded.drafts().contains(name) && reloaded.names().contains(name)
                        && template.equals(empty.getString(saved + "template")),
                "Incomplete draft edits preserve the previously playable saved version");
        maps.close();
        reloaded.close();
        return assertions;
    }

    private static Location at(World world, int x, int z) {
        return new Location(world, x + .5, 65, z + .5);
    }

    private static YamlConfiguration load(Path path) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        return yaml;
    }

    private void fails(Runnable operation) {
        boolean failed = false;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        require(failed, "Invalid removal rejects its point number or team");
    }

    private void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
}
