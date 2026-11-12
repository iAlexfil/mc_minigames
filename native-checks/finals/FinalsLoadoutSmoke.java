import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.damage.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.Scoreboard;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/** Native registries/inventories/attributes with fake player transport; production lifecycle is invoked unchanged. */
public final class FinalsLoadoutSmoke {
    private final JavaPlugin smoke, main;
    private final ClassLoader loader;
    private final Map<UUID, FakePlayer> players = new LinkedHashMap<>();
    private final Server originalServer = Bukkit.getServer();
    private Object session, worlds;
    private Field serverField;
    private Server lookupProxy;
    private int assertions;

    private FinalsLoadoutSmoke(JavaPlugin smoke, JavaPlugin main) {
        this.smoke=smoke; this.main=main; this.loader=main.getClass().getClassLoader();
    }
    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsLoadoutSmoke(smoke,main).check();
    }
    private Class<?> type(String name)throws Exception {
        return nativeAccess(() -> Class.forName(name.contains(".")?name:"me.petr1furious.potatominigames.games.finals."+name,true,loader));
    }
    /** Paper remapping and native factories require the real CraftServer, even in a lookup fixture. */
    private <T> T nativeAccess(java.util.concurrent.Callable<T> operation)throws Exception {
        if(serverField==null)return operation.call();
        Server previous=Bukkit.getServer();
        serverField.set(null,originalServer);
        try{return operation.call();}finally{serverField.set(null,previous);}
    }
    private void preloadProductionClasses()throws Exception {
        java.nio.file.Path jar=java.nio.file.Path.of(main.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        try(java.util.jar.JarFile archive=new java.util.jar.JarFile(jar.toFile())){
            var entries=archive.entries();
            while(entries.hasMoreElements()){
                String name=entries.nextElement().getName();
                if(name.startsWith("me/petr1furious/potatominigames/")&&name.endsWith(".class"))
                    Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader);
            }
        }
    }
    private Object withPlayerLookup(Object target,String name,Object... args)throws Exception {
        Server previous=Bukkit.getServer();serverField.set(null,lookupProxy);
        try{return call(target,name,args);}finally{serverField.set(null,previous);}
    }
    private static boolean accepts(Class<?> parameter,Object argument) {
        if(argument==null)return !parameter.isPrimitive();
        if(parameter.isInstance(argument))return true;
        return parameter==int.class&&argument instanceof Integer || parameter==long.class&&argument instanceof Long
                ||parameter==boolean.class&&argument instanceof Boolean||parameter==float.class&&argument instanceof Float
                ||parameter==double.class&&argument instanceof Double;
    }
    private static Object invoke(Method method,Object receiver,Object... args)throws Exception {
        method.setAccessible(true);
        try{return method.invoke(receiver,args);}catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception e)throw e;
            if(failure.getCause() instanceof Error e)throw e;
            throw failure;
        }
    }
    private Object call(Object target,String name,Object... args)throws Exception {
        Class<?> cls=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> current=cls;current!=null;current=current.getSuperclass())for(Method method:methods(current)){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            boolean match=true;for(int i=0;i<args.length;i++)if(!accepts(method.getParameterTypes()[i],args[i]))match=false;
            if(match)return invoke(method,target instanceof Class<?>?null:target,args);
        }
        throw new NoSuchMethodException(cls.getName()+"."+name);
    }
    private Method[] methods(Class<?> type)throws Exception{return nativeAccess(type::getDeclaredMethods);}
    private Object make(String name,Object... args)throws Exception {
        for(Constructor<?> constructor:type(name).getDeclaredConstructors()){
            if(constructor.getParameterCount()!=args.length)continue;
            boolean match=true;for(int i=0;i<args.length;i++)if(!accepts(constructor.getParameterTypes()[i],args[i]))match=false;
            if(match){constructor.setAccessible(true);return constructor.newInstance(args);}
        }
        throw new NoSuchMethodException(name);
    }
    private static Field field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object get(Object object,String name)throws Exception{return field(object,name).get(object);}
    private static void set(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
    @SuppressWarnings({"unchecked","rawtypes"}) private Object value(String type,String name)throws Exception{return Enum.valueOf((Class)type(type),name);}
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
    private void close(double actual,double expected,String label){require(Math.abs(actual-expected)<.00001,label+": expected "+expected+", actual "+actual);}

    @SuppressWarnings("unchecked") private int check()throws Exception {
        World initial=Bukkit.getWorlds().getFirst();
        FakePlayer light=new FakePlayer("NativeLight",initial,28,.27f,.14f);
        FakePlayer medium=new FakePlayer("NativeMedium",initial,32,.31f,.16f);
        FakePlayer heavy=new FakePlayer("NativeHeavy",initial,36,.29f,.18f);
        for(FakePlayer player:List.of(light,medium,heavy))players.put(player.id,player);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);
        preloadProductionClasses();
        Server proxy=(Server)Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(receiver,method,args)->{
            if(method.getName().equals("getPlayer")&&args[0] instanceof UUID id&&players.containsKey(id))return players.get(id).player;
            if(method.getName().equals("getOnlinePlayers"))return players.values().stream().map(p->p.player).toList();
            serverField.set(null,originalServer);
            try{return method.invoke(originalServer,args);}
            catch(InvocationTargetException error){throw error.getCause();}
            finally{serverField.set(null,receiver);}
        });
        lookupProxy=proxy;
        try {
            Object registry=make("me.petr1furious.potatominigames.core.player.ParticipantRegistry");
            for(FakePlayer player:players.values())call(registry,"add",player.player);
            Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            Object active=((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(type("me.petr1furious.potatominigames.core.session.ActiveSession"));
            set(active,"phase",value("me.petr1furious.potatominigames.core.session.GamePhase","RUNNING"));
            set(active,"test",true);set(active,"options",Map.of());
            Object configs=make("me.petr1furious.potatominigames.core.config.ConfigRegistry",main);
            Object config=make("FinalsConfig",configs);
            Object countdown=get(config,"countdownSeconds");
            call(countdown,"reset");
            require(call(countdown,"get").equals(18),"Native Finals configuration defaults to an eighteen-second loadout window");
            call(countdown,"set",5);
            Object stateService=make("me.petr1furious.potatominigames.core.player.PlayerStateService",main);
            Object sidebar=make("me.petr1furious.potatominigames.core.hud.SidebarService",(Supplier<Collection<Player>>)List::of);
            Object bossbars=make("me.petr1furious.potatominigames.core.hud.BossBarService",(Supplier<Collection<Player>>)()->players.values().stream().map(p->p.player).toList());
            Object arena=make("me.petr1furious.potatominigames.core.world.ArenaService");
            worlds=make("me.petr1furious.potatominigames.core.world.SessionWorlds",make("me.petr1furious.potatominigames.core.world.ThrowawayWorldService",main),null);
            Object messages=make("me.petr1furious.potatominigames.core.text.Messages",(Supplier<Audience>)Audience::empty,null);
            Object services=make("me.petr1furious.potatominigames.core.game.SessionServices",main,active,registry,stateService,null,sidebar,bossbars,arena,worlds,null,messages,null);
            Object loadouts=make("FinalsLoadouts",main,(Consumer<Player>)player->{
                if(session!=null)try{call(session,"loadoutChanged",player);}catch(Exception error){throw new RuntimeException(error);}
            });
            Map<UUID,Object> choices=(Map<UUID,Object>)get(loadouts,"choices");
            for(var entry:Map.of(light,"LIGHT",medium,"MEDIUM",heavy,"HEAVY").entrySet()){
                Object role=value("FinalsClass",entry.getValue());
                Object weapon=((List<?>)call(type("FinalsWeapon"),"byClass",role)).getFirst();
                choices.put(entry.getKey().id,make("FinalsLoadouts$Choice",role,weapon));
            }
            session=make("FinalsSession",services,config,null,loadouts);
            // World creation and mutation require the real CraftServer. As in the original
            // fixture, fake participant transports are spawned explicitly after real startup.
            call(session,"onStart");
            Object ticker=get(session,"ticker");
            require(ticker!=null,"Prepared startup installs the production match ticker");
            call(ticker,"cancel");
            require((boolean)call(ticker,"isCancelled"),"Manual countdown checks disable the scheduled ticker to avoid interference");
            require(((Map<?,?>)get(session,"roster")).size()==players.size(),"Real onStart builds the full participant roster");
            require(get(session,"world") instanceof World,"Real onStart creates the native match world");
            // These packet/display helpers have independent native coverage. Do not create
            // world entities while the scoped lookup-only Server proxy drives manual ticks.
            for(String helper:List.of("highlights","tabList")){
                Object presentation=get(session,helper);
                if(presentation!=null)call(presentation,"clear");
                set(session,helper,null);
            }
            for(FakePlayer player:players.values()){
                call(session,"spawn",player.player);
                call(loadouts,"open",player.player);
            }
            require((long)get(session,"countdownEnd")==360L,"A legacy five-second setting still grants the complete eighteen-second loadout window");
            Map<UUID,Integer> killsBeforeCountdown=(Map<UUID,Integer>)call(get(session,"kills"),"snapshot");
            require(killsBeforeCountdown.isEmpty(),"A prepared match starts with no carried-over kills");
            Map<UUID,Object> frozen=(Map<UUID,Object>)get(session,"frozen");
            for(var entry:Map.of(light,15d,medium,25d,heavy,35d).entrySet()){
                FakePlayer player=entry.getKey();double expected=entry.getValue();
                close(player.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),expected,"Native spawn class max health");
                close(player.attribute.getHealth(),expected,"Native spawn class current health");
                require(player.storage.getItem(0)!=null&&player.storage.getItem(0).getItemMeta().hasDisplayName(),"Native spawn installs a named weapon");
                for(int slot=1;slot<=4;slot++)require(player.storage.getItem(slot)!=null,"Native spawn equips each class gadget");
                require(player.storage.getItem(8).getType()==Material.NETHER_STAR,"Native spawn equips loadout menu control");
                close(player.walkSpeed,0,"Countdown freezes movement");
                require((boolean)call(type("FinalsLoadouts"),"viewing",player.player),"The native selection fixture displays the Finals loadout menu");
            }
            close((float)call(frozen.get(light.id),"walkSpeed"),.24,"Light class speed before freeze");
            close((float)call(frozen.get(medium.id),"walkSpeed"),.20,"Medium class speed before freeze");
            close((float)call(frozen.get(heavy.id),"walkSpeed"),.17,"Heavy class speed before freeze");

            require(light.top.getSize()==54&&light.top.getItem(11).getType()==Material.FEATHER,"Native loadout GUI paints class controls");
            InventoryClickEvent countdownClick=new InventoryClickEvent(light.view,InventoryType.SlotType.CONTAINER,15,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            call(loadouts,"onClick",countdownClick);
            require(countdownClick.isCancelled(),"Loadout GUI consumes inventory clicks");
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),35,"Countdown GUI class change applies immediately");
            close((float)call(frozen.get(light.id),"walkSpeed"),.17,"Countdown class change saves Heavy speed while frozen");
            withPlayerLookup(session,"onParticipantQuit",heavy.player);
            restored(heavy);
            require(((Map<?,?>)get(session,"classes")).get(heavy.id).toString().equals("HEAVY"),
                    "Temporary disconnect restores outside stats while preserving the equipped class for Tab");
            withPlayerLookup(session,"onParticipantRejoin",heavy.player);
            require(heavy.menuOpens==2&&(boolean)call(type("FinalsLoadouts"),"viewing",heavy.player),
                    "Rejoining during the countdown automatically reopens the loadout menu");
            close(heavy.walkSpeed,0,"A countdown rejoin remains frozen for the rest of the selection window");
            set(session,"tick",358L);
            withPlayerLookup(session,"tick");
            require((long)get(session,"tick")==359L&&get(session,"state")==null,
                    "At tick359 the full loadout countdown is still active");
            for(FakePlayer player:players.values()){
                close(player.walkSpeed,0,"Players remain frozen before the exact GO tick");
                require((boolean)call(type("FinalsLoadouts"),"viewing",player.player),
                        "The selection menu remains visible through tick359");
            }
            require(light.menuOpens==1&&medium.menuOpens==1&&heavy.menuOpens==2,
                    "Countdown refreshes and class changes never reopen the menu repeatedly");
            withPlayerLookup(session,"tick");
            require((long)get(session,"tick")==360L&&get(session,"state")!=null,
                    "Tick360 creates the actual match state at the end of eighteen seconds");
            require(frozen.isEmpty(),"The actual GO transition releases every countdown freeze");
            for(FakePlayer player:players.values())
                require(!(boolean)call(type("FinalsLoadouts"),"viewing",player.player),
                        "The actual GO transition closes the visible Finals loadout menus");
            require(call(get(session,"kills"),"snapshot").equals(killsBeforeCountdown),
                    "Countdown selection, reconnect and GO leave match kill totals unchanged");
            close(light.walkSpeed,.17,"Releasing countdown restores new class speed");
            close(medium.walkSpeed,.20,"Releasing countdown restores Medium speed");
            close(heavy.walkSpeed,.17,"Releasing countdown restores Heavy speed");
            withPlayerLookup(session,"onParticipantQuit",heavy.player);
            withPlayerLookup(session,"onParticipantRejoin",heavy.player);
            require(((Map<?,?>)get(session,"classes")).get(heavy.id).toString().equals("HEAVY")
                            &&((Map<?,?>)get(session,"respawnAt")).containsKey(heavy.id),
                    "A mid-match reconnect keeps the actual Heavy class throughout respawn recovery");
            call(session,"spawn",heavy.player);
            call(loadouts,"open",light.player);
            InventoryClickEvent activeClick=new InventoryClickEvent(light.view,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            ItemStack previousWeapon=light.storage.getItem(0).clone();
            call(loadouts,"onClick",activeClick);
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),35,"Active GUI change waits for next spawn");
            close(light.walkSpeed,.17,"Active GUI change preserves current movement speed");
            require(light.storage.getItem(0).equals(previousWeapon),"Active GUI change preserves equipped weapon until respawn");
            require(call(call(loadouts,"choice",light.id),"playerClass").toString().equals("MEDIUM"),"Active GUI change records next-spawn preference");
            call(session,"spawn",light.player);
            close(light.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),25,"Next spawn applies selected Medium health");
            close(light.attribute.getHealth(),25,"Next spawn fully heals selected class");
            close(light.walkSpeed,.20,"Next spawn applies selected Medium speed");
            equipmentChecks(loadouts,light,heavy);
            customLoadoutChecks(loadouts,light);
            teamWipeChecks(light,medium,heavy);
            call(session,"onParticipantLeave",light.player);
            restored(light);
            require(!((Map<?,?>)get(session,"classes")).containsKey(light.id),"Permanent leave removes the former contestant's class entry");
            withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));
            restored(medium);restored(heavy);
            require(((Map<?,?>)get(session,"originalVitals")).isEmpty(),"Session end clears captured original vitals");
            require(((Map<?,?>)get(session,"classes")).isEmpty(),"Session end clears retained match class entries");
            smoke.getLogger().info("FINALS_LOADOUT_SMOKE_PASS: "+assertions+" native lifecycle, GUI, health, speed and restoration assertions");
            return assertions;
        } finally {
            if(session!=null)try{withPlayerLookup(session,"onEnd",value("me.petr1furious.potatominigames.core.game.EndReason","ABORTED"));}catch(Exception error){smoke.getLogger().warning("Loadout smoke cleanup: "+error);}
            serverField.set(null,originalServer);
            if(worlds!=null)call(worlds,"destroyVoid","finals");
            for(FakePlayer player:players.values())player.attribute.remove();
        }
    }
    @SuppressWarnings("unchecked") private void equipmentChecks(Object loadouts,FakePlayer source,FakePlayer victim)throws Exception {
        Object weapons=get(session,"weapons"),gadgets=get(session,"gadgets");
        Object[] roles=type("FinalsClass").getEnumConstants();
        int selections=0;long now=1000;
        for(int roleIndex=0;roleIndex<roles.length;roleIndex++){
            Object role=roles[roleIndex];
            call(loadouts,"open",source.player);click(loadouts,source,11+roleIndex*2);
            List<?> available=(List<?>)call(type("FinalsWeapon"),"byClass",role);
            require(available.size()==5,"Each class exposes five real GUI choices");
            for(int index=0;index<available.size();index++){
                Object weapon=available.get(index);
                call(loadouts,"open",source.player);
                ItemStack menuItem=source.top.getItem(28+index);
                require(menuItem!=null&&nativeText(menuItem).length()>0,"Every weapon GUI choice has resolved native metadata");
                click(loadouts,source,28+index);
                require(call(call(loadouts,"choice",source.id),"weapon")==weapon,"GUI selection saves the intended weapon");
                source.player.closeInventory();call(session,"spawn",source.player);
                require(call(weapons,"selected",source.id)==weapon,"Next spawn equips every selected primary weapon");
                require(nativeText(source.storage.getItem(0)).length()>0,"Every equipped weapon has resolved native name and lore");
                now+=200;set(session,"tick",now);
                Object equipped=((Map<UUID,Object>)get(weapons,"equipped")).get(source.id);
                if((boolean)call(weapon,"isGun")){
                    Object gun=get(equipped,"gun");int before=(int)call(gun,"ammo");
                    PlayerInteractEvent fire=new PlayerInteractEvent(source.player,Action.RIGHT_CLICK_AIR,source.storage.getItem(0),null,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
                    call(session,"onPlayerInteract",fire);
                    require(fire.isCancelled()&&(int)call(gun,"ammo")==before-1,"Native gun right click fires and consumes one round");
                    PlayerSwapHandItemsEvent reload=new PlayerSwapHandItemsEvent(source.player,source.storage.getItem(0),new ItemStack(Material.AIR));
                    call(session,"onSwapHandItems",reload);
                    require(reload.isCancelled()&&(boolean)call(gun,"reloading"),"Native F event starts reload without swapping inventory");
                }else{
                    EntityDamageByEntityEvent melee=new EntityDamageByEntityEvent(source.player,victim.player,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK,DamageSource.builder(DamageType.GENERIC).build(),1);
                    call(session,"onEntityDamageByEntity",melee);
                    require(melee.isCancelled()&&(long)get(equipped,"nextMelee")>now,"Native melee event dispatches a custom swing and consumes vanilla damage");
                }
                selections++;
            }
            List<String> english=new ArrayList<>();
            source.locale=Locale.ENGLISH;call(gadgets,"equip",source.player,role);
            for(int slot=1;slot<=4;slot++)english.add(nativeText(source.storage.getItem(slot)));
            source.locale=Locale.forLanguageTag("ru");call(gadgets,"equip",source.player,role);
            for(int slot=1;slot<=4;slot++){
                String russian=nativeText(source.storage.getItem(slot));
                require(!russian.equals(english.get(slot-1)),"Every gadget name/lore changes for a Russian viewer");
            }
            call(loadouts,"open",source.player);
            for(int slot:new int[]{11,13,15,28,29,30,31,32})
                require(nativeText(source.top.getItem(slot)).length()>0,"Russian GUI class/weapon metadata is rendered natively");
            source.player.closeInventory();source.locale=Locale.ENGLISH;
        }
        require(selections==15,"All fifteen weapon GUI and equip paths were exercised");
    }
    private void click(Object loadouts,FakePlayer player,int slot)throws Exception{
        InventoryClickEvent event=new InventoryClickEvent(player.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        call(loadouts,"onClick",event);require(event.isCancelled(),"Native GUI click is consumed");
    }
    @SuppressWarnings("unchecked") private void customLoadoutChecks(Object loadouts,FakePlayer player)throws Exception {
        call(loadouts,"open",player.player);
        click(loadouts,player,11);
        click(loadouts,player,22);
        Object selected=call(loadouts,"choice",player.id);
        require(call(selected,"specialization").toString().equals("CLOAKING_DEVICE"),
                "The actual loadout menu saves the selected class specialization");
        List<Object> available=(List<Object>)call(type("FinalsGadgetState$Kind"),"gadgets",value("FinalsClass","LIGHT"));
        Object smoke=value("FinalsGadgetState$Kind","SMOKE_GRENADE");
        Object flash=value("FinalsGadgetState$Kind","FLASHBANG");
        call(loadouts,"open",player.player,1);
        click(loadouts,player,available.indexOf(smoke));
        selected=call(loadouts,"choice",player.id);
        require(((List<?>)call(selected,"gadgets")).get(1)==smoke,
                "The gadget picker replaces only the chosen gadget slot");
        call(loadouts,"open",player.player,2);
        Object before=call(loadouts,"choice",player.id);
        click(loadouts,player,available.indexOf(value("FinalsGadgetState$Kind","FRAG_GRENADE")));
        require(before.equals(call(loadouts,"choice",player.id)),
                "The actual picker rejects a duplicate gadget without changing the saved loadout");
        click(loadouts,player,available.indexOf(flash));
        call(loadouts,"open",player.player);
        click(loadouts,player,29);
        selected=call(loadouts,"choice",player.id);
        require(call(selected,"weapon").toString().equals("XP54")
                        &&call(selected,"specialization").toString().equals("CLOAKING_DEVICE")
                        &&((List<?>)call(selected,"gadgets")).get(1)==smoke
                        &&((List<?>)call(selected,"gadgets")).get(2)==flash,
                "Selecting a new weapon preserves the custom specialization and gadget trio");
        call(session,"spawn",player.player);
        var key=new NamespacedKey("potatominigames","finals_gadget");
        for(var entry:Map.of(1,"FRAG_GRENADE",2,"SMOKE_GRENADE",3,"FLASHBANG",4,"CLOAKING_DEVICE").entrySet()) {
            ItemStack equipped=player.storage.getItem(entry.getKey());
            require(entry.getValue().equals(equipped.getItemMeta().getPersistentDataContainer()
                            .get(key,org.bukkit.persistence.PersistentDataType.STRING)),
                    "A real respawn equips the chosen gadget or specialization in its intended slot");
            nativeText(equipped);
        }
        Object reloaded=make("FinalsLoadouts",main,(Consumer<Player>)ignored -> {});
        require(selected.equals(call(reloaded,"choice",player.id)),
                "Reloading the real YAML restores all custom choices");
        org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener)reloaded);
        player.player.closeInventory();
    }
    @SuppressWarnings("unchecked") private void teamWipeChecks(FakePlayer first,FakePlayer second,FakePlayer third)throws Exception {
        List<FakePlayer> cohort=List.of(first,second,third);
        Map<UUID,Integer> roster=(Map<UUID,Integer>)get(session,"roster");
        Map<UUID,Integer> previousRoster=new LinkedHashMap<>(roster);
        Object previousState=get(session,"state");
        Map<UUID,Long> respawns=(Map<UUID,Long>)get(session,"respawnAt");
        Map<UUID,Location> positions=(Map<UUID,Location>)get(session,"teamRespawnPositions");
        long base=(long)get(session,"tick")+100;
        try {
            roster.replaceAll((id,team)->0);
            set(session,"state",make("FinalsState",new LinkedHashMap<>(roster),base,get(session,"rules")));
            require(respawns.isEmpty(),"The native wipe fixture begins with three living contestants");
            for(int wave=0;wave<2;wave++) {
                long start=base+wave*500;
                for(int index=0;index<cohort.size();index++) {
                    FakePlayer victim=cohort.get(index);
                    set(session,"tick",start+index*10);
                    nativeAccess(()->{
                        PlayerDeathEvent event=new PlayerDeathEvent(victim.player,
                                DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),true);
                        call(session,"onPlayerDeath",event);return null;
                    });
                    if(index<2)require(respawns.get(victim.id)==start+index*10+100,
                            "The first two actual death handlers retain normal five-second respawns");
                }
                long deadline=start+260;
                require(cohort.stream().allMatch(player->Objects.equals(respawns.get(player.id),deadline)),
                        "The third native death handler synchronizes all three to last death plus twelve seconds");
                set(session,"tick",deadline-1);
                Location before=first.location.clone();
                nativeAccess(()->{call(session,"spawn",first.player);return null;});
                require(first.location.equals(before)&&respawns.containsKey(first.id),
                        "The actual spawn entry cannot release a teammate one tick before the shared deadline");
                Object context=call(session,"combatContext");
                Long held=respawns.remove(first.id);
                Location firstBefore=first.location.clone();
                GameMode modeBefore=first.mode;
                try {
                    first.mode=GameMode.SURVIVAL;
                    first.location=((Map<UUID,Location>)get(session,"deathLocations")).get(second.id).clone();
                    require(!(boolean)call(context,"revive",second.player,first.player),
                            "The production defibrillator context cannot bypass a pending wipe even with an active reviver");
                } finally { respawns.put(first.id,held);first.location=firstBefore;first.mode=modeBefore; }
                if(wave==1) {
                    nativeAccess(()->{call(session,"onParticipantQuit",third.player);return null;});
                    require(Objects.equals(respawns.get(third.id),deadline),
                            "Temporary disconnect preserves a dead cohort member's shared deadline");
                }
                set(session,"tick",deadline);
                nativeAccess(()->{call(session,"prepareTeamRespawns");return null;});
                Location chosen=positions.get(first.id).clone();
                require(positions.size()==3&&positions.values().stream().allMatch(chosen::equals),
                        "Deadline resolution chooses and clones exactly one spawn for the whole team");
                for(FakePlayer player:wave==0?cohort:List.of(first,second))
                    nativeAccess(()->{call(session,"spawn",player.player);return null;});
                if(wave==1) {
                    require(positions.containsKey(third.id)&&Objects.equals(respawns.get(third.id),deadline),
                            "An offline teammate retains its completed cohort anchor until returning");
                    nativeAccess(()->{call(session,"onParticipantRejoin",third.player);return null;});
                    require(Objects.equals(respawns.get(third.id),deadline),
                            "Late rejoin cannot replace the completed wipe with a fresh five-second wait");
                    nativeAccess(()->{call(session,"spawn",third.player);return null;});
                }
                for(FakePlayer player:cohort) {
                    require(player.location.equals(chosen)&&player.mode==GameMode.SURVIVAL,
                            "Actual equipped respawns place every teammate at the same complete Location");
                    require(player.storage.getItem(0)!=null&&player.storage.getItem(4)!=null,
                            "Group respawns reinstall primary weapons and class specialization");
                    require(player.attribute.getHealth()==player.attribute.getAttribute(Attribute.MAX_HEALTH).getValue(),
                            "Group respawns restore full class health");
                }
                require(respawns.isEmpty()&&positions.isEmpty(),
                        "Completed native waves consume every timer and cached anchor before the next wipe");
            }
        } finally {
            roster.clear();roster.putAll(previousRoster);
            set(session,"state",previousState);
        }
    }

    private String nativeText(ItemStack item){
        require(item!=null&&item.hasItemMeta()&&item.getItemMeta().displayName()!=null,"Native item name exists");
        var meta=item.getItemMeta();List<Component> components=new ArrayList<>();components.add(meta.displayName());
        if(meta.lore()!=null)components.addAll(meta.lore());
        String result=components.stream().map(PlainTextComponentSerializer.plainText()::serialize).collect(java.util.stream.Collectors.joining(" | "));
        require(!result.contains("potatominigames."),"Item metadata contains resolved text rather than custom translation keys");
        return result;
    }
    private void restored(FakePlayer player){
        close(player.attribute.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),player.originalHealth,"Original max health restored");
        close(player.walkSpeed,player.originalWalk,"Original walk speed restored");
        close(player.flySpeed,player.originalFly,"Original fly speed restored");
    }
    private static Object zero(Class<?> type){
        if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
        if(type==float.class)return 0f;if(type==double.class)return 0d;return null;
    }
    private final class FakePlayer {
        final UUID id=UUID.randomUUID(); final String name;
        final ArmorStand attribute;
        final double originalHealth;final float originalWalk,originalFly;
        final Inventory storage=Bukkit.createInventory(null,54);
        final Inventory fallback=Bukkit.createInventory(null,9);
        final Map<PotionEffectType,PotionEffect> effects=new HashMap<>();
        final PlayerInventory inventory;
        final InventoryView view;
        final Player player;
        Inventory top=fallback;Locale locale=Locale.ENGLISH;
        Location location;GameMode mode=GameMode.SURVIVAL;float walkSpeed,flySpeed;int held,menuOpens;
        ItemStack offhand=new ItemStack(Material.AIR),chest;
        Scoreboard scoreboard=Bukkit.getScoreboardManager().getMainScoreboard();
        FakePlayer(String name,World world,double originalHealth,float originalWalk,float originalFly){
            this.name=name;this.originalHealth=originalHealth;this.originalWalk=originalWalk;this.originalFly=originalFly;
            location=world.getSpawnLocation();walkSpeed=originalWalk;flySpeed=originalFly;
            attribute=world.spawn(location,ArmorStand.class);attribute.setGravity(false);attribute.setInvulnerable(true);
            attribute.getAttribute(Attribute.MAX_HEALTH).setBaseValue(originalHealth);attribute.setHealth(originalHealth);
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "setItem"->{storage.setItem((int)a[0],(ItemStack)a[1]);yield null;}
                case "getItem"->storage.getItem((int)a[0]);
                case "setItemInOffHand"->{offhand=(ItemStack)a[0];yield null;}
                case "getItemInOffHand"->offhand;
                case "getItemInMainHand"->Optional.ofNullable(storage.getItem(held)).orElse(new ItemStack(Material.AIR));
                case "setHeldItemSlot"->{held=(int)a[0];yield null;}
                case "getHeldItemSlot"->held;
                case "clear"->{storage.clear();yield null;}
                case "setChestplate"->{chest=(ItemStack)a[0];yield null;}
                case "getChestplate"->chest;
                case "setArmorContents","setHelmet","setLeggings","setBoots"->null;
                default->zero(m.getReturnType());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(o,m,a)->switch(m.getName()){
                case "equals"->o==a[0];case "hashCode"->System.identityHashCode(o);case "toString","getName"->name;
                case "getUniqueId"->id;case "locale"->locale;case "displayName"->Component.text(name);
                case "getWorld"->location.getWorld();case "getLocation","getEyeLocation"->location.clone();
                case "getInventory"->inventory;case "getGameMode"->mode;
                case "setGameMode"->{mode=(GameMode)a[0];yield null;}
                case "getWalkSpeed"->walkSpeed;case "getFlySpeed"->flySpeed;
                case "setWalkSpeed"->{walkSpeed=(float)a[0];yield null;}case "setFlySpeed"->{flySpeed=(float)a[0];yield null;}
                case "isOnline"->true;case "isDead","isSneaking"->false;
                case "getKiller"->null;
                case "getAttribute"->attribute.getAttribute((Attribute)a[0]);case "getHealth"->attribute.getHealth();
                case "setHealth"->{attribute.setHealth((double)a[0]);yield null;}
                case "getActivePotionEffects"->List.copyOf(effects.values());
                case "addPotionEffect"->{PotionEffect effect=(PotionEffect)a[0];effects.put(effect.getType(),effect);yield true;}
                case "removePotionEffect"->{effects.remove((PotionEffectType)a[0]);yield null;}
                case "getMaximumAir"->300;
                case "teleport"->{location=((Location)a[0]).clone();yield true;}
                case "getScoreboard"->scoreboard;case "setScoreboard"->{scoreboard=(Scoreboard)a[0];yield null;}
                case "openInventory"->{top=(Inventory)a[0];menuOpens++;yield view();}
                case "getOpenInventory"->view();case "closeInventory"->{top=fallback;yield null;}
                default->zero(m.getReturnType());
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(o,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;
                case "getType"->InventoryType.CHEST;case "getTitle","getOriginalTitle"->"Native loadout smoke";
                case "convertSlot"->a[0];case "getSlotType"->InventoryType.SlotType.CONTAINER;
                case "getInventory"->(int)a[0]<top.getSize()?top:inventory;
                case "getItem"->top.getItem((int)a[0]);case "countSlots"->top.getSize()+36;
                default->zero(m.getReturnType());
            });
        }
        private InventoryView view(){return view;}
    }
}
