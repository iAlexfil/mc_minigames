import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.world.SessionWorlds;
import me.petr1furious.potatominigames.core.world.ThrowawayWorldService;
import me.petr1furious.potatominigames.games.finals.*;

/** Real Paper world, registry and native structure integration. Runs only in its private server directory. */
public final class FinalsMapsSmoke extends JavaPlugin {
    private int assertions;
    private final List<String> stages = new ArrayList<>();
    private Path evidence;

    @Override public void onEnable() {
        evidence = getDataFolder().toPath().resolve("result.txt");
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (!Boolean.getBoolean("finals.smoke.loadoutOnly")) runChecks();
                if (Boolean.getBoolean("finals.smoke.includeLoadouts") || Boolean.getBoolean("finals.smoke.loadoutOnly")) {
                    evidence = getDataFolder().toPath().resolve("loadout-result.txt");
                    JavaPlugin main=(JavaPlugin)Bukkit.getPluginManager().getPlugin("PotatoMinigames");
                    int count=FinalsLoadoutSmoke.run(this,main);
                    Files.createDirectories(evidence.getParent());
                    Files.writeString(evidence,"PASS: "+count+" native loadout, lifecycle, GUI, weapon and localization assertions\n");
                }
            }
            catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "FINALS_MAPS_SMOKE_FAIL", failure);
                try { Files.createDirectories(evidence.getParent()); Files.writeString(evidence,
                        "FAIL after " + assertions + " assertions\n" + String.join("\n", stages) + "\n" + failure); }
                catch (IOException ignored) { }
            } finally { Bukkit.getScheduler().runTask(this, Bukkit::shutdown); }
        });
    }
    private void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
    private void stage(String label) { stages.add(label); getLogger().info("MAP_STAGE_PASS: " + label); }
    private Location at(World world, double x, double y, double z) { return new Location(world,x,y,z); }
    private void configure(FinalsMaps maps, String name, World world) {
        maps.corner(name, 0, at(world,84,64,184)); maps.corner(name,1,at(world,116,80,216));
        maps.spawn(name,0,new Location(world,88.5,65,200.5,-90,0));
        maps.spawn(name,0,new Location(world,88.5,65,202.5,-90,0));
        maps.spawn(name,1,new Location(world,112.5,65,200.5,90,0));
        maps.vault(name,at(world,100.5,65,200.5));
        maps.station(name,0,at(world,100.5,65,188.5)); maps.station(name,1,at(world,100.5,65,212.5));
    }
    private void checkMetadata(World world, String stairData) {
        Chest chest=(Chest)world.getBlockAt(94,65,206).getState();
        ItemStack item=chest.getBlockInventory().getItem(0);
        require(item != null && item.getType()==Material.DIAMOND && item.getAmount()==3,"Native chest inventory persists");
        require(Component.text("Snapshot diamonds").equals(item.getItemMeta().displayName()),"Native item metadata persists");
        require(Component.text("Native cargo").equals(chest.customName()),"Native chest custom name persists");
        Sign sign=(Sign)world.getBlockAt(94,65,194).getState();
        require(Component.text("Native template").equals(sign.getSide(Side.FRONT).line(0)),"Native sign text persists");
        require(world.getBlockAt(94,65,196).getBlockData().getAsString().equals(stairData),"Native stair orientation persists");
    }
    private void runChecks() throws Exception {
        JavaPlugin main=(JavaPlugin)Bukkit.getPluginManager().getPlugin("PotatoMinigames");
        require(main!=null && main.isEnabled(),"Combined minigame plugin is enabled");
        FinalsMaps maps=new FinalsMaps(main);
        World editor=Bukkit.getWorlds().getFirst();
        require(editor!=null,"Native editor world loads");
        editor.setGameRule(GameRules.SPAWN_MOBS,false); editor.setSpawnLocation(88,65,200);
        for(int x=84;x<=116;x++)for(int z=184;z<=216;z++)editor.getBlockAt(x,64,z).setType(Material.STONE,false);
        editor.getBlockAt(106,66,206).setType(Material.WHITE_CONCRETE,false);
        editor.getBlockAt(94,65,206).setType(Material.CHEST,false);
        Chest chest=(Chest)editor.getBlockAt(94,65,206).getState();
        chest.customName(Component.text("Native cargo")); chest.update(true,false);
        ItemStack diamonds=new ItemStack(Material.DIAMOND,3); var itemMeta=diamonds.getItemMeta();
        itemMeta.displayName(Component.text("Snapshot diamonds")); diamonds.setItemMeta(itemMeta);
        chest.getBlockInventory().setItem(0,diamonds);
        editor.getBlockAt(94,65,194).setType(Material.OAK_SIGN,false);
        Sign sign=(Sign)editor.getBlockAt(94,65,194).getState();
        sign.getSide(Side.FRONT).line(0,Component.text("Native template")); sign.update(true,false);
        Stairs stairs=(Stairs)Material.OAK_STAIRS.createBlockData(); stairs.setFacing(BlockFace.EAST); stairs.setHalf(Stairs.Half.TOP);
        editor.getBlockAt(94,65,196).setBlockData(stairs,false); String stairData=stairs.getAsString();
        ArmorStand actor=editor.spawn(at(editor,96.5,65,200.5),ArmorStand.class); actor.setGravity(false);
        checkMetadata(editor,stairData);
        maps.create("native",at(editor,88.5,65,200.5)); configure(maps,"native",editor); maps.save("native"); maps.select("native");
        require(editor.getBlockAt(100,65,200).getType()==Material.AIR,"Saving does not place source objective markers");
        Path finals=main.getDataFolder().toPath().resolve("finals");
        Path snapshot;
        try(var paths=Files.list(finals.resolve("templates"))) { snapshot=paths.filter(p->p.getFileName().toString().startsWith("native-")).findFirst().orElseThrow(); }
        String snapshotHash=hash(snapshot);
        var structure=Bukkit.getStructureManager().loadStructure(snapshot.toFile());
        require(structure.getSize().getBlockX()==33 && structure.getSize().getBlockY()==17 && structure.getSize().getBlockZ()==33,
                "Native structure snapshot has inclusive bounds");
        require(structure.getEntityCount()==0,"Native structure excludes source entities");
        require(actor.isValid(),"Saving leaves source entity intact");
        stage("Native structure saved block states, inventory, sign data and source coordinates without entities");

        maps.corner("native",1,at(editor,800,80,216));
        byte[] beforeFailure=Files.readAllBytes(finals.resolve("maps.yml"));
        boolean rejected=false;
        try{maps.save("native");}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Oversized draft save is rejected");
        require(Arrays.equals(beforeFailure,Files.readAllBytes(finals.resolve("maps.yml"))),"Failed save preserves persisted registry");
        maps=new FinalsMaps(main);
        require(maps.selected().equals("native") && maps.names().contains("native"),"Saved map and selected choice survive reload with invalid draft");
        require(hash(snapshot).equals(snapshotHash),"Failed save preserves previous template bytes");
        stage("Failed draft save preserved the selected map, registry and immutable template across service reload");

        SessionWorlds worlds=new SessionWorlds(new ThrowawayWorldService(main),null);
        SessionServices services=new SessionServices(main,null,null,null,null,null,null,null,worlds,null,null,null);
        FinalsMapArena first=maps.open(services,null); first.build(); World match=first.world();
        require(match!=editor && match.getName().startsWith("potato_tmp_"),"Match uses a disposable world");
        require(first.center().getX()==100.5 && first.center().getZ()==200.5 && first.borderSize()==33,"Border geometry preserves source offset");
        require(first.spawn(0,0).getYaw()==-90 && first.spawn(0,1).getZ()==202.5,"Multiple spawns and facing persist");
        require(first.vault().getBlock().getType()==Material.EMERALD_BLOCK,"Vault marker exists only in match copy");
        require(first.station(0).getBlock().getType()==Material.GOLD_BLOCK && first.station(1).getBlock().getType()==Material.GOLD_BLOCK,"Both cashout markers exist");
        require(!first.isCover(match.getBlockAt(100,64,200)) && !first.isCover(first.vault().getBlock()),"Floor and objectives are protected");
        require(!first.isCover(first.spawn(0,0).clone().subtract(0,1,0).getBlock()),"Spawn support is protected");
        require(first.isCover(match.getBlockAt(106,66,206)),"Interior cover is destructible");
        require(match.getEntities().isEmpty(),"Source armor stand is absent from the match copy");
        checkMetadata(match,stairData);
        match.getBlockAt(106,66,206).setType(Material.AIR,false);
        ((Chest)match.getBlockAt(94,65,206).getState()).getBlockInventory().clear();
        require(editor.getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Match destruction leaves source blocks intact");
        checkMetadata(editor,stairData);
        FinalsMapArena second=maps.open(services,"native"); second.build();
        require(second.world().getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Fresh match restores destroyed cover");
        checkMetadata(second.world(),stairData);
        require(hash(snapshot).equals(snapshotHash),"Multiple matches never rewrite the saved template");
        worlds.destroyVoid("finals");
        stage("Two real disposable match copies restored destructible cover and block-entity metadata without touching the source/template");

        editor.save(); Path editorFolder=editor.getWorldFolder().toPath();
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save-all flush"),"Primary overworld flushes before import fixture copy");
        Path imports=finals.resolve("imports"), folder=imports.resolve("native-folder");
        Path levelRoot=editorFolder.toAbsolutePath().normalize();
        Path container=Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        while(levelRoot.startsWith(container) && !Files.isRegularFile(levelRoot.resolve("level.dat"))) levelRoot=levelRoot.getParent();
        require(levelRoot!=null && levelRoot.startsWith(container),"Native world metadata root is located");
        Files.createDirectories(folder);
        try(var sourceFiles=Files.walk(levelRoot)) {
            for(Path file:sourceFiles.filter(Files::isRegularFile).toList()) {
                if(file.getFileName().toString().equals("session.lock")) continue;
                Path destination=folder.resolve(levelRoot.relativize(file));
                Files.createDirectories(destination.getParent()); Files.copy(file,destination);
            }
        }
        require(Files.isDirectory(folder.resolve("dimensions/minecraft/overworld/region")),"Import fixture uses the actual native overworld save layout");
        Files.writeString(folder.resolve("uid.dat"),"fixture source identity");
        Files.createDirectories(folder.resolve("datapacks")); Files.writeString(folder.resolve("datapacks/unused.txt"),"not copied");
        Map<String,String> sourceHashes=hashes(folder);
        maps.importWorld("folderimport","native-folder"); World imported=maps.editorLocation("folderimport").getWorld();
        require(imported!=null && imported.getName().startsWith("finals_edit_"),"Folder import creates an editor copy");
        require(imported.getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Folder import retains native region blocks");
        checkMetadata(imported,stairData);
        String importedName=imported.getName(); imported.save();
        require(Bukkit.unloadWorld(imported,true),"Imported editor unloads cleanly");
        maps=new FinalsMaps(main); imported=maps.editorLocation("folderimport").getWorld();
        require(imported.getName().equals(importedName),"Imported editor reloads from migrated native storage");
        checkMetadata(imported,stairData);
        require(Boolean.FALSE.equals(imported.getGameRuleValue(GameRules.COMMAND_BLOCKS_WORK)),"Imported editor disables command block execution");
        require(!Files.exists(imported.getWorldFolder().toPath().resolve("datapacks/unused.txt")),"World datapacks are not imported");
        configure(maps,"folderimport",imported); maps.save("folderimport");
        imported.getBlockAt(106,66,206).setType(Material.AIR,false); imported.save();
        require(sourceHashes.equals(hashes(folder)),"Editor changes do not alter any original import file");
        stage("Native world-folder import preserved region/chest/sign data, disabled command blocks and left original files byte-identical");

        Path zip=imports.resolve("native.zip");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip));var paths=Files.walk(folder)) {
            for(Path file:paths.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry("wrapped/"+folder.relativize(file).toString().replace('\\','/')));
                Files.copy(file,out); out.closeEntry();
            }
        }
        String zipHash=hash(zip); maps.importWorld("zipimport","native.zip"); World zipped=maps.editorLocation("zipimport").getWorld();
        require(zipped.getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Wrapped ZIP imports native region data");
        checkMetadata(zipped,stairData); require(hash(zip).equals(zipHash),"ZIP source bytes remain unchanged");
        Path evil=imports.resolve("unsafe.zip");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(evil))) {
            for(String name:List.of("../escaped.txt","level.dat","region/r.0.0.mca")) {
                out.putNextEntry(new ZipEntry(name)); out.write(new byte[]{1,2,3}); out.closeEntry();
            }
        }
        rejected=false; try{maps.importWorld("unsafe","unsafe.zip");}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected && !maps.drafts().contains("unsafe"),"ZIP traversal rejected without registering an editor");
        require(maps.selected().equals("native") && hash(snapshot).equals(snapshotHash),"Failed import preserves selected map and template");
        stage("Wrapped ZIP import preserved block metadata and archive bytes; ZIP traversal failed without changing the selected map");
        maps.select("generated"); require(new FinalsMaps(main).selected().equals("generated"),"Generated fallback selection persists");
        stage("Generated fallback remains selectable after custom-map setup/import");
        String result="PASS: "+assertions+" native Bukkit map assertions\n"+String.join("\n",stages)+"\n";
        Files.createDirectories(evidence.getParent()); Files.writeString(evidence,result);
        getLogger().info("FINALS_MAPS_SMOKE_PASS: "+assertions+" assertions");
    }
    private static String hash(Path path)throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static Map<String,String> hashes(Path root)throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList())result.put(root.relativize(path).toString(),hash(path));}
        return result;
    }
}
