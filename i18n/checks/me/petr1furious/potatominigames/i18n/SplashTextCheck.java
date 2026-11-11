package me.petr1furious.potatominigames.i18n;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Locale;

/** Editorial budgets for center-screen text, not pixel-perfect client layout guarantees. */
final class SplashTextCheck {
    private static final Component PLAYER = Component.text("W".repeat(16));
    private static final List<String> TITLES =
            List.of(
                    "chase.round",
                    "party.duels.arms.you_killed",
                    "party.duels.death.eliminated",
                    "party.duels.death.you_killed",
                    "party.duels.death.you_pushed",
                    "party.rounds.anvils.move",
                    "party.rounds.anvils.wave",
                    "party.rounds.build_memory.build",
                    "party.rounds.build_memory.memorize",
                    "party.rounds.go",
                    "party.rounds.hole_wall.edge",
                    "party.rounds.hole_wall.narrow",
                    "party.rounds.miners.finale",
                    "party.rounds.miners.open",
                    "party.rounds.mob_count.round",
                    "party.rounds.one_shot.you_killed",
                    "pillars.effects.ad.title",
                    "pillars.effects.ad.title_starred",
                    "pillars.effects.event.airdrop.title",
                    "pillars.effects.event.block_ban.title",
                    "pillars.effects.event.boats.title",
                    "pillars.effects.event.contract_ad.title",
                    "pillars.effects.event.darkness.title",
                    "pillars.effects.event.disco.title",
                    "pillars.effects.event.frenzy.title",
                    "pillars.effects.event.giants.title",
                    "pillars.effects.event.hangover.title",
                    "pillars.effects.event.hot_potato.title",
                    "pillars.effects.event.knockback.title",
                    "pillars.effects.event.low_gravity.title",
                    "pillars.effects.event.math.title",
                    "pillars.effects.event.slippery.title",
                    "pillars.effects.event.spotlight.title",
                    "pillars.effects.event.stiff_neck.title",
                    "pillars.effects.event.superhot.title",
                    "pillars.effects.event.vanishing.title",
                    "pillars.effects.event.wind.title",
                    "pillars.effects.math.question",
                    "pillars.play.adhd.title",
                    "pillars.play.title.draw",
                    "pillars.play.title.go",
                    "pillars.play.title.stopped",
                    "pillars.play.title.time_over",
                    "pillars.play.title.winner");
    private static final List<String> SUBTITLES =
            List.of(
                    "chase.hunter",
                    "chase.runner",
                    "party.duels.arms.next_stage",
                    "party.duels.death.pushed_by",
                    "party.duels.death.you_fell",
                    "party.rounds.anvils.hide",
                    "party.rounds.anvils.push",
                    "party.rounds.anvils.shelters_moved",
                    "party.rounds.hole_wall.edge_warning",
                    "party.rounds.hole_wall.narrow_warning",
                    "party.rounds.miners.finale.subtitle",
                    "party.rounds.miners.open.subtitle",
                    "party.rounds.mob_count.choose",
                    "party.rounds.mob_count.count",
                    "party.rounds.mob_count.wrong_disappear",
                    "party.rounds.mode.anvils",
                    "party.rounds.mode.arms_race",
                    "party.rounds.mode.build_memory",
                    "party.rounds.mode.color_floor",
                    "party.rounds.mode.hole_in_the_wall",
                    "party.rounds.mode.magic_battle",
                    "party.rounds.mode.miners",
                    "party.rounds.mode.mob_count",
                    "party.rounds.mode.one_shot",
                    "party.rounds.mode.parkour_race",
                    "party.rounds.mode.party_games",
                    "party.rounds.mode.pvp",
                    "party.rounds.mode.pvp_ffa",
                    "party.rounds.mode.rhino",
                    "party.rounds.mode.sumo",
                    "party.rounds.mode.sumo_ffa",
                    "party.rounds.mode.tnt_run",
                    "party.rounds.mode.tool_race",
                    "party.rounds.one_shot.kill_refill",
                    "pillars.effects.ad.subtitle",
                    "pillars.effects.event.airdrop.description",
                    "pillars.effects.event.block_ban.description",
                    "pillars.effects.event.boats.description",
                    "pillars.effects.event.contract_ad.description",
                    "pillars.effects.event.darkness.description",
                    "pillars.effects.event.disco.description",
                    "pillars.effects.event.frenzy.description",
                    "pillars.effects.event.giants.description",
                    "pillars.effects.event.hangover.description",
                    "pillars.effects.event.hot_potato.description",
                    "pillars.effects.event.knockback.description",
                    "pillars.effects.event.low_gravity.description",
                    "pillars.effects.event.math.description",
                    "pillars.effects.event.slippery.description",
                    "pillars.effects.event.spotlight.description",
                    "pillars.effects.event.stiff_neck.description",
                    "pillars.effects.event.superhot.description",
                    "pillars.effects.event.vanishing.description",
                    "pillars.effects.event.wind.description",
                    "pillars.effects.math.instructions",
                    "pillars.effects.wind.instructions",
                    "pillars.play.adhd.subtitle",
                    "pillars.play.title.draw_players",
                    "pillars.play.title.kills");

    static void run() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
            for (String key : TITLES) check(sample(key), locale, 24, key);
            for (String key : SUBTITLES) check(sample(key), locale, 48, key);
            for (String key : I18n.keys())
                if (key.startsWith("party.rounds.build_memory.model."))
                    check(I18n.tr(key), locale, 48, "Model subtitle: " + key);
            for (String key :
                    List.of("party.duels.arms.next_stage", "party.rounds.one_shot.kill_refill"))
                check(
                        PLAYER.append(Component.text(" • ")).append(I18n.tr(key)),
                        locale,
                        48,
                        key + " with victim");
            check(
                    PLAYER.append(Component.text(" • "))
                            .append(I18n.tr("pillars.play.title.kills", 999)),
                    locale,
                    48,
                    "Winner and kills");
            check(
                    I18n.tr("core.session.mode_name", I18n.tr("pillars.play.name"), "W".repeat(24)),
                    locale,
                    48,
                    "Longest custom Pillars preset name");
        }
    }

    private static Component sample(String key) {
        if (I18n.arguments(key) == 0) return I18n.tr(key);
        Object value =
                key.contains("you_killed") || key.endsWith(".winner") || key.endsWith(".pushed_by")
                        ? PLAYER
                        : key.endsWith("wind.instructions")
                                ? I18n.tr("pillars.effects.direction.north-west")
                                : key.endsWith("math.question") ? "20 + 20" : 300;
        return I18n.tr(key, value);
    }

    private static void check(Component component, Locale locale, int limit, String label) {
        String plain = I18n.plain(component, locale);
        int length = plain.codePointCount(0, plain.length());
        if (length > limit)
            throw new AssertionError(
                    label + " [" + locale + "]: " + length + "/" + limit + " — " + plain);
    }
}
