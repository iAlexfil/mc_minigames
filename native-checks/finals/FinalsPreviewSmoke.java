import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.petr1furious.potatominigames.games.finals.*;

/** Real native display entities and world snapshots; only player transport is simulated. */
public final class FinalsPreviewSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsPreviewSmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        FinalsMaps maps = new FinalsMaps(main);
        World world = Bukkit.getWorlds().getFirst();
        String name = "preview-smoke";
        for (int x = 479; x <= 521; x++) for (int z = 479; z <= 521; z++) {
            world.getBlockAt(x,64,z).setType(Material.STONE, false);
            world.getBlockAt(x,65,z).setType(Material.AIR, false);
            world.getBlockAt(x,66,z).setType(Material.AIR, false);
        }
        maps.create(name, at(world,500.5,65,500.5));
        maps.corner(name,0,at(world,479,64,479));
        maps.corner(name,1,at(world,521,70,521));
        maps.vault(name,at(world,500.5,65,500.5));
        maps.station(name,0,at(world,500.5,65,520.5));
        maps.station(name,1,at(world,520.5,65,500.5));
        maps.spawn(name,0,new Location(world,480.5,65,500.5,90,0));
        maps.spawn(name,1,new Location(world,520.5,65,520.5,-90,0));
        FakePlayer viewer = new FakePlayer(world);
        FakePlayer other = new FakePlayer(world);
        FinalsMapPreview preview = new FinalsMapPreview(main,maps);
        try {
            preview.show(viewer.player,name);
            Set<TextDisplay> first = viewer.active();
            require(first.size()==5,"Every configured vault, cashout and team spawn has a display");
            require(other.active().isEmpty(),"Private previews do not target another editor");
            for (TextDisplay display : first) {
                require(!display.isVisibleByDefault(),"Native labels are hidden from other players by default");
                require(!display.isPersistent(),"Native labels cannot persist in editor world saves");
                require(display.getBillboard()==Display.Billboard.CENTER,"Labels face their viewing editor");
            }
            Set<String> labels = labels(first);
            require(labels.containsAll(Set.of("Vault 1","Cashout 1","Cashout 2","Red spawn 1","Blue spawn 1")),
                    "All labels are numbered and rendered into English before native transmission");
            require(viewer.particles>0 && other.particles==0,"Outline particles are sent only to the preview viewer");
            require(viewer.particleLocations.stream().anyMatch(point -> Math.abs(point.getX()-479.3)<.001
                            && Math.abs(point.getZ()-500.5)<.001),
                    "A red spawn facing west draws its arrow toward the stored yaw");
            require(viewer.particleLocations.stream().anyMatch(point -> Math.abs(point.getX()-521.7)<.001
                            && Math.abs(point.getZ()-520.5)<.001),
                    "A blue spawn facing east draws its arrow in the opposite direction");
            tick(preview);
            require(first.equals(viewer.active()),"Unchanged drafts reuse display entities during refresh");
            maps.vaultAdd(name,at(world,500.5,65,490.5));
            tick(preview);
            require(viewer.active().size()==6 && viewer.active().containsAll(first),
                    "Added candidate appears live without rebuilding unchanged labels");
            require(world.getBlockAt(500,65,500).getType()==Material.AIR
                    && world.getBlockAt(500,64,500).getType()==Material.STONE,
                    "Preview rendering preserves both location blocks and supporting terrain");
            maps.save(name);
            Path templates=main.getDataFolder().toPath().resolve("finals/templates");
            Path snapshot;
            try(var files=Files.list(templates)) {
                snapshot=files.filter(file->file.getFileName().toString().startsWith(name+"-")).findFirst().orElseThrow();
            }
            require(Bukkit.getStructureManager().loadStructure(snapshot.toFile()).getEntityCount()==0,
                    "Saving while previews are visible excludes their display entities from the playable map");
            viewer.locale=Locale.forLanguageTag("ru");
            tick(preview);
            require(labels(viewer.active()).stream().anyMatch(label->label.startsWith("Хранилище")),
                    "Changing client language refreshes native labels into Russian");
            require(first.stream().noneMatch(Entity::isValid),"A language refresh removes superseded displays");
            maps.vaultClear(name);
            tick(preview);
            require(viewer.active().size()==4 && labels(viewer.active()).stream().noneMatch(label->label.startsWith("Хранилище")),
                    "Cleared candidate lists disappear immediately while other previews remain");
            viewer.location=at(world,700,65,700);
            tick(preview);
            require(viewer.active().isEmpty(),"Faraway markers are removed instead of loading or displaying the whole map");
            viewer.location=at(world,500.5,65,500.5);
            tick(preview);
            require(viewer.active().size()==4,"Markers return when the editor approaches configured points");
            preview.show(other.player,name);
            require(other.active().size()==4 && Collections.disjoint(viewer.active(),other.active()),
                    "Two editors receive independent display sets");
            preview.hide(viewer.player);
            require(viewer.active().isEmpty() && other.active().size()==4,"Turning off one preview leaves other editors intact");
            other.permission=false;
            tick(preview);
            require(other.active().isEmpty(),"Revoking admin permission removes private preview entities");
            preview.show(viewer.player,name);
            preview.onChangedWorld(new PlayerChangedWorldEvent(viewer.player,world));
            require(viewer.active().isEmpty(),"Changing worlds removes the editor overlay");
            preview.show(viewer.player,name);
            preview.onQuit(new PlayerQuitEvent(viewer.player,Component.empty()));
            require(viewer.active().isEmpty(),"Disconnecting removes the editor overlay");
            preview.show(viewer.player,name);
            preview.onDisable(new PluginDisableEvent(main));
            require(viewer.active().isEmpty(),"Plugin disable removes every remaining editor entity");
            Field ticker=FinalsMapPreview.class.getDeclaredField("ticker");ticker.setAccessible(true);
            require(ticker.get(preview)==null,"Final cleanup cancels the preview refresh task");
            smoke.getLogger().info("FINALS_PREVIEW_SMOKE_PASS: "+assertions+" native assertions");
            return assertions;
        } finally {
            preview.close();
        }
    }

    private static Set<String> labels(Collection<TextDisplay> displays) {
        Set<String> labels=new HashSet<>();
        for(TextDisplay display:displays) labels.add(PlainTextComponentSerializer.plainText().serialize(display.text()));
        return labels;
    }

    private static Location at(World world,double x,double y,double z) { return new Location(world,x,y,z); }

    private static void tick(FinalsMapPreview preview) throws Exception {
        Method method=FinalsMapPreview.class.getDeclaredMethod("tick");method.setAccessible(true);
        try { method.invoke(preview); }
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof Exception e) throw e;
            if(failure.getCause() instanceof Error e) throw e;
            throw failure;
        }
    }

    private void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        assertions++;
    }

    private static final class FakePlayer {
        final UUID id=UUID.randomUUID();
        final Set<TextDisplay> shown=new LinkedHashSet<>();
        final List<Location> particleLocations=new ArrayList<>();
        final Player player;
        Location location;
        Locale locale=Locale.ENGLISH;
        boolean permission=true;
        int particles;

        FakePlayer(World world) {
            location=at(world,500.5,65,500.5);
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getUniqueId"->id;
                case "getWorld"->location.getWorld();
                case "getLocation"->location.clone();
                case "locale"->locale;
                case "isOnline"->true;
                case "hasPermission"->permission;
                case "showEntity"->{shown.add((TextDisplay)args[1]);yield null;}
                case "spawnParticle"->{particles++;particleLocations.add(((Location)args[1]).clone());yield null;}
                case "equals"->proxy==args[0];
                case "hashCode"->id.hashCode();
                case "toString"->"PreviewEditor["+id+"]";
                default->throw new AssertionError("Unexpected player transport call: "+method.getName());
            });
        }

        Set<TextDisplay> active() {
            Set<TextDisplay> active=new LinkedHashSet<>();
            for(TextDisplay display:shown)if(display.isValid())active.add(display);
            return active;
        }
    }
}
