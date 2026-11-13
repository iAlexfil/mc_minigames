import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;

/** Calls production flash detonation against the actual Paper particle factory, not a mock. */
public final class CsGrenadeSmoke extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler()
                .runTask(
                        this,
                        () -> {
                            try {
                                JavaPlugin main =
                                        (JavaPlugin)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("PotatoMinigames");
                                ClassLoader loader = main.getClass().getClassLoader();
                                String pkg = "me.petr1furious.potatominigames.games.cs.";
                                Class<?> sessionType =
                                        Class.forName(pkg + "CsSession", true, loader);
                                Constructor<?> sessionConstructor =
                                        sessionType.getDeclaredConstructor(
                                                Class.forName(
                                                        "me.petr1furious.potatominigames.core.game.SessionServices",
                                                        true,
                                                        loader),
                                                Class.forName(pkg + "CsConfig", true, loader),
                                                Class.forName(pkg + "CsGame", true, loader));
                                sessionConstructor.setAccessible(true);
                                Object session = sessionConstructor.newInstance(null, null, null);
                                World world = Bukkit.getWorlds().getFirst();
                                Field field = sessionType.getDeclaredField("world");
                                field.setAccessible(true);
                                field.set(session, world);
                                Class<?> utility =
                                        Class.forName(pkg + "CsMatch$Utility", true, loader);
                                Object flash =
                                        java.util.Arrays.stream(utility.getEnumConstants())
                                                .filter(value -> value.toString().equals("FLASH"))
                                                .findFirst()
                                                .orElseThrow();
                                Class<?> grenadesType =
                                        Class.forName(pkg + "CsGrenades", true, loader);
                                Constructor<?> grenadesConstructor =
                                        grenadesType.getDeclaredConstructor(sessionType);
                                grenadesConstructor.setAccessible(true);
                                Object grenades = grenadesConstructor.newInstance(session);
                                Class<?> flight =
                                        Class.forName(pkg + "CsGrenades$Flight", true, loader);
                                Class<?> throwMode =
                                        Class.forName(
                                                pkg + "CsGrenadePhysics$ThrowMode", true, loader);
                                Constructor<?> constructor =
                                        flight.getDeclaredConstructor(
                                                UUID.class,
                                                utility,
                                                Item.class,
                                                Location.class,
                                                Vector.class,
                                                long.class,
                                                throwMode);
                                constructor.setAccessible(true);
                                Location at = world.getSpawnLocation().add(0, 2, 0);
                                Object payload =
                                        constructor.newInstance(
                                                UUID.randomUUID(),
                                                flash,
                                                null,
                                                at,
                                                new Vector(),
                                                0L,
                                                java.util.Arrays.stream(
                                                                throwMode.getEnumConstants())
                                                        .filter(value ->
                                                                value.toString().equals("LONG"))
                                                        .findFirst()
                                                        .orElseThrow());
                                Method detonate =
                                        grenadesType.getDeclaredMethod("detonate", flight);
                                detonate.setAccessible(true);
                                // The old untyped FLASH call throws from CraftParticle here, even
                                // with no clients.
                                detonate.invoke(grenades, payload);
                                CsScopeSmoke.run(loader, world);
                                getLogger().info("CS_SCOPE_SMOKE_PASS");
                                CsVisualSmoke.run(loader, world);
                                getLogger().info("CS_VISUAL_SMOKE_PASS");
                                if (Boolean.getBoolean("cs.smoke.includeFinalsGlow")) {
                                    Object assertions =
                                            Class.forName("FinalsTeammateGlowSmoke")
                                                    .getMethod(
                                                            "run",
                                                            JavaPlugin.class,
                                                            JavaPlugin.class)
                                                    .invoke(null, this, main);
                                    getLogger().info("CS_SHARED_GLOW_SMOKE_PASS: " + assertions);
                                }
                                getDataFolder().mkdirs();
                                Files.writeString(
                                        getDataFolder().toPath().resolve("result.txt"),
                                        "CS_GRENADE_SMOKE_PASS: native flash detonation"
                                                + " completed\n");
                                getLogger().info("CS_GRENADE_SMOKE_PASS");
                            } catch (Throwable failure) {
                                getLogger().log(Level.SEVERE, "CS_GRENADE_SMOKE_FAIL", failure);
                            } finally {
                                Bukkit.shutdown();
                            }
                        });
    }
}
