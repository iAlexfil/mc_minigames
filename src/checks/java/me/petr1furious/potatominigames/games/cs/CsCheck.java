package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Location;

import java.util.*;

import javax.imageio.ImageIO;

/** Server-free behavioral checks: objective deadlines, economy, equipment and imported geometry. */
public final class CsCheck {
    private static final UUID T = new UUID(0, 1),
            T2 = new UUID(0, 2),
            CT = new UUID(0, 3),
            CT2 = new UUID(0, 4);
    private static final Map<UUID, Integer> TEAMS = Map.of(T, 0, T2, 0, CT, 1, CT2, 1);
    private static final Random RNG = new Random(87234);
    private static int checks;

    public static void main(String[] args) throws Exception {
        hudTranslations();
        buying();
        ammunition();
        damageAndRewards();
        objectives();
        rounds();
        lifecycle();
        importedMap();
        smokeGeometry();
        System.out.println("CsCheck: " + checks + " checks passed");
    }

    private static void hudTranslations() {
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (String state : List.of("yes", "no")) {
                var label = me.petr1furious.potatominigames.i18n.I18n.tr("cs." + state);
                var health =
                        me.petr1furious.potatominigames.i18n.I18n.tr(
                                "cs.hud.health", 100, 100, label);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String plain = me.petr1furious.potatominigames.i18n.I18n.plain(health, locale);
                    check(
                            plain.contains("100") && !plain.contains("potatominigames."),
                            "start-round health HUD renders with and without helmet");
                }
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static CsMatch live() {
        return live(15);
    }

    private static CsMatch live(int half) {
        CsMatch m = new CsMatch(TEAMS, half, 2, 100, 20, 10, false);
        m.start(0, RNG);
        check(
                m.phase == CsMatch.Phase.FREEZE
                        && !m.hurt(CT, T, 500, false, false, CsWeapon.AK47, false).killed(),
                "freeze blocks damage");
        m.tick(1, RNG);
        check(m.phase == CsMatch.Phase.FREEZE, "freeze lasts full duration");
        m.tick(2, RNG);
        return m;
    }

    private static void buying() {
        CsMatch m = live();
        var t = m.actor(T);
        var ct = m.actor(CT);
        check(t.money == 800 && ct.money == 800, "pistol round starts with $800");
        check(
                t.secondary.weapon == CsWeapon.GLOCK && ct.secondary.weapon == CsWeapon.USP,
                "side-specific default pistols");
        check(
                m.weapon(t, CsWeapon.AK47, 3) == CsMatch.Buy.MONEY && t.primary == null,
                "unaffordable buy has no effect");
        CsMatch.credit(t, 50000);
        CsMatch.credit(ct, 50000);
        check(t.money == 16000, "cash is capped at $16000");
        check(
                m.weapon(t, CsWeapon.M4A4, 3) == CsMatch.Buy.SIDE && t.money == 16000,
                "opposing primary cannot be bought");
        check(
                m.weapon(t, CsWeapon.AK47, 3) == CsMatch.Buy.OK && t.money == 13300,
                "AK purchase charges exact price");
        check(
                m.weapon(ct, CsWeapon.AWP, 3) == CsMatch.Buy.OK && ct.money == 11250,
                "AWP available to CT");
        check(m.gear(t, "kit", 3) == CsMatch.Buy.SIDE, "defuse kit is CT-only");
        check(m.gear(t, "vest", 3) == CsMatch.Buy.OK && t.armour == 100, "kevlar gives 100 armor");
        int cash = t.money;
        check(
                m.gear(t, "helmet", 3) == CsMatch.Buy.OK && cash - t.money == 350 && t.helmet,
                "helmet upgrade costs $350 with intact vest");
        check(m.gear(t, "vest", 3) == CsMatch.Buy.LIMIT, "cannot buy redundant vest");
        for (String id : List.of("he", "flash", "flash", "smoke"))
            check(m.gear(t, id, 3) == CsMatch.Buy.OK, "legal grenade purchase");
        check(
                m.gear(t, "fire", 3) == CsMatch.Buy.LIMIT && t.grenades() == 4,
                "total grenade limit");
        check(
                m.consume(t, CsMatch.Utility.FLASH) && t.utility.get(CsMatch.Utility.FLASH) == 1,
                "throw consumes one flash");
        check(m.gear(t, "he", 3) == CsMatch.Buy.LIMIT, "HE limited to one");
        check(m.canBuy(t, 12) && !m.canBuy(t, 13), "buy deadline is enforced");
        cash = t.money;
        check(
                m.weapon(t, CsWeapon.DEAGLE, 13) == CsMatch.Buy.CLOSED && t.money == cash,
                "expired shop does not charge");
        CsMatch.credit(t, -50000);
        check(t.money == 0, "penalty cannot make money negative");
    }

    private static void ammunition() {
        for (CsWeapon weapon : CsWeapon.values()) {
            CsGun g = new CsGun(weapon);
            long now = 0;
            for (int i = 0; i < weapon.magazine; i++, now += 100)
                check(g.fire(now), "loaded weapon fires: " + weapon);
            check(g.magazine == 0 && !g.fire(now), "empty gun cannot fire: " + weapon);
            check(g.reload(now), "empty gun reloads: " + weapon);
            g.tick(now + 200);
            check(
                    g.magazine == weapon.magazine && g.reserve == weapon.reserve - weapon.magazine,
                    "finite reserve used by reload: " + weapon);
            g.magazine = 0;
            g.reserve = 0;
            check(!g.reload(now + 201), "no ammunition creation: " + weapon);
        }
        CsGun ak = new CsGun(CsWeapon.AK47);
        check(ak.fire(0) && !ak.fire(1) && ak.fire(2), "AK fire cadence");
        ak.reload(3);
        ak.switchAway();
        ak.tick(100);
        check(
                ak.magazine == 28 && ak.reserve == 90 && ak.reloadAt < 0,
                "switching cancels reload without free rounds");
        ak.deploy(100);
        check(!ak.fire(111) && ak.fire(112), "weapon draw delay enforced");
        ak.restock();
        check(
                ak.magazine == 30 && ak.reserve == 90 && !ak.scoped,
                "round restock resets transient gun state");
        CsGun nova = new CsGun(CsWeapon.NOVA);
        nova.magazine = 0;
        nova.reserve = 3;
        nova.reload(0);
        nova.tick(19);
        check(nova.magazine == 0, "shotgun first shell waits");
        nova.tick(20);
        check(nova.magazine == 1 && nova.reserve == 2, "shotgun reloads one shell");
        check(
                nova.fire(21) && nova.reloadAt < 0 && nova.magazine == 0,
                "loaded shell can interrupt shotgun reload");
    }

    private static void damageAndRewards() {
        CsMatch m = live();
        var ct = m.actor(CT);
        ct.armour = 100;
        ct.helmet = true;
        var hit = m.hurt(CT, T, 144, true, false, CsWeapon.AK47, false);
        check(
                hit.killed() && m.actor(T).kills == 1 && m.actor(T).money == 1100,
                "AK headshot kills through helmet and rewards once");
        check(
                m.hurt(CT, T, 144, true, false, CsWeapon.AK47, false).health() == 0
                        && ct.deaths == 1,
                "duplicate death cannot score");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        ct.helmet = true;
        check(
                !m.hurt(CT, T, 132, true, false, CsWeapon.M4A4, false).killed() && ct.health > 7,
                "M4 helmet headshot is survivable");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        check(
                m.hurt(CT, T, 140, true, false, CsWeapon.USP, false).killed(),
                "vest alone does not protect head");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        double armor = ct.armour;
        m.hurt(CT, T, 86.25, false, true, CsWeapon.AWP, false);
        check(ct.health > 13 && ct.armour == armor, "AWP leg hit survives and bypasses armor");
        m = live();
        m.hurt(CT, T, 45, false, true, CsWeapon.GLOCK, false);
        m.hurt(CT, T2, 100, false, true, CsWeapon.MP9, false);
        check(
                m.actor(T).assists == 1 && m.actor(T2).money == 1400,
                "40-damage assist and SMG kill bonus");
        m = live();
        m.hurt(T2, T, 100, false, true, CsWeapon.AK47, false);
        check(m.actor(T2).health == 67, "friendly bullets have reduced damage");
        m.hurt(T2, T, 1000, false, true, CsWeapon.AK47, false);
        check(
                m.actor(T).kills == -1 && m.actor(T).money == 500,
                "teamkill penalizes kills and money");
        m = live();
        m.hurt(CT, T, 180, false, false, null, false);
        check(m.actor(T).money == 2300, "knife reward is $1500");
        m = live();
        m.suicide(T);
        m.hurt(CT, T, 500, false, false, null, true);
        check(
                m.actor(T).kills == 1 && m.actor(CT).deaths == 1,
                "a grenade remains attributable after its thrower dies");
    }

    private static void objectives() {
        CsMatch m = live();
        UUID carrier = m.carrier;
        check(
                !m.plant(CT, 0, 3) && !m.plant(carrier, -1, 3),
                "only T carrier at a valid site can plant");
        check(m.dropBomb(carrier) && !m.dropBomb(carrier), "C4 drop is single-use");
        check(!m.takeBomb(CT) && m.takeBomb(T), "only alive T may recover dropped C4");
        check(
                m.plant(T, 1, 5) && m.carrier == null && m.detonateAt == 25,
                "plant consumes carrier and starts full fuse");
        check(!m.plant(T, 0, 6), "cannot plant twice");
        check(m.actor(T).money == 1100, "plant bonus credited once");
        m.hurt(T, CT, 1000, false, false, CsWeapon.AWP, false);
        m.hurt(T2, CT, 1000, false, false, CsWeapon.AWP, false);
        m.tick(6, RNG);
        check(m.phase == CsMatch.Phase.LIVE, "plant keeps round alive after all T die");
        check(
                !m.defuse(T, 7) && m.defuse(CT, 24),
                "only alive CT can finish defuse before deadline");
        check(
                m.lastRound.reason() == CsMatch.Reason.DEFUSE
                        && m.score[1] == 1
                        && m.actor(CT).mvps == 1,
                "defuse wins round and awards MVP");
        check(m.actor(T).money == 3800, "losing T receive loss bonus plus plant team bonus");
        int score = m.score[1];
        check(!m.defuse(CT2, 24) && m.score[1] == score, "defuse scores once");
        m = live();
        carrier = m.carrier;
        m.plant(carrier, 0, 5);
        check(!m.defuse(CT, 25), "defuse at detonation deadline is too late");
        m.tick(25, RNG);
        check(
                m.lastRound.reason() == CsMatch.Reason.EXPLOSION && m.actor(carrier).mvps == 1,
                "explosion wins and planter gets MVP");
        m = live();
        check(!m.plant(m.carrier, 0, 102), "plant completion at round deadline rejected");
        m.tick(102, RNG);
        check(
                m.lastRound.reason() == CsMatch.Reason.TIMEOUT && m.actor(T).money == 800,
                "alive T saving on timeout get no loss income");
    }

    private static void win(CsMatch m, int winner, long now) {
        UUID source = winner == 0 ? T : CT;
        for (var a : m.actors())
            if (a.team != winner && a.alive)
                m.hurt(a.id, source, 10000, false, false, CsWeapon.AK47, false);
        m.tick(now, RNG);
    }

    private static void rounds() {
        CsMatch m = live();
        var survivor = m.actor(T);
        survivor.primary = new CsGun(CsWeapon.AK47);
        survivor.primary.magazine = 4;
        survivor.armour = 42;
        survivor.health = 20;
        win(m, 0, 3);
        check(m.score[0] == 1 && m.actor(CT).money == 2700, "first loss pays $1900");
        check(
                m.losses[0] == 0 && m.losses[1] == 2,
                "loss counter decreases on win and increases on loss");
        m.tick(103, RNG);
        check(
                m.number == 2
                        && survivor.health == 100
                        && survivor.primary.magazine == 30
                        && survivor.armour == 42,
                "survivor keeps gear and armor, heals and restocks next round");
        check(
                m.actor(CT).primary == null
                        && m.actor(CT).armour == 0
                        && m.actor(CT).secondary.weapon == CsWeapon.USP,
                "dead player receives default pistol only");
        m.tick(105, RNG);
        win(m, 0, 106);
        check(m.actor(CT).money == 5100, "consecutive second loss pays $2400");
        m = live(1);
        m.actor(T).primary = new CsGun(CsWeapon.AK47);
        win(m, 0, 3);
        m.tick(103, RNG);
        check(m.phase == CsMatch.Phase.HALFTIME, "halftime occurs after configured half");
        m.tick(403, RNG);
        check(
                m.number == 2 && m.side(m.actor(T)) == 1 && m.side(m.actor(CT)) == 0,
                "stable teams exchange factions");
        check(
                m.actor(T).money == 800
                        && m.actor(T).primary == null
                        && m.actor(T).secondary.weapon == CsWeapon.USP,
                "halftime resets economy and equipment");
        m.tick(405, RNG);
        win(m, 0, 406);
        m.tick(506, RNG);
        check(m.phase == CsMatch.Phase.FINISHED && m.score[0] == 2, "first to half-plus-one wins");
        m = live(1);
        win(m, 0, 3);
        m.tick(103, RNG);
        m.tick(403, RNG);
        m.tick(405, RNG);
        win(m, 1, 406);
        m.tick(506, RNG);
        check(
                m.phase == CsMatch.Phase.FINISHED && m.score[0] == m.score[1],
                "full-length tied match ends as draw");
    }

    private static void lifecycle() {
        CsMatch m = live();
        UUID c = m.carrier;
        m.disconnect(c);
        check(!m.live(c) && m.actor(c).deaths == 1, "disconnect removes combatant once");
        m.disconnect(c);
        m.reconnect(c);
        check(!m.live(c) && m.actor(c).deaths == 1, "rejoin cannot respawn in same round");
        check(
                m.dropBomb(c) && m.takeBomb(c.equals(T) ? T2 : T),
                "disconnect permits recovery of dropped C4");
        check(m.pause(0) && !m.pause(0), "one queued timeout per team");
        win(m, 1, 3);
        m.tick(103, RNG);
        check(
                m.until == 705 && m.timeouts[0] == 3,
                "queued timeout extends next freeze by 30 seconds");
        check(m.live(c), "rejoined player returns next round");
        m.forfeit(1);
        check(m.phase == CsMatch.Phase.FINISHED && m.score[1] >= 16, "forfeit ends match");
        CsMatch solo = new CsMatch(Map.of(T, 0), 15, 2, 100, 20, 10, true);
        solo.start(0, RNG);
        solo.tick(2, RNG);
        solo.tick(3, RNG);
        check(
                solo.phase == CsMatch.Phase.LIVE && solo.actor(T).money == 16000,
                "practice does not end from missing opponents");
    }

    private static void importedMap() throws Exception {
        var bp = CsArena.Blueprint.load();
        check(
                bp.cells().size() > 190000 && bp.palette().size() > 700,
                "full imported architecture, not a generated substitute");
        Map<CsArena.Pos, String> blocks = new HashMap<>();
        for (var cell : bp.cells()) blocks.put(cell.pos(), bp.palette().get(cell.state()));
        int[][] spawn = {
            {0, 78, 50},
            {2, 78, 50},
            {4, 78, 50},
            {0, 78, 53},
            {4, 78, 53},
            {93, 75, 39},
            {96, 75, 39},
            {99, 75, 39},
            {93, 75, 41},
            {97, 75, 41}
        };
        for (int[] p : spawn) {
            check(
                    !blocks.containsKey(new CsArena.Pos(p[0], p[1], p[2]))
                            && !blocks.containsKey(new CsArena.Pos(p[0], p[1] + 1, p[2])),
                    "spawn has headroom");
            check(
                    blocks.containsKey(new CsArena.Pos(p[0], p[1] - 1, p[2])),
                    "spawn supported by original geometry");
        }
        check(
                blocks.get(new CsArena.Pos(103, 81, 88)).startsWith("minecraft:red_concrete"),
                "A marker matches imported raised platform");
        check(
                blocks.get(new CsArena.Pos(114, 78, 6)).startsWith("minecraft:blue_concrete"),
                "B marker matches imported platform");
        check(
                bp.palette().stream()
                        .noneMatch(
                                s -> s.contains("command_block") || s.contains("structure_block")),
                "no executable source world blocks");
        try (var in = CsCheck.class.getResourceAsStream("/maps/cs/radar.png")) {
            var image = ImageIO.read(Objects.requireNonNull(in));
            check(image.getWidth() == 128 && image.getHeight() == 128, "native map radar size");
        }
    }

    private static void smokeGeometry() {
        Location a = new Location(null, -10, 1, 0),
                b = new Location(null, 10, 1, 0),
                center = new Location(null, 0, 1, 0);
        check(CsGrenades.intersects(a, b, center, 3.7), "smoke between players obscures sight");
        check(
                !CsGrenades.intersects(a, b, new Location(null, 0, 10, 0), 3.7),
                "smoke on another level does not obscure");
        check(
                CsGrenades.intersects(center, center, center, 3.7),
                "inside smoke handles zero-length sightline");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
