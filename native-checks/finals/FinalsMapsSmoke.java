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
                if (!Boolean.getBoolean("finals.smoke.loadoutOnly") && !Boolean.getBoolean("finals.smoke.previewOnly")) runChecks();
                if (Boolean.getBoolean("finals.smoke.includePreviews") || Boolean.getBoolean("finals.smoke.previewOnly")) {
                    evidence = getDataFolder().toPath().resolve("preview-result.txt");
                    JavaPlugin main=(JavaPlugin)Bukkit.getPluginManager().getPlugin("PotatoMinigames");
                    int count=FinalsPreviewSmoke.run(this,main);
                    Files.createDirectories(evidence.getParent());
                    Files.writeString(evidence,"PASS: "+count+" native map preview assertions\n");
                    getLogger().info("FINALS_PREVIEW_SMOKE_PASS: "+count+" assertions");
                }
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
        maps.corner(name, 0, at(world,84,65,184)); maps.corner(name,1,at(world,116,65,216));
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
    private void checkVaults(FinalsMapArena arena, World source) {
        require(arena.vaultLocations().size()==3,"All three saved vault candidates survive service reload");
        Location original=arena.vaultLocations().getFirst().clone();
        arena.vaultLocations().getFirst().add(400,0,0);
        require(arena.vaultLocations().getFirst().equals(original),"Candidate coordinates are defensive copies");
        Location active=arena.vault(); arena.build();
        require(arena.vault().equals(active),"Repeated build does not move an active vault");
        for(Location candidate:arena.vaultLocations()) {
            Block head=candidate.getBlock().getRelative(BlockFace.UP); var originalData=head.getBlockData();
            head.setType(Material.WHITE_CONCRETE,false);
            require(!arena.isCover(head),"Future vault headroom stays protected even before its marker is active");
            head.setBlockData(originalData,false);
        }
        for(int cycle=0;cycle<24;cycle++) {
            Location previous=arena.vault(); arena.nextVault();
            require(!previous.equals(arena.vault()),"Successive cashout cycles do not repeat the same vault spot");
            int markers=0;
            for(Location candidate:arena.vaultLocations()) {
                Block block=candidate.getBlock();
                require(!arena.isCover(block) && !arena.isCover(block.getRelative(BlockFace.DOWN))
                        && !arena.isCover(block.getRelative(BlockFace.UP)),"All vault candidates retain protected block, support and headroom");
                if(candidate.equals(arena.vault())) {
                    markers++;
                    require(block.getType()==Material.EMERALD_BLOCK && arena.isVault(block),"Active candidate is the interactive emerald vault");
                } else {
                    require(!arena.isVault(block),"Inactive candidates cannot activate the vault");
                    Block expected=source.getBlockAt(block.getX(),block.getY(),block.getZ());
                    require(block.getBlockData().equals(expected.getBlockData()),"Inactive vault restores its original native block data");
                    if(expected.getState() instanceof Sign sign) {
                        require(block.getState() instanceof Sign restored
                                && restored.getSide(Side.FRONT).line(0).equals(sign.getSide(Side.FRONT).line(0)),
                                "Moving a vault restores the original sign text");
                    }
                }
            }
            require(markers==1,"Exactly one configured vault is active");
        }
    }
    private void checkVaultSetup(JavaPlugin main, World editor, SessionServices services) {
        FinalsMaps maps=new FinalsMaps(main);
        maps.create("vaultsettings",at(editor,88.5,65,200.5)); configure(maps,"vaultsettings",editor);
        maps.vaultAdd("vaultsettings",at(editor,96.5,65,200.5));
        maps.vaultClear("vaultsettings");
        boolean rejected=false;
        try { maps.save("vaultsettings"); } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected,"Clearing all vaults requires a new candidate before saving");
        maps.vaultAdd("vaultsettings",at(editor,96.5,65,200.5)); maps.save("vaultsettings");
        maps=new FinalsMaps(main);
        FinalsMapArena single=maps.open(services,"vaultsettings"); single.build();
        require(single.vaultLocations().size()==1 && single.vault().getBlockX()==96,"Clear then add persists exactly one new location");
        Location previous=single.vault(); single.nextVault();
        require(single.vault().equals(previous) && single.vault().getBlock().getType()==Material.EMERALD_BLOCK,
                "One-location maps keep a working vault on the next cycle");
        maps.vaultAdd("vaultsettings",at(editor,100.5,65,200.5)); maps.save("vaultsettings");
        FinalsMapArena pair=new FinalsMaps(main).open(services,"vaultsettings"); pair.build();
        require(pair.vaultLocations().size()==2,"Appending retains the existing candidate after save and reload");
        for(int cycle=0;cycle<2;cycle++) {
            Location old=pair.vault(); pair.nextVault();
            require(!old.equals(pair.vault()),"Two-location map always alternates candidates");
            Block restored=old.getBlock();
            require(restored.getBlockData().equals(editor.getBlockAt(old.getBlockX(),old.getBlockY(),old.getBlockZ()).getBlockData()),
                    "Moving between two candidates restores exact original block data");
            if(old.getBlockX()==96)
                require(restored.getState() instanceof Sign sign
                        && sign.getSide(Side.FRONT).line(0).equals(Component.text("Vault candidate sign")),
                        "Sign block entity text survives a full vault placement and removal");
        }
        maps.vault("vaultsettings",at(editor,104.5,65,200.5)); maps.save("vaultsettings");
        FinalsMapArena reset=new FinalsMaps(main).open(services,"vaultsettings"); reset.build();
        require(reset.vaultLocations().size()==1 && reset.vault().getBlockX()==104,
                "Legacy vault setter replaces all candidates with the specified single location");
        stage("Vault setup append, clear, reset and single-location compatibility persisted through native service reload");
    }
    private void checkCashouts(FinalsMapArena arena, World source) {
        require(arena.cashoutLocations().size()==4,"All saved cashout candidates survive reload");
        for(Location candidate:arena.cashoutLocations()) {
            Block head=candidate.getBlock().getRelative(BlockFace.UP); var originalData=head.getBlockData();
            head.setType(Material.WHITE_CONCRETE,false);
            require(arena.isCover(head),"Terrain near cashout candidates remains destructible");
            head.setBlockData(originalData,false);
        }
        for(int cycle=0;cycle<20;cycle++) {
            int slot=cycle%2;
            Location previous=arena.station(slot), other=arena.station(1-slot);
            arena.nextCashout(slot);
            require(!previous.equals(arena.station(slot)),"Completed cashout relocates to an unused different candidate");
            require(other.equals(arena.station(1-slot)),"The other active cashout remains in place");
            require(!arena.station(0).equals(arena.station(1)),"Two active cashouts never overlap");
            int active=0;
            for(Location candidate:arena.cashoutLocations()) {
                Block block=candidate.getBlock(); int recognized=arena.stationAt(block);
                require(arena.isCover(block.getRelative(BlockFace.DOWN)),
                        "Cashout support can be destroyed so the simulated body can fall");
                if(recognized>=0) {
                    active++;
                    require(candidate.equals(arena.station(recognized)),
                            "Only active cashout candidates accept objective interactions");
                }
                {
                    Block original=source.getBlockAt(block.getX(),block.getY(),block.getZ());
                    require(block.getBlockData().equals(original.getBlockData()),"Cashout rotation never changes source block data");
                    if(original.getState() instanceof Sign sign)
                        require(block.getState() instanceof Sign restored
                                && restored.getSide(Side.FRONT).line(0).equals(sign.getSide(Side.FRONT).line(0)),
                                "Cashout rotation leaves native sign text untouched");
                }
            }
            require(active==2,"Exactly two cashout candidates are active after every replacement");
        }
        stage("Cashout rotation preserves exactly two active stations and restores retired block metadata");
    }

    private void checkCashoutSetup(JavaPlugin main, World editor, SessionServices services) {
        FinalsMaps maps=new FinalsMaps(main);
        maps.create("cashoutsettings",at(editor,88.5,65,200.5)); configure(maps,"cashoutsettings",editor);
        maps.save("cashoutsettings");
        FinalsMapArena legacy=new FinalsMaps(main).open(services,"cashoutsettings"); legacy.build();
        Location a=legacy.station(0), b=legacy.station(1);
        legacy.nextCashout(0); legacy.nextCashout(1);
        require(legacy.station(0).equals(a) && legacy.station(1).equals(b),
                "A two-location map keeps both old cashouts usable after completion");
        maps.cashoutAdd("cashoutsettings",at(editor,88.5,65,188.5));
        editor.getBlockAt(88,66,188).setType(Material.STONE,false);
        boolean rejected=false;
        try { maps.save("cashoutsettings"); } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected,"Saving validates headroom at additional cashout locations");
        editor.getBlockAt(88,66,188).setType(Material.AIR,false);
        maps.cashoutClear("cashoutsettings");
        rejected=false;
        try { maps.save("cashoutsettings"); } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected,"A cleared cashout pool cannot be saved without two locations");
        maps.cashoutAdd("cashoutsettings",at(editor,88.5,65,188.5));
        maps.cashoutAdd("cashoutsettings",at(editor,112.5,65,212.5)); maps.save("cashoutsettings");
        FinalsMapArena reset=new FinalsMaps(main).open(services,"cashoutsettings"); reset.build();
        require(reset.cashoutLocations().size()==2 && reset.station(0).getBlockX()==88 && reset.station(1).getBlockX()==112,
                "Clearing and adding replacement cashouts persists exactly the new pool");
        stage("Cashout setup append, clear, legacy pair and candidate validation survive reload");
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
        editor.getBlockAt(106,editor.getMinHeight(),206).setType(Material.COPPER_BLOCK,false);
        editor.getBlockAt(106,editor.getMaxHeight()-1,206).setType(Material.LAPIS_BLOCK,false);
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
        editor.getBlockAt(96,65,200).setBlockData(Bukkit.createBlockData("minecraft:oak_sign[rotation=5,waterlogged=false]"),false);
        Sign vaultSign=(Sign)editor.getBlockAt(96,65,200).getState();
        vaultSign.getSide(Side.FRONT).line(0,Component.text("Vault candidate sign")); vaultSign.update(true,false);
        editor.getBlockAt(88,65,188).setBlockData(Bukkit.createBlockData("minecraft:oak_sign[rotation=9,waterlogged=false]"),false);
        Sign cashoutSign=(Sign)editor.getBlockAt(88,65,188).getState();
        cashoutSign.getSide(Side.FRONT).line(0,Component.text("Cashout candidate sign")); cashoutSign.update(true,false);
        ArmorStand actor=editor.spawn(at(editor,96.5,65,200.5),ArmorStand.class); actor.setGravity(false);
        checkMetadata(editor,stairData);
        maps.create("native",at(editor,88.5,65,200.5)); configure(maps,"native",editor);
        maps.vaultAdd("native",at(editor,96.5,65,200.5)); maps.vaultAdd("native",at(editor,104.5,65,200.5));
        maps.cashoutAdd("native",at(editor,88.5,65,188.5)); maps.cashoutAdd("native",at(editor,112.5,65,212.5));
        editor.getBlockAt(104,66,200).setType(Material.STONE,false);
        boolean unsafeVaultRejected=false;
        try { maps.save("native"); } catch(IllegalArgumentException expected) { unsafeVaultRejected=true; }
        require(unsafeVaultRejected,"Saving validates headroom of additional vault candidates");
        editor.getBlockAt(104,66,200).setType(Material.AIR,false);
        editor.getBlockAt(96,64,200).setType(Material.AIR,false);
        unsafeVaultRejected=false;
        try { maps.save("native"); } catch(IllegalArgumentException expected) { unsafeVaultRejected=true; }
        require(unsafeVaultRejected,"Saving validates supporting floor of additional vault candidates");
        editor.getBlockAt(96,64,200).setType(Material.STONE,false);
        maps.save("native"); maps.select("native");
        require(editor.getBlockAt(100,65,200).getType()==Material.AIR,"Saving does not place source objective markers");
        Path finals=main.getDataFolder().toPath().resolve("finals");
        Path snapshot;
        try(var paths=Files.list(finals.resolve("templates"))) { snapshot=paths.filter(p->p.getFileName().toString().startsWith("native-")).findFirst().orElseThrow(); }
        String snapshotHash=hash(snapshot);
        require(Files.isDirectory(snapshot) && snapshot.getFileName().toString().endsWith(".tiles"),
                "Saved maps use a tiled native structure template");
        var manifest=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(snapshot.resolve("manifest.yml").toFile());
        require(manifest.getIntegerList("bounds").equals(List.of(84,editor.getMinHeight(),184,116,editor.getMaxHeight()-1,216)),
                "Same-height horizontal corners snapshot the full native world height");
        var savedRegistry=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(finals.resolve("maps.yml").toFile());
        require(savedRegistry.getDoubleList("maps.native.draft.pos1").get(1)==editor.getMinHeight()
                && savedRegistry.getDoubleList("maps.native.draft.pos2").get(1)==editor.getMaxHeight()-1,
                "Draft corners persist the world's bottom and highest buildable block");
        try(var tiles=Files.list(snapshot)) {
            var pieces=tiles.filter(path->path.getFileName().toString().endsWith(".nbt")).toList();
            require(!pieces.isEmpty(),"Tiled snapshot contains native structure pieces");
            for(Path piece:pieces)
                require(Bukkit.getStructureManager().loadStructure(piece.toFile()).getEntityCount()==0,
                        "Native structure tiles exclude source entities");
        }
        require(actor.isValid(),"Saving leaves source entity intact");
        stage("Native structure saved block states, inventory, sign data and source coordinates without entities");

        maps.vaultClear("native");
        byte[] beforeFailure=Files.readAllBytes(finals.resolve("maps.yml"));
        boolean rejected=false;
        try{maps.save("native");}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"A draft missing required vault candidates cannot replace a saved map");
        require(Arrays.equals(beforeFailure,Files.readAllBytes(finals.resolve("maps.yml"))),"Failed save preserves persisted registry");
        maps=new FinalsMaps(main);
        require(maps.selected().equals("native") && maps.names().contains("native"),"Saved map and selected choice survive reload with invalid draft");
        require(hash(snapshot).equals(snapshotHash),"Failed save preserves previous template bytes");
        stage("Failed draft save preserved the selected map, registry and immutable template across service reload");

        SessionWorlds worlds=new SessionWorlds(new ThrowawayWorldService(main),null);
        SessionServices services=new SessionServices(main,null,null,null,null,null,null,null,worlds,null,null,null);
        FinalsMapArena first=maps.open(services,null); first.build(); World match=first.world();
        require(match!=editor && match.getName().startsWith("potato_tmp_"),"Match uses a disposable world");
        require(match.getBlockAt(106,editor.getMinHeight(),206).getType()==Material.COPPER_BLOCK
                && match.getBlockAt(106,editor.getMaxHeight()-1,206).getType()==Material.LAPIS_BLOCK,
                "The bottom and highest native world layers survive save, reload and match placement");
        require(first.center().getX()==100.5 && first.center().getZ()==200.5 && first.borderSize()==33,"Border geometry preserves source offset");
        require(first.spawn(0,0).getYaw()==-90 && first.spawn(0,1).getZ()==202.5,"Multiple spawns and facing persist");
        require(first.vault().getBlock().getType()==Material.EMERALD_BLOCK,"Vault marker exists only in match copy");
        require(first.station(0).getBlock().getBlockData().equals(editor.getBlockAt(first.station(0).getBlockX(),first.station(0).getBlockY(),first.station(0).getBlockZ()).getBlockData())
                && first.station(1).getBlock().getBlockData().equals(editor.getBlockAt(first.station(1).getBlockX(),first.station(1).getBlockY(),first.station(1).getBlockZ()).getBlockData()),
                "Cashout selection leaves the native anchor blocks untouched");
        require(!first.isCover(match.getBlockAt(100,64,200)) && !first.isCover(first.vault().getBlock()),"Floor and objectives are protected");
        require(!first.isCover(first.spawn(0,0).clone().subtract(0,1,0).getBlock()),"Spawn support is protected");
        require(first.isCover(match.getBlockAt(106,66,206)),"Interior cover is destructible");
        require(match.getEntities().isEmpty(),"Source armor stand is absent from the match copy");
        checkMetadata(match,stairData);
        checkVaults(first,editor);
        checkCashouts(first,editor);
        stage("Random vault rotation kept one active marker, avoided repeats and restored native block states");
        match.getBlockAt(106,66,206).setType(Material.AIR,false);
        ((Chest)match.getBlockAt(94,65,206).getState()).getBlockInventory().clear();
        require(editor.getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Match destruction leaves source blocks intact");
        checkMetadata(editor,stairData);
        FinalsMapArena second=maps.open(services,"native"); second.build();
        require(second.world().getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,"Fresh match restores destroyed cover");
        checkMetadata(second.world(),stairData);
        require(hash(snapshot).equals(snapshotHash),"Multiple matches never rewrite the saved template");
        checkLegacySnapshot(main,editor,services,finals);
        checkVaultSetup(main,editor,services);
        checkCashoutSetup(main,editor,services);
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
    private void checkLegacySnapshot(JavaPlugin main, World editor, SessionServices services, Path finals)throws Exception {
        Path registry=finals.resolve("maps.yml"), legacy=finals.resolve("templates/native-legacy-fixture.nbt");
        byte[] original=Files.readAllBytes(registry);
        var structure=Bukkit.getStructureManager().createStructure();
        structure.fill(at(editor,84,64,184),new org.bukkit.util.BlockVector(33,17,33),false);
        Bukkit.getStructureManager().saveStructure(legacy.toFile(),structure);
        try {
            var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(registry.toFile());
            yaml.set("maps.native.saved.template",legacy.getFileName().toString());
            yaml.set("maps.native.saved.pos1",List.of(84d,64d,184d,0d,0d));
            yaml.set("maps.native.saved.pos2",List.of(116d,80d,216d,0d,0d));
            yaml.save(registry.toFile());
            FinalsMapArena restored=new FinalsMaps(main).open(services,"native"); restored.build();
            checkMetadata(restored.world(),editor.getBlockAt(94,65,196).getBlockData().getAsString());
            require(restored.world().getBlockAt(106,66,206).getType()==Material.WHITE_CONCRETE,
                    "Existing single-file native snapshots retain cover and metadata compatibility");
            stage("Legacy single-file NBT snapshots still load and place alongside tiled snapshots");
        } finally { Files.write(registry,original); }
    }
    private static String hash(Path path)throws Exception {
        byte[] bytes=Files.isDirectory(path) ? hashes(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8) : Files.readAllBytes(path);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String,String> hashes(Path root)throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList())result.put(root.relativize(path).toString(),hash(path));}
        return result;
    }
}
