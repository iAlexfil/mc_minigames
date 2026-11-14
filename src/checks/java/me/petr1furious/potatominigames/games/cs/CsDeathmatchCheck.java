package me.petr1furious.potatominigames.games.cs;

import me.petr1furious.potatominigames.i18n.I18n;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** FFA lifecycle and loadout behavior independently of Bukkit player transport. */
final class CsDeathmatchCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static int run() {
        CsGame game = new CsGame((CsConfig) null);
        check(
                game.modes().equals(List.of("default", "deathmatch", "wingman"))
                        && game.showModes()
                        && game.defaultMode().equals("default"),
                "CS exposes all three modes while retaining its default queue ID");
        check(
                game.maxPlayers("deathmatch") == 16
                        && game.teamColors("deathmatch").isEmpty()
                        && game.maxPlayers("default") == 10
                        && game.teamColors("default").size() == 2,
                "deathmatch has its own roster cap and no team picker");
        check(
                ((CsSession) game.createSession(null, "deathmatch")).deathmatch()
                        && !((CsSession) game.createSession(null, "default")).deathmatch(),
                "menu modes actually create different gameplay rules");
        var input = CsDeathmatchCheck.class.getResourceAsStream("/config.yml");
        var config =
                YamlConfiguration.loadConfiguration(
                        new InputStreamReader(
                                Objects.requireNonNull(input), StandardCharsets.UTF_8));
        check(
                config.getBoolean("queues.cs.deathmatch.enabled")
                        && config.getInt("queues.cs.deathmatch.maxPlayers") == 16
                        && config.getBoolean("queues.cs.default.enabled"),
                "both CS queues ship enabled");
        Map<UUID, Integer> roster = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) roster.put(new UUID(0, i + 1), 0);
        CsMatch match = new CsMatch(roster, 12, 100, 12000, 800, 400, false, true);
        var ids = new ArrayList<>(roster.keySet());
        var a = match.actor(ids.get(0));
        var b = match.actor(ids.get(1));
        CsDeathmatch dm = new CsDeathmatch();
        match.start(0, new Random(1));
        for (var actor : match.actors()) dm.equip(actor, 0);
        check(
                match.carrier == null && !match.plant(a.id, 0, 0) && !match.pause(0),
                "deathmatch has no bomb assignment, plant or tactical timeout");
        check(
                dm.select(match, a.id, CsWeapon.M4A4, 0)
                        && a.primary.weapon == CsWeapon.M4A4
                        && a.money == 0,
                "either faction's gun can be selected freely during countdown");
        match.tick(100, new Random(1));
        check(
                match.phase == CsMatch.Phase.LIVE && match.until == 12100,
                "countdown starts one timed deathmatch instead of competitive rounds");
        var hit = match.hurt(b.id, a.id, 40, false, true, CsWeapon.AK47, false);
        check(
                hit.health() == 40 && b.health == 60,
                "players with the same internal team ID take full FFA damage");
        match.hurt(b.id, a.id, 1000, false, true, CsWeapon.AK47, false);
        check(
                a.kills == 1 && b.deaths == 1 && a.money == 0,
                "FFA kills count individually without friendly-fire penalties or income");
        for (var actor : match.actors()) if (actor != a && actor.alive) match.suicide(actor.id);
        match.tick(200, new Random(1));
        check(
                match.phase == CsMatch.Phase.LIVE,
                "last survivor never ends a respawning deathmatch");
        int deaths = b.deaths;
        check(
                match.respawnDeathmatch(b.id)
                        && b.alive
                        && b.health == 100
                        && b.armour == 100
                        && b.helmet
                        && b.deaths == deaths
                        && match.until == 12100,
                "respawn restores health/armor without resetting stats or the match clock");
        dm.equip(b, 200);
        check(
                dm.protectedSpawn(b.id, 259) && !dm.protectedSpawn(b.id, 260),
                "spawn protection expires after exactly three seconds");
        dm.protect(b.id, 300);
        dm.attack(b.id);
        check(!dm.protectedSpawn(b.id, 300), "attacking immediately ends spawn protection");
        CsGun old = b.primary;
        old.magazine = 7;
        check(
                !dm.select(match, b.id, CsWeapon.AWP, 301)
                        && b.primary == old
                        && b.primary.magazine == 7,
                "combat selection saves a loadout without granting ammunition or swapping the"
                        + " active gun");
        match.suicide(b.id);
        match.respawnDeathmatch(b.id);
        dm.equip(b, 400);
        check(
                b.primary.weapon == CsWeapon.AWP && b.primary.magazine == CsWeapon.AWP.magazine,
                "saved loadout applies with a fresh magazine on the next life");
        a.kills = 7;
        a.deaths = 3;
        b.kills = 7;
        b.deaths = 4;
        check(
                CsDeathmatch.ranking(match).getFirst() == a
                        && CsDeathmatch.place(match, a) == 1
                        && CsDeathmatch.place(match, b) == 2,
                "individual standings rank kills first and fewer deaths second");
        b.deaths = 3;
        check(
                CsDeathmatch.place(match, a) == CsDeathmatch.place(match, b),
                "equal kills and deaths share a place");
        check(
                new HashSet<>(CsShop.deathmatchSlots().values()).size() == CsWeapon.values().length
                        && CsShop.deathmatchSlots().size() == CsWeapon.values().length,
                "free selection includes every gun once without overflowing category rows");
        match.tick(12100, new Random(1));
        check(
                match.phase == CsMatch.Phase.FINISHED && !match.respawnDeathmatch(b.id),
                "the time limit ends deathmatch and prevents further respawns");
        check(
                CsArena.deathmatchSpawns().size() >= 24,
                "deathmatch has distributed spawn candidates beyond the classic T/CT starts");
        I18n.register();
        try {
            for (var locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru")))
                for (String key :
                        List.of(
                                "mode.default",
                                "mode.deathmatch",
                                "mode.wingman",
                                "wingman.description",
                                "wingman.rules",
                                "dm.rules",
                                "dm.controls",
                                "dm.weapons"))
                    check(
                            !I18n.plain(CsItems.tr(key), locale).contains("potatominigames."),
                            "deathmatch menu and controls resolve in both languages");
        } finally {
            I18n.unregister();
        }
        return checks;
    }
}
